package eurogate.misc.users ;
import java.io.* ;
import java.util.* ;
import eurogate.misc.* ;

public class AclDb {

   private class AclItem implements AcDictionary {
       private String    _name     = null ;
       private Hashtable _users    = new Hashtable() ;
       private String    _inherits = null ;
       private AclItem( String name ){ _name = name ; }
       private void setInheritance( String aclItem ){
           _inherits = aclItem ;
       }
       private void addAccess( String user , boolean access ){
           _users.put( user , new Boolean(access) ) ;
       }
       private Enumeration getUsers(){ return _users.keys() ; }
       private void removeUser(String user ){
          _users.remove( user ) ;
       }
       private Boolean getUserAccess(String user) 
               throws NoSuchElementException{
           return  (Boolean)_users.get(user) ;
       }
       private void merge( String user , Boolean access ){
           if( _users.get( user ) == null )
              _users.put( user , access ) ;
       }
       //
       // the AcDictionary interface
       //
       public Enumeration getPrincipals(){
           return _users.keys() ;
       }
       public boolean getPermission( String principal ){
           Boolean ok = (Boolean)_users.get(principal) ;
           if( ok == null )
              throw new NoSuchElementException(principal) ;
           return ok.booleanValue() ;
       }
       public boolean isResolved(){ return _inherits == null ; }
       public String getInheritance(){ return _inherits ; }
   }
   private File      _aclDir = null ;
   private AgingHash _hash   = new AgingHash(20) ;
   public AclDb( File aclDir ){
      if( ! aclDir.isDirectory() )
         throw new
         IllegalArgumentException("Not a acl DB(not a dir)");
      _aclDir = aclDir ;
   }
   private void putAcl( String aclName , AclItem item )
           throws DatabaseException{
       _storeAcl( aclName , item ) ;
       _hash.put( aclName , item ) ;
       return ;       
   }
   private AclItem getAcl( String aclName )
           throws NoSuchElementException{
       AclItem item = (AclItem)_hash.get( aclName ) ;
       if( item != null )return item ;
       return _loadAcl( aclName ) ;       
   }
   private void _storeAcl( String aclName , AclItem item )
           throws DatabaseException {
   
       File file = new File( _aclDir , "."+aclName ) ;
       PrintWriter pw = null ;
       try{     
           pw = new PrintWriter(
                    new FileWriter( file ) ) ;
      }catch(IOException ioe ){
          throw new
          DatabaseException( "Can't create : "+aclName ) ;
      }
      String inherit = item.getInheritance() ;
      if( inherit != null )
         pw.println( "$="+inherit ) ;
      Enumeration e = item.getUsers() ;
      while( e.hasMoreElements() ){
          String user = e.nextElement().toString() ;
          Boolean access = item.getUserAccess(user) ;
          pw.println(user+"="+(access.booleanValue()?"allowed":"denied")) ;
      }
      pw.close() ;
      file.renameTo( new File( _aclDir , aclName ) ) ; 
      return ;
   }
   private AclItem _loadAcl( String aclName )
           throws NoSuchElementException {
           
       File file = new File( _aclDir , aclName ) ;
       if( ! file.exists() )
          throw new
          NoSuchElementException( "Acl  not found : "+aclName ) ;   
           
        BufferedReader br   = null ;
        
        try{
          br = new BufferedReader( new FileReader( file ) ) ;
        }catch( IOException e ){
           throw new 
           NoSuchElementException( "No found "+file ) ;
        }
        String line = null ;
        StringTokenizer st   = null ;
        AclItem         item = new AclItem( aclName ) ;
        String user = null , access = null ;
        try{
           while( ( line = br.readLine() ) != null ){
              st     = new StringTokenizer( line , "=" ) ;
              user   = st.nextToken() ;
              access = st.nextToken() ;
              if( user.equals("$") ){
                 item.setInheritance(access) ;
              }else if( access.equals("allowed" ) ){
                 item.addAccess( user , true ) ;
              }else if( access.equals("denied" ) ){
                 item.addAccess( user , false ) ;
              }
           }
        }catch(NoSuchElementException nsee ){
           throw new 
           NoSuchElementException( "Syntax error in "+file ) ;        
        }catch(IOException ioe ){
           throw new 
           NoSuchElementException( "IOError on "+file ) ;        
        }catch(Exception ee ){
           throw new 
           NoSuchElementException( "IOError on "+file ) ;        
        }finally{
            try{ br.close() ; }catch(Exception ee){}
        } 
        return item ;
   }
   public synchronized void createAclItem( String aclItem )
          throws DatabaseException {
       putAcl( aclItem , new AclItem(aclItem) ) ;
   }
   public synchronized void removeAclItem( String aclItem )
          throws DatabaseException {
      _hash.remove( aclItem ) ;
      new File( _aclDir , aclItem ).delete() ;
      return  ;
   }
   public synchronized void setInheritance( String aclItem , String inheritsFrom)
          throws DatabaseException {
       AclItem item = getAcl( aclItem ) ;
       item.setInheritance( inheritsFrom ) ;
       putAcl( aclItem , item ) ;
       return ;
   }
   public synchronized void addAllowed( String aclItem , String user )
          throws DatabaseException {          
       AclItem item = getAcl( aclItem ) ;
       item.addAccess( user , true ) ;
       putAcl( aclItem , item ) ;
       return ;
   }
   public synchronized void addDenied( String aclItem , String user )
          throws DatabaseException {
          
       AclItem item = getAcl( aclItem ) ;
       item.addAccess( user , false ) ;
       putAcl( aclItem , item ) ;
       return ;
   }
   public synchronized void removeUser( String aclItem , String user )
          throws DatabaseException {
          
       AclItem item = getAcl( aclItem ) ;
       item.removeUser( user ) ;
       putAcl( aclItem , item ) ;
       return ;
   }
   public AcDictionary getPermissions( String aclName , boolean resolve )
          throws NoSuchElementException{
          
        return resolve ? _resolveAclItem( aclName ) : getAcl( aclName ) ;
      
   }
   private AclItem _resolveAclItem( String aclItem )
           throws NoSuchElementException{
       AclItem inItem   = null ;
       AclItem item     = getAcl( aclItem ) ;  
       String inherited = null ;
       while( ( inherited = item.getInheritance() ) != null ){
          inItem = getAcl( inherited ) ;
          Enumeration e = inItem.getUsers() ;
          while( e.hasMoreElements() ){
             String  user   = (String)e.nextElement() ;
             item.merge( user , inItem.getUserAccess(user) ) ;
          }
          item = inItem ;
       }
       item.setInheritance(null);
       return item ;     
   }
   public synchronized boolean check( String aclItem , 
                         String user , 
                         UserRelationable relations ){
       try{    
          AclItem item = _resolveAclItem( aclItem ) ;
          return _check( item , user , relations ) ;
       }catch(Exception e){
          return false ;
       }   
   }
   private boolean _check( AclItem item , 
                           String user , 
                           UserRelationable relations )
           throws NoSuchElementException{
           
       Boolean ok = item.getUserAccess(user) ;
       if( ok != null )return ok.booleanValue() ;
           
       Vector  v = new Vector() ;
       Boolean x = null ;

       v.addElement( user ) ;

       for( int i = 0 ; i < v.size() ; i++ ){
           user = (String)v.elementAt(i) ;
           if( ( x = item.getUserAccess( user ) ) != null ){
              if( x.booleanValue() )return true ;
              continue ;
           }
           Enumeration e = relations.getParentsOf( user ) ;
           while( e.hasMoreElements() )
             v.addElement(e.nextElement()) ;

       }
       return false ;
   }
}
