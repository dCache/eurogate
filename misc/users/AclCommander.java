package eurogate.misc.users ;

import eurogate.misc.* ;
import java.io.* ;
import java.util.* ;
import dmg.util.* ;


public class AclCommander extends CommandInterpreter {
    private AclDb            _aclDb  = null ;
    private UserRelationable _userDb = null ;
    private UserMetaDb       _userMetaDb = null ;
    private String           _user = "<unknown>" ;
    public String ac_hello_$_1( Args args ) throws Exception {
       throw new NoSuchElementException( "otto" ) ;
    }
    public AclCommander( String [] args )throws Exception {
        if( args.length > 0 ){
           _aclDb = new AclDb( new File( args[0] , "acls" ) ) ;
           _userDb = new InMemoryUserRelation( 
                       new FileUserRelation( 
                           new File( args[0] , "relations" ) ) ) ;
           _userMetaDb = new UserMetaDb( new File( args[0] , "meta" ) ) ;
           
        }
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
    public String hh_open_meta = "<userMetaDbDirectory>" ;
    public String ac_open_meta_$_1( Args args )throws Exception {
       _userMetaDb = new UserMetaDb( new File( args.argv(0) ) ) ;
       return "" ;
    }
    /////////////////////////////////////////////////////////////////
    //
    //   generic part
    //
    private void checkDatabase() throws Exception {
           if( ( _userMetaDb != null ) &&
               ( _aclDb      != null ) && 
               ( _userDb     != null )   ) return ;
        throw new 
        Exception( "Not add databases are open" ) ;
    }
    private void checkPermission( String acl ) throws Exception {
       if( ! _user.equals("admin") ){
          if( ! _aclDb.check(acl,_user,_userDb) )
             throw new 
             Exception( "Acl >"+acl+"< negative for "+_user ) ;       
       }
    }
    public String hh_id = "[<newUserName>]" ;
    public String ac_id_$_0_1( Args args )throws Exception {
       checkDatabase() ;
       if( args.argc() == 0 )return _user+"\n" ;
       _user = args.argv(0) ;
       return "" ;
    }
    public String hh_create_user = "<userName>" ;
    public String ac_create_user_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( "user.*.create" ) ;
       String user = args.argv(0) ;
       _userMetaDb.createUser( user ) ;
       return "" ;
    }
    public String hh_create_group = "<groupName>" ;
    public String ac_create_group_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( "user.*.create" ) ;
       String group = args.argv(0) ;
       _userMetaDb.createGroup( group ) ;
       _userDb.createContainer( group ) ;
       _aclDb.createAclItem( "group."+group+".access" ) ;
       return "" ;
    }
    public String hh_destroy_principal = "<principalName>" ;
    public String ac_destroy_principal_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( "user.*.create" ) ;
       String user = args.argv(0) ;
       Enumeration e = _userDb.getElementsOf(user) ;
       if( e.hasMoreElements() )
         throw new
         DatabaseException( "Not Empty : "+user ) ;
       e = _userDb.getParentsOf(user) ;
       if( e.hasMoreElements() )
         throw new
         DatabaseException( "Still in groups : "+user ) ;
       _userMetaDb.removePrincipal( user ) ;
       try{
          _userDb.removeContainer( user ) ;
          _aclDb.removeAclItem( "group."+user+".access" ) ;
       }catch( Exception ee ){
           //
           // not an error
           //
//          System.err.println(" removeContainer : "+ee ) ;
       }
       return "" ;
    }
    public String hh_add = "<principalName> to <groupName>" ;
    public String ac_add_$_3( Args args )throws Exception {
       checkDatabase() ;
       if( ! args.argv(1).equals("to") )
          throw new
          CommandSyntaxException( "keyword 'to' missing" ) ;
       String group = args.argv(2) ;
       String princ = args.argv(0) ;
       checkPermission( "group."+group+".access" ) ;
       _userDb.addElement(group, princ);
       return "" ;
    }
    public String hh_remove = "<principalName> from <groupName>" ;
    public String ac_remove_$_3( Args args )throws Exception {
       checkDatabase() ;
       if( ! args.argv(1).equals("from") )
          throw new
          CommandSyntaxException( "keyword 'from' missing" ) ;
       String group = args.argv(2) ;
       String princ = args.argv(0) ;
       checkPermission( "group."+group+".access" ) ;
       _userDb.removeElement(group,princ);
       return "" ;
    }
    public String hh_add_access = "[-allowed|-denied] <acl> <principal>" ;
    public String ac_add_access_$_2( Args args )throws Exception {
       checkDatabase() ;
       boolean allowed = args.getOpt("denied") == null ;
       String acl   = args.argv(0) ;
       String princ = args.argv(1) ;
       checkPermission( "acl."+acl+".access" ) ;
       if( allowed ){
           _aclDb.addAllowed( acl , princ ) ;
       }else{
           _aclDb.addDenied( acl , princ ) ;
       }
       return "" ;
    }
    public String hh_create_acl = "<aclName>" ;
    public String ac_create_acl_$_1( Args args )throws Exception {
        checkDatabase() ;
        checkPermission("super.access");
        _aclDb.createAclItem(args.argv(0));
        return "" ;
    }
    /////////////////////////////////////////////////////////////////
    //
    //   the meta data stuff
    //
    public String hh_ls_principal = "<principalName>" ;
    public String ac_ls_principal_$_1( Args args )throws Exception {
        if( _userMetaDb == null )throw new Exception("UserMetaDb not open") ;
        UserMetaDictionary dict = _userMetaDb.getDictionary(args.argv(0)) ;
        Enumeration e = dict.keys() ;
        while( e.hasMoreElements() ){
            String user = (String)e.nextElement() ;
            System.out.println( user+" -> "+dict.valueOf(user) ) ;
        }
        return "" ;
    }
    public String hh_meta_set_principal = "<principalName> <key>=<value> [...]" ;
    public String ac_meta_set_principal_$_1_99( Args args )throws Exception {
        if( _userMetaDb == null )throw new Exception("UserMetaDb not open") ;
        StringTokenizer st = null ;
        String key = null , value = null ;
        for( int i = 1 ; i < args.argc() ; i++ ){
           st  = new StringTokenizer( args.argv(i) , "=" ) ;
           key = st.nextToken() ;
           try{ value = st.nextToken() ;
           }catch(Exception ee){ value = "" ; }
           _userMetaDb.setAttribute( args.argv(0) , key , value ) ;
        }
        return "" ;
    }
    public String ac_meta_create_principal_$_1( Args args )throws Exception {
        if( _userMetaDb == null )throw new Exception("UserMetaDb not open") ;
        if( args.getOpt("group") != null ){
            _userMetaDb.createGroup( args.argv(0) ) ;
        }else{
            _userMetaDb.createUser( args.argv(0) ) ;
        }
        return "" ;
    }
    public String ac_meta_rm_principal_$_1( Args args )throws Exception {
        if( _userMetaDb == null )throw new Exception("UserMetaDb not open") ;
        _userMetaDb.removePrincipal( args.argv(0) ) ;
        return "" ;
    }
    /////////////////////////////////////////////////////////////////
    //
    //   the user relation stuff
    //
    public String hh_rel_create_group = "<group>" ;
    public String ac_rel_create_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.createContainer(args.argv(0)) ;
        return "" ;
    }
    public String hh_rel_rm_group = "<group>" ;
    public String ac_rel_rm_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.removeContainer(args.argv(0)) ;
        return "" ;
    }
    public String hh_rel_ls_group = "<group>" ;
    public String ac_rel_ls_group_$_1( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        Enumeration e = _userDb.getElementsOf(args.argv(0)) ;
        while( e.hasMoreElements() ){
           System.out.println( e.nextElement().toString() ) ;
        }
        return "" ;
    }
    public String hh_rel_ls_groups = "" ;
    public String ac_rel_ls_groups( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        Enumeration e = _userDb.getContainers() ;
        while( e.hasMoreElements() ){
           System.out.println( e.nextElement().toString() ) ;
        }
        return "" ;
    }
    public String hh_rel_add_user = "<group> <user>" ;
    public String ac_rel_add_user_$_2( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.addElement(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_rel_rm_user = "<group> <user>" ;
    public String ac_rel_rm_user_$_2( Args args )throws Exception {
        if( _userDb == null )throw new Exception("UserDb not open") ;
        _userDb.removeElement(args.argv(0),args.argv(1));
        return "" ;
    }
    /////////////////////////////////////////////////////////////////
    //
    //   the acl stuff
    //
    public String hh_acl_create_acl = "<aclName>" ;
    public String ac_acl_create_acl_$_1( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.createAclItem(args.argv(0));
        return "" ;
    }
    public String hh_acl_ls_acl = "<aclName> -resolve" ;
    public String ac_acl_ls_acl_$_1( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        boolean resolve = args.getOpt("resolve") != null ;
        AcDictionary dict = _aclDb.getPermissions(args.argv(0),resolve);
        Enumeration e = dict.getPrincipals() ;
        String inherits = dict.getInheritance() ;
        if( inherits == null )System.out.println( "<resolved>") ;
        else System.out.println("<inherits="+inherits+">") ;
        while( e.hasMoreElements() ){
            String user = (String)e.nextElement() ;
            System.out.println( user+" -> "+dict.getPermission(user) ) ;
        }
        return "" ;
    }
    public String hh_acl_rm_acl = "<aclName>" ;
    public String ac_acl_rm_acl_$_1( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.removeAclItem(args.argv(0));
        return "" ;
    }
    public String hh_acl_set_inherit = "<aclName> <aclNameFrom>" ;
    public String ac_acl_set_inherit_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.setInheritance(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_acl_add_allowed = "<acl> <user>" ;
    public String ac_acl_add_allowed_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.addAllowed(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_acl_add_denied = "<acl> <user>" ;
    public String ac_acl_add_denied_$_2( Args args )throws Exception {
        if( _aclDb == null )throw new Exception("AclDb not open") ;
        _aclDb.addDenied(args.argv(0),args.argv(1));
        return "" ;
    }
    public String hh_acl_rm_access = "<acl> <user>" ;
    public String ac_acl_rm_access_$_2( Args args )throws Exception {
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
    public static void main( String [] args ) throws Exception {
       AclCommander commander = new AclCommander(args) ;
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
