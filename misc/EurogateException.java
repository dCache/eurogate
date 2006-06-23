package eurogate.misc ;

public class EurogateException extends Exception {
   private int _code = 0 ;
   public EurogateException( int code , String message ){
       super(message);
       _code = code ;
   }
   public int getErrorCode(){  return _code ;}
   public String getMessage(){
       return "["+_code+"] "+super.getMessage() ;
   }
}
