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
    private String      _destination = null ;
    public EurogateShell( String user , CellNucleus nucleus , Args args ){
       _nucleus = nucleus ;
       _user    = user ;
       
       for( int i = 0 ; i < args.argc() ; i++ )
          _nucleus.say( "arg["+i+"]="+args.argv(i) ) ;
          
       if( ( args.argc() > 0 ) && ( args.argv(0).equals("kill" ) ) )
          throw new IllegalArgumentException( "hallo du da" )  ;
          
    }
    private void say( String str ){ _nucleus.say( str ) ; }
    private void esay( String str ){ _nucleus.esay( str ) ; }
    private void esay( Exception e ){ _nucleus.esay(e) ; }
    private void checkPrivileges( String user , 
                                     String className ,
                                     String instanceName ,
                                     String action     )
            throws AclPermissionException  {
    
       String acl = className+"."+instanceName+"."+action ;
       say("requesting acl {"+acl+"} for user "+user ) ;
           
       if( ! user.equals( "patrick" ) )
          throw new
          AclPermissionException( "Permission denied ("+acl+") for "+user ) ;  
       return  ;
    }
    private String _prompt = " >> " ;
    public Object executeCommand( String str )throws Exception {
       say( "String command "+str ) ;
          Object or = executeLocalCommand( new Args( str ) ) ;
          if( or == null )return _prompt ;
          String r = or.toString() ;
          if(  r.length() < 1)return "" ;
          if( r.substring(r.length()-1).equals("\n" ) )            
             return r   ;
          else 
             return r + "\n"  ;
    }
    //
    // !!! getPrompt is called by our observer (StreamObjectCell)
    //
    public String getPrompt(){ return _prompt ; }
    public Object executeCommand( Object obj ) throws Exception {
       say( "Object command "+obj ) ;
       
       String command = obj.toString() ;
       Args args = new Args( command ) ;
       if( args.argc() == 0 )return null ;
       return executeLocalCommand( args );
    }
    private Object sendCommand( String destination , String command )
       throws Exception 
   {
    
        CellPath cellPath = new CellPath(destination);
        CellMessage res = 
              _nucleus.sendAndWait( 
                   new CellMessage( cellPath , 
                                    new AuthorizedString( _user , 
                                                          command)
                                  ) , 
              10000 ) ;
          if( res == null )throw new Exception("Request timed out" ) ;
          return res.getMessageObject() ;
    
    }
    private Object executeLocalCommand( Args args ) throws Exception {
       say( "Loacal command "+args ) ;
       try{
          return  command( args ) ;
       }catch( CommandThrowableException cte ){
          throw (Exception)cte.getTargetException() ;
       }catch( CommandPanicException cpe ){
          throw (Exception)cpe.getTargetException() ;
       }
    }
    ////////////////////////////////////////////////
    //
    //   local commands
    //
    public String hh_show_all = "exception|null|object|string|exit" ;
    public Object ac_show_all_$_1( Args args )throws Exception {
        String command = args.argv(0) ;
        say( "show all : mode="+command+";user="+_user) ;
        if( command.equals("exception") )
           throw new  Exception( "hallo otto" ) ;
        if( command.equals("null") )return null ;
        if( command.equals("object") )return args ;
        if( command.equals("exit") )
           throw new CommandExitException("$exit$");
        return "Done" ;

    }
    public Object ac_logoff( Args args ) throws CommandException {
       throw new CommandExitException( "Done" , 0  ) ;
    }
    public String hh_send = "<destinationCell> <message" ;
    public Object ac_send_$_2(Args args )throws Exception {
        return sendCommand( args.argv(0) , args.argv(1) ) ;
    }
    public String hh_loadshell = "<fullShellClassName>|system" ;
    public Object ac_loadshell_$_1(Args args )throws Exception {
       if( ! args.argv(0).equals("system") )
          throw new
          CommandException( "Only system is currently supported" ) ;
       
       //
       // check if the CellShell is allowed for us.
       //
       checkPrivileges( _user , "shells" , "system" , "execute" )  ;
       
       _cellShell = new CellShell( _nucleus ) ;
       addCommandListener( _cellShell ) ;
       return "System Shell installed" ;
    
    }
}
