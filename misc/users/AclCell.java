package eurogate.misc.users ;

import eurogate.misc.* ;
import java.lang.reflect.* ;
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
public class       AclCell 
       extends     CellAdapter            {

  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private Args         _args ;

  private AclDb            _aclDb  = null ;
  private UserRelationable _userDb = null ;
  private UserMetaDb       _userMetaDb = null ;

  public AclCell( String name , String argString ) throws Exception {
  
      super( name , argString , false ) ;

      _cellName  = name ;
      _args      = getArgs() ;
      
    
      try{
      
         if( _args.argc() < 1 )
           throw new 
           IllegalArgumentException( "Usage : ... <dbPath>" ) ;
           
         File dbBase   = new File( _args.argv(0) ) ;
           _aclDb      = new AclDb( 
                              new File( dbBase , "acls" ) ) ;
           _userDb     = new InMemoryUserRelation( 
                              new FileUserRelation( 
                                  new File( dbBase , "relations" ) 
                                                   ) 
                          ) ;
           _userMetaDb = new UserMetaDb( 
                              new File( dbBase , "meta" ) ) ;
         
          UserAdminCommands uac = new UserAdminCommands( _userDb , _aclDb , _userMetaDb ) ;
          addCommandListener( uac ) ;
      }catch( Exception e ){
         esay( "Exception while <init> : "+e ) ;
         esay(e) ;
         start() ;
         kill() ;
         throw e ;
      }
      
      start() ;
  
  }
  /////////////////////////////////////////////////////////////
  //
  //   the interpreter
  //
  public String hh_show_all = "<user> exception|null|object|string" ;
  public Object ac_show_all_$_1( Args args )throws Exception {
      
      String user = args.getOpt("auth") ;
      if( user == null )throw new Exception("Not authenticated" ) ;
      String command = args.argv(0) ;
      say( "show all : mode="+command+";user=user") ;
      if( command.equals("exception") )
         throw new 
         Exception( "hallo otto" ) ;
      if( command.equals("null") )return null ;
      if( command.equals("object") )return args ;
      return "Done" ;
  
  }
}
