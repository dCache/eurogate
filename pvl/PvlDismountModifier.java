package eurogate.pvl ;


public class PvlDismountModifier extends PvlResourceModifier {

   public PvlDismountModifier( String pvrName ,
                               String driveName ,
                               String cartridgeName ){
       super( "dismounted" , pvrName , driveName , cartridgeName ) ;
   }
}
