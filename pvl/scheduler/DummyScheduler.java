package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import dmg.util.cdb.* ;
import java.util.* ;

public class DummyScheduler implements PvlResourceScheduler {

   private PvlDb _pvlDb = null ;
   
   private class DriveInfo {
       public String  cartridge ;
       public boolean usable ;
       public boolean allocated ;
       public String  name ;
       public DriveInfo( String name , String cartridge ,
                         boolean allocated , boolean usable ) {
                         
         this.cartridge = cartridge ;
         this.name      = name ;
         this.allocated = allocated ;
         this.usable    = usable ;                 
       }
       public String toString(){
          return "Drive="+name+";cart="+cartridge+";u="+usable+";a="+allocated ;
       }
   }
   private class PvrInfo {
       private String _name ;
       private Vector _drives = new Vector() ;
       private Hashtable _byCartridge = new Hashtable() ;
       
       private DriveInfo _unallocatedDrive = null ,
                         _emptyDrive       = null ;
                         
       public PvrInfo( String name ){ _name = name ; }
       public String getName(){ return _name ; }
       public void addDriveInfo( DriveInfo drive ){
           _drives.addElement( drive ) ;
           _byCartridge.put( drive.cartridge , drive ) ;
           if( drive.usable ){
              if( ! drive.allocated )_unallocatedDrive = drive ;
              
              if( drive.cartridge.equals("empty") )_emptyDrive = drive ;
           }
       }
       public DriveInfo getEmptyDrive(){ return _emptyDrive ; }
       public DriveInfo getUnallocatedDrive(){ return _unallocatedDrive ; }
       public DriveInfo getDriveByCartridge( String cartridge ){       
          return (DriveInfo)_byCartridge.get( cartridge ) ;
       }
       public String toString(){
         StringBuffer sb = new StringBuffer() ;
         sb.append( "Pvr="+_name+"\n" ) ;
         Enumeration e = _drives.elements() ;
         for( ; e.hasMoreElements() ; ){
            sb.append( e.nextElement().toString() + "\n" ) ;
         }
         return sb.toString() ;
       }
   }
   private class PvrSet {
       private Hashtable _pvrs = new Hashtable() ;
       public Enumeration pvrs(){
          return _pvrs.elements() ;
       }
       public PvrInfo getPvrInfoByName( String pvrName ){
          return (PvrInfo)_pvrs.get( pvrName ) ;
       }
       public void addPvrInfo( PvrInfo info ){
          _pvrs.put( info.getName() , info ) ;
       }
       public String toString(){
         StringBuffer sb = new StringBuffer();
         Enumeration e = _pvrs.elements() ;
         for( ; e.hasMoreElements() ; ){
            sb.append( e.nextElement().toString()  ) ;
         }
         return sb.toString() ;
       }
   
   }
   
   public DummyScheduler( PvlDb pvlDb ){
       _pvlDb = pvlDb ;
       System.out.println( "DummyScheduler ready" ) ;
   }
   private void say( String msg ){
      System.out.println( "SCHEDULER : "+msg ) ;
   }
   public PvlResourceModifier []
      nextEvent( PvlResourceRequestQueue requestQueue ,
                 PvlResourceModifier []  modifierList   )
                 
      throws CdbException , InterruptedException            {
      
      if( modifierList.length != 1 )
        throw new 
        IllegalArgumentException( "modifierList.lenght != 1 : not supported" );
       
      PvlResourceModifier  modifier = modifierList[0] ;
      PvlResourceModifier  result   = null ;

      say( "Modifier arrived : "+modifier ) ;
      
      String action = modifier.getActionEvent() ;
      
      if( action.equals( "dismounted" ) ){
         result = dismountAction( requestQueue , modifier ) ;
      }else if( action.equals( "deallocated" ) ){
         result = deallocateAction( requestQueue , modifier ) ;
      }else if( action.equals( "newRequest" ) ){
         result = newRequestAction( requestQueue , modifier ) ;
      }else{
        throw new 
        IllegalArgumentException( " action not supported : "+action );
      }
      
      PvlResourceModifier [] resultList = new PvlResourceModifier[1] ;
      if( result == null )return null ;
      resultList[0] = result ;
      return resultList ;
      
   }
   private PvlResourceModifier 
      newRequestAction( PvlResourceRequestQueue requestQueue ,
                        PvlResourceModifier  modifier  )

