package eurogate.pvl.regexp;

/** Class UnReplaceAbleString contains a String.
* All characters are allowed. (even + - * / ?)
* In the DriveExpression it is not allowed to replace an UnReplaceAbleString
* by a value from a request assignment.
*/
public class UnReplaceAbleString{
 
  private String _string;

  public UnReplaceAbleString(String s){
    _string= s;
  } // constructor

  public String getInputString(){
    return _string;
  }

  public String toString(){
    return _string.substring(1,(_string.length()-1));
  }

} // class UnReplaceAbleString
