package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ;

 
public class      PvrElement 
       extends    CdbGLock
       implements CdbContainable, CdbElementable  {
       
   private CdbDirectoryContainer _drives     = null ;
   private CdbDirectoryContainer _cartridges = null ;
   
   public PvrElement(   CdbLockable superLock ,  
                        File file ,
                        boolean  create ) throws CdbException{
    
//       System.out.println( "Pvr.java.<init>" ) ;
       
       if( create )file.mkdir() ;
       
//       System.out.println( "Pvr.java.<init> creating drives" ) ;
        _drives  = new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvl.DriveHandle.class  ,
                          new File( file , "drives" ) ,
                          create ) ;


//       System.out.println( "Pvr.java.<init> creating cartridges" ) ;
       _cartridges = new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvl.CartridgeHandle.class ,
                          new File( file , "cartridges" ) ,
                          create ) ;

   }
   public void remove() throws CdbException {
   
   }
   public void unlinkElement( String name ) throws CdbException {
   
   }
   public DriveHandle createDrive( String name )
          throws CdbException , InterruptedException {
    
      return (DriveHandle)_drives.createElement( name ) ;      
   }
   public DriveHandle getDriveByName( String name )
          throws CdbException , InterruptedException {
    
      return (DriveHandle)_drives.getElementByName( name ) ;      
   }
   public String [] getDriveNames(  ) {
    
      return (String [])_drives.getElementNames() ;      
   }
   public void removeDrive( String name )
          throws CdbException , InterruptedException {
       _drives.removeElement( name ) ;
   }
   public CartridgeHandle createCartridge( String name )
          throws CdbException , InterruptedException {
    
      return (CartridgeHandle)_cartridges.createElement( name ) ;      
   }
   public CartridgeHandle getCartridgeByName( String name )
          throws CdbException , InterruptedException {
    
      return (CartridgeHandle)_cartridges.getElementByName( name ) ;      
   }
   public String [] getCartridgeNames(  ) {
    
      return (String [])_cartridges.getElementNames() ;      
   }
 
 
}
