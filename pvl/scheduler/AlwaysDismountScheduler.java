package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import dmg.util.cdb.* ;
import java.util.* ;

public class AlwaysDismountScheduler extends PvlScheduler {

   private PvlDb _pvlDb = null ;
   
   public AlwaysDismountScheduler( PvlDb pvlDb ){
       super( pvlDb ) ;
       _pvlDb = pvlDb ;
       say( "AlwaysDismountScheduler ready" ) ;
   }
   public AlwaysDismountScheduler( PvlDb pvlDb , Dictionary hash ){
       super( pvlDb , hash ) ;
       _pvlDb = pvlDb ;
       say( "AlwaysDismountScheduler ready" ) ;
   }
   public PvlResourceModifier 
      dismountAction( PvlResourceRequestQueue requestQueue ,
                      PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      
      PvlResourceRequest request   = null ; 
      PvrSet             pvrSet    = getPvrSet() ;
      DriveInfo          driveInfo = null ;
      String             volumeStatus = null ;
      //
      // we loop over all pending requests
      //
      String      pvrName  = modifier.getPvr() ;
      
      PvrInfo pvrInfo = pvrSet.getPvrInfoByName( pvrName ) ;
      
      for( int i = 0 ; i < requestQueue.getRequestCount() ; i++ ){

          request = requestQueue.getRequestAt(i) ;
          say( "Scanning "+i+" : "+request ) ;
          //
          //
          if( request.getDirection().equals("get") ){
              //
             // is the required cartridge in use ?
             //
             String neededCart = request.getCartridge() ;
             
             driveInfo = pvrInfo.getDriveByCartridge( neededCart ) ;
             if( driveInfo != null )continue ;  
             //
             // the required cartridge is not in use.
             // So we take it.
             //
             request.setDrive(modifier.getDrive()) ;
             return request ;
             
          }else if( request.getDirection().equals("put") ){
              long fileSize   = request.getFileSize() ;
              long size       = 0 ;
              String                cartName    = null ;
              VolumeSetHandle       volumeSet   = null ;
              PvrVolumeSubsetHandle subset      = null ;
              String            []  volumeNames = null ;
              String                volumeName  = null ;
              VolumeHandle          volume      = null ;
              try{
                 volumeSet =_pvlDb.getVolumeSetByName(request.getVolumeSet()) ;
                 subset    = volumeSet.getPvrVolumeSubsetByName( pvrName ) ;
              }catch( Exception eeee ){
                 continue ;
              }
              subset.open( CdbLockable.READ ) ;
                 volumeNames = subset.getVolumeNames() ;
              subset.close( CdbLockable.COMMIT ) ;
              for( int j = 0; j < volumeNames.length ; j++ ){
                //
                // get the volume attributes
                //
                volumeName = volumeNames[j] ;
                volume     = _pvlDb.getVolumeByName( volumeName ) ;
                volume.open( CdbLockable.READ ) ;
                   size         = volume.getResidualBytes() ;
                   cartName     = volume.getCartridge() ;
                   volumeStatus = volume.getStatus() ;
                volume.close( CdbLockable.COMMIT ) ;
                //
                // is the volume size of and the cartridge not in use ?
                //
                if( 
                    ( size > fileSize ) &&
                    ( ! volumeStatus.equals("full") ) &&
                    ( pvrInfo.getDriveByCartridge( cartName ) == null )
                     
                  ){
                        say( "NEW REQUEST : ("+pvrName+
                             ","+cartName+","+modifier.getDrive()+")");

                        request.setVolume(volumeName);
                        request.setDrive(modifier.getDrive()) ;
                        request.setCartridge(cartName) ;
                        request.setPvr( pvrName ) ;
                        return request ;
                }
                   
              }
              
          }
      }
      return null ;
   }
   public PvlResourceModifier 
      deallocateAction( PvlResourceRequestQueue requestQueue ,
                        PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      
       modifier.setActionEvent( "dismounted" ) ;
       return modifier ;
   }
   public PvlResourceModifier newPutRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) 
           throws Exception {
      //
      // get a snapshot of the drives and count the total number 
      // of drives.
      //
      long fileSize   = request.getFileSize() ;
      VolumeSetHandle 
            volumeSet =  _pvlDb.getVolumeSetByName(request.getVolumeSet()) ;
      
      PvrSet pvrSet    = getPvrSet() ;
       
      String pvrName  = null ;
      PvrVolumeSubsetHandle subset = null ;
      String [] volumeNames = null ;
      String    volumeName  = null ;
      String    cartName    = null ;
      VolumeHandle volume   = null ;
      long         size     = 0 ;
      PvrInfo      pvrInfo      = null ;
      DriveInfo    driveInfo    = null ;    
      DriveInfo    emptyDrive   = null ;
      String       volumeStatus = null ;
      //
      //  loop over all pvrs
      //
  
      for( Enumeration e = pvrSet.pvrs() ; e.hasMoreElements() ; ){
         pvrInfo    = (PvrInfo)e.nextElement() ;
         pvrName    = pvrInfo.getName() ;
         say( "newPutRequest : scanning pvr : "+pvrName ) ;
         //
         // is there an empty drive ?
         //
         emptyDrive = pvrInfo.getEmptyDrive() ;
         //
         // no empty drive : skip this pvr
         //
         if( emptyDrive == null ){
            say( "newPutRequest : no empty drive found in "+pvrName ) ;
            continue ;
         }
         //
         // get the corresponding PvrVolumeSubset ( skip it, if it doesn't exist)
         //
         try{
            subset  = volumeSet.getPvrVolumeSubsetByName( pvrName ) ;
         }catch( Exception notFound ){
            say( "newPutRequest : no pvrSubSet found : "+pvrName ) ;
            continue ;
         }
         //
         // get a list of all volumes within this volumeSubSet
         //
         subset.open( CdbLockable.READ ) ;
            volumeNames = subset.getVolumeNames() ;
         subset.close( CdbLockable.COMMIT ) ;
         //
         // find a volume which has enough bytes left AND
         // is not in a drive
         //
         for( int j = 0; j < volumeNames.length ; j++ ){
            //
            // get the volume attributes
            //
            volumeName = volumeNames[j] ;
            volume     = _pvlDb.getVolumeByName( volumeName ) ;
            volume.open( CdbLockable.READ ) ;
               size         = volume.getResidualBytes() ;
               cartName     = volume.getCartridge() ;
               volumeStatus = volume.getStatus() ;
            volume.close( CdbLockable.COMMIT ) ;
            //
            // is the volume size ok and the cartridge not in use ?
            //
            say( "Checking cart="+cartName+";rest="+size+";size="+fileSize) ;
            if( 
                ( size > fileSize ) &&
                ( ! volumeStatus.equals("full") ) &&
                ( pvrInfo.getDriveByCartridge( cartName ) == null )

              ){
                     request.setCartridge( cartName ) ;
                     request.setVolume( volumeName ) ;
                     request.setPvr( pvrName ) ;
                     request.setDrive( emptyDrive.name ) ;

                     return request ;
            }

         }
      } 
      return null ;      
      
   }
   public PvlResourceModifier newGetRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) throws Exception {
                    
       PvrHandle   pvr          = _pvlDb.getPvrByName( request.getPvr() ) ;
       String      reqCartridge = request.getCartridge() ;
       PvrSet      pvrSet       = getPvrSet() ;
       
       PvrInfo pvrInfo     = pvrSet.getPvrInfoByName( request.getPvr() ) ;
       DriveInfo driveInfo = pvrInfo.getEmptyDrive() ;
       
       if( ( driveInfo == null ) ||
           ( pvrInfo.getDriveByCartridge( request.getCartridge() ) != null ) ){
          say( "newGetRequest : couldn't get an empty drive or cart. in use" ) ;
          return null ;
       }
       request.setDrive( driveInfo.name ) ;
       
       return request ;
   }

}
