package eurogate.store ;

import dmg.util.db.* ;

import java.util.* ;
import java.io.* ;

public class BfidHandle {

   private String _storageGroup = null ;
   private String _name         = null ;
   private DbResourceHandle  _handle = null ;
   
   public BfidHandle( DbResourceHandle handle , 
                      String name ,
                      String storageGroup ){
                      
     _handle       = handle ;            
     _storageGroup = storageGroup ;
     _name         = name ;
   }
   public String getStorageGroup(){ return _storageGroup ; }
   public String getName(){ return _name ; }
   public DbResourceHandle getResourceHandle(){ return _handle ; }
   public void open( int mode ) throws DbLockException, InterruptedException {
      _handle.open( mode ) ;
   
   }
   public void close() throws DbLockException, InterruptedException {
      _handle.close() ;
   }
   public String getCreationDate(){ 
       return  (String)_handle.getAttribute("creationTime") ;
   }
   public String getSize(){ 
       return  (String)_handle.getAttribute("size") ;
   }
   public String getStatus(){ 
       return  (String)_handle.getAttribute("status") ;
   }
   public void setStatus( String status ){
       _handle.setAttribute( "status" , status ) ;
   }
   public void remove(){ _handle.remove() ; }
} 
