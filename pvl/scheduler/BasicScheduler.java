package eurogate.pvl.scheduler ;


import eurogate.db.pvl.* ;
import eurogate.pvl.* ;
import dmg.util.cdb.* ;
import java.util.* ;

public class BasicScheduler extends PvlScheduler {

   private PvlDb _pvlDb = null ;
   
   public BasicScheduler( PvlDb pvlDb , Dictionary hash ){
       super( pvlDb , hash ) ;
       _pvlDb = pvlDb ;
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
      
      return findNextFair( getPvrSet() , requestQueue ) ;
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
