package eurogate.pvl ;


public class PvlErrorModifier extends PvlResourceModifier {

   public PvlErrorModifier( int errorCode  , String errorMessage ){
       super( "error" ) ;
       setReturnValue( errorCode , errorMessage ) ;
   }
}
 
