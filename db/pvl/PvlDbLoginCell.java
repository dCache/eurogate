package  eurogate.db.pvl ;

import   dmg.util.cdb.* ;
import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.protocols.ssh.* ;

import java.util.* ;
import java.io.* ;
import java.net.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      PvlDbLoginCell 
       extends    CellAdapter
       implements Runnable  {

  private StreamEngine   _engine ;
  private BufferedReader _in ;
  private PrintWriter    _out ;
  private InetAddress    _host ;
  private String         _user ;
  private Thread         _workerThread ;
  private CellShell      _shell ; 
  private String         _destination = null ;
  private boolean        _syncMode    = true ;
  private Gate           _readyGate   = new Gate(false) ;
  private int            _syncTimeout = 10 ;
  private int            _commandCounter = 0 ;
  private String         _lastCommand    = "<init>" ;
  private Reader         _reader = null ;
  private Hashtable      _defaultHash = new Hashtable() ;
  private String         _container = "/home/patrick/eurogate/db/pvl/database" ;
  private PvlDb          _pvlDb     = null ;
  
  public PvlDbLoginCell( String name , StreamEngine engine ){
     super( name ) ;
     _engine  = engine ;
     
     _reader = engine.getReader() ;
     _in   = new BufferedReader( _reader ) ;
     _out  = new PrintWriter( engine.getWriter() ) ;
     _user = engine.getUserName() ;
     _host = engine.getInetAddress() ;
      
     _destination  = getCellName() ;
     _workerThread = new Thread( this ) ;         
     
     _workerThread.start() ;
     setPrintoutLevel( 11 ) ;
     useInterpreter(false) ;
     //
     // 
     // create the database if not yet done
     //
     Dictionary dict = getDomainContext() ;
     _pvlDb = (PvlDb) dict.get( "database" ) ;
     if( _pvlDb == null ){
        kill() ;
        throw new IllegalArgumentException( "database not defined" ) ;
     }
     addCommandListener( new PvlCommander( _pvlDb ) ) ;
  }
  public void run(){
    if( Thread.currentThread() == _workerThread ){
        print( prompt() ) ;
        while( true ){
           try{
               if( ( _lastCommand = _in.readLine() ) == null )break ;
               _commandCounter++ ;
               if( execute( _lastCommand ) > 0 ){
                  //
                  // we need to close the socket AND
                  // have to go back to readLine to
                  // finish the ssh protocol gracefully.
                  //
                  try{ _out.close() ; }catch(Exception ee){} 
               }else{
                  print( prompt() ) ;
               }       
           }catch( IOException e ){
              esay("EOF Exception in read line : "+e ) ;
              break ;
           }catch( Exception e ){
              esay("I/O Error in read line : "+e ) ;
              break ;
           }
        
        }
        say( "EOS encountered" ) ;
        _readyGate.open() ;
        kill() ;
    
    }
  }
  //
  //   the database
  //
  public String hh_create_database = "[<path>]" ;
  public String ac_create_database_$_0_1( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = new PvlDb( new File( _container ) , true ) ;     
     return "Done (db="+_container+")" ;
  }
  public String hh_open_database = "[<path>]" ;
  public String ac_open_database_$_0_1( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = new PvlDb( new File( _container ) , false ) ;     
     return "Done (db="+_container+")" ;
  }
  public String hh_release_database = "" ;
  public String ac_release_database( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = null ;     
     return "Done" ;
  }
  //
  //    this and that
  //
   public void   cleanUp(){
   
     say( "Clean up called" ) ;
     println("");
     try{ _out.close() ; }catch(Exception ee){} 
     _readyGate.check() ;
     say( "finished" ) ;

   }
  public void println( String str ){ 
     _out.print( str ) ;
     if( ( str.length() > 0 ) &&
         ( str.charAt(str.length()-1) != '\n' ) )_out.print("\n") ;
     _out.flush() ;
  }
  public void print( String str ){
     _out.print( str ) ;
     _out.flush() ;
  }
   public String prompt(){ 
      return _destination == null ? " .. > " : (_destination+" > ")  ; 
   }
   public int execute( String command ) throws Exception {
      if( command.equals("") )return 0 ;
      
         try{
             println( command( command ) ) ;
             return 0 ;
         }catch( CommandExitException cee ){
             return 1 ;
         }
   
   }
   private void printObject( Object obj ){
      if( obj == null ){
         println( "Received 'null' Object" ) ;
         return ;
      }  
      String output = null ;    
      if( obj instanceof Object [] ){
         Object [] ar = (Object []) obj ;
         for( int i = 0 ; i < ar.length ; i++ ){
            if( ar[i] == null )continue ;
             
            print( output = ar[i].toString() ) ;
            if(  ( output.length() > 0 ) &&
                 ( output.charAt(output.length()-1) != '\n' ) 

               )print("\n") ;
         }
      }else{
         print( output =  obj.toString() ) ;
         if( ( output.length() > 0 ) &&
             ( output.charAt(output.length()-1) != '\n' ) )print("\n") ;
      }
   
   }
  //
  // the cell implemetation 
  //
   public String toString(){ return _user+"@"+_host ; }
   public void getInfo( PrintWriter pw ){
     pw.println( "            Stream LoginCell" ) ;
     pw.println( "         User  : "+_user ) ;
     pw.println( "         Host  : "+_host ) ;
     pw.println( " Last Command  : "+_lastCommand ) ;
     pw.println( " Command Count : "+_commandCounter ) ;
   }
   public void   messageArrived( CellMessage msg ){
   
        Object obj = msg.getMessageObject() ;
        println("");
        println( " CellMessage From   : "+msg.getSourceAddress() ) ; 
        println( " CellMessage To     : "+msg.getDestinationAddress() ) ; 
        println( " CellMessage Object : "+obj.getClass().getName() ) ;
        printObject( obj ) ;
     
   }
   ///////////////////////////////////////////////////////////////////////////
   //                                                                       //
   // the interpreter stuff                                                 //
   //                                                                       //
   public String ac_set_timeout_$_1( Args args ) throws Exception {
      _syncTimeout = new Integer( args.argv(0) ).intValue() ;
      return "" ;
   }
   public String ac_exit( Args args ) throws CommandExitException {
      throw new CommandExitException( "" , 0 ) ;
   }
      


}
