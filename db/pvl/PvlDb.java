package eurogate.db.pvl ;

import dmg.util.cdb.* ;
import java.io.* ;

public class PvlDb extends CdbGLock  {

   private CdbDirectoryContainer _pvrContainer                  = null ;
   private CdbDirectoryContainer _volumeContainer               = null ;
   private CdbDirectoryContainer _volumeSetContainer            = null ;
   private CdbDirectoryContainer _volumeDescriptionContainer    = null ;
   private CdbDirectoryContainer _cartridgeDescriptionContainer = null ;
   
   public PvlDb( File file , boolean create ) throws CdbException {
      
      if( ! file.isDirectory() )
         throw new CdbException( "Database doesn't exits : "+file ) ;
         
      _pvrContainer = 
               new CdbDirectoryContainer(
                          this ,
                          eurogate.db.pvl.PvrElement.class ,
                          eurogate.db.pvl.PvrHandle.class ,
                          new File( file , "pvrs" ) ,
                          create ) ;
                          
      _volumeContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvl.VolumeHandle.class ,
                          new File( file , "volumes" ) ,
                          create ) ;
                          
      _volumeSetContainer = 
               new CdbDirectoryContainer(
                          this ,
                          eurogate.db.pvl.VolumeSetElement.class ,
                          eurogate.db.pvl.VolumeSetHandle.class ,
                          new File( file , "volumeSets" ) ,
                          create ) ;
                          
