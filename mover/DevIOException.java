package eurogate.mover;

import java.io.*;

  /**
   * public class DevIOException extends IOException
   *  extends IOException with an errorCode Parameter 
   *  and supplies a function to retrieve the errorcode 
   */

public class DevIOException extends IOException {
  private int _error = 0;

  public DevIOException (String errorString, int errorCode) {
   
    super( errorString );        // call superclass IOException
    _error = errorCode;          // set variable for "getErrorCode()"
    
  }

  public DevIOException(String str) {
    super(str);
    _error = 1;
  }

  public int getErrorCode() {    // method to retrieve the errorcode
  return _error;
  }
}

