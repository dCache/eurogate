package eurogate.gate ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

import dmg.util.Args ;

public class EuroSyncClient implements Runnable {
 
   private DataInputStream   _in     = null ;
   private DataOutputStream  _out    = null ;
   private Socket            _socket = null ;
   private Thread            _comThread   = null ;
   private Hashtable         _requests    = new Hashtable() ;
   private Object            _pendingLock = new Object() ;
   private int               _pending     = 0 ;
   private boolean           _debug       = false ;
   
   public EuroSyncClient( String host , int port ) throws Exception {
       _socket = new Socket( host , port ) ;
       
//       System.setErr( new PrintStream( new FileOutputStream("/dev/null" ) ) ) ;
       _in  = new DataInputStream( _socket.getInputStream() ) ;
       _out = new DataOutputStream( _socket.getOutputStream() ) ;
       
       try{
          _out.writeUTF( "SESSION WELCOME 0.1" ) ;
          String reply = _in.readUTF() ;
          if( ! reply.startsWith( "OK" ) )
            throw new IOException( reply ) ;
       }catch( IOException ioe ){
          try{ _socket.close() ; }catch(Exception ci){}
          throw ioe ;
       }
       _comThread = new Thread( this ) ;
       _comThread.start() ;
   }
   private int _counter = 100 ;
   private synchronized String getNextRequestId(){
       return "client-"+(_counter++) ;
   }
   ///////////////////////////////////////////////////////////
   //
   //   the io controller
   //
   private class DataController implements Runnable  {
      private InputStream  _in  = null ;
      private OutputStream _out = null ;
      
