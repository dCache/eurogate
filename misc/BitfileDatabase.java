package eurogate.misc ;

import eurogate.vehicles.* ;

import java.io.* ;
import java.util.* ;

public class BitfileDatabase  {
   private File      _dbBase    = null ;
   private Hashtable _groupList = new Hashtable() ;
   
   public BitfileDatabase( String dbFileName ) throws DatabaseException {
       _dbBase = new File( dbFileName ) ;
       if( ! _dbBase.isDirectory() )
          throw new DatabaseException( 1 , "Database not found : "+dbFileName ) ;
       
   }
   public  void createStorageGroup( String group ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( groupDir.isDirectory() )
          throw new DatabaseException( 2 , "StorageGroup exists: "+group ) ;
       
       if( ! groupDir.mkdir() ) 
          throw new 
          DatabaseException( 2 , "creating StorageGroup failed : "+group ) ;
       return ;
   }
   public  void removeStorageGroup( String group ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( ! groupDir.isDirectory() )
          throw new DatabaseException( 2 , "StorageGroup not found : "+group ) ;
       
       if( ! groupDir.delete() ) 
          throw new 
          DatabaseException( 2 , "removing StorageGroup failed : "+group ) ;
       return ;
   }
   public  void storeBitfileId( String group , BitfileId bitfile ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( ! groupDir.isDirectory() )
          throw new DatabaseException( 2 , "StorageGroup not found : "+group ) ;
       
       File bitfileFile = new File( groupDir , bitfile.getName() ) ;
             
       ObjectOutputStream output   = null  ;
       try{
           output = new ObjectOutputStream( 
                    new FileOutputStream( bitfileFile ) ) ;
           output.writeObject( bitfile ) ; 
       }catch( Exception e ){
           throw new 
           DatabaseException( 5 , "Can't write dbRecord : "+bitfile.getName() ) ;
       }finally{
           try{ output.close() ; }catch(Exception eeee){}
       }
       return  ;
           
   }
   public  void removeBitfileId( String group , String bfid ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( ! groupDir.isDirectory() )
          throw new 
          DatabaseException( 2 , "StorageGroup not found : "+group ) ;
       
       File bfidFile = new File( groupDir , bfid ) ;
       if( ! bfidFile.delete() )
          throw new DatabaseException( 3 , "Bfid not found in "+group+" : "+bfid ) ;
       return ;  

   }
   public  String [] getGroups()throws DatabaseException{
       String [] groups = _dbBase.list() ;
       if( groups == null )
          throw new 
          DatabaseException( 3 , "Not groups found in database" ) ;
       
       return groups ;
   }
   public  String [] getBitfileIds( String group ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( ! groupDir.isDirectory() )
          throw new DatabaseException( 2 , "StorageGroup not found : "+group ) ;

       return groupDir.list() ;
   }
   public  BitfileId getBitfileId( String bfid ) 
           throws DatabaseException {
       String [] groups = getGroups() ;
       for( int i = 0 ; i < groups.length ; i++ ){
           try{
               return getBitfileId( groups[i] , bfid ) ;
           }catch( DatabaseException dbe ){
              continue ;
           }
       
       }
       throw new DatabaseException( 10 , "Bfid not found : "+bfid ) ;
   }
   public  String getGroupByBitfileId( String bfid ) 
           throws DatabaseException {
       String [] groups = getGroups() ;
       for( int i = 0 ; i < groups.length ; i++ ){
           try{
               getBitfileId( groups[i] , bfid ) ;
               return groups[i] ;
           }catch( DatabaseException dbe ){
              continue ;
           }
       
       }
       throw new DatabaseException( 10 , "Bfid not found : "+bfid ) ;
   }
   public  BitfileId getBitfileId( String group , String bfid ) 
           throws DatabaseException {
           
       File groupDir = new File( _dbBase , group ) ;
       if( ! groupDir.isDirectory() )
          throw new DatabaseException( 2 , "StorageGroup not found : "+group ) ;
       
       File bfidFile = new File( groupDir , bfid ) ;
       if( ! bfidFile.isFile() )
          throw new DatabaseException( 3 , "Bfid not found in "+group+" : "+bfid ) ;
              
       ObjectInputStream  input    = null  ;
       BitfileId          bitfile  = null ;
       try{
           input = new ObjectInputStream( new FileInputStream( bfidFile) ) ;
           Object  obj = input.readObject() ; 
           if( ( obj == null ) || ! ( obj instanceof BitfileId ) )
               throw new 
               DatabaseException( 5 , "Invalid database record : "+bfid ) ;
           bitfile = (BitfileId)obj ;
       }catch( Exception e ){
           throw new DatabaseException( 5 , "Can't access dbRecord : "+bfid ) ;
       }finally{
           try{ input.close() ; }catch(Exception eeee){}
       }
       return bitfile ;
   }


}
