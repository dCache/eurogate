package eurogate.store ;

import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.protocols.ssh.* ;
import   dmg.util.db.* ;

import java.util.* ;
import java.io.* ;
import java.net.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      StoreLoginCell 
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
  
  private BfidHandler       _handler   = null ;
  private String            _container = "/home/patrick/cells/dmg/util/db/container" ;
  private BfidHandle        _handle    = null ;
  private XClass            _xobject   = null ;
  
  public StoreLoginCell( String name , StreamEngine engine ){
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
     _handler = (BfidHandler)dict.get( "database" ) ;
     if( _handler == null ){
        _container = (String) dict.get( "databaseName" ) ;
        if( _container == null ){
           kill() ;
           throw new IllegalArgumentException( "databaseName not defined" ) ;
        }
        _handler = new BfidHandler( new File( _container )  ) ;
        dict.put( "database" , _handler ) ;
        say( "Database handler created on : "+_container ) ;
     }
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
  public String ac_show_handle( Args args )throws CommandException{
      if( _handle == null )throw new CommandException("No Handle assigned" ) ;
      return "Current Handle points to "+_handle.toString() ;
  }
  public String ac_show_handler( Args args )throws CommandException{
      return _handler.toString() ;
  }
  public String ac_show_thread( Args args )throws CommandException{
      return "Current Thread is "+Thread.currentThread() ;
  }
  public String hh_ls_bfids  = "" ;
  public String ac_ls_bfids( Args args ) throws CommandException {
     try{
        String [] names = _handler.getBfidNames() ; 
        StringBuffer sb = new StringBuffer() ;
        if( args.optc() > 0 ){
           BfidHandle handle = null ;
           for( int i = 0 ; i < names.length ; i++ ){
              sb.append( names[i] ).append( "   " ) ;
              handle = _handler.getBfidByName( names[i] ) ;
              handle.open( DbGLock.READ_LOCK ) ;
              sb.append( handle.getCreationDate() ).
                 append( "\t   " ).
                 append( handle.getStatus() ).
                 append( "\t   " ).
                 append( handle.getSize() ).
                 append( "\n" ) ;
              
              handle.close() ;
           }
         }else{
           for( int i = 0 ; i < names.length ; i++ )
              sb.append( names[i] ).append( "\n" ) ;
        }
        return sb.toString() ;     
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
  }
  public String hh_set_bfid  = "<attribute> <value>" ;
  public String ac_set_bfid_$_2( Args args ) throws Exception {
     if( _handle == null )throw new CommandException("No Handle assigned" ) ;
     
     _handle.open(DbGLock.WRITE_LOCK) ;
     try{
         DbResourceHandle handle = _handle.getResourceHandle() ;
         String attr = (String)handle.getAttribute(args.argv(0)) ;
         if( attr == null )
            throw new Exception("Not an attribute : "+args.argv(0) ) ;
         handle.setAttribute( args.argv(0) , args.argv(1) ) ;
     }catch(Exception dbe ){
        throw new CommandException( "Problem : "+dbe ) ;
     }finally{
        _handle.close() ;
     }
     return "Done" ;
  
  }
  public String ac_chaos_$_1( Args args ){
     int count = Integer.parseInt( args.argv(0) ) ;
     byte [] v = null ;
     for( int i = 0 ; i < count ; i++ )v = new byte[1024] ;
     v=null ;
     return "Done" ;
  }
  public String hh_create_bfid  = "<bfidName>" ;
  public String ac_create_bfid( Args args ) throws CommandException {
     try{
        long num = System.currentTimeMillis() ;
        String name = "1."+(num%10)+"."+num ;
        _handle = _handler.createBfid( name ) ; 
        return "Bfid : "+name ;     
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
  }
  public String hh_get_bfid = "<bfidName>" ;
  public String ac_get_bfid_$_1( Args args ) throws CommandException {
     try{
      _handle = _handler.getBfidByName( args.argv(0) ) ;
      
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
     return "Current Handle points to "+_handle.getName() ;
  }
  public String hh_open = " read|write " ;
  public String ac_open_$_1( Args args ) throws CommandException {
     if( _handle == null )throw new CommandException("No Handle assigned" ) ;
     try{
        int mode = args.argv(0).equals("read") ?
                   DbGLock.READ_LOCK : 
                   DbGLock.WRITE_LOCK  ;
                   
        _handle.open( mode ) ;
      
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
     return _handle.getName()+" opened" ;
  }
  public String ac_close( Args args ) throws CommandException {
     if( _handle == null )throw new CommandException("No Handle assigned" ) ;
     try{
        _handle.close() ;
      
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
     return _handle.getName()+" closed" ;
  }
  public String ac_remove( Args args ) throws CommandException {
     if( _handle == null )throw new CommandException("No Handle assigned" ) ;
     try{
        _handle.remove() ;
      
     }catch( Exception dbe ){
        throw new CommandException( dbe.toString()) ;
     }
     return _handle.getName()+" removed" ;
  }
  public String ac_release( Args args ) throws CommandException {
     _handle = null ;
     return "Current Handle released" ;
  }
  public String ac_x( Args args ){
     String name = new Date().toString() ;
     _xobject = new XClass( name ) ;
     String x = _xobject.toString() ;
     _xobject = null ;
     return "Created name : "+x ;
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
