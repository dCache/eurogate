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

}
