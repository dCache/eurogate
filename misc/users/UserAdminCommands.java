package eurogate.misc.users ;

import eurogate.misc.* ;
import java.io.* ;
import java.util.* ;
import dmg.util.* ;

public class UserAdminCommands implements  Interpretable {

   private AclDb            _aclDb  = null ;
   private UserRelationable _userDb = null ;
   private UserMetaDb       _userMetaDb = null ;
   public UserAdminCommands( UserRelationable userDb ,
                             AclDb            aclDb ,
                             UserMetaDb       metaDb  ){
    
      _userDb     = userDb ;
      _aclDb      = aclDb ;
      _userMetaDb = metaDb ;                         
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
        Exception( "Not all databases are open" ) ;
    }
    private void checkPermission( Args args , String acl ) throws Exception {
       if( ! ( args instanceof Authorizable ) )return ;
       String user = ((Authorizable)args).getAuthorizedPrincipal() ;
       if( user.equals("admin") )return ;
       try{
          if( _aclDb.check( "super.access" , user , _userDb ) )return ;
       }catch(Exception ee ){}
       if( ! _aclDb.check(acl,user,_userDb) )
          throw new 
          Exception( "Acl >"+acl+"< negative for "+user ) ;       
    }
    public String hh_create_user = "<userName>" ;
    public String ac_create_user_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( args , "user.*.create" ) ;
       String user = args.argv(0) ;
       _userMetaDb.createUser( user ) ;
//       String acl = "user."+user+".access" ;
//       _aclDb.createAclItem( acl ) ;
//       _aclDb.addAllowed( acl , user ) ;
       return "" ;
    }
    public String hh_create_group = "<groupName>" ;
    public String ac_create_group_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( args , "user.*.create" ) ;
       String group = args.argv(0) ;
       _userMetaDb.createGroup( group ) ;
       _userDb.createContainer( group ) ;
       _aclDb.createAclItem( "user."+group+".access" ) ;
       return "" ;
    }
    public String hh_destroy_principal = "<principalName>" ;
    public String ac_destroy_principal_$_1( Args args )throws Exception {
       checkDatabase() ;
       checkPermission( args , "user.*.create" ) ;
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
          _aclDb.removeAclItem( "user."+user+".access" ) ;
       }catch( Exception ee ){
          // This is not an error. removeContainer may
          // have failed which indicates that this is
          // an user.
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
       checkPermission( args , "user."+group+".access" ) ;
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
       checkPermission( args , "user."+group+".access" ) ;
       _userDb.removeElement(group,princ);
       return "" ;
    }
    public String hh_show_group = "<group>" ;
    public String ac_show_group_$_1( Args args )throws Exception {
        Enumeration  e  = _userDb.getElementsOf(args.argv(0)) ;
        StringBuffer sb = new StringBuffer() ;
        while( e.hasMoreElements() ){
           sb.append( e.nextElement().toString() ).append("\n") ;
        }
        return sb.toString() ;
    }
    public String hh_show_groups = "" ;
    public String ac_show_groups( Args args )throws Exception {
        Enumeration  e  = _userDb.getContainers() ;
        StringBuffer sb = new StringBuffer() ;
        while( e.hasMoreElements() ){
           sb.append( e.nextElement().toString() ).append("\n") ;
        }
        return sb.toString() ;
    }
    public String hh_add_access = "[-allowed|-denied] <acl> <principal>" ;
    public String ac_add_access_$_2( Args args )throws Exception {
       checkDatabase() ;
       boolean allowed = args.getOpt("denied") == null ;
       String acl   = args.argv(0) ;
       String princ = args.argv(1) ;
       checkPermission( args , "acl."+acl+".access" ) ;
       if( allowed ){
           _aclDb.addAllowed( acl , princ ) ;
       }else{
           _aclDb.addDenied( acl , princ ) ;
       }
       return "" ;
    }
    public String hh_remove_access = "<acl> <principal>" ;
    public String ac_remove_access_$_2( Args args )throws Exception {
        String acl   = args.argv(0) ;
        String princ = args.argv(1) ;
        checkPermission( args , "acl."+acl+".access" ) ;
        _aclDb.removeUser( acl , princ );
        return "" ;
    }
    public String hh_create_acl = "<aclName>" ;
    public String ac_create_acl_$_1( Args args )throws Exception {
        checkDatabase() ;
        checkPermission( args , "super.access");
        _aclDb.createAclItem(args.argv(0));
        return "" ;
    }
    public String hh_destroy_acl = "<aclName>" ;
    public String ac_destroy_acl_$_1( Args args )throws Exception {
        checkDatabase() ;
        checkPermission( args , "super.access");
        _aclDb.removeAclItem(args.argv(0));
        return "" ;
    }
    public String hh_show_acl = "<aclName> [-resolve]" ;
    public String ac_show_acl_$_1( Args args )throws Exception {
        checkDatabase() ;
        boolean resolve = args.getOpt("resolve") != null ;
        AcDictionary dict = _aclDb.getPermissions(args.argv(0),resolve);
        Enumeration e = dict.getPrincipals() ;
        String inherits = dict.getInheritance() ;
        StringBuffer sb = new StringBuffer() ;
        if( inherits == null )sb.append( "<resolved>\n") ;
        else sb.append("<inherits="+inherits+">\n") ;
        while( e.hasMoreElements() ){
            String user = (String)e.nextElement() ;
            sb.append( user+" -> "+dict.getPermission(user)+"\n" ) ;
        }
        return sb.toString() ;
    }
    public String hh_check = "<acl> <user>" ;
    public String ac_check_$_2( Args args )throws Exception {
        checkDatabase() ;
        boolean ok = _aclDb.check(args.argv(0),args.argv(1),_userDb);
        return  ( ok ? "Allowed" : "Denied" ) + "\n" ;
    }
    public String hh_show_principal = "<principalName>" ;
    public String ac_show_principal_$_1( Args args )throws Exception {
        UserMetaDictionary dict = _userMetaDb.getDictionary(args.argv(0)) ;
        Enumeration e = dict.keys() ;
        StringBuffer sb = new StringBuffer() ;
        while( e.hasMoreElements() ){
            String user = (String)e.nextElement() ;
            sb.append( user+" -> "+dict.valueOf(user) ).append("\n") ;
        }
        return sb.toString() ;
    }
    public String hh_set_principal = "<principalName> <key>=<value> [...]" ;
    public String ac_set_principal_$_1_99( Args args )throws Exception {
        checkPermission( args , "user."+args.argv(0)+".access");
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
    public String hh_let = "<aclName> inheritfrom <aclNameFrom>" ;
    public String ac_let_$_3( Args args )throws Exception {
       if( ! args.argv(1).equals("inheritfrom") )
          throw new
          CommandSyntaxException( "keyword 'inheritfrom' missing" ) ;
        checkPermission( args , "acl."+args.argv(0)+".access");
        _aclDb.setInheritance(args.argv(0),args.argv(2));
        return "" ;
    }
}
