package eurogate.pvl.regexp;

/**  Class DriveExpression represents a TreeNode of an unresolved tree.
*  This tree consist of Expressions for a Drive.
*/
public class DriveExpression{
  private Object _urLeftValue= null;
  private Object _urOpValue= null;
  private Object _urRightValue= null;
  
  public DriveExpression(Object urLeftValue,
		         Object urOpValue,
		         Object urRightValue){
    _urLeftValue= urLeftValue;
    _urOpValue= urOpValue;
    _urRightValue= urRightValue;
  }//constructor
		
  public Object getLeftValue(){
    return _urLeftValue;
  }
  
  public Object getOpValue(){
    return _urOpValue;
  }
  
  public Object getRightValue(){
    return _urRightValue;
  }
  
  public String toString(){
    return "(" +
           _urLeftValue.toString() +
           _urOpValue.toString() +
           _urRightValue.toString() +
	   ")";
  }

} // class DriveExpression
