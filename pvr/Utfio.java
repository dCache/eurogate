package  eurogate.pvr ;
import java.net.* ;
import java.io.* ;
import java.util.* ;

public class Utfio implements Runnable {

   private ServerSocket     _socket         = null ;
   private Socket           _echoSocket     = null ;
   private int              _port           = 0 ;
   private Thread           _listenThread   = null ;
   private Thread           _terminalThread = null ;
   private Thread           _tickerThread   = null ;
   private Thread           _echoThread     = null ;
   private Hashtable        _threadHash     = new Hashtable() ;
   private DataOutputStream _out            = null ;
   private Object           _outLock        = new Object()  ;
   private boolean          _echo           = false ;
   
   public Utfio( String host , int port ) throws IOException {
      _echo       = true ;
      _echoSocket = new Socket( host , port ) ;
      _echoThread = new Thread( this ) ;
      _echoThread.start() ;

//         _tickerThread = new Thread( this ) ;
//         _tickerThread.start() ;
   }
   public Utfio( String host , int port , boolean io ) throws IOException {
      _echo       = false ;
      _echoSocket = new Socket( host , port ) ;

      System.out.println( "Starting net listener thread" ) ;
      Thread ioThread = new Thread( this ) ;
      _threadHash.put( ioThread , _echoSocket ) ;
      ioThread.start() ;
      
      System.out.println( "Starting terminal listener thread" ) ;
      _terminalThread = new Thread( this ) ;
      _terminalThread.start() ;
      
//         _tickerThread = new Thread( this ) ;
//         _tickerThread.start() ;
   }
   public Utfio( int port ) throws IOException {
      _echo   = false ;
      _socket = new ServerSocket(port) ;
      _port   = _socket.getLocalPort() ;
      System.out.println( "Local Port Number : "+_port ) ;

      System.out.println( "Starting net listener thread" ) ;
      _listenThread = new Thread( this ) ;
      _listenThread.start() ;

      System.out.println( "Starting terminal listener thread" ) ;
      _terminalThread = new Thread( this ) ;
      _terminalThread.start() ;
         
//         _tickerThread = new Thread( this ) ;
//         _tickerThread.start() ;
         
   }
   public void run(){
      Thread myself = Thread.currentThread() ;
      Socket socket = null ;
      
      if( myself == _terminalThread ){
          
          terminalListener() ;
      
      }else if( myself == _tickerThread ){
      
          runTicker() ;
          
      }else if( myself == _echoThread ){
      
          runEcho() ;
          
      }else if( myself == _listenThread ){
          
          runListener() ;
      
      }else if( ( socket = (Socket)_threadHash.remove( myself ) ) != null ){
      
          runIo( socket ) ;
      }
   
   }
   private void runEcho(){
       DataInputStream   in  = null ;
       DataOutputStream  out = null ;
       try{
          in  = new DataInputStream( _echoSocket.getInputStream() ) ;
          out = new DataOutputStream( _echoSocket.getOutputStream() ) ;
       }catch( IOException ioe ){
          System.out.println( "Problem in opening io streams : "+ioe) ;
          try{ in.close() ; }catch(Exception e){}
          try{ out.close() ; }catch(Exception e){}
          return ;
       }
       synchronized( _outLock ){ 
          if( _out != null ){
             try{ _out.close() ; }catch( Exception ee ){}
          }
          _out = out ; 
       }
       String line = null ;
       try{
          while( ( line = in.readUTF() ) != null ){
             System.out.println( "Echo : "+line) ;
             out.writeUTF( line ) ;
          }
       }catch( EOFException eofe ){
          System.out.println( "Remote node closed connection" ) ;
       }catch( IOException ioe ){
          System.err.println( "Problem in readUTF() : "+ioe ) ;
       }

       try{ in.close() ; }catch(Exception e){}
       try{ out.close() ; }catch(Exception e){}
      
   }
   private void runListener(){
       Socket socket = null ;
       while(true){
          try{
             socket = _socket.accept() ;
          }catch( IOException ioe ){
             System.out.println( "IoException : "+ioe ) ;
             continue ;
          }
          System.out.println( "Connection accepted from : "+socket ) ;
          Thread thread = new Thread( this ) ;
          _threadHash.put( thread , socket ) ;
          thread.start() ;       
       }
   }
   private void terminalListener(){
      BufferedReader reader = new BufferedReader( 
                              new InputStreamReader( System.in ) ) ;
      String line = null ;
      try{
         while( ( line = reader.readLine() ) != null ){
//            System.out.println( "Terminal : >"+line+"<" ) ;
             synchronized( _outLock ){
                if( line.equals( "..." ) ){
                   System.out.println( "Close requested form terminal" ) ;
                   try{ _out.close() ; }catch(Exception eee ){
                      System.err.println( "Problem closing out : "+eee ) ;
                   }
                   System.out.println( "Closed remote connection" ) ;
                   _out = null ;
                   continue ;
                }
                if( _out == null )continue ;
                try{
                   _out.writeUTF( line ) ;
                }catch( IOException iioe ){
                   System.err.println( "Problem sending on out : "+iioe ) ;
                }
             }
         }
      }catch( IOException ioe ){
         System.err.println( "Problem while reading from Stdio : "+ioe ) ;
      }
   }
   private void runIo( Socket socket ){
       DataInputStream   in  = null ;
       DataOutputStream  out = null ;
       try{
          in  = new DataInputStream( socket.getInputStream() ) ;
          out = new DataOutputStream( socket.getOutputStream() ) ;
       }catch( IOException ioe ){
          System.out.println( "Problem in opening io streams : "+ioe) ;
          try{ in.close() ; }catch(Exception e){}
          try{ out.close() ; }catch(Exception e){}
          return ;
       }
       synchronized( _outLock ){ 
          if( _out != null ){
             try{ _out.close() ; }catch( Exception ee ){}
          }
          _out = out ; 
       }
       String line = null ;
       try{
          while( ( line = in.readUTF() ) != null ){
             System.out.println( "Got : "+line) ;
          }
       }catch( EOFException eofe ){
          System.out.println( "Remote node closed connection" ) ;
       }catch( IOException ioe ){
          System.err.println( "Problem in readUTF() : "+ioe ) ;
       }

       try{ in.close() ; }catch(Exception e){}
       try{ 
          synchronized( _outLock ){
             _out = null ;
             out.close() ;
          } 
       }catch(Exception e){}
   }
   private void runTicker(){
      while(true){
         try{
             Thread.currentThread().sleep(4000) ; 
             if( _echo ){
                synchronized( _outLock ){
                   if( _out != null ){
                      try{
                         _out.writeUTF( "Tick Tack" ) ;
                      }catch( IOException ioe ){}
                   }
                }
             }else{
                System.out.println( "Tick Tack" ) ;
             }
         }catch( InterruptedException ire ){
         
         }
      }
   }
   public static void main( String [] args ) throws Exception {
      if( args.length == 0 ){
         System.out.println( 
             "USAGE : ... <port>        "+
             "# listens on port <port> or any if <port>=0" ) ;
         System.out.println( 
             "        ... <host> <port> "+
             "# connects to <host> <port> and echos" ) ;
         System.exit(0);
      }else if( args.length == 1 ){
         int port = Integer.parseInt( args[0] ) ;
         new Utfio( port ) ;
      }else if( args.length == 2 ){
         int port = Integer.parseInt( args[1] ) ;
         new Utfio( args[0] , port ) ;
      }else{
         int port = Integer.parseInt( args[2] ) ;
         new Utfio( args[1] , port , true ) ;
      }
      
   }
}
