package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import dmg.util.cdb.* ;
import java.util.* ;

public class PvlScheduler implements PvlResourceScheduler {

   private PvlDb _pvlDb = null ;
   
   public class DriveInfo {
       public String  cartridge ;
       public boolean usable ;
       public boolean allocated ;
       public String  name ;
       DriveInfo( String name , String cartridge ,
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
   public class PvrInfo {
       private String _name ;
       private Vector _drives = new Vector() ;
       private Hashtable _byCartridge = new Hashtable() ;
       
       private DriveInfo _unallocatedDrive = null ,
                         _emptyDrive       = null ;
                         
       PvrInfo( String name ){ _name = name ; }
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
       say( "PvlScheduler ready" ) ;
   }
   protected void say( String msg ){
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

}