      _volumeDescriptionContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvl.VolumeDescriptorHandle.class ,
                          new File( file , "volumeDescriptors" ) ,
                          create ) ;
   
      _cartridgeDescriptionContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          eurogate.db.pvl.CartridgeDescriptorHandle.class ,
                          new File( file , "cartridgeDescriptors" ) ,
                          create ) ;
   
   }
   public VolumeSetHandle createVolumeSet( String name )
       throws CdbException , InterruptedException {
       return (VolumeSetHandle)_volumeSetContainer.createElement( name ) ;
   }
   public VolumeSetHandle getVolumeSetByName( String name )
       throws CdbException , InterruptedException {
       return (VolumeSetHandle)_volumeSetContainer.getElementByName( name ) ;
   }
   public String [] getVolumeSetNames(){
       return _volumeSetContainer.getElementNames() ;
   }
   //
   // the pvr
   //
   public PvrHandle createPvr( String name )
       throws CdbException , InterruptedException {
       return (PvrHandle)_pvrContainer.createElement( name ) ;
   }
   public PvrHandle getPvrByName( String name )
       throws CdbException , InterruptedException {
       return (PvrHandle)_pvrContainer.getElementByName( name ) ;
   }
   public String [] getPvrNames(){
      return _pvrContainer.getElementNames() ;
   }
   //
   //   the volume descriptor
   //
   public VolumeDescriptorHandle createVolumeDescriptor( String name )
       throws CdbException , InterruptedException {
         VolumeDescriptorHandle vd =
               (VolumeDescriptorHandle)
                  _volumeDescriptionContainer.createElement( name ) ;
                  
       vd.open( CdbLockable.WRITE ) ;
         vd.setAttribute( "size" , "0" ) ;
       vd.close( CdbLockable.COMMIT ) ;

         return vd ;
   }
   public String [] getVolumeDescriptorNames(){
      return _volumeDescriptionContainer.getElementNames() ;
   }
   public VolumeDescriptorHandle 
          getVolumeDescriptorByName( String name )
       throws CdbException , InterruptedException {
      return (VolumeDescriptorHandle)
            _volumeDescriptionContainer.getElementByName( name ) ;
   }
   //
   //   the cartridge descriptor
   //
   public CartridgeDescriptorHandle createCartridgeDescriptor( String name )
       throws CdbException , InterruptedException {
       CartridgeDescriptorHandle cd =
                 (CartridgeDescriptorHandle)
                 _cartridgeDescriptionContainer.createElement( name ) ;
       cd.open( CdbLockable.WRITE ) ;
         cd.setAttribute( "type" , "unknown" ) ;
         cd.setAttribute( "mode" , "standard" ) ;
       cd.close( CdbLockable.COMMIT ) ;
       
       return cd ;
   }
   public String [] getCartridgeDescriptorNames(){
      return _cartridgeDescriptionContainer.getElementNames() ;
   }
   public CartridgeDescriptorHandle 
          getCartridgeDescriptorByName( String name )
       throws CdbException , InterruptedException {
      return (CartridgeDescriptorHandle)
            _cartridgeDescriptionContainer.getElementByName( name ) ;
   }
   
   //
   // the cartridge
   //
   public CartridgeHandle createCartridge( String pvrName , 
                                           String cartridgeName ,
                                           String cartridgeDescriptorName )
        throws CdbException , InterruptedException {
        
       PvrHandle pvr = (PvrHandle) _pvrContainer.getElementByName( pvrName ) ;

       CartridgeDescriptorHandle cartDesc =
           (CartridgeDescriptorHandle)
           _cartridgeDescriptionContainer.getElementByName( cartridgeDescriptorName ) ;

       CartridgeHandle cartridge = pvr.createCartridge( cartridgeName ) ;
       cartridge.open( CdbLockable.WRITE ) ;
         cartridge.setAttribute( "cartridgeDescriptor" , cartridgeDescriptorName ) ;
         cartridge.setAttribute( "pvr"        , pvrName ) ;
         cartridge.setAttribute( "status"     , "ok" ) ;
         cartridge.setAttribute( "mode"       , "online" ) ;
         cartridge.setAttribute( "volumeList" ,  new String[0] ) ;
         cartridge.setAttribute( "eor"        ,  "-" ) ;
         cartridge.setAttribute( "position"   ,  "0" ) ;
         cartridge.setAttribute( "usageCount" ,  "0" ) ;
       cartridge.close( CdbLockable.COMMIT ) ;
       cartDesc.incrementUsageCount() ;
       
       return cartridge ;
   }
   //
   // the volume
   //
   public VolumeHandle getVolumeByName( String volumeName )
       throws CdbException , InterruptedException {
     return (VolumeHandle)_volumeContainer.getElementByName( volumeName ) ;
   }
   public String [] getVolumeNames(){
      return _volumeContainer.getElementNames(  ) ;
   }
   public VolumeHandle createVolume( String pvrName ,
                                     String cartridgeName ,
                                     String volumeName , 
                                     String volumeDescriptorName )
       throws CdbException , InterruptedException {
       //
       // getElementByName will throw an exception if the target
       // doesn't exist.
       //
       PvrHandle pvr = (PvrHandle) _pvrContainer.getElementByName( pvrName ) ;
       
       
       VolumeDescriptorHandle volDesc =
           (VolumeDescriptorHandle)
           _volumeDescriptionContainer.getElementByName( volumeDescriptorName ) ;
        
       CartridgeHandle cartridge = pvr.getCartridgeByName( cartridgeName ) ;
       //
       // add the volume to the volume list of the cartridge
       //
       cartridge.open( CdbLockable.WRITE ) ;
//       try{
       cartridge.addListItem( "volumeList" , volumeName ) ;
//       }catch( IllegalArgumentException iae ){
//          System.out.println( "Problem !!! : "+iae ) ;
//          iae.printStackTrace() ;
//       }
       cartridge.close( CdbLockable.COMMIT ) ;
       
       
       VolumeHandle volume = (VolumeHandle)_volumeContainer.createElement( volumeName ) ;
       volDesc.open( CdbLockable.READ ) ;
       long size = volDesc.getSize() ;
       volDesc.close( CdbLockable.COMMIT ) ;
       //
       // set the cartridge name , the volumeDescriptor
       //
       volume.open( CdbLockable.WRITE ) ;
          volume.setAttribute( "volumeDescriptor" , volumeDescriptorName ) ;
          volume.setAttribute( "residualBytes"    , ""+size ) ;
          volume.setAttribute( "cartridge" , cartridgeName ) ;
          volume.setAttribute( "pvr"       , pvrName ) ;
          volume.setAttribute( "status"    , "empty" ) ;
          volume.setAttribute( "eor"       , "-" ) ;
          volume.setAttribute( "position"  , "0" ) ;
          volume.setAttribute( "files"     , "0" ) ;
       volume.close( CdbLockable.COMMIT ) ;
       
       volDesc.incrementUsageCount() ;
       
       return volume ;
   }
}
