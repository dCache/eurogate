package  eurogate.pvr ;

import  java.net.* ;
import  java.io.* ;
import  dmg.util.* ;

public class PvrStreamEngine implements StreamEngine {

   private Socket _socket ;
   private String _pvrType ;
   private String _pvrName ;
   
   public PvrStreamEngine( Socket socket ) throws IOException {
       _socket = socket ;
       DataInputStream  in  = new DataInputStream(  _socket.getInputStream() ) ;
       DataOutputStream out = new DataOutputStream( _socket.getOutputStream() ) ;
       //
       // wait for the hello
       //
       String hello = in.readUTF() ;
       if( hello == null )
          throw new EOFException( "Sorry, protocol violation" ) ;
       Args args = new Args( hello ) ;
       if( args.argc() < 3 )
          throw new 
          IOException( "Protocol violation : not enough arguments" ) ;
       if( ! args.argv(0).equalsIgnoreCase( "hello" ) )
          throw new 
          IOException( "Protocol violation : not hello : "+args.argv(0) ) ;
          
       _pvrName = args.argv(1) ;
       _pvrType = args.argv(2) ;
       
       out.writeUTF( "welcome" ) ;
       out.flush() ;
       out = null ;
       in  = null ;
   }
   public String getUserName(){return _pvrName ; }
   public String getPvrName(){ return _pvrName ; }
   public String getPvrType(){ return _pvrType ; }
   public InetAddress getInetAddress(){ return _socket.getInetAddress(); }
   public InetAddress getLocalAddress(){ return _socket.getLocalAddress() ; }
   public Socket      getSocket() { return _socket ; }
   public InputStream getInputStream(){ 
     try{
       return _socket.getInputStream(); 
     }catch( Exception e ){ return null ; }
   }
   public OutputStream getOutputStream(){ 
     try{
       return _socket.getOutputStream(); 
     }catch( Exception e ){return null ; }
   }
   public Reader getReader() {
     try{
      return new InputStreamReader( _socket.getInputStream() ); 
     }catch( Exception e ){ return null ; }
   }
   public Writer getWriter(){
     try{
      return new OutputStreamWriter( _socket.getOutputStream() ) ;
     }catch( Exception e ){ return null ; }
   }

}
 
