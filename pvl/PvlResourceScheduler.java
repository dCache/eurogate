package eurogate.pvl ;
import  dmg.util.cdb.* ;
/**
  *   The following constructor is required as well :
  *   <pre>
  *   <init>( PvlDb pvlDatabase ) throws CdbException
  *   </pre>
  */
public interface PvlResourceScheduler {

   public PvlResourceModifier [] 
      nextEvent( PvlResourceRequestQueue requestQueue ,
                 PvlResourceModifier  [] modifierList   ) 
      
      throws CdbException , InterruptedException ;

}
