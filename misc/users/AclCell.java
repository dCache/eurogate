package eurogate.misc.users ;

import eurogate.misc.* ;
import java.lang.reflect.* ;
import java.io.* ;
import java.util.*;
import dmg.cells.nucleus.*; 
import dmg.util.*;
import dmg.security.digest.Crypt ;

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

  private UserPasswords    _sysPassword  = null ;
  private UserPasswords    _egPassword   = null ;
  private Crypt            _crypt        = new Crypt() ;
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
          //
          // read the password file information
          //
          String tmp = null ;
          if( ( tmp = _args.getOpt( "syspassword" ) ) != null ){
             _sysPassword = new UserPasswords( new File( tmp ) ) ;
             say( "using as SystemPasswordfile : "+tmp ) ;
          }
          if( ( tmp = _args.getOpt( "egpassword"  ) ) != null ){
             _egPassword  = new UserPasswords( new File( tmp ) ) ;
             say( "using as EgPasswordfile : "+tmp ) ;
          }
      }catch( Exception e ){
         esay( "Exception while <init> : "+e ) ;
         esay(e) ;
         start() ;
         kill() ;
         throw e ;
      }
      
      start() ;
  
  }
  //
  // for now we also serve the password checking request
  //
  public void messageArrived( CellMessage msg ){
      Object obj     = msg.getMessageObject() ;
      Object answer  = "PANIX" ;
      
      try{
         say( "Message type : "+obj.getClass() ) ;
         if( ( obj instanceof Object []              )  &&
             (  ((Object[])obj).length >= 3          )  &&
             (  ((Object[])obj)[0].equals("request") ) ){
             
            Object [] request    = (Object[])obj ;
            String user          = request[1] == null ? 
                                   "unknown" : (String)request[1] ;
            String command       = (String)request[2] ;

            say( ">"+command+"< request from "+user ) ;
            try{
              if( command.equals( "check-password" ) )
                  answer  =  acl_check_password( request ) ;
              else
                  new Exception( "Command not found : "+command ) ;
            }catch( Exception xe ){
               throw new Exception( "Problem : "+xe ) ;
            }
         }else{
             String r = "Illegal message object received from : "+
                         msg.getSourcePath() ;
             esay( r ) ;
             throw new Exception( r ) ;
         }
      }catch(Exception iex ){
         answer = iex ;
      }
      
      if( answer instanceof Object [] )
        ((Object[])answer)[0] = "response" ;
        
      msg.revertDirection() ;
      msg.setMessageObject( answer ) ;
      try{
         sendMessage( msg ) ;
      }catch( Exception ioe ){
         esay( "Can't send acl_response : "+ioe ) ;
         esay(ioe) ;
      }
  }
  ///////////////////////////////////////////////////////////////////////////
  //
  //  r[0] : request
  //  r[1] : <anything>
  //  r[2] : check-password
  //  r[3] : <user>
  //  r[4] : <password>[plainText]
  //
  //  checks : nothing
  //
  //    response
  //
  //  r[0] : response
  //  r[1] : <user>
  //  r[2] : check-password
  //  r[3] : <user>
  //  r[4] : <password>[plainText]
  //  r[5] : Boolean(true/false)
  //
  private Object 
          acl_check_password( Object [] request )
          throws Exception {
     
      if( request.length < 5 )
         throw new 
         IllegalArgumentException( 
         "Not enough arguments for 'check-password'" ) ;
      
      Object [] response = new Object[6] ;
      for( int i = 0 ;i < 5; i++ )response[i] =  request[i] ;
      response[1]     = request[3] ;
      String userName = (String)request[3] ;
      String password = (String)request[4] ;
      
      response[5] = new Boolean( matchPassword( userName , password ) ) ;
      return response ;
  }
  private static final String DUMMY_ADMIN = "5t2Hw7lNqVock"  ;
  private boolean matchPassword( String userName , String password ){
  
      String pswd     = null ;
      updatePassword() ;
      
      boolean answer = false ;
      
      try{
         if( userName.equals("admin" ) ){
            if( _egPassword == null ){
               pswd = DUMMY_ADMIN ;
            }else{
               pswd = _egPassword.getPassword(userName) ;
               if( pswd == null )pswd = DUMMY_ADMIN ;
            }
            return _crypt.crypt( pswd , password ).equals(pswd) ;
         }else{
            //
            // the user must have been created.
            //
            UserMetaDictionary dict = _userMetaDb.getDictionary(userName) ;
            if( dict == null )return false ;
            //
            // check for login disabled.
            //
            String dis = dict.valueOf("login") ;
            if( ( dis != null ) && ( dis.equals("no") ) )return false ;
            
            if( _sysPassword == null )return false ;
            //
            // first check in /etc/shadow
            //
            pswd = _sysPassword.getPassword(userName) ;
            //
            // only if not found here we allow the user
            // to be in the eurogate passwdFile.
            // ( if the user is in /etc/shadow, but the
            //   passwd is wrong, we DONT check in 
            //   in the eurogatePasswd )
            //
            if( pswd == null )
               pswd = _egPassword.getPassword(userName) ;

            if( pswd != null )
               return _crypt.crypt( pswd , password ).equals(pswd) ;
 
         }
      }catch( Throwable t ){
         esay( "Found : "+t ) ;
      }
      return false ;
  }
  private void updatePassword(){
     try{
        if( _sysPassword != null )_sysPassword.update() ;
     }catch(Exception ee ){
        esay( "Updating failed : "+_sysPassword ) ;
     }
     try{
        if( _egPassword != null )_egPassword.update() ;
     }catch(Exception ee ){
        esay( "Updating failed : "+_egPassword ) ;
     }
   }
  
  /////////////////////////////////////////////////////////////
  //
  //   the interpreter
  //
}
