package eurogate.misc.users ;

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
public class       UserSecurityCell 
       extends     CellAdapter            {

  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private Args         _args ;

  public UserSecurityCell( String name , String argString ) throws Exception {
  
      super( name , argString , false ) ;

      _cellName      = name ;
      
      _args = getArgs() ;
      
    
      try{
      
//         if( _args.argc() < 1 )
//           throw new IllegalArgumentException( "Usage : ... <dbPath>" ) ;
         
      }catch( Exception e ){
         start() ;
         kill() ;
         throw e ;
      }
      
      start() ;
  
  }
  public void messageArrived( CellMessage msg ){
  
      Object obj     = msg.getMessageObject() ;
      Object answer  = "PANIX" ;
      
      try{
         say( "Message : "+obj.getClass() ) ;
         if( ( ! ( obj instanceof Object [] ) ) ||
             (  ((Object[])obj).length < 3 )    ||
             ( !((Object[])obj)[0].equals("request") ) ){
             String r = "Illegal message object received from : "+
                         msg.getSourcePath() ;
             esay( r ) ;
             throw new Exception( r ) ;
         }
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
      
      
      response[5] = new Boolean(true) ;
      return response ;
  }
}
