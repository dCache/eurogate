package eurogate.misc ;

import java.io.* ;
import java.util.* ;

public class DatabaseException  extends Exception {
   private int _retCode = 0 ;
   public DatabaseException( int retCode  , String retMessage ){
      super( retMessage ) ;
      _retCode = retCode  ;
   }
   public DatabaseException( String retMessage ){
      super( retMessage ) ;
   }
   public int getCode(){ return _retCode ; }
   public String toString(){
      return super.toString()+ " Code="+_retCode ;
   }

}
