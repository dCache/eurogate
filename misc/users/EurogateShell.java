package eurogate.misc.users ;

import   dmg.cells.services.login.* ;
import   dmg.cells.nucleus.* ;
import   dmg.util.* ;

/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class   EurogateShell 
       extends CommandInterpreter {
    private CellNucleus _nucleus ;
    private String      _user ;
    private CellPath    _cellPath  = null ;
    private CellShell   _cellShell = null ;
    
    public EurogateShell( String user , CellNucleus nucleus , Args args ){
       _nucleus = nucleus ;
       _user    = user ;
       
       for( int i = 0 ; i < args.argc() ; i++ )
          _nucleus.say( "arg["+i+"]="+args.argv(i) ) ;
          
       if( ( args.argc() > 0 ) && ( args.argv(0).equals("kill" ) ) )
          throw new IllegalArgumentException( "hallo du da" )  ;
          
       //
       // check if the CellShell is allowed for us.
       //
       if( checkPrivileges( user , "exec-shell" , "system" , "*" ) ){
       
          _cellShell = new CellShell( _nucleus ) ;
          addCommandListener( _cellShell ) ;
          say( "Shell installed" ) ;
       }else{
          say( "Installation of Shell not permitted" ) ;
       }
    }
    private void say( String str ){ _nucleus.say( str ) ; }
    private void esay( String str ){ _nucleus.esay( str ) ; }
    private boolean checkPrivileges( String user , 
                                     String action ,
                                     String className ,
                                     String instanceName ){
    
       say("requesting acl {"+className+"."+instanceName+"."+action+
           "} for user "+user ) ;
       return true ;
    }
    private String _prompt = " >> " ;
    public Object executeCommand( String str ){
       say( "String command" ) ;
       try{
          String r = command( str ) ;
          if( r.length() < 1 )return _prompt ;
          if( r.substring(r.length()-1).equals("\n" ) )            
             return command( str )+ _prompt  ;
          else 
             return command( str ) + "\n" + _prompt  ;
       }catch( CommandExitException cee ){
          return null ;
       }
    }
    public Object ac_logoff( Args args ) throws CommandException {
       throw new CommandExitException( "Done" , 0  ) ;
    }
    public Object executeCommand( Object obj ){
       say( "Object command" ) ;
       if( obj instanceof Object [] ){
          Object [] array  = (Object [] )obj ;
          if( array.length < 2 )
              throw new 
              IllegalArgumentException( "not enough arguments" ) ;
          try{
             obj =  runCommand( (String) array[0] , array ) ;
          }catch(Exception eee ){
             obj = eee ;
          }
       }
       return obj ;
    }
    private Object runCommand( String command , Object [] args )
       throws Exception 
   {
    
       if( command.equals( "set-dest" ) ){
           _cellPath = new CellPath( (String)args[1] ) ;
           return args ;
       }else if( command.equals( "request" ) ||
                 command.equals( "pvl-request" ) ){
          if( args.length < 3 )
              throw new 
              IllegalArgumentException( "not enough arguments" ) ;
          if( ((String)args[0]).startsWith("pvl" ) ){
            _cellPath = new CellPath("pvl");
          }else{
            _cellPath = new CellPath("acm");
          }
       
          args[1] = _user ;
          CellMessage res = _nucleus.sendAndWait( 
                   new CellMessage( _cellPath , args ) , 
                   10000 ) ;
          if( res == null )throw new Exception("Request timed out" ) ;
          return res.getMessageObject() ;
       }else 
          throw new 
          IllegalArgumentException( "Command not found : "+command ) ;
    
    }
}
