package  eurogate.pvl ;

import   eurogate.db.pvl.* ;
import   eurogate.vehicles.* ;

import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.util.cdb.* ;

import java.util.* ;
import java.lang.reflect.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 9 Feb 1999
  */
public class      DummyPvlCell
       extends    CellAdapter    {
 
   private PvlDb                _pvlDb     = null ;
   private PvlResourceScheduler _scheduler = null ; 
 
   private Class [] schedulerConstructorArgs = {       
                        eurogate.db.pvl.PvlDb.class  } ;
                        
   public DummyPvlCell( String name , String args ){
       super( name , args , true ) ;
    
       setPrintoutLevel( 0xff ) ;
       try{
           //
           // 
           //
           Dictionary dict = getDomainContext() ;
           _pvlDb = (PvlDb) dict.get( "database" ) ;
           if( _pvlDb == null )
              throw new 
              IllegalArgumentException( "database not defined" ) ;
          
           String schedulerClassName = (String)dict.get("scheduler") ;
           if( schedulerClassName == null )
              throw new 
              IllegalArgumentException( "scheduler not defined" ) ;
              
           _scheduler = 
                    initiateScheduler( schedulerClassName ) ;
                    
       }catch( Exception e ){
          say( "Problem in <init> : "+e ) ;
          kill() ;
          return ;
       }
    
    
   }
   private PvlResourceScheduler initiateScheduler( String name )
        throws Exception {
   
        Class schedulerClass = Class.forName( name ) ;
        Constructor con = 
             schedulerClass.getConstructor( schedulerConstructorArgs ) ;
        
        Object [] args = new Object[1] ;
        args[0] = _pvlDb ;
        
        try{
           return (PvlResourceScheduler)con.newInstance( args ) ;
        }catch( InvocationTargetException ee ){
           Throwable t = ee.getTargetException() ;
           if( t instanceof Exception ){
              throw (Exception)t ;
           }else{
              throw new Exception( "Problem : "+t ) ;
           }
            
        } 
   }
   PvlResourceRequestQueue _queue  = new PvlResourceRequestQueue() ;
   
   private String  processModifier( PvlResourceModifier modifier )
           throws Exception {
           
      System.out.println( "From scheduler : "+modifier ) ;
      
      if( modifier instanceof PvlResourceRequest ){
      
         PvlResourceRequest req = (PvlResourceRequest)modifier ;
        
         PvrHandle pvr     = _pvlDb.getPvrByName(req.getPvr()) ;
         String cartridge  = req.getCartridge() ;
         String driveName  = req.getDrive() ;
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         drive.open( CdbLockable.WRITE ) ;
         drive.setCartridge( cartridge ) ;
         drive.setOwner( "OWNED" ) ;
         drive.close( CdbLockable.COMMIT ) ;
         //
         // remove the request from the queue.
         //
         int pos = 0 ;
         if( ( pos = req.getPosition() ) < 0 )
                   pos = _queue.getRequestCount() - 1 ;
         _queue.removeRequestAt( pos ) ;
         return "Done" ;
      }else if( modifier.getActionEvent().equals("dismounted" ) ){
         //
         //  the scheduler wants us to dismount the 
         //  drive /cartridge pair.
         //
         PvrHandle pvr     = _pvlDb.getPvrByName(modifier.getPvr()) ;
         String cartridge  = modifier.getCartridge() ;
         String driveName  = modifier.getDrive() ;
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         drive.open( CdbLockable.WRITE ) ;
         drive.setCartridge( "empty" ) ;
         drive.setOwner( "-" ) ;
         drive.close( CdbLockable.COMMIT ) ;
         
         
         PvlResourceModifier [] modList = new PvlResourceModifier[1] ;      
         modList[0] = modifier ;
         //
         // at this point the scheduler gets what he wanted.
         //
         modList = _scheduler.nextEvent( _queue , modList ) ;
      
         if( ( modList == null ) || ( modList.length < 1 ) )
            return "Scheduler returned NULL" ;

         try{
            return processModifier( modList[0] ) ; 
         }catch( Exception e ){
            e.printStackTrace() ;
            return "processModifier ERROR after rec. call : "+e ;
         }
      }
      return "Panic : "+ modifier.getActionEvent();
   }
   //
   //
   //
   public String hh_writerequest = "<volumeSet> <fileSize>" ;
   
   public String ac_writerequest_$_2(Args args ) throws Exception {
      String volumeSetName = args.argv(0) ;
      long   fileSize      = Long.parseLong( args.argv(1) ) ;
      
      VolumeSetHandle volumeSet = null ;
      try{
        volumeSet = _pvlDb.getVolumeSetByName( volumeSetName ) ;
      }catch( Exception e ){
        return "VolumeSet not found : "+volumeSetName  ;
      }
      PvlRequestImpl pvlRequest = 
            new PvlRequestImpl( volumeSetName , fileSize ) ;
   
      PvlResourceModifier [] modList = new PvlResourceModifier[1] ;
      
      PvlResourceRequest req = new PvlResourceRequest( pvlRequest ) ;
      
      modList[0] = req ;
      _queue.addRequest( req ) ;
      
      try{
         modList = _scheduler.nextEvent( _queue , modList ) ;
      }catch(Exception e ){
         e.printStackTrace() ;
         return "Scheduler Problem : "+e  ;
      }
      
      if( ( modList == null ) || ( modList.length < 1 ) )
         return "Scheduler returned NULL" ;
        
      try{
         return processModifier( modList[0] ) ; 
      }catch( Exception e ){
         return "processModifier ERROR : "+e ;
      }
   }
   //
   //
   //
   public String hh_readrequest = "<volume>" ;
   
   public String ac_readrequest_$_1(Args args ) throws Exception {
      String       volumeName = args.argv(0) ;
      VolumeHandle volume     = null ;

      PvlRequestImpl pvlRequest = new PvlRequestImpl( volumeName ) ;

      try{
         volume = _pvlDb.getVolumeByName( volumeName ) ;
      }catch( Exception e ){
         return "Volume not found : "+volumeName ;
      }
      volume.open( CdbLockable.READ ) ;
      String pvrName       = volume.getPvr() ;
      String cartridgeName = volume.getCartridge() ;
      volume.close( CdbLockable.COMMIT ) ;
      
      say( "Readrequest : pvr="+pvrName+";cart="+cartridgeName) ;
      
      PvlResourceRequest req = new PvlResourceRequest( pvlRequest ) ;
      req.setPvr( pvrName ) ;
      req.setCartridge( cartridgeName ) ;

      PvlResourceModifier [] modList = new PvlResourceModifier[1] ;
      
      modList[0] = req ;
      _queue.addRequest( req ) ;
      System.out.println( "PvlResourceRequest ("+_queue.getRequestCount()+") : "+req) ;
      
      modList = _scheduler.nextEvent( _queue , modList ) ;
      
      if( ( modList == null ) || ( modList.length < 1 ) )
         return "Scheduler returned NULL" ;
        
      try{
         return processModifier( modList[0] ) ; 
      }catch( Exception e ){
         e.printStackTrace() ;
         return "processModifier ERROR : "+e ;
      }
   }
   public String hh_ls_drive = "" ;
   public String ac_ls_drive( Args args )throws Exception {
     String [] pvrNameList = _pvlDb.getPvrNames() ;
     String pvrName = null ;
     StringBuffer sb          = new StringBuffer() ;
    
     for( int j = 0 ; j < pvrNameList.length ; j++ ){
        pvrName = pvrNameList[j] ;
        PvrHandle    pvr         = _pvlDb.getPvrByName( pvrName ) ;
        String []    driveNames  = pvr.getDriveNames() ;
        DriveHandle  drive       = null ;
        String status = null , cartridge = null , owner = null ;
        for( int i = 0 ; i < driveNames.length ; i++ ){
           drive = pvr.getDriveByName( driveNames[i] ) ;
           drive.open( CdbLockable.READ ) ;
           status    = drive.getStatus() ;
           cartridge = drive.getCartridge() ;
           owner     = drive.getOwner() ;
           drive.close( CdbLockable.COMMIT ) ;
           sb.append( Formats.field( driveNames[i]  , 12 ) ).
              append( Formats.field( status         , 12 ) ).
              append( Formats.field( cartridge      , 12 ) ).
              append( Formats.field( pvrNameList[j] , 8  ) ).
              append( Formats.field( owner          , 8  ) ).
              append("\n") ;
        }
     }
     return sb.toString() ;
   
   }
   public String hh_ls_request = "" ;
   public String ac_ls_request( Args args )throws Exception {
      PvlResourceRequest [] reqs = _queue.getRequests() ;
      StringBuffer sb = new StringBuffer() ;
      for( int i= 0 ;i < reqs.length ; i++ ){
         sb.append( reqs[i].toLine() ).append("\n") ;
      }
      return sb.toString() ;
   
   }
   public String hh_dismount = "<pvr> <drive>" ;
   
   public String ac_dismount_$_2(Args args ) throws Exception {
   
   
       String pvrName   = args.argv(0) ;
       String driveName = args.argv(1) ;
       
       PvrHandle pvr = null ;
       try{
          pvr = _pvlDb.getPvrByName( pvrName ) ;
       }catch( Exception e ){
          return "Pvr not found : "+pvrName ;
       }
       DriveHandle drive = null ;
       try{
          drive = pvr.getDriveByName( driveName ) ;
       }catch( Exception e ){
          return "Drive not found : "+driveName ;
       }
       drive.open( CdbLockable.READ ) ;
       String owner = drive.getOwner() ;
       String cart  = drive.getCartridge() ;
       drive.close( CdbLockable.COMMIT ) ;
       
       if( ! drive.getOwner().equals("-") )
         return "Drive "+driveName+" still allocated" ;
       if( drive.getCartridge().equals("empty") )
         return "Drive "+driveName+" is empty" ;

       drive.open( CdbLockable.WRITE ) ;
       drive.setCartridge("empty") ;
       drive.close( CdbLockable.COMMIT ) ;
   
      PvlResourceModifier [] modList = new PvlResourceModifier[1] ;
      modList[0] = 
         new PvlDismountModifier( pvrName ,
                                  driveName ,
                                  cart    ) ;
      modList = _scheduler.nextEvent( _queue , modList ) ;
      
      if( ( modList == null ) || ( modList.length < 1 ) )
         return "Scheduler returned NULL" ;
        
      try{
         return processModifier( modList[0] ) ; 
      }catch( Exception e ){
         return "processModifier ERROR : "+e ;
      }
   }
   public String hh_deallocate = "<pvr> <drive>" ;
   
   public String ac_deallocate_$_2(Args args ) throws Exception {
       String pvrName   = args.argv(0) ;
       String driveName = args.argv(1) ;
       
       PvrHandle pvr = null ;
       try{
          pvr = _pvlDb.getPvrByName( pvrName ) ;
       }catch( Exception e ){
          return "Pvr not found : "+pvrName ;
       }
       DriveHandle drive = null ;
       try{
          drive = pvr.getDriveByName( driveName ) ;
       }catch( Exception e ){
          return "Drive not found : "+driveName ;
       }
       drive.open( CdbLockable.READ ) ;
       String owner = drive.getOwner() ;
       String cart  = drive.getCartridge() ;
       drive.close( CdbLockable.COMMIT ) ;
       
       if( drive.getOwner().equals("-") )
         return "Drive "+driveName+" not allocated" ;
       if( drive.getCartridge().equals("empty") )
         return "Drive "+driveName+" is empty" ;

       drive.open( CdbLockable.WRITE ) ;
       drive.setOwner("-") ;
       drive.close( CdbLockable.COMMIT ) ;

      PvlResourceModifier [] modList = new PvlResourceModifier[1] ;
      modList[0] = 
         new PvlResourceModifier( "deallocated" ,
                                  pvrName ,
                                  driveName ,
                                  cart         ) ;
      modList = _scheduler.nextEvent( _queue , modList ) ;
      
      if( ( modList == null ) || ( modList.length < 1 ) )
         return "Scheduler returned NULL" ;
      try{
         return processModifier( modList[0] ) ; 
      }catch( Exception e ){
         return "processModifier ERROR : "+e ;
      }

   }
}
