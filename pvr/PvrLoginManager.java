package  eurogate.pvr ;

import java.lang.reflect.* ;
import java.net.* ;
import java.io.* ;
import java.util.*;

import dmg.cells.nucleus.*; 
import dmg.util.*;

/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  * 
 */
public class      PvrLoginManager 
       extends    CellAdapter
       implements Runnable  {

  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private int          _listenPort ;
  private ServerSocket _serverSocket ;
  private Thread       _listenThread ;
  private int          _connectionRequestCounter   = 0 ;
  private int          _connectionAcceptionCounter = 0 ;
  private Hashtable    _connectionThreads = new Hashtable() ;
  private Args         _args ;
  private String       _loginCellClass =  "eurogate.pvr.PvrLoginCell" ;
  private boolean      _opt_dummy ;
  private Dictionary   _context  = null ;


  private static final String __usage =  "<port> [loginCell] [-dummy]" ;
  /**
  */
  public PvrLoginManager( String name , String args ) throws Exception {
       super( name , args , false ) ;
       
       _nucleus       = getNucleus() ;
       _args          = getArgs() ;      
       _cellName      = name ;
       
       try{
          if( _args.argc() < 1 )
             throw new IllegalArgumentException( "USAGE : ... "+__usage ) ;
          //
          // get our listen port
          //
          _listenPort = new Integer( _args.argv(0) ).intValue() ;
          //
          // get the loginCellClass is supplied
          //
          if( _args.argc() > 1 )_loginCellClass = _args.argv(1) ;
          //
          // scan possible options ( _dummy just as an example ) 
          //
          _opt_dummy     = false ;
          
          for( int i = 0 ; i < _args.optc() ; i++ ){
          
             if( _args.optv(i).equals( "-dummy" ) )_opt_dummy = true ;

          }
          //
          // last but not least : open the listen socket 
          //
          _serverSocket  = new ServerSocket( _listenPort ) ;
       }catch( Exception e ){
          esay(e);
          //
          // something went wrong, so we start, kill and exit
          //
          start() ;
          kill() ;
             
          throw e ;
       }
       //
       // start the listener thread
       //
       _context       = getDomainContext() ;
       
       _listenThread  = new Thread( this , "listenThread" ) ;       
       _listenThread.start() ;
  }
  public void say( String str ){ pin( str ) ; super.say( str ) ; }
  public void esay( String str ){ pin( str ) ; super.esay( str ) ; }
  private void acceptConnections(){
         //
         //
         while( true ){
            Socket socket = null ;
            try{
               socket = _serverSocket.accept() ;
               _connectionRequestCounter ++ ;
               _nucleus.say( "Connection request from "+socket.getInetAddress() ) ;
               //
               // prepare the thread together with the socket
               // to be processed. The Engine needs to run in 
               // a separate thread to avoid wait times while
               // we authenticate the pvr.
               //
               Thread t = new Thread( this ) ;
               _connectionThreads.put( t , socket ) ;
               t.start() ;
               
            }catch( Exception ee ){
               _nucleus.esay( "Got an Exception  : "+ee ) ;
               try{ socket.close() ; }catch( IOException ioex ){}
               continue ;
            }
            
         }
  
  }
  public void acceptConnection( Socket socket ){
    Thread t = Thread.currentThread() ;
    try{
       _nucleus.say( "acceptThread ("+t+"): creating protocol engine" ) ;
       //
       // the engine has to do all the neccessary checks 
       //
       PvrStreamEngine engine = new PvrStreamEngine( socket ) ;
                
       String cellName = engine.getUserName()  ;
       _nucleus.say( "acceptThread ("+t+
                     "): connection created for pvr : "+cellName+
                     " (pvrType="+engine.getPvrType()+")" ) ;
       
       String [] paraNames = new String[1] ;
       Object [] parameter = new Object[1] ;
       paraNames[0] = "dmg.util.StreamEngine" ;
       parameter[0] = engine ;
       createNewCell( _loginCellClass , cellName , paraNames , parameter ) ;
    
    }catch( Exception e ){
       _nucleus.esay( "Exception in TelnetStreamEngine : "+e ) ;
       if( e instanceof InvocationTargetException ){
          Exception ie = 
             (Exception)((InvocationTargetException)e).getTargetException() ;
             _nucleus.esay( "TargetException in TelnetStreamEngine : "+ie ) ;
       }
       try{ socket.close(); }catch(Exception ee){}
    }
  
  
  }
  public void run(){
     Socket currentSocket = null ;
     
     if( Thread.currentThread() == _listenThread ){
     
         acceptConnections() ;
         
      }else if( ( currentSocket = (Socket)
                  _connectionThreads.remove( Thread.currentThread() )
                ) != null ){
                
         acceptConnection( currentSocket ) ;      
                
      } 
  
  }
  public String toString(){
       return "P="+_listenPort+";C="+_loginCellClass; 
  }
  public void getInfo( PrintWriter pw){
    pw.println( " ListenPort     : "+_listenPort ) ;
    pw.println( " LoginCellClass : "+_loginCellClass ) ;
    return  ;
  }

}
 
