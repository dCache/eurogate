package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

public class PvrHandle extends CdbElementHandle {
   private PvrElement _pvr = null ;
   public PvrHandle(   String  name ,
                       CdbContainable container  ,
                       CdbElementable element ){
    
        super( name , container , element ) ; 
        
        _pvr = (PvrElement)element ;                  
   }
   /**
     *  create a new drive
     */
   public DriveHandle createDrive( String name )
          throws CdbException , InterruptedException {
      return  _pvr.createDrive( name ) ;
   }
   /**
     *  get the drive handle by name
     */
   public DriveHandle getDriveByName( String name )
          throws CdbException , InterruptedException {
      return  _pvr.getDriveByName( name ) ;
   }
   /**
     *  get all drive names of this pvr.
     */
   public String [] getDriveNames()
          throws CdbException , InterruptedException {
      return  _pvr.getDriveNames() ;
   }
   public void removeDrive( String name )
          throws CdbException , InterruptedException {
      _pvr.removeDrive( name ) ;
   }
   //
   // cartridges
   //
   /**
     *  create a new cartridge
     */
   public CartridgeHandle createCartridge( String name )
          throws CdbException , InterruptedException {
      return  _pvr.createCartridge( name ) ;
   }
   /**
     *  get the cartridge handle by name
     */
   public CartridgeHandle getCartridgeByName( String name )
          throws CdbException , InterruptedException {
      return  _pvr.getCartridgeByName( name ) ;
   }
   /**
     *  get all cartridge names of this pvr.
     */
   public String [] getCartridgeNames()
          throws CdbException , InterruptedException {
      return  _pvr.getCartridgeNames() ;
   }

}
 
