package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

public class VolumeSetHandle extends CdbElementHandle {

   private VolumeSetElement _volumeSet = null ;
   
   public VolumeSetHandle( String name  ,
                           CdbContainable container ,
                           CdbElementable element    ){
      super( name , container ,element ) ;                    
      if( ! ( element instanceof VolumeSetElement ) )
         throw new IllegalArgumentException( "PANIC : not a volumeset" ) ;
         
      _volumeSet = (VolumeSetElement)element ;
                           
   }
   public PvrVolumeSubsetHandle createPvrVolumeSubset( String name )
       throws CdbException , InterruptedException {
        PvrVolumeSubsetHandle pvs =
             (PvrVolumeSubsetHandle)_volumeSet.createElement( name ) ;
             
          pvs.open( CdbLockable.WRITE ) ;
          pvs.setWindowSize(0) ;
          pvs.setWindow( "-" , "-" ) ;
          pvs.setAttribute( "volumeList" , new String[0] ) ;
          pvs.close( CdbLockable.COMMIT ) ;
        return pvs ;
   } 
   public PvrVolumeSubsetHandle getPvrVolumeSubsetByName( String name )
       throws CdbException , InterruptedException {
   
        return  (PvrVolumeSubsetHandle)_volumeSet.getElementByName( name ) ;
   } 
   public String [] getPvrVolumeSubsetNames(){
      return _volumeSet.getElementNames() ;
   }
   public void addVolume( VolumeHandle volume )
       throws CdbException , InterruptedException {
   
       volume.open( CdbLockable.WRITE ) ;
       
       String    pvr     = (String)volume.getAttribute( "pvr" ) ;
       
       PvrVolumeSubsetHandle pvrSubset = null ;
       try{
          pvrSubset = getPvrVolumeSubsetByName( pvr ) ;
       }catch( CdbException e ){
          pvrSubset = createPvrVolumeSubset( pvr ) ;
       }
       pvrSubset.open(CdbLockable.WRITE ) ;
       pvrSubset.addListItem( "volumeList" , volume.getName() ) ;
       pvrSubset.close( CdbLockable.COMMIT ) ;
       
       volume.setAttribute( "volumeSet" , getName() ) ;
        
       volume.close( CdbLockable.COMMIT ) ;
   
   }                       
}
