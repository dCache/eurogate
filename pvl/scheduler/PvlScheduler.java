package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import eurogate.misc.parser.* ;

import dmg.util.cdb.* ;
import dmg.util.*;
import java.util.* ;

public class PvlScheduler implements PvlResourceScheduler {

   private PvlDb      _pvlDb   = null ;
   private Dictionary _env     = null ;
   private Dictionary _context = null ;
   private Logable    _log     = null ;
   private Args       _args    = null ;
   
   public class DriveInfo {
       public String  cartridge ;
       public boolean usable ;
       public boolean allocated ;
       public String  name ;
       public String  pvrName ;
       public long    time = 0 ;
       public PCode   code = null ;
       DriveInfo( String name , String cartridge ,
                  boolean allocated , boolean usable,
                  String  pvrName  ) {
                         
         this.cartridge = cartridge ;
         this.name      = name ;
         this.allocated = allocated ;
         this.usable    = usable ;
         this.pvrName   = pvrName ;                 
       }
       public void setTime( long time ){ this.time = time ; }
       public void setSelectionCode( PCode code ){ this.code = code ; }
       public String toString(){
          return "Drive="+name+
                 ";cart="+cartridge+
                 ";u="+usable+
                 ";a="+allocated ;
       }
   }
   public class PvrInfo {
       private String    _name ;
       private Vector    _drives           = new Vector() ;
       private Hashtable _byCartridge      = new Hashtable() ;
       private DriveInfo _unallocatedDrive = null ,
                         _emptyDrive       = null ;
                         
       PvrInfo( String name ){ _name = name ; }
       public Enumeration drives(){ return _drives.elements() ; }
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
   public class PvrSet {
       private Hashtable _pvrs = new Hashtable() ;
       PvrSet(){}
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
   
   public PvlScheduler( PvlDb pvlDb ){
       _pvlDb = pvlDb ;
       say( "PvlScheduler (V1) ready" ) ;
   }
   public PvlScheduler( PvlDb pvlDb , Dictionary environment ){
       _pvlDb    = pvlDb ;
       _env      = environment ;
       _log      = (Logable)_env.get( "logable" ) ;
       _args     = (Args)_env.get( "args" ) ;
       _context  = (Dictionary)_env.get( "context" ) ;
       say( "PvlScheduler (V2) ready" ) ;
   }
   protected String getOption( String key ){
       return _args == null ? null : _args.getOpt(key) ;
   }
   protected Object getContext( String key ){
       return _context == null ? null : _context.get(key) ;
   }
   protected void say( String msg ){
      if( _log == null )
         System.out.println( "SCHEDULER : "+msg ) ;
      else 
         _log.log( "(S) "+msg ) ;
   }
   protected void esay( String msg ){
      if( _log == null )
         System.out.println( "SCHEDULER(E) : "+msg ) ;
      else 
         _log.elog( msg ) ;
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
         result = otherAction( requestQueue , modifier ) ;
      }
      
      PvlResourceModifier [] resultList = new PvlResourceModifier[1] ;
      if( result == null )return null ;
      resultList[0] = result ;
      return resultList ;
      
   }
   public PvlResourceModifier 
      otherAction( PvlResourceRequestQueue requestQueue ,
                   PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      return null ;
   }
   public PvlResourceModifier 
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
 