      public DataController( Socket socket ) throws Exception {
           _in  = socket.getInputStream() ;
           _out = socket.getOutputStream() ;
           Thread t = new Thread(this) ;
           t.start() ;   
      }
      public void run(){
         try{
            DataInputStream cnt   = new DataInputStream(_in) ;
            String          hello = cnt.readUTF() ;
            Args            args  = new Args( hello ) ;
            if(_debug)System.err.println( "From Mover : "+hello ) ;
            if( args.argc() < 2 )
               throw new Exception( "Invalid control on data stream" ) ;
               
            String           id = args.argv(1) ;
            RequestCompanion rc = (RequestCompanion)_requests.get(id) ;
            if( rc == null )
              throw new
              Exception( "Unknown session id arrived : "+id ) ;
            if( rc instanceof PutCompanion ){
               PutCompanion pc     = (PutCompanion)rc ;
               long         size   = pc.getSize() ;
               byte []      data   = new byte[1024] ;
               InputStream  fileIn = pc.getInputStream() ;
               int          count  = 0 ;
               try{
                   while(true){
                       count = fileIn.read( data ) ;
                       if( count <= 0 )break ;
                       _out.write( data , 0 , count ) ;               
                   }
               }catch( Exception ee ){
                  if(_debug)System.err.println( "Problem in I/O : "+ee ) ;
               }finally{
                  try{ fileIn.close() ; }catch(Exception xe){}
               }
               args = new Args( cnt.readUTF() ) ;
               try{ _out.close() ; }catch(Exception xx ){}
               try{ _in.close() ; }catch( Exception yy ){}
               
            }else if( rc instanceof GetCompanion ){
               GetCompanion gc = (GetCompanion)rc ;
               long         size = gc.getSize() ;
               byte []      data = new byte[1024] ;
               OutputStream fileOut = gc.getOutputStream() ;
               int now , count ;
               long rest = size ;
               try{
                  while( rest > 0 ){

                     now = (int)(  rest > (long)data.length ? data.length : rest ) ;
                     count = _in.read( data , 0 , now ) ; 
                     if( count <= 0 )break ;
                     fileOut.write( data , 0 , count ) ;
                     rest -= count ;
                  }
               }catch( Exception ee ){
                  if(_debug)System.err.println( "Problem in I/O : "+ee ) ;
               }finally{
                  try{ fileOut.close() ; }catch(Exception xe){}
               }
               args = new Args( cnt.readUTF() ) ;
               try{ _out.close() ; }catch(Exception xx ){}
               try{ _in.close() ; }catch( Exception yy ){}
            }
         }catch( Exception ee ){
            if(_debug)System.err.println( "Problem with mover connection : "+ee ) ;
            if(_debug)ee.printStackTrace(System.err) ;
         }
      
      }
   
   }
   //
   //////////////////////////////////////////////////////////////////
   public void run(){
      if( Thread.currentThread() == _dataListenThread ){
         while(true){
            try{
                Socket data = _dataListenerSocket.accept() ;
                new DataController( data ) ;
            }catch(Exception se ){
                System.err.println( "Problem in accept : "+se ) ;
            }
         }
      }else if( Thread.currentThread() == _comThread ){
         
          try{
             while( true ){
                String info = _in.readUTF() ;
                
                if(_debug)System.err.println( "<<< '"+info+"'" ) ;
                
                processServerReply( new Args( info ) ) ;
                
                
             }
          
          }catch( IOException ioe ){
             System.err.println( "Exception while readingUTF : "+ioe ) ;
          }
      
      }
   }
   private synchronized void processServerReply( Args args ){
         
      if( args.argc() == 0 ){
         System.err.println( "Zero answer from server" ) ;
         return ;
      }
      RequestCompanion rc = null ;
      if( args.argv(0).equals( "OK" ) ){
      
         if( args.argc() < 4 ){
            //
            // REMOVEX request 
            //
            if( args.argc() != 2 ){
               System.err.println( "invalid OK from server (noId)" ) ;
               return ;
            }
            rc = (RequestCompanion)_requests.remove( args.argv(1) ) ;
            if( rc == null ){
               System.err.println( "we were not waiting for this request : "+args.argv(1) ) ;
               return ;
            }
            synchronized( _pendingLock ){
               rc.setReturnCode( 0 , "o.k." );
               _pending-- ;
               _pendingLock.notifyAll() ;
            }            
            return ;
         }
         
         
         rc = (RequestCompanion)_requests.get( args.argv(3) ) ;
         
         if( rc == null ){
            System.err.println( "we were not waiting for this request : "+args.argv(3) ) ;
            return ;
         }
         rc.setServerId( args.argv(1) ) ;
         _requests.put( args.argv(1) , rc ) ;
         if( rc instanceof PutCompanion ){
             PutCompanion pc = (PutCompanion)rc ;
             pc.setBfid( args.argv(2) ) ;
         }else if( rc instanceof GetCompanion ){
             GetCompanion gc = (GetCompanion)rc ;
             gc.setSize( Long.parseLong( args.argv(2) ) ) ;
         }
      }else if( args.argv(0).equals( "BBACK" ) ){
         String serverId = args.argv(1) ;
         String bfid     = args.argv(2) ;
         String rcode    = args.argv(3) ;
         String rmsg     = args.argv(4) ;
         rc = (RequestCompanion)_requests.remove( serverId ) ;
         if( rc == null ){
            System.err.println( "server id not found : "+serverId ) ;
            return ;
         }
         if( _requests.remove( rc.getId() ) == null ){
            System.err.println( "client id not found : "+rc.getId() ) ;
            return ;
         }
         synchronized( _pendingLock ){
            rc.setReturnCode( Integer.parseInt(rcode) , rmsg );
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
      }else if( args.argv(0).equals( "NOK" ) ){
         Enumeration e = _requests.elements() ;
         if( ! e.hasMoreElements() ){
            System.err.println( "NOK arrived for non request" ) ;
            System.exit(66);
         }
         rc = (RequestCompanion)e.nextElement() ;
         rc.setReturnCode( Integer.parseInt(args.argv(1)) , args.argv(2) );
         synchronized( _pendingLock ){
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
         return ;
      }else if( args.argv(0).equals( "CANCEL" ) ){
         Enumeration e = _requests.elements() ;
         if( ! e.hasMoreElements() ){
            System.err.println( "CANCEL arrived for non request" ) ;
            System.exit(66);
         }
         rc = (RequestCompanion)e.nextElement() ;
         rc.setReturnCode( Integer.parseInt(args.argv(2)) , args.argv(3) );
         synchronized( _pendingLock ){
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
         return ;
      }else{
         System.err.print( "Nok from server : " ) ;
         for( int i = 0 ; i < args.argc() ; i++ )
            System.err.print( args.argv(i)+" ") ;
         System.err.println("");
         return ;      
      }
   }
   public void close() throws InterruptedException { sync() ; }
   public void sync() throws InterruptedException {
      while( true ){
         
          synchronized( _pendingLock ){
              if( _pending <= 0 )return ;
              _pendingLock.wait() ;
          }
         
      
      }
   }
   ////////////////////////////////////////////////////////////////
   //
   //     the request companions
   //
   abstract class RequestCompanion implements EuroCompanion {
       private String _id    = null ;
       private String _store = null ;
       private String _host  = null ;
       private int    _port  = 0 ;
       private String  _serverId = null ;
       private boolean _finished = false ;
       private int     _returnCode = -1 ;
       private String  _returnMsg  = "inProgress" ;
       RequestCompanion( String store ) throws Exception {
          _store = store ;
          _id    = getNextRequestId() ;
          _port  = startDataListener() ;
          _host  = "localhost" ;
       }
       public void   setServerId( String serverId ){ _serverId = serverId ; }
       public String getServerId(){ return _serverId ; }
       public int    getPort(){ return _port ; }
       public String getStore(){ return _store ; }
       public String getId(){ return _id ;}
       public String getHost(){ return _host ; }
       public void   setReturnCode( int rc , String rMsg ){
          _returnCode = rc ;
          _returnMsg  = rMsg ;
          _finished   = true ;
       }
       //
       // Eurocomanion interface
       //
       public boolean isFinished(){ return _finished ; }
       public int     getReturnCode(){ return _returnCode ; }
       public String  getReturnMessage(){ return _returnMsg ; }
       public String  toString(){ return "RC("+_returnCode+";"+_returnMsg+")" ; }
   }
   class GetCompanion extends RequestCompanion {
   
      private File         _file    = null ;
      private OutputStream _dataOut = null ;
      private String       _bfid    = null ;
      private long         _size    = -1 ;
      public String       getFilename(){ return _file.toString() ; }
      public OutputStream getOutputStream(){ return _dataOut ; }
      public String       getBfid(){ return _bfid ; }
      public void         setSize( long size ){ _size = size ; }
      public long         getSize(){ return _size ; }
      GetCompanion( File   file  , 
                    String store , 
                    String bfid    ) throws Exception {
         super( store ) ;
         if( file.exists() && ! file.toString().equals("/dev/null") )
            throw new 
            IllegalArgumentException( "File exists : "+file);
         _bfid  = bfid ;
         _file  = file  ;
       
         _dataOut = new FileOutputStream( _file ) ;
      }
      public String toString(){ 
              return "[get,size="+_size+";"+super.toString()+"]" ;};
   
   }
   class RemoveCompanion extends RequestCompanion {
   
      private String       _bfid    = null ;
      
      RemoveCompanion( String store , 
                       String bfid    ) throws Exception {
         super( store ) ;
         _bfid  = bfid ;
      }
      public String toString(){ 
              return "[remove,bfid="+_bfid+";"+super.toString()+"]" ;
      }
      public String getBfid(){ return _bfid ; }
      public long   getSize(){ return -1 ; }
   
   }
   class PutCompanion extends RequestCompanion {
      private long        _size   = 0 ;
      private String      _group  = null ;
      private File        _file   = null ;
      private InputStream _dataIn = null ;
      private String      _bfid   = null ;
      
      PutCompanion( File   file  , 
                    String store , 
                    String group    ) throws Exception {
         super( store ) ;
         if( ! file.exists() )
            throw new 
            IllegalArgumentException( "File not found : "+file);
         _group = group ;
         _size  = file.length() ;
         _file  = file  ;
       
         _dataIn = new FileInputStream( _file ) ;
      }
      public String getGroup(){ return _group ; }
      public long   getSize(){ return _size ; }
      public String getFilename(){ return _file.toString() ; }
      public InputStream getInputStream(){ return _dataIn ; }
      public void   setBfid( String bfid ){ _bfid = bfid ; }
      public String getBfid(){ return _bfid ; }
      public String toString(){ return "[put,bfid="+_bfid+";"+super.toString()+"]" ;};
   }
   public EuroCompanion
          remove( String store , String bfid ) 
          throws Exception {
               
      
      RequestCompanion rc = new RemoveCompanion( store , bfid ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "REMOVEDATASETX " ) ;
      r.append( store ).append( " " ).
        append( bfid ).append(" ").append( rc.getId() ) ;
      
      return sendAndWait( rc , r.toString() ) ;  
   
   }
   public EuroCompanion
          put( File   file ,  String store , String group , String param ) 
          throws Exception {
               
      
      RequestCompanion rc = new PutCompanion( file , store , group ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "WRITEDATASET " ) ;
      r.append( store ).append( " " ) ;
      r.append( group ).append( " " ) ;
      r.append( "nomig" ).append( " " ) ;
      r.append( file.length() ).append( " " ) ;
      r.append( rc.getHost() ).append(" ").append(rc.getPort()).append(" ??? ") ;
      r.append( rc.getId() ).append(" \"").
        append( param==null?"noparams":param ).append("\"") ;
//      System.out.println( "request : "+r.toString() ) ;
      return sendAndWait( rc , r.toString() ) ;  
   
   }
   public EuroCompanion
          get( File   file ,  String store , String bfid ) 
          throws Exception {
               
      
      RequestCompanion rc = new GetCompanion( file , store , bfid ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "READDATASET " ) ;
      r.append( store ).append( " " ) ;
      r.append( bfid ).append( " " ) ;
      r.append( rc.getHost() ).append(" ").append(rc.getPort()).append(" ??? ") ;
      r.append( rc.getId() ).append( " noparams" ) ;
      
      return sendAndWait( rc , r.toString() ) ;  
   
   }
   private EuroCompanion 
           sendAndWait( RequestCompanion rc , String request )
           throws Exception {
      synchronized( _pendingLock ){
         _requests.put( rc.getId() , rc ) ;
         _out.writeUTF( request ) ;
         _pending++ ;
          try{
             while(true){
                if( _pending <= 0 )return rc ;
                _pendingLock.wait() ;
             }
          }catch( InterruptedException ie ){
             if(_debug)System.err.println( "Sync wait has been interrupted" ) ;
          }
      }
      return rc ;  
   
   }
   private int          _dataListenPort     = 0 ;
   private ServerSocket _dataListenerSocket = null ;
   private Thread       _dataListenThread   = null ;
   private int startDataListener() throws Exception  {
      if( _dataListenerSocket == null ){
         _dataListenerSocket = new ServerSocket(0) ;
         _dataListenPort     = _dataListenerSocket.getLocalPort() ;
         _dataListenThread   = new Thread(this);
         _dataListenThread.start() ;
      }
      return _dataListenPort ;
   }
    public static void main( String [] args ) throws Exception {
      String host = "localhost" ;
      int    port = 28000 ;
      
      Vector r = new Vector() ;
      try{
         if( args.length == 0 )
            throw new NoSuchElementException("");
         
         Vector v = new Vector() ;
         for( int i= 0 ; i < args.length ; i++ )v.addElement(args[i]) ;
         Enumeration e = v.elements() ;
            String command = (String)e.nextElement() ;
            if( command.equals( "write" ) ){
               String filename   = (String)e.nextElement() ;
               String storegroup = (String)e.nextElement() ;
               String param      = null ;
               try{
                  param = (String)e.nextElement() ;
               }catch(Exception ee){}
               r.addElement( 
                   new EuroContainer( "put" , 
                                      filename , 
                                      storegroup , 
                                      param) ) ;         
            }else if( command.equals( "read" ) ){
               String bfid       = (String)e.nextElement() ;
               String filename   = (String)e.nextElement() ;
               r.addElement( new EuroContainer( "get" , bfid , filename ) ) ;         
            }else if( command.equals( "rm" ) || command.equals("remove") ){
               String bfid       = (String)e.nextElement() ;
               r.addElement( new EuroContainer( "rm" , bfid , null ) ) ;         
            }else
               throw new 
               NoSuchElementException( "Illegal command : "+command ) ;
      }catch( NoSuchElementException nsee ){
          System.err.println( "Usage : ... req" ) ;
          System.err.println( "  req :  write <filename> <storageGroup> [<key>]" ) ;
          System.err.println( "  req :  read  <bfid>     <filename>" ) ;
          System.err.println( "  req :  rm|remove  <bfid>" ) ;
          System.exit(3);
      }
      EuroSyncClient euro = null ;
      int ret = 0 ;
      try{
         euro = new EuroSyncClient( host , port ) ;
         Enumeration e = r.elements();
         EuroContainer ec = (EuroContainer)e.nextElement() ;

         if( ec.getDirection().equals( "put" ) ){
             EuroCompanion com =   
                 euro.put( new File(ec.getFilename()) , 
                           "MAIN" , 
                           ec.getGroup() ,
                           ec.getParam()   ) ; 
                     
             if( ( ret = com.getReturnCode() ) == 0 ){
                System.out.println( com.getBfid() ) ;
             }else{ 
                System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
             }              
         }else if( ec.getDirection().equals( "get" ) ){
             EuroCompanion com =   
                  euro.get( new File(ec.getFilename()) , 
                            "MAIN" , 
                            ec.getBfid() )  ;
             if( ( ret = com.getReturnCode() ) != 0 ){
                System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
             }              
         }else if( ec.getDirection().equals( "rm" ) ){
             EuroCompanion com =   
                  euro.remove( "MAIN" , ec.getBfid() )  ;
             if( ( ret = com.getReturnCode() ) != 0 ){
                System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
             }              
         }
         
       
      }catch( Exception exx ){
          System.err.println( exx.getMessage() ) ;
          ret = 5 ;
//         exx.printStackTrace(System.err) ;
      }finally{
         try{ euro.close() ; }catch(Exception xxx ){}
      }
      System.exit(ret);
   }
}
