package eurogate.db.pvr ;

import dmg.util.cdb.* ;
import java.io.* ;

public class PvrDb extends CdbGLock  {

   private CdbDirectoryContainer _cartrideContainer   = null ;
   private CdbDirectoryContainer _driveContainer      = null ;
   
   public PvrDb( File file , boolean create ) throws CdbException {
      
      if( ! file.isDirectory() )
         throw new CdbException( "Database doesn't exits : "+file ) ;
         
      _cartrideContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvr.PvrCartridgeHandle.class ,
                          new File( file , "cartridges" ) ,
                          create ) ;
                          
      _driveContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvr.PvrDriveHandle.class ,
                          new File( file , "drives" ) ,
                          create ) ;
                          
   
   }
   public PvrCartridgeHandle createCartridge( String name )
       throws CdbException , InterruptedException {
       return (PvrCartridgeHandle)_cartrideContainer.createElement( name ) ;
   }
   public PvrCartridgeHandle getCartridgeByName( String name )
       throws CdbException , InterruptedException {
       return (PvrCartridgeHandle)_cartrideContainer.getElementByName( name ) ;
   }
   public String [] getCartridgeNames(){
       return _cartrideContainer.getElementNames() ;
   }
   public PvrDriveHandle createDrive( String name )
       throws CdbException , InterruptedException {
       return (PvrDriveHandle)_driveContainer.createElement( name ) ;
   }
   public PvrDriveHandle getDriveByName( String name )
       throws CdbException , InterruptedException {
       return (PvrDriveHandle)_driveContainer.getElementByName( name ) ;
   }
   public String [] getDriveNames(){
       return _driveContainer.getElementNames() ;
   }
} 