   public PvlResourceModifier 
      dismountAction( PvlResourceRequestQueue requestQueue ,
                      PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      throw new IllegalArgumentException("dismount not supported" ) ;
      
   }
   public PvlResourceModifier 
      deallocateAction( PvlResourceRequestQueue requestQueue ,
                        PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      
      throw new IllegalArgumentException("deallocate not supported" ) ;
   }
   public PvlResourceModifier newPutRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) 
           throws Exception {
           
      throw new IllegalArgumentException("newPutRequest not supported" ) ;
      
   }
   public PvlResourceModifier newGetRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) throws Exception {
                    
      throw new IllegalArgumentException("newGetRequest not supported" ) ;
                    
   }
   public PvlResourceModifier otherRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) throws Exception {
                    
      throw new IllegalArgumentException("newGetRequest not supported" ) ;
                    
   }
   protected PvrSet getPvrSet(){
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
     long      time       = 0 ;
     PCode     code       = null ;
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
                    time      = drive.getTime() ;
                    code      = drive.getSelectionCode() ;
                 drive.close( CdbLockable.COMMIT ) ;  
                 driveInfo = new DriveInfo( 
                                   driveNames[j] , 
                                   cartName ,
                                   allocated , 
                                   usable ,
                                   pvrNames[i] )  ;
                 driveInfo.setTime(time) ;
                 driveInfo.setSelectionCode(code);
                 pvrInfo.addDriveInfo( driveInfo ) ;
              }catch( Exception eeee ){
                 say( "Problem in 'getPvrSet' (drives) : "+eeee ) ;
                 continue ;
              }      
           }
           pvrSet.addPvrInfo( pvrInfo ) ;
        }catch( Exception iiii ){
           say( "Problem in 'getPvrSet' (pvrs) : "+iiii ) ;
           continue ;
        }
        
     }
