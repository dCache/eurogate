package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import dmg.util.cdb.* ;
import dmg.util.* ;
import java.util.* ;

public class BasicScheduler extends PvlScheduler {

   private PvlDb _pvlDb = null ;
   private Dictionary _env     = null ;
   private Dictionary _context = null ;
   private Logable    _log     = null ;
   private Args       _args    = null ;
   
   public BasicScheduler( PvlDb pvlDb , Dictionary env ){
       super( pvlDb , env ) ;
       _pvlDb    = pvlDb ;
       _env      = env ;
       _log      = (Logable)_env.get( "logable" ) ;
       _args     = (Args)_env.get( "args" ) ;
       _context  = (Dictionary)_env.get( "context" ) ;
       say( "BasicScheduler (V1) ready" ) ;
   }
   public PvlResourceModifier 
      dismountAction( PvlResourceRequestQueue requestQueue ,
                      PvlResourceModifier  modifier  )
          throws CdbException , InterruptedException            {
      
      return findNextFair( getPvrSet() , requestQueue ) ;
   }
   public PvlResourceModifier 
      deallocateAction( PvlResourceRequestQueue requestQueue ,
                        PvlResourceModifier  modifier  )
          throws CdbException , InterruptedException            {
      String command = null ;
      if( ( _context != null ) && 
          ( ( command = (String)_context.get("alwaysDismount") ) != null ) &&
          (  command.indexOf(modifier.getDrive()) > -1           )    ){
          
         modifier.setActionEvent( "dismounted" ) ;
         return modifier ;
         
      }else{
      
         return findNextFair( getPvrSet() , requestQueue ) ;
         
      }
   }
   public PvlResourceModifier newPutRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) 
          throws Exception {
           
      return findNextFair( getPvrSet() , requestQueue ) ;
      
   }
   public PvlResourceModifier newGetRequest(
                    PvlResourceRequestQueue requestQueue ,
                    PvlResourceRequest      request        ) 
          throws Exception {
                    
      return findNextFair( getPvrSet() , requestQueue ) ;
   }
   public PvlResourceModifier 
      otherAction( PvlResourceRequestQueue requestQueue ,
                   PvlResourceModifier  modifier  )
      throws CdbException , InterruptedException            {
      say( "otherAction : "+modifier.getActionEvent() ) ;
      
      if( modifier instanceof PvlResourceKicker ){
         say( "kicked");
         return findNextFair( getPvrSet() , requestQueue ) ;
      }else if( modifier instanceof PvlResourceTimer ){
         String [] pvrNameList = _pvlDb.getPvrNames() ;
         long   time  = 0 , idle = 0 ;
         long   now   = System.currentTimeMillis() ;
         for( int j = 0 ; j < pvrNameList.length ; j++ ){
            String       pvrName     = pvrNameList[j] ;
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
                  action    = drive.getAction() ;
                  time      = drive.getTime() ;
                  idle      = drive.getIdleTime() * 1000 ;
                  owner     = drive.getOwner() ;
               drive.close( CdbLockable.COMMIT ) ;
               say( "check : "+driveNames[i]+
                    ";s="+status+
                    ";c="+cartridge+
                    ";a="+action+
                    ";o="+owner+
                    ";t="+time+
                    ";idle="+idle+
                    ";now-time="+(now-time) ) ;
               if( ( status.equals("enabled")     ) &&
                    ( action.equals("none")       ) &&
                    ( owner.equals("-" )          ) &&
                    ( ! cartridge.equals("empty") ) &&
                    ( ( now - time ) > idle      ) ){

                    return 
                        new PvlDismountModifier(
                                  pvrName ,
                                  driveNames[i] ,
                                  cartridge ) ;
                }
            }
         }
      }
          
      
      return null ;
   }

}
