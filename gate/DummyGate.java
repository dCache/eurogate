package eurogate.gate ;

import eurogate.vehicles.* ;
import eurogate.misc.* ;

import dmg.util.* ;
import dmg.cells.nucleus.* ;
import java.net.* ;
import java.util.* ;
import java.io.* ;

public class DummyGate extends CellAdapter implements Runnable {

    private DataInputStream   _input  = null ;
    private DataOutputStream  _output = null ;
    private Thread            _readerThread = null ;
    private String            _state   = "init" ;
    private Object            _ioLock  = new Object() ;
    private Socket       _socket       = null ;
    private String       _remotePeer   = "" ;
    private CellNucleus  _nucleus      = null ;
    
    public DummyGate( String name , StreamEngine engine ) throws Exception {
        super( name , "" , false ) ;
        
        _nucleus = getNucleus() ;
        try{
	   _socket = null ;
           _input  = new DataInputStream( engine.getInputStream() ) ;
           _output = new DataOutputStream( engine.getOutputStream() ) ;
        }catch(Exception ee ){
           start() ;
           kill() ;
           throw ee ;
        }
        _readerThread = _nucleus.newThread( this ) ;
        _readerThread.start() ;
	_remotePeer   = engine.getInetAddress().toString()  ;
        start() ;
    }
    public void cleanUp(){
       say( "clean up triggerd" ) ;
       if( _input != null )try{ 
           _input.close() ; 
           _input = null ;
       }catch(Exception ee ){}
       if( _output != null )try{ 
           _output.close() ; 
           _output = null ;
       }catch(Exception ee ){}
       try{ _socket.close() ; }catch(Exception ioe){}
    }
    public String toString(){
      return "Peer="+_remotePeer ;
    }
    public void getInfo( PrintWriter pw ){
       pw.println( "Connection from : "+_remotePeer ) ;
       pw.println( "  -- Counters -- " ) ;
    }
    public void run(){
       if( Thread.currentThread() == _readerThread ){
       
          runReaderThread() ;
          
       }
    
    }
    
    private void runReaderThread(){
       String command = null , reply = null ;
       try{
          say( "Going into read loop" ) ;
          while( true ){
             //
             command = _input.readUTF() ;
             say( "!!!  CLIENT : >"+command+"<" ) ;
             try{
                reply = (String)command( new Args( command ) ) ;
             }catch( Exception ce ){
                esay( "Exception in executing command "+ce ) ;
                break ;
             }
             if( reply == null )continue ;
             _output.writeUTF( reply ) ;
          }
       }catch(Exception e ){
          say( "Exception in readerThread : "+e ) ;
       }
       try{ _output.close() ; }catch(Exception e){} ;
       say( "Client thread closed" ) ;
       kill() ;
    }
}