//     say( "getPVrSet : \n"+pvrSet.toString() ) ;
     return pvrSet ;
   }
   protected PvlResourceModifier 
             findNextFair( PvrSet pvrSet ,
                           PvlResourceRequestQueue requestQueue  ){
                           
       PvlResourceRequest  request  = null ;
       PvlResourceModifier modifier = null ;
       for( int i = 0 ; i < requestQueue.getRequestCount() ; i++ ){
       
           request = requestQueue.getRequestAt(i) ;
           try{
              if( request.getDirection().equals("put") ){
              
                 modifier = findPut( pvrSet , request ) ;
                 
              }else if( request.getDirection().equals("get") ){
              
                 modifier = findGet( pvrSet , request ) ;
              }
              say( "checking : "+request+" --> "+modifier ) ;
              if( modifier != null )return modifier ;
           }catch( Exception e ){
              esay( "Exception while checking : "+request ) ;
              esay( "Exception : "+e ) ;
              continue ;
           }
       
       }  
       return null ;                
   }
   protected PvlResourceModifier  findGet( PvrSet pvrSet ,
                                           PvlResourceRequest request )
             throws Exception {
             
       VolumeHandle volume = _pvlDb.getVolumeByName( request.getVolume() ) ;
       volume.open( CdbLockable.READ ) ;
          String  volumeStatus = volume.getStatus() ;
          String  cartName     = volume.getCartridge() ;
          String  pvrName      = volume.getPvr() ;
       volume.close(CdbLockable.COMMIT ) ;
       if( volumeStatus.indexOf("bad"  )  > -1 )return null ;
       
       PvrInfo pvr = pvrSet.getPvrInfoByName( pvrName ) ;
       if( pvr == null )return null ;
       
       DriveInfo drive = pvr.getDriveByCartridge( cartName ) ;
       //
       //
       if( drive != null ){
          
          if( ! drive.usable )return null ;
          
          if( drive.allocated )return null ; 
                    
          request.setDrive( drive.name ) ;
          request.setPvr( pvrName ) ;
          request.setCartridge( cartName ) ; 
          return request ;         
          
       }
       
       drive = pvr.getEmptyDrive() ;
       
       if( drive != null ){
          if( ! drive.usable )return null ;
          request.setDrive( drive.name ) ;
          request.setPvr( pvrName ) ;
          request.setCartridge( cartName ) ;
          return request ;
       }
       
       drive = pvr.getUnallocatedDrive() ;
       
       if( drive != null ){
          if( ! drive.usable )return null ;
          return new PvlDismountModifier( 
                        drive.pvrName ,
                        drive.name ,
                        drive.cartridge  ) ;
       
       }
       
       return null ;
   }
   //////////////////////////////////////////////////////////////////////////////
   //                                                                          //
   //   get all deallocated cartridges / volumes                               //
   //--------------------------------------------------------------------------//
   //                 ? can we usee one of them ?                              //
   //   YES   //                            NO                                 //
   //         //---------------------------------------------------------------//
   //         //                 get all empty drives                          //
   //         //---------------------------------------------------------------//
   //         //   ? does one of them belong to a pvr where we can use  ?      //
   //         //   ? the volumeSet                                      ?      //
   //         //   YES   //                      NO                            //
   //         //         //        get all deallocated drives                  //
   //         //         //----------------------------------------------------//
   //         //         //     ? does one of them belongs to a pvr where   ?  //
   //         //         //     ? we can write to a sunset                  ?  //
   //         //         //  YES     //                                        //
   // newReq  // mount   // dismount //         queue                          //
   //                                                                          //
   //////////////////////////////////////////////////////////////////////////////
   protected PvlResourceModifier  findPut( PvrSet pvrSet ,
                                           PvlResourceRequest request )
             throws Exception {
   
      //
      // get an hash array of all usable volumes per pvr.
      //
      
      Hashtable pvrHash = new Hashtable() ;
      
      VolumeSetHandle volumeSet = 
         _pvlDb.getVolumeSetByName( request.getVolumeSet() ) ;
      Enumeration e = pvrSet.pvrs() ;
      while( e.hasMoreElements() ){
         PvrInfo   pvr        = (PvrInfo)e.nextElement() ;
         Hashtable volumeHash = new Hashtable() ;
         pvrHash.put( pvr.getName() , volumeHash ) ;
         PvrVolumeSubsetHandle subset = 
              volumeSet.getPvrVolumeSubsetByName( pvr.getName() ) ;
         subset.open( CdbLockable.READ ) ;
            String [] volumeNames = subset.getVolumeNames() ;
         subset.close( CdbLockable.COMMIT ) ;
         for( int i = 0 ; i < volumeNames.length ; i++ ){
            volumeHash.put( volumeNames[i] , volumeNames[i] ) ;
         }
      
      }
      
      long   fileSize     = request.getFileSize() ;
      long   volumeSize   = 0 ;
      String volumeStatus = null ;
      //
      //  walk throu all deallocated drives and try to find
      //  a cartridge which contains a volume which we
      //  can use. 
      //
      say( "Checking for 'nonempty' 'deall' 'match'" ) ;
      e = pvrSet.pvrs() ;
      while( e.hasMoreElements() ){
          PvrInfo   pvrInfo = (PvrInfo)e.nextElement() ;
          PvrHandle pvr     = _pvlDb.getPvrByName( pvrInfo.getName() ) ;
          Enumeration f     = pvrInfo.drives() ;
          while( f.hasMoreElements() ){
             DriveInfo drive = (DriveInfo)f.nextElement() ;
             say( "put(1) drive="+drive.toString() ) ; 
             if(  ( ! drive.usable    )  ||
                  ( drive.allocated   )  ||
                  ( drive.cartridge.equals("empty" ) ) )continue ;
               
             CartridgeHandle cart  = pvr.getCartridgeByName( drive.cartridge ) ;
             Hashtable  volumeHash = (Hashtable)pvrHash.get( drive.pvrName ) ;
             String [] volumeNames = null ;
             //
             //
             if( volumeHash == null )continue ;

             cart.open( CdbLockable.READ ) ;
                volumeNames = cart.getVolumeNames() ;
             cart.close(CdbLockable.COMMIT ) ;
             
             say( "put(1) Volume on cartridge >"+drive.cartridge+"< " ) ;
             for( int i = 0 ; i < volumeNames.length ; i++ )
                say( "      "+volumeNames[i] ) ;
             for( int i = 0 ; i < volumeNames.length ; i++ ){
                if( volumeHash.get( volumeNames[i] ) == null )continue ;
                VolumeHandle volume = _pvlDb.getVolumeByName(volumeNames[i]) ;
                volume.open( CdbLockable.READ ) ;
                   volumeSize   = volume.getResidualBytes() ;
                   volumeStatus = volume.getStatus() ;
                volume.close(CdbLockable.COMMIT ) ;
                if( (  volumeSize > fileSize ) &&
                    (  volumeStatus.indexOf("full" )   < 0 ) &&
                    (  volumeStatus.indexOf("bad"  )   < 0 ) &&
                    (  volumeStatus.indexOf("rdonly" ) < 0 )    ){

                    request.setPvr( drive.pvrName ) ;
                    request.setVolume( volumeNames[i] ) ;
                    request.setDrive( drive.name ) ;
                    request.setCartridge( drive.cartridge ) ;
                    say( "put(1) selected : "+request ) ;
                    return request ;
                }
             }
         }
      }
      say( "Checking for 'empty'" ) ;
      e = pvrSet.pvrs() ;
      while( e.hasMoreElements() ){
          PvrInfo   pvrInfo = (PvrInfo)e.nextElement() ;
          PvrHandle pvr     = _pvlDb.getPvrByName( pvrInfo.getName() ) ;
          Enumeration f     = pvrInfo.drives() ;
          while( f.hasMoreElements() ){
             DriveInfo drive = (DriveInfo)f.nextElement() ;
             say( "put(2) drive="+drive.toString() ) ; 
             if(  ( ! drive.usable    )  ||
                  ( drive.allocated   )  ||
                  ( ! drive.cartridge.equals("empty" ) ) )continue ;
                  
             Hashtable  volumeHash = (Hashtable)pvrHash.get( drive.pvrName ) ;
             //
             if( volumeHash == null )continue ;
             Enumeration g = volumeHash.elements() ;
             while( g.hasMoreElements() ){
                String volumeName   = (String)g.nextElement() ;
                say( "put(2) volume="+volumeName ) ;
                String cartName     = null ;
                VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
                volume.open( CdbLockable.READ ) ;
                   volumeSize   = volume.getResidualBytes() ;
                   volumeStatus = volume.getStatus() ;
                   cartName     = volume.getCartridge() ;
                volume.close(CdbLockable.COMMIT ) ;
                if( (  volumeSize > fileSize ) &&
                    (  volumeStatus.indexOf("full" )   < 0 ) &&
                    (  volumeStatus.indexOf("bad"  )   < 0 ) &&
                    (  volumeStatus.indexOf("rdonly" ) < 0 ) &&
                    (  pvrInfo.getDriveByCartridge( cartName ) == null ) ){

                    request.setPvr( drive.pvrName ) ;
                    request.setVolume( volumeName ) ;
                    request.setDrive( drive.name ) ;
                    request.setCartridge( cartName ) ;
                    say( "put(2) selected "+request ) ; 
                    return request ;
                }
             
             }
          }
      }
      say( "Checking for 'nonempty' 'deall' 'nomatch'" ) ;
      e = pvrSet.pvrs() ;
      while( e.hasMoreElements() ){
          PvrInfo   pvrInfo = (PvrInfo)e.nextElement() ;
          PvrHandle pvr     = _pvlDb.getPvrByName( pvrInfo.getName() ) ;
          Enumeration f     = pvrInfo.drives() ;
          while( f.hasMoreElements() ){
             DriveInfo drive = (DriveInfo)f.nextElement() ;
             if(  ( ! drive.usable    )  ||
                  ( drive.allocated )  ||
                  ( drive.cartridge.equals("empty" ) ) )continue ;
                 
             Hashtable  volumeHash = (Hashtable)pvrHash.get( drive.pvrName ) ;
             //
             if( volumeHash == null )continue ;
             Enumeration g = volumeHash.elements() ;
             while( g.hasMoreElements() ){
                String volumeName   = (String)g.nextElement() ;
                String cartName     = null ;
                VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
                volume.open( CdbLockable.READ ) ;
                   volumeSize   = volume.getResidualBytes() ;
                   volumeStatus = volume.getStatus() ;
                   cartName     = volume.getCartridge() ;
                volume.close(CdbLockable.COMMIT ) ;
                if( (  volumeSize > fileSize ) &&
                    (  volumeStatus.indexOf("full" )   < 0 ) &&
                    (  volumeStatus.indexOf("bad"  )   < 0 ) &&
                    (  volumeStatus.indexOf("rdonly" ) < 0 ) &&
                    (  pvrInfo.getDriveByCartridge( cartName ) == null ) ){

                    return new PvlDismountModifier( 
                                  drive.pvrName ,
                                  drive.name ,
                                  drive.cartridge  ) ;
                }
             
             }
         }
      }
      return null ;
   }
}
