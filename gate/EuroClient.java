package eurogate.gate ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

import dmg.util.Args ;

public class EuroClient implements Runnable {
 
   private DataInputStream   _in     = null ;
   private DataOutputStream  _out    = null ;
   private Socket            _socket = null ;
   private Thread            _comThread = null ;
   private Hashtable         _requests  = new Hashtable() ;
   
   public EuroClient( String host , int port ) throws Exception {
       _socket = new Socket( host , port ) ;
       
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
            System.err.println( "From Mover : "+hello ) ;
            if( args.argc() < 2 )
               throw new Exception( "Invalid control on data stream" ) ;
               
            String           id = args.argv(1) ;
            RequestCompanion rc = (RequestCompanion)_requests.get(id) ;
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
               }finally{
                  try{ fileIn.close() ; }catch(Exception xe){}
               }
               args = new Args( cnt.readUTF() ) ;
               try{ _out.close() ; }catch(Exception xx ){}
               try{ _in.close() ; }catch( Exception yy ){}
               
            }
         }catch( Exception ee ){
            System.err.println( "Problem with mover connection : "+ee ) ;
            ee.printStackTrace() ;
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
                
                System.out.println( "<<< '"+info+"'" ) ;
                
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
            System.err.print( "invalid OK from server (noId) : " ) ;
            return ;
         }
         rc = (RequestCompanion)_requests.get( args.argv(3) ) ;
         if( rc == null ){
            System.err.print( "we were not waiting for this request : " ) ;
            return ;
         }
         rc.setServerId( args.argv(1) ) ;
         _requests.put( args.argv(1) , rc ) ;
         if( rc instanceof PutCompanion ){
             PutCompanion pc = (PutCompanion)rc ;
             pc.setBfid( args.argv(2) ) ;
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
         if( rc instanceof PutCompanion ){
            System.out.println( ((PutCompanion)rc).getBfid() ) ;
         }else{
            System.out.println( "Done" ) ;
         }
      }else{
         System.err.print( "Nok from server : " ) ;
         for( int i = 0 ; i < args.argc() ; i++ )
            System.err.print( args.argv(i)+" ") ;
         System.err.println("");
         return ;      
      }
   }
   ////////////////////////////////////////////////////////////////
   //
   //     the request companions
   //
   class RequestCompanion {
       private String _id    = null ;
       private String _store = null ;
       private String _host  = null ;
       private int    _port  = 0 ;
       private String _serverId = null ;
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
   }
   class PutCompanion extends RequestCompanion {
      private long   _size  = 0 ;
      private String _group = null ;
      private String _name  = null ;
      private InputStream _dataIn = null ;
      private String      _bfid   = null ;
      
      PutCompanion( String filename , 
                    String store , 
                    String group , 
                    long size      ) throws Exception {
         super( store ) ;
         _group = group ;
         _size  = size ;
         _name  = filename ;
       
         _dataIn = new FileInputStream( new File( _name ) ) ;
      }
      public String getGroup(){ return _group ; }
      public long   getSize(){ return _size ; }
      public String getFilename(){ return _name ; }
      public InputStream getInputStream(){ return _dataIn ; }
      public void   setBfid( String bfid ){ _bfid = bfid ; }
      public String getBfid(){ return _bfid ; }
   }
   public void put( String filename , 
                    String store , 
                    String group , 
                    long   size         ) throws Exception {
               
      
      RequestCompanion rc = new PutCompanion( filename , store , group , size ) ;
      StringBuffer r = new StringBuffer() ;
      r.append( "WRITEDATASET " ) ;
      r.append( store ).append( " " ) ;
      r.append( group ).append( " " ) ;
      r.append( "nomig" ).append( " " ) ;
      r.append( size ).append( " " ) ;
      r.append( rc.getHost() ).append(" ").append(rc.getPort()).append(" ??? ") ;
      r.append( rc.getId() ).append( " noparams" ) ;
      
      _requests.put( rc.getId() , rc ) ;
      _out.writeUTF( r.toString() ) ;
          
   
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
      EuroClient euro = new EuroClient( host , port ) ;
      File f = new File( "/etc/group" ) ;
      euro.put( "/etc/group" , "MAIN" , "raw" , f.length() ) ;
      euro.put( "/etc/passwd" , "MAIN" , "raw" , f.length() ) ;
   }
}
