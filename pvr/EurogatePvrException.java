package  eurogate.pvr ;

import   eurogate.db.pvr.* ;
import   eurogate.misc.EurogateException ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 20 June 2007
  * 
 */
public class EurogatePvrException extends EurogateException {
   private String _pvr = null ;
   public EurogatePvrException( int code , String message ){
      super( code , message ) ;
   }
   public void setPvrName( String name ){
      _pvr = name ;
   }
   public String getPvrName(){ return _pvr ; }
}
