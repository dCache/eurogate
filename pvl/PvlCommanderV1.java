package  eurogate.pvl ;

import   eurogate.db.pvl.* ;
import   eurogate.vehicles.* ;
import   eurogate.misc.* ;

import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.util.cdb.* ;

import java.util.* ;
import java.text.* ;
import java.lang.reflect.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 22 Apr 1999
  */
public class PvlCommanderV1 {

   private CellAdapter _cell  = null ;
   private PvlDb       _pvlDb = null ;
   private FifoY       _fifo  = null ;
   private PermissionCheckable _permission  = null ;
   private PvlResourceRequestQueue   _queue = null ;
   
   public PvlCommanderV1( CellAdapter cell , 
                          PvlDb pvlDb , 
                          FifoY fifo ,
                          PvlResourceRequestQueue queue ){
      _cell  = cell ;
      _pvlDb = pvlDb ;
      _fifo  = fifo ;   
      _queue = queue ;
   }
   public void setPermissionCheckable( PermissionCheckable permission ){
       _permission = permission ;
   }
   private void checkPermission( Args args , String acl ) throws AclException{
      if( ( _permission == null ) || ! ( args instanceof Authorizable ) )return ;

      _permission.checkPermission( (Authorizable)args , acl ) ;

   }
   public void say( String msg ){
      _cell.say( msg ) ;
   }
   public void esay( String msg ){
      _cell.esay( msg ) ;
   }
   public String ac_interrupted( Args args ){ return "" ; }
   public String hh_ls_drive = "[-t]" ;
   public String ac_ls_drive( Args args )throws Exception {
     String [] pvrNameList = _pvlDb.getPvrNames() ;
     String pvrName     = null ;
     long         time  = 0 ;
     StringBuffer sb    = new StringBuffer() ;
     boolean      st    = args.getOpt("t") != null ; 
     DateFormat   df    = new SimpleDateFormat("hh.mm.ss" ) ;
     for( int j = 0 ; j < pvrNameList.length ; j++ ){
        pvrName = pvrNameList[j] ;
        PvrHandle    pvr         = _pvlDb.getPvrByName( pvrName ) ;
        String []    driveNames  = pvr.getDriveNames() ;
        DriveHandle  drive       = null ;
        String status = null , cartridge = null ,
               owner  = null , action    = null ;
        for( int i = 0 ; i < driveNames.length ; i++ ){
           drive = pvr.getDriveByName( driveNames[i] ) ;
           drive.open( CdbLockable.READ ) ;
              status    = drive.getStatus() ;
              cartridge = drive.getCartridge() ;
              owner     = drive.getOwner() ;
              action    = drive.getAction() ;
              time      = drive.getTime() ;
           drive.close( CdbLockable.COMMIT ) ;
           sb.append( Formats.field( driveNames[i]  , 12 ) ).
              append( Formats.field( status         , 12 ) ).
              append( Formats.field( cartridge      , 12 ) ).
              append( Formats.field( pvrNameList[j] , 8  ) ).
              append( Formats.field( owner          , 8  ) ).
              append( Formats.field( action         , 12  ) ) ;
           if(st)sb.append(df.format(new Date(time))) ; 
           sb.append("\n") ;
        }
     }
     return sb.toString() ;
   
   }
   public String hh_update = "" ;
   public String ac_update( Args args )throws Exception {
     String [] pvrNameList = _pvlDb.getPvrNames() ;
     String pvrName     = null ;
     StringBuffer error = new StringBuffer() ;
     for( int j = 0 ; j < pvrNameList.length ; j++ ){
                     pvrName     = pvrNameList[j] ;
        PvrHandle    pvr         = _pvlDb.getPvrByName( pvrName ) ;
        String []    driveNames  = pvr.getDriveNames() ;
        DriveHandle  drive       = null ;
        String       specific    = null ;
        String       acl         = "pvr."+pvrName+".expert" ;
        try{
           checkPermission( args , acl ) ;
        }catch( Exception eee){
           error.append( pvrName ).
                 append(" : you don't own ").
                 append(acl).
                 append("\n");
           continue ;
        }
        for( int i = 0 ; i < driveNames.length ; i++ ){
           try{
              drive = pvr.getDriveByName( driveNames[i] ) ;
              drive.open( CdbLockable.WRITE ) ;
                 specific = drive.getSpecificName() ;
              drive.close( CdbLockable.COMMIT ) ;

              PvrRequest pvrReq = 
                   new PvrRequestImpl( "newdrive" , 
                                       pvrName ,
                                       "" , 
                                       driveNames[i] , 
                                       specific ) ;
              
              sendRequest( pvrName , pvrReq ) ;     
           }catch(Exception ee ){
              error.append(driveNames[i]).
                    append(" : ").
                    append(ee.getMessage()).
                    append("\n");
           }
        }
     }
     return error.toString();
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
   public String hh_xdismount = "<pvr> <drive>" ;
   
   public String ac_xdismount_$_2(Args args ) throws Exception {
   
   
       String pvrName   = args.argv(0) ;
       String driveName = args.argv(1) ;
       
       checkPermission( args , "pvr."+pvrName+".expert" ) ;
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

//       drive.open( CdbLockable.WRITE ) ;
//       drive.setCartridge("empty") ;
//       drive.close( CdbLockable.COMMIT ) ;
   
      _fifo.push( new PvlDismountModifier( 
                            pvrName ,
                            driveName ,
                            cart    )            ) ;
      return "" ;
   }
   public String hh_deallocate = "<pvr> <drive>" ;
   
   public String ac_deallocate_$_2(Args args ) throws Exception {
       String pvrName   = args.argv(0) ;
       String driveName = args.argv(1) ;
       checkPermission( args , "pvr."+pvrName+".expert" ) ;
       
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

      _fifo.push(   new PvlResourceModifier( "deallocated" ,
                                   pvrName ,
                                   driveName ,
                                   cart         )   ) ;
      return "Queued" ;

   }
   public Object ac_x_lsdrive( Args args ) throws Exception {
   
      return getPvrSet() ;
   }
   public  Object [] getPvrSet(){
   
     String [] pvrNames   = _pvlDb.getPvrNames() ;
     Object [] pvrSet     = null ;
     Object [] pvrInfo    = null ;
     String [] driveInfo  = null ;
     String [] driveNames = null ;
     PvrHandle pvr        = null ;
     DriveHandle drive    = null ;
     
     pvrSet = new Object[pvrNames.length] ;
     for( int i= 0 ; i < pvrNames.length ; i++ ){
        try{
           pvr = _pvlDb.getPvrByName( pvrNames[i] ) ;

           driveNames  = pvr.getDriveNames() ;        
           pvrInfo     = new Object[driveNames.length+1] ;
           pvrInfo[0]  = pvrNames[i] ;

           for( int j = 0 ; j < driveNames.length ; j++ ){
           
              drive = pvr.getDriveByName( driveNames[j] ) ;
              try{
                 driveInfo = new String[5] ;
                 drive.open( CdbLockable.READ ) ;
                    driveInfo [0]  = driveNames[j] ;
                    driveInfo [1]  = drive.getCartridge()  ;
                    driveInfo [2]  = drive.getAction() ;
                    driveInfo [3]  = drive.getStatus() ;
                    driveInfo [4]  = drive.getOwner() ;
                 drive.close( CdbLockable.COMMIT ) ;
                 
                 pvrInfo[j+1] = driveInfo  ;
              }catch( Exception eeee ){
                 say( "Problem in 'getPvrSet' (drives) : "+eeee ) ;
                 continue ;
              }      
           }
           pvrSet[i] = pvrInfo ;
        }catch( Exception iiii ){
           say( "Problem in 'getPvrSet' (pvrs) : "+iiii ) ;
           continue ;
        }
     }
//     say( "getPVrSet : \n"+pvrSet.toString() ) ;
     return pvrSet ;
   } 
   public String hh_pvr_mount = "<pvr> <drive> <cartridge>" ;
   public String ac_pvr_mount_$_3( Args args )throws Exception {

      String      pvrName   = args.argv(0) ;
      String      driveName = args.argv(1) ;
      String      cartName  = args.argv(2) ;
      checkPermission( args , "pvr."+pvrName+".expert" ) ;

      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      DriveHandle drive   = pvr.getDriveByName( driveName ) ;
      
      drive.open( CdbLockable.WRITE ) ;
         String specific = drive.getSpecificName() ;
         drive.setAction( "mounting" ) ;
      drive.close( CdbLockable.COMMIT ) ;
      PvrRequest pvrReq = new PvrRequestImpl( "mount" , 
                                           pvrName ,
                                           cartName ,
                                           driveName ,
                                           specific       ) ;

      CellMessage msg = _cell.getThisMessage() ;
      msg.setMessageObject( pvrReq ) ;
      msg.getDestinationPath().add( pvrName ) ;  
      msg.nextDestination() ;                                  
      _cell.sendMessage( msg ) ;     

      return "Queued" ;
   }
   public String hh_dismount = "<pvr> <drive1> [<drive2>...]" ;
   public String ac_dismount_$_2_99( Args args )throws Exception {

      String      pvrName = args.argv(0) ;
      checkPermission( args , "pvr."+pvrName+".manageDrives" ) ;
      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      args.shift() ;
      StringBuffer error  = new StringBuffer() ;
      int d = args.argc() ;
      for( int i = 0 ; i < d ; i++ ){
          String      driveName = args.argv(0) ;
          try{
            DriveHandle drive   = pvr.getDriveByName( driveName ) ;

            drive.open( CdbLockable.WRITE ) ;
               String specific = drive.getSpecificName() ;
               drive.setAction( "dismounting" ) ;
            drive.close( CdbLockable.COMMIT ) ;
            PvrRequest pvrReq = 
                  new PvrRequestImpl( "dismount" , 
                                      pvrName ,
                                      "*" ,
                                      driveName ,
                                      specific       ) ;

            sendRequest( pvrName , pvrReq ) ;     
         }catch(Exception ee ){
            error.append(driveName).append(" : ").
                  append(ee.getMessage()).append("\n");
         }
      }   
      return error.toString() ;
   }
   public String hh_pvr_dismount = "<pvr> <drive> <cartridge>" ;
   public String ac_pvr_dismount_$_3( Args args )throws Exception {

      String      pvrName   = args.argv(0) ;
      String      driveName = args.argv(1) ;
      String      cartName  = args.argv(2) ;
      checkPermission( args , "pvr."+pvrName+".expert" ) ;
      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      DriveHandle drive   = pvr.getDriveByName( driveName ) ;
      
      drive.open( CdbLockable.WRITE ) ;
         String specific = drive.getSpecificName() ;
         drive.setAction( "dismounting" ) ;
      drive.close( CdbLockable.COMMIT ) ;
      PvrRequest pvrReq = new PvrRequestImpl( "dismount" , 
                                           pvrName ,
                                           cartName ,
                                           driveName ,
                                           specific       ) ;

      sendRequest( pvrName , pvrReq ) ;     

      return "Queued" ;
   }
   private void sendRequest( String destination , Object request )
           throws Exception{
      CellMessage msg = _cell.getThisMessage() ;
      msg.setMessageObject( request ) ;
      msg.getDestinationPath().add( destination ) ;  
      msg.nextDestination() ;                                  
      _cell.sendMessage( msg ) ;     
           
   }
   public String hh_pvr_newdrive = "<pvr> <drive>" ;
   public String ac_pvr_newdrive_$_2( Args args )throws Exception {

      String      pvrName   = args.argv(0) ;
      String      driveName = args.argv(1) ;
      checkPermission( args , "pvr."+pvrName+".expert" ) ;
      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      DriveHandle drive   = pvr.getDriveByName( driveName ) ;
      
      drive.open( CdbLockable.WRITE ) ;
         String specific = drive.getSpecificName() ;
      drive.close( CdbLockable.COMMIT ) ;

      PvrRequest pvrReq = new PvrRequestImpl( "newdrive" , 
                                           pvrName ,
                                           "" , 
                                           driveName , 
                                           specific ) ;

      sendRequest( pvrName , pvrReq ) ;     

      return "Queued" ;
   }
   public String hh_pvr_unload = "<pvr> <drive>" ;
   public String ac_pvr_unload_$_2( Args args )throws Exception {

      String      pvrName   = args.argv(0) ;
      String      driveName = args.argv(1) ;
      checkPermission( args , "pvr."+pvrName+".expert" ) ;
      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      DriveHandle drive   = pvr.getDriveByName( driveName ) ;
      
      drive.open( CdbLockable.WRITE ) ;
         String specific = drive.getSpecificName() ;
         drive.setAction( "unloading" ) ;
      drive.close( CdbLockable.COMMIT ) ;

      UnloadDismountRequest moverReq = 
         new UnloadDismountRequest( pvrName , 
                                    "" ,
                                    driveName ,
                                    specific    ) ;

      CellMessage msg = _cell.getThisMessage() ;
      msg.setMessageObject( moverReq ) ;
      msg.getDestinationPath().add( driveName ) ;                                    
      msg.nextDestination() ;                                  
      _cell.sendMessage( msg ) ;     

      return "Queued" ;
   }
   public String hh_enable = "<pvr> [<drive1> [ <drive2>...]]" ;
   public String ac_enable_$_1_99( Args args )throws Exception {
      String      pvrName   = args.argv(0) ;
      checkPermission( args , "pvr."+pvrName+".manageDrives" ) ;
   
      args.shift() ;

      return handle_drives( pvrName , true , args ) ;         
      
   }
   public String hh_disable = "<pvr> [<drive1> [ <drive2>...]]" ;
   public String ac_disable_$_1_99( Args args )throws Exception {
      String      pvrName   = args.argv(0) ;
      checkPermission( args , "pvr."+pvrName+".manageDrives" ) ;
   
      args.shift() ;

      return handle_drives( pvrName , false , args ) ;         
      
   }
   private  String handle_drives( String pvrName , 
                                  boolean enable , 
                                  Args drives  )throws Exception {

      String newMode    =  enable ? "enabled" : "disabled" ;
      PvrHandle   pvr     = _pvlDb.getPvrByName( pvrName ) ;
      int d = drives.argc() ;
      Enumeration driveNames = null ;
      if( d == 0 ){ 
         Vector v = new Vector() ;
         String [] names = pvr.getDriveNames() ; 
         for( int i = 0 ; i < names.length ; i++ )
             v.addElement( names[i] ) ;
         driveNames = v.elements() ;
      }else{
         Vector v = new Vector() ;
         for( int i = 0 ; i < d ; i++ )
             v.addElement( drives.argv(i) ) ;
         driveNames = v.elements() ;
      }
      StringBuffer errors = new StringBuffer() ;
      DriveHandle  drive  = null ;
      while( driveNames.hasMoreElements() ){
          
          String  driveName = (String)driveNames.nextElement() ;
          try{
             drive = pvr.getDriveByName( driveName ) ;
             drive.open( CdbLockable.WRITE ) ;
                drive.setStatus( newMode ) ;
                drive.setAction("none");
                drive.setOwner("-");
             drive.close( CdbLockable.COMMIT ) ;
          }catch(Exception ee ){
             errors.append(driveName).
                    append(" : ").
                    append(ee.getMessage()).
                    append("\n");
          }
      }
      if( enable )_fifo.push( new PvlResourceKicker() ) ;

      return errors.toString() ;
   }
}
