package eurogate.misc.users ;

import eurogate.misc.* ;
import java.io.* ;
import java.util.* ;
import dmg.util.* ;


public class AclCommander extends CommandInterpreter {
    private AclDb            _aclDb  = null ;
    private UserRelationable _userDb = null ;
    public String ac_hello_$_1( Args args ) throws Exception {
       throw new NoSuchElementException( "otto" ) ;
    }
    public String hh_open_acl = "<aclDbDirectory>" ;
    public String ac_open_acl_$_1( Args args )throws Exception {
       _aclDb = new AclDb( new File( args.argv(0) ) ) ;
       return "" ;
    }
    public String hh_open_relations = "<userRelationDbDirectory>" ;
    public String ac_open_relations_$_1( Args args )throws Exception {
       _userDb = new InMemoryUserRelation( 
                   new FileUserRelation( 
                       new File( args.argv(0) ) ) ) ;
       return "" ;
    }
    public String hh_create_group = "<group>" ;
    public String ac_create_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.createContainer(args.argv(0)) ;
        return "" ;
    }
    public String hh_rm_group = "<group>" ;
    public String ac_rm_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.removeContainer(args.argv(0)) ;
        return "" ;
    }
    public String hh_ls_group = "<group>" ;
    public String ac_ls_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        Enumeration e = _userDb.getElementsOf(args.argv(0)) ;
        while( e.hasMoreElements() ){
           System.out.println( e.nextElement().toString() ) ;
        }
        return "" ;
    }
    public String hh_ls_groups = "" ;
    public String ac_ls_groups( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        Enumeration e = _userDb.getContainers() ;
        while( e.hasMoreElements() ){
           System.out.println( e.nextElement().toString() ) ;
        }
        return "" ;
    }
    public String hh_add_user = "<group> <user>" ;
    public String ac_add_user_$_2( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.addElement(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_rm_user = "<group> <user>" ;
    public String ac_rm_user_$_2( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.removeElement(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_create_acl = "<aclName>" ;
    public String ac_create_acl_$_1( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.createAclItem(args.argv(0));
        return "" ;
    }
    public String hh_rm_acl = "<aclName>" ;
    public String ac_rm_acl_$_1( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.removeAclItem(args.argv(0));
        return "" ;
    }
    public String hh_set_inherit = "<aclName> <aclNameFrom>" ;
    public String ac_set_inherit_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.setInheritance(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_add_allowed = "<acl> <user>" ;
    public String ac_add_allowed_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.addAllowed(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_add_denied = "<acl> <user>" ;
    public String ac_add_denied_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.addDenied(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_rm_access = "<acl> <user>" ;
    public String ac_rm_access_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.removeUser(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_check = "<acl> <user>" ;
    public String ac_check_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        if( _userDb == null )throw new Exception("UserDb not open") ;
        boolean ok = _aclDb.check(args.argv(0),args.argv(1),_userDb);
        return  ( ok ? "Allowed" : "Denied" ) + "\n" ;
    }
    public static void main( String [] args ){
       AclCommander commander = new AclCommander() ;
       BufferedReader br = new BufferedReader(
                               new InputStreamReader( System.in ) ) ;
        
        
       String line = null ;
       while( true ){
          System.out.print("acl > ") ;
          try{
             if( ( line = br.readLine() ) == null )break ;            
          }catch( Exception ioe ){
             System.err.println("Input problem : "+ioe ) ;
             break ;
          }
          Args a = new Args( line ) ;
          if( a.argc() < 1 )continue ;
          if( a.argv(0).equals("exit") )break ;
          try{
              System.out.print( commander.command( line ) ) ;
          }catch(NoSuchElementException nse ){
              System.err.println( "Problem : "+nse ) ;
          }catch(Exception e ){
              System.err.println( "Problem : "+e ) ;
//              e.printStackTrace() ;
          }
       
       }
       System.exit(0) ;
    
    }
}