      throws CdbException , InterruptedException            {
      
      try{  
         PvlResourceRequest request = (PvlResourceRequest)modifier ;    
         //
         // the get reqeust 
         //
         if( request.getDirection().equals("get") ){
            return newGetRequest( requestQueue , request ) ;
         }else if( request.getDirection().equals("put") ){
            return newPutRequest( requestQueue , request ) ;
         }else
           throw new IllegalArgumentException("put not supported" ) ;
      }catch( Exception e){
         e.printStackTrace() ;
         throw new IllegalArgumentException( e.toString()) ;
      }
   }
 
   private PvlResourceModifier 
      dismountAction( PvlResourceRequestQueue requestQueue ,
                      PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      
      PvlResourceRequest request   = null ; 
      PvrSet             pvrSet    = getPvrSet() ;
      DriveInfo          driveInfo = null ;
      //
      // we loop over all pending requests
      //
      String      pvrName  = modifier.getPvr() ;
//      PvrHandle   pvr      = _pvlDb.getPvrByName( pvrName ) ;
      
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
                   size     = volume.getResidualBytes() ;
                   cartName = volume.getCartridge() ;
                volume.close( CdbLockable.COMMIT ) ;
                //
                // is the volume size of and the cartridge not in use ?
                //
                if( 
                    ( size > fileSize ) &&
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
   private PvlResourceModifier 
      deallocateAction( PvlResourceRequestQueue requestQueue ,
                        PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      
       modifier.setActionEvent( "dismounted" ) ;
       return modifier ;
   }
   private PvlResourceModifier newPutRequest(
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
      PvrInfo      pvrInfo   = null ;
      DriveInfo    driveInfo   = null ;    
      DriveInfo    emptyDrive  = null ;
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
               size     = volume.getResidualBytes() ;
               cartName = volume.getCartridge() ;
            volume.close( CdbLockable.COMMIT ) ;
            //
            // is the volume size ok and the cartridge not in use ?
            //
            say( "Checking cart="+cartName+";rest="+size+";size="+fileSize) ;
            if( 
                ( size > fileSize ) &&
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
   private PvrSet getPvrSet(){
     String [] pvrNames   = _pvlDb.getPvrNames() ;
     PvrSet    pvrSet     = new PvrSet() ;
     PvrInfo   pvrInfo    = null ;
     DriveInfo driveInfo  = null ;
     String [] driveNames = null ;
     PvrHandle pvr        = null ;
     DriveHandle drive    = null ;
     String    cartName   = null ;
     boolean   usable     = false ;
     boolean   allocated  = false ;
     
     for( int i= 0 ; i < pvrNames.length ; i++ ){
        pvrInfo = new PvrInfo( pvrNames[i] ) ;
        try{
           pvr = _pvlDb.getPvrByName( pvrNames[i] ) ;

           driveNames  = pvr.getDriveNames() ;        

           for( int j = 0 ; j < driveNames.length ; j++ ){
           
              drive = pvr.getDriveByName( driveNames[j] ) ;
              try{
                 drive.open( CdbLockable.READ ) ;
                    cartName = drive.getCartridge() ;
                    usable   = 
                       drive.getAction().equals( "none" ) &&
                       drive.getStatus().equals( "enabled" )  ;
                    allocated = ! drive.getOwner().equals("-") ; 
                 drive.close( CdbLockable.COMMIT ) ;  
                 driveInfo = new DriveInfo( 
                                   driveNames[j] , 
                                   cartName ,
                                   allocated , 
                                   usable )  ;
//                 say( " adding drive : \n"+driveInfo ) ;
                 pvrInfo.addDriveInfo( driveInfo ) ;
              }catch( Exception eeee ){
                 say( "Problem in 'getPvrSet' (drives) : "+eeee ) ;
                 continue ;
              }      
           }
//           say( " adding pvr : \n"+driveInfo ) ;
           pvrSet.addPvrInfo( pvrInfo ) ;
        }catch( Exception iiii ){
           say( "Problem in 'getPvrSet' (pvrs) : "+iiii ) ;
           continue ;
        }
        
     }
     say( "getPVrSet : \n"+pvrSet.toString() ) ;
     return pvrSet ;
   }
   private PvlResourceModifier newGetRequest(
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
