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
   private String            _replyHost   = "localhost" ;
   private String            _version     = "0.2" ;
   public EuroSyncClient( String host , int port ) throws Exception {
       _socket = new Socket( host , port ) ;
       
//       System.setErr( new PrintStream( new FileOutputStream("/dev/null" ) ) ) ;
       _in  = new DataInputStream( _socket.getInputStream() ) ;
       _out = new DataOutputStream( _socket.getOutputStream() ) ;
       
       try{
          _out.writeUTF( "SESSION WELCOME "+_version ) ;
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
   public void setDebug( boolean debug ){ _debug = debug ; }
   public void setReplyHost( String replyHost ){ _replyHost = replyHost ; }
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
            if( rc == null ){
//               System.out.println( "Session id : "+id ) ;
//               Enumeration e = _requests.elements() ;
//               while( e.hasMoreElements() ){
//                  System.out.println( " -> "+e.nextElement() ) ;
//               }
              throw new
              Exception( "Unknown session id arrived : "+id ) ;
            }
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
               synchronized( _pendingLock ){
                  pc.moverOk = true ;
                  if( pc.bbackOk ){
                     _requests.remove( rc.getId() ) ;
                     _pending-- ;
                     _pendingLock.notifyAll() ;
                  }
               }
               
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
               try{
                   args = new Args( cnt.readUTF() ) ;
               }catch( Exception eee ){
                   if( _debug)System.err.println( 
                     "Problem in reading last UTF" ) ;
               }
               try{ _out.close() ; }catch(Exception xx ){}
               try{ _in.close() ; }catch( Exception yy ){}
               synchronized( _pendingLock ){
                  gc.moverOk = true ;
                  if( gc.bbackOk ){
                     _requests.remove( rc.getId() ) ;
                     _pending-- ;
                     _pendingLock.notifyAll() ;
                  }
               }
            }else if( rc instanceof ListCompanion ){
               ListCompanion lc   = (ListCompanion)rc ;
               String        out  = lc.getOutputFile() ;
               String        line = null ;
               PrintWriter   pw   = null ;
               if(_debug)System.err.println("Printout to : >"+out+"<" ) ;
               if( out.equals("") ){
                   try{
                     while( ( line = cnt.readUTF() ) != null )
                        System.out.println(line);
                   }catch( Exception ee){}
               }else{
     
                  try{
                     pw = new PrintWriter( new FileWriter( out ) ) ;
                     while( ( line = cnt.readUTF() ) != null ){
                        pw.println(line);
                     }
                     pw.flush() ;
                  }catch( Exception ee ){
                     if(_debug)System.err.println( "Problem in I/O : "+ee ) ;
                  }finally{                  
                     if( pw != null )try{ pw.close() ; }catch(Exception xe){}
                  }
               }
               try{ _out.close() ; }catch(Exception xx ){}
               try{ _in.close() ; }catch( Exception xx ){}
               lc.ioOk = true ;
               synchronized( _pendingLock ){
                  if( lc.msgOk ){
                     _requests.remove(id) ;
                     _pending-- ;
                     _pendingLock.notifyAll() ;
                  }
               }
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
         
      if( args.argc() < 2 ){
         if(_debug)System.err.println( "Zero answer from server" ) ;
         return ;
      }
      RequestCompanion rc = null ;
      if( args.argv(0).equals( "ok" ) ){
         rc = (RequestCompanion)_requests.get( args.argv(1) ) ;
         if( rc instanceof QueryCompanion ){
            QueryCompanion qc = (QueryCompanion)rc ;
            if( args.argc() < 3 ){
               if(_debug)System.err.println( "Incomplet answer from server" ) ;
               return ;
            }
            qc.setReturnCode( 0 , args.argv(2) ) ;
            _requests.remove( args.argv(1) );
         }else if( rc instanceof ListCompanion ){
            ListCompanion lc = (ListCompanion)rc ;
            if( args.argc() < 2 ){
               if(_debug)System.err.println( "Incomplet answer from server" ) ;
               return ;
            }
            if( args.argv(0).equals("ok") ){
                lc.setReturnCode( 0 , "" ) ;
                if( lc.ioOk )_requests.remove( args.argv(1) ) ;
                else return ;  // pending not decr.
            }else{
                int rcd = 33 ;
                if( args.argc() > 2 ){
                   try{
                      rcd = Integer.parseInt( args.argv(2) ) ;
                   }catch(Exception e){}
                }
                String msg = "Error="+rcd ;
                if( args.argc() > 3 )msg = args.argv(3) ;
                lc.setReturnCode( rcd , msg ) ;
            }
         }else{
            rc.setReturnCode( 99 , "PANIC : Unknown companion found" ) ;
            _requests.remove( args.argv(1) ) ;
         }
         synchronized( _pendingLock ){
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
         return ;            
      }else if( args.argv(0).equals( "failed" ) ){
         rc = (RequestCompanion)_requests.remove( args.argv(1) ) ;
         if( args.argc() < 4 ){
            if(_debug)System.err.println( "Incomplet negative answer from server" ) ;
            return ;
         }
         rc.setReturnCode( Integer.parseInt(args.argv(2)) , args.argv(3) ) ;
         synchronized( _pendingLock ){
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
         return ;            
      }else if( args.argv(0).equals( "OK" ) ){
      
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
         synchronized( _pendingLock ){
            rc = (RequestCompanion)_requests.get( rc.getId() ) ;
            if( rc  == null ){
               System.err.println( "client id not found : "+rc.getId() ) ;
               return ;
            }
            boolean moverOk = false ;
            if( rc instanceof PutCompanion ){
                PutCompanion pc = (PutCompanion)rc ;
                pc.bbackOk = true ;
                moverOk    = pc.moverOk ;
            }else if( rc instanceof GetCompanion ){
                GetCompanion gc = (GetCompanion)rc ;
                gc.bbackOk = true ;
                moverOk    = gc.moverOk ;
            }
            rc.setReturnCode( Integer.parseInt(rcode) , rmsg );
            if( moverOk ){
               _requests.remove( rc.getId() ) ;
               _pending-- ;
               _pendingLock.notifyAll() ;
            }
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
      }else if( args.argv(0).equals( "CANCEL" )){
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
         synchronized( _pendingLock ){
            _pending-- ;
            _pendingLock.notifyAll() ;
         }
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
          _host  = _replyHost ;
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
      private boolean      bbackOk  = false ;
      private boolean      moverOk  = false ;
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
   class ListCompanion extends RequestCompanion {
   
      private String  _command        = null ;
      private String  _outputFileName = null ;
      private boolean ioOk           = false ;
      private boolean msgOk          = false ;
      ListCompanion( String command ,
                     String store ,
                     String outputFileName  ) throws Exception {
         super( store ) ;
         _command        = command ;
         _outputFileName = outputFileName ;
      }
      public String toString(){ 
              return "[queryList="+_command+
                     ";file="+_outputFileName+
                     ";"+super.toString()+"]" ;
      }
      public String getBfid(){ return null ; }
      public long   getSize(){ return -1 ; }
      public String getOutputFile(){ return _outputFileName ; }
      public String getQuery(){ return _command ; }
   
   }
   class QueryCompanion extends RequestCompanion {
   
      private String  _bfid    = null ;
      private String  _query   = null ;
       
      QueryCompanion(  String query ,
                       String store , 
                       String bfid    ) throws Exception {
         super( store ) ;
         _bfid  = bfid ;
         _query = query;
      }
      public String toString(){ 
              return "[query="+_query+";bfid="+_bfid+";"+super.toString()+"]" ;
      }
      public String getBfid(){ return _bfid ; }
      public long   getSize(){ return -1 ; }
      public String getQuery(){ return _query ; } 
   
   }
   class PutCompanion extends RequestCompanion {
      private long        _size   = 0 ;
      private String      _group  = null ;
      private File        _file   = null ;
      private InputStream _dataIn = null ;
      private String      _bfid   = null ;
      private boolean      bbackOk  = false ;
      private boolean      moverOk  = false ;
      
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
          listVolume( String store , String volume , String outputFile ) 
          throws Exception {
               
      
      RequestCompanion rc = 
              new ListCompanion( "list-volume" , store , outputFile  ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "list volume " ).append( rc.getId() ).append(" ") ;
      r.append( store ).append( " " ) ;
      r.append( volume ).append( " " )  ;
      r.append( rc.getHost() ).append(" ").append(rc.getPort()) ;
      
      return sendAndWait( rc , r.toString() ) ;  
   
   }
   public EuroCompanion
          getBfid( String store , String bfid ) 
          throws Exception {
               
      
      RequestCompanion rc = new QueryCompanion( "get-bfid" , store , bfid ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "get bfid " ).append( rc.getId() ).append(" ") ;
      r.append( store ).append( " " ).append( bfid ) ;
      
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
   public static Hashtable readSetup( String setupFile ){
      Hashtable      hash = new Hashtable() ;
      BufferedReader br   = null ;
      try{
         br = new BufferedReader( new FileReader( setupFile ) ) ;
         String line = null ;                
         while( ( line = br.readLine() ) != null ){
            if( ( line.length() < 4 ) ||
                ( ! line.startsWith("eg-") ) )continue ;
            StringTokenizer st = new StringTokenizer( line , "=" ) ;
            String key   = st.nextToken().substring(3) ;
            String value = "" ;
            if( st.hasMoreTokens() ){ value = st.nextToken() ; }
//            System.out.println( "key="+key+";value="+value);
            hash.put( key , value ) ;
         }
       
      }catch(Exception e){
      
      }finally{
         if( br != null )try{ br.close() ; }catch(Exception ee){}
      }
      return hash ;
   }
   public static void main( String [] argsString ) throws Exception {
      String  host   = "localhost" ;
      int     port   = 28000 ;
      String  store  = "MAIN" ;
      boolean debug  = false ;
      int     ret    = 0 ;
      EuroSyncClient euro  = null ;
      Hashtable      setup = null ;
      try{
         Args args = new Args( argsString ) ;
         if( args.argc() < 1 )throw new IllegalArgumentException("") ;
         
         String command   = args.argv(0) ;
         String opt       = null ;
         String replyHost = "localhost" ;
         //
         // debug flag only taken from 'command line'
         //
         if( ( opt = args.getOpt("debug") ) != null )debug = true ;
         //
         // try to get the local hostname
         //
         try{
            replyHost = InetAddress.getLocalHost().getHostName() ;
         }catch( Exception ee ){}
         //
         // now, set the options by scanning the setuptfile
         //
         String setupFile = "/etc/egSetup" ;
         if( ( opt = args.getOpt("setup") ) != null )setupFile = opt ;
         setup = readSetup( setupFile ) ;

         if( ( opt = (String)setup.get("host")  ) != null )host = opt ;
         if( ( opt = (String)setup.get("store") ) != null )store = opt ;
         if( ( opt = (String)setup.get("reply") ) != null )replyHost = opt ;
         if( ( opt = (String)setup.get("port")  ) != null ){
            try{ port = Integer.parseInt( opt ) ;
            }catch(Exception x){ }
         }
         
         if( ( opt = args.getOpt("host")  ) != null )host = opt ;
         if( ( opt = args.getOpt("store") ) != null )store = opt ;
         if( ( opt = args.getOpt("reply") ) != null )replyHost = opt ;
         if( ( opt = args.getOpt("port")  ) != null ){
            try{
               port = Integer.parseInt( opt ) ;
            }catch(Exception x){ 
                throw new 
                IllegalArgumentException("Port must be decimal");
            }
         }
         if( debug )System.err.println( "Using Hostname : "+replyHost ) ;
         args.shift() ;
         
         euro = new EuroSyncClient( host , port ) ;
         euro.setDebug( debug ) ;
         euro.setReplyHost( replyHost ) ;
         if( command.equals( "get-bf" ) ){
            //
            // get-bfid <bfid>
            //
            if( args.argc() < 1 )
               throw new 
               IllegalArgumentException("get-bfid : not enough arguments");
               
            String bfid   = args.argv(0) ;
            
            EuroCompanion com = euro.getBfid( store , bfid ) ;

            if( ( ret = com.getReturnCode() ) == 0 ){
               System.out.println( com.getReturnMessage() ) ;
            }else{ 
               System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
            }              
         }else if( command.equals( "list-volume" ) ){
            //
            // list-volume <volume>
            //
            if( args.argc() < 1 )
               throw new 
               IllegalArgumentException("list-volume : not enough arguments");
               
            String volume   = args.argv(0) ;
            String outputFile = args.getOpt("output") ;
            if( outputFile == null )outputFile = "" ;
            EuroCompanion com = euro.listVolume( store , volume , outputFile ) ;

            if( ( ret = com.getReturnCode() ) == 0 ){
//               System.out.println( com.getReturnMessage() ) ;
            }else{ 
               System.err.println( "Failed X "+ret+" "+com.getReturnMessage() ) ;
            }              
         }else if( command.equals( "write" ) ){
            //
            // write <filename> <storageGroup> [<parameter>]
            //
            if( args.argc() < 2 )
               throw new 
               IllegalArgumentException("write : not enough arguments");
               
            String filename   = args.argv(0) ;
            String storegroup = args.argv(1) ;
            String param      = args.argc() > 2 ? args.argv(2) : null ;
            
            EuroCompanion com = euro.put( new File( filename ) , 
                                          store , 
                                          storegroup , 
                                          param ) ;

            if( ( ret = com.getReturnCode() ) == 0 ){
               System.out.println( com.getBfid() ) ;
            }else{ 
               System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
            }              
         }else if( command.equals( "read" ) ){
            //
            // read <bfid> <filename>
            //
            if( args.argc() < 2 )
               throw new 
               IllegalArgumentException("read : not enough arguments");
               
            String bfid       = args.argv(0) ;
            String filename   = args.argv(1) ;

            EuroCompanion com = euro.get( new File( filename ) ,
                                          store ,
                                          bfid      ) ;
                                          
            if( ( ret = com.getReturnCode() ) != 0 ){
               System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
            }              

         }else if( command.equals( "rm" ) || command.equals("remove") ){
            //
            // remove <bfid>
            //
            String bfid       = args.argv(0) ;
            EuroCompanion com = euro.remove( store , bfid )  ;
             if( ( ret = com.getReturnCode() ) != 0 ){
                System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
             }              
         }else
            throw new 
            IllegalArgumentException( "Illegal command : "+command ) ;
      }catch( IllegalArgumentException nsee ){
          if( ! nsee.getMessage().equals("") )
            System.err.println( "Problem : "+nsee.getMessage() ) ;
          System.err.println( "Usage : ... [options] request" ) ;
          System.err.println( "  request :" ) ;
          System.err.println( "    write <filename> <storageGroup> [<key>]" ) ;
          System.err.println( "    read  <bfid>     <filename>" ) ;
          System.err.println( "    remove  <bfid>" ) ;
          System.err.println( "    get-bf  <bfid>" ) ;
          System.err.println( "    list-volume  <volume>" ) ;
          System.err.println( "  options :" ) ;
          System.err.println( "    -host=<hostname> -port=<portNumber> -debug" ) ;
          System.err.println( "    -reply=<replyHostName>" ) ;
          System.exit(3);
      }catch(Exception exx ){
          System.err.println( exx.toString() ) ;
          ret = 5 ;
          if( debug )exx.printStackTrace(System.err) ;
      }finally{
          if(euro!=null)try{ euro.close() ; }catch(Exception xxx ){}
      }
      System.exit(ret);
      
   }
}
