import java.io.StringReader;
import java.util.Hashtable;


import eurogate.pvl.regexp.drive.TreeCut;
import eurogate.pvl.regexp.DriveExpression;
// import eurogate.pvl.regexp.drive.ParseException;
// import eurogate.pvl.regexp.drive.TokenMgrError;
import java.util.EmptyStackException;


import eurogate.pvl.regexp.request.SyntaTrial;
// import eurogate.pvl.regexp.request.ParseException;
// import eurogate.pvl.regexp.request.TokenMgrError;


import eurogate.pvl.regexp.RegFitCheck;
import eurogate.pvl.regexp.NoSupportException;


public class RegExSample{

  public static void main(String[] args){
    // java RegExSample "(host== horst)" "host=horst;"
    // java RegExSample "(host=="\""h*"\"")" "host="\""hugo1.2.3"\"";"


                  
    Object driveUnresolvedTree= Boolean.TRUE;
    String driveExpression= "true";          // the Drive serves any request
    // Object driveUnresolvedTree= Boolean.FALSE;                               
    // String driveExpression= "false";      // the Drive serves no request


					      
    Hashtable requestExprAssignsTable= null;
    String requestExpression= null;                          


       
    // create an unresolved tree for a drive 
    try{
      driveUnresolvedTree= new TreeCut(
                             new StringReader(args[0])
			                ).createTree();
      driveExpression= args[0];				
    }
    // catch( ParseException ep){
    catch( eurogate.pvl.regexp.drive.ParseException ep){
     System.out.println(
        driveExpression+
        "\n"+
        ep.toString().substring(ep.toString().indexOf(" ")+1)
	                 );
    }  
    // catch( TokenMgrError et){
    catch( eurogate.pvl.regexp.drive.TokenMgrError et){
      System.out.println(
        driveExpression+
        "\n"+
        et.toString().substring(et.toString().indexOf(" ")+1)
	                 );
    }        
    catch( EmptyStackException es){
      System.out.println("the parser for drive got an empty String");
    }        



    // create a table for a request    
    try{
      requestExprAssignsTable= new SyntaTrial(
                                  new StringReader(args[1])
				               ).createTable();
      requestExpression= args[1];
    }
    // catch( ParseException ep){
    catch( eurogate.pvl.regexp.request.ParseException ep){
      System.out.println(
        requestExpression+
        "\n"+
        ep.toString().substring( ep.toString().indexOf(" ")+1)
	                 );
    }  
    // catch( TokenMgrError et){
    catch( eurogate.pvl.regexp.request.TokenMgrError et){
      System.out.println(
        requestExpression+
        "\n"+
        et.toString().substring( et.toString().indexOf(" ")+1)
	                 );
    }         


    
    // compare regexpr for drive and request
    try{
      System.out.println( new RegFitCheck().resolveDriveExpression(
                                              driveUnresolvedTree,
			                      requestExprAssignsTable )
			 );
    }
    catch(NoSupportException ens){
      System.out.println(
                     ens.toString().substring(ens.toString().indexOf(" ")+1)
		         );
      System.out.println(driveExpression);
      System.out.println(requestExpression);
    }



  }

} 
