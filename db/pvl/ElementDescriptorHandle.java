package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

 
public class  ElementDescriptorHandle extends CdbFileRecordHandle {

   public ElementDescriptorHandle( String  name ,
                                     CdbContainable container  ,
                                     CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   public void incrementUsageCount() throws CdbLockException, InterruptedException{
     open( CdbLockable.WRITE ) ;
        setUsageCounter( getUsageCounter() + 1 ) ;
     close( CdbLockable.COMMIT ) ;
   }
   public void decrementUsageCount()throws CdbLockException, InterruptedException{
     open( CdbLockable.WRITE ) ;
        setUsageCounter( getUsageCounter() - 1 ) ;
     close( CdbLockable.COMMIT ) ;
   }
   public int getUsageCounter(){
      String uc = (String)getAttribute( "usageCounter" ) ;
      if( uc == null )return 0 ;
      int counter = 0 ;
      try{
         counter = Integer.parseInt( uc ) ;
      }catch( Exception e ){
         counter = 0 ;
      }
      return counter ;
   }
   public void setUsageCounter( int counter ){
      setAttribute( "usageCounter" , ""+counter ) ;
   }
} 
