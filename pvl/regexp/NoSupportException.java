package eurogate.pvl.regexp;

/**
 * This exception is thrown by class RegFitCheck
 * when a method is called with unsupported arguments.
 * eg: "abc+2", "abc>=2", "1/0"    
 */
public class NoSupportException extends Exception{
  private static final String __expl=
                              "Method was called with unsupported arguments"; 

  public NoSupportException(){
    super(": "+ __expl+ ".");
  }


  public NoSupportException(String message){
    super(": "+ __expl+ ":"+ "\n"+ message);
  }


  public String getMessage(){
    return super.getMessage();
  }


  public String toString(){
    return "NoSupportException"+ super.getMessage();
  }

} // class NoSupportException
