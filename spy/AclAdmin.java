package eurogate.spy ;

import dmg.cells.services.login.* ;
import   dmg.cells.nucleus.* ;
import   dmg.util.* ;

/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      AclAdmin extends CommandInterpreter {
    private CellNucleus _nucleus ;
    private String      _user ;
    private CellPath    _cellPath  = new CellPath( "AclCell" ) ;
    private CellShell   _cellShell = null ;
    public AclAdmin( String user , CellNucleus nucleus , Args args ){
       _nucleus = nucleus ;
       _user    = user ;
       for( int i = 0 ; i < args.argc() ; i++ )
          _nucleus.say( "arg["+i+"]="+args.argv(i) ) ;
          
    }
    public String getPrompt(){ return "Acl("+_user+") >> " ; }
    private void say( String str ){ _nucleus.say( str ) ; }
    private void esay( String str ){ _nucleus.esay( str ) ; }
    public String getHello(){
      return "\n    Welcome to AclAdmin for Eurogate (user="+_user+")\n\n" ;
    }
    public String ac_id_$_0_1( Args args )throws Exception {
       if( args.argc() == 1 )
         _user = args.argv(0) ;
       return _user + "\n" ;
    }
    public Object executeCommand( String str ) throws Exception {
       String tr = str.trim() ;
       if( tr.equals("") )return "" ;
       if( tr.equals("logout") )throw new CommandExitException() ;
       if( tr.startsWith(".") ){
          tr = tr.substring(1) ;
          return command( tr ) ;
       }
       CellMessage res = 
         _nucleus.sendAndWait( 
                new CellMessage( 
                     _cellPath , 
                     new AuthorizedString( _user , str ) 
                    ) , 
                10000 
         ) ;
       if( res == null )throw new Exception("Request timed out" ) ;
       Object resObject = res.getMessageObject() ;
       if( resObject instanceof Exception )throw (Exception)resObject ;
       String r = resObject.toString() ;
       if( r.length() == 0 )return "" ;
       if( r.substring(r.length()-1).equals("\n" ) )return r ;            
       else   return r + "\n" ;
    }
}
