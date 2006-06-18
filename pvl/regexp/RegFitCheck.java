package eurogate.pvl.regexp;

import eurogate.pvl.regexp.NoSupportException;
import java.util.Hashtable;

/** class RegFitCheck solves an unsolved tree of a drive
 *  with help of a hashtable of a request. 
 *  The tree is assembled by class TreeCut.
 *  The hashtable is filled by class SyntaTrial.  
 */
public class RegFitCheck{
  // private static final boolean _DEBUG= false;
  // private static final boolean _DEBUG= true;
  private static final Boolean FALSE= Boolean.FALSE; 
  private static final Boolean TRUE= Boolean.TRUE;
  private Hashtable _requestAssigns= null;


  public RegFitCheck(){
  } // constructor()


 /** resolveDriveExpression(Object, Hashtable)
  *  solves an unsolved tree of a drive with expressions of a request
  *  output is a boolean 
  *  NoSupportException is thrown if a method is called with wrong arguments
  */
  public Object resolveDriveExpression(
    Object drvTree, Hashtable requestAssigns) throws NoSupportException{
    if( drvTree instanceof DriveExpression ){
      _requestAssigns= requestAssigns;
      return resolveDriveExpression( drvTree );
    }else{
      return drvTree;
    }
  } 


  private Object resolveDriveExpression( Object result )
                                                    throws NoSupportException{ 
    // try to replace it against a subDriveExpression
    if ( result instanceof DriveExpression ){
      DriveExpression driveExpression= (DriveExpression)result;
/*
      if( _DEBUG ){               
        System.out.print("resolveDriveExpression: ");
        System.out.print(driveExpression.getLeftValue().toString());
        System.out.print(" "+driveExpression.getOpValue().toString());      
        System.out.println(" "+driveExpression.getRightValue().toString());      
      }
*/               
      if( driveExpression.getOpValue().equals("&&") ){
        result= land( resolveDriveExpression(driveExpression.getLeftValue()),
	              resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("||") ){
	result= lor( resolveDriveExpression(driveExpression.getLeftValue()),
		     resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals(">=") ){
        result= cge( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("<=") ){
        result= cle( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("==") ){
        result= ceq( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("!=") ){
        result= cne( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("+") ){
        result= add( resolveDriveExpression(driveExpression.getLeftValue()),
	              resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("-") ){
        result= sub( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("*") ){
        result= mul( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else if( driveExpression.getOpValue().equals("/") ){
        result= div( resolveDriveExpression(driveExpression.getLeftValue()),
	             resolveDriveExpression(driveExpression.getRightValue()));
        }
      else{
        throw new NoSupportException(driveExpression.getLeftValue().toString()+
                                     driveExpression.getOpValue().toString()+
				     driveExpression.getRightValue().toString()
                                     );     
      } 
/*
      if( _DEBUG ) System.out.println( "resolveDriveExpression i: "+
        result.toString() );      
*/
      return result;
    } //instanceof DriveExpression
    else{
      // try to replace the keyword (can be on both sides)
      // from the driveExpression against a value from the request
      try{
        if ( _requestAssigns.containsKey( result ) )
             result= _requestAssigns.get( result );
      }catch( Exception e){
        // ups, Request has no Hashtable _requestAssigns defined.
	//      -> no look up, no replace!
      }
/*
      if( _DEBUG ) System.out.println( "resolveDriveExpression= "+
        result.toString() );
*/	  
      return result;      
    }
  } //method resolveDriveExpression


  private Boolean land( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    try{
      Boolean left= (Boolean)leftValue;
      Boolean right= (Boolean)rightValue;
      return  new Boolean( left.booleanValue() && right.booleanValue() );   
    }catch( ClassCastException e){
      throw new NoSupportException(leftValue.toString()+
                                                "&&"+ rightValue.toString() );
    }
  } // method land


  private Boolean lor( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    try{
      Boolean left= (Boolean)leftValue;
      Boolean right= (Boolean)rightValue;
      return  new Boolean( left.booleanValue() || right.booleanValue() );   
    }catch( ClassCastException e){
      throw new NoSupportException(leftValue.toString()+
                                                 "||"+ rightValue.toString() );
    }
  } // method lor


  private Boolean cge( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    if( ( leftValue instanceof Integer) && ( rightValue instanceof Integer) )
    {
      Integer li= (Integer)leftValue; 
      Integer ri= (Integer)rightValue;
      if( li.intValue() >= ri.intValue() ) return TRUE; else return FALSE;
    }
    else if( rightValue instanceof Integer)
    {
      rightValue= new Float( rightValue.toString() ); 
    }
    else if( leftValue instanceof Integer)
    {
      leftValue= new Float( leftValue.toString() );      	
    }
    
    try{
      Float lf = (Float)leftValue; 
        if( lf.compareTo( rightValue) < 0 ) return FALSE; else return TRUE;      
    }
    catch( ClassCastException ec){}
    catch( NumberFormatException ef){}

    throw new NoSupportException( leftValue.toString()+
                                                ">="+ rightValue.toString() );
  } // method cge

  
  private Boolean cle( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    if( ( leftValue instanceof Integer ) && ( rightValue instanceof Integer) )
    {
      Integer li= (Integer)leftValue; 
      Integer ri= (Integer)rightValue;
      if( li.intValue() <= ri.intValue() ) return TRUE; else return FALSE;
    }
    else if( rightValue instanceof Integer )
    {
        rightValue= new Float( rightValue.toString() );
    }
    else if( leftValue instanceof Integer )
    {
        leftValue= new Float( leftValue.toString() );
    }

    try{
        Float lf = (Float)leftValue; 
        if( lf.compareTo( rightValue ) > 0 ) return FALSE; else return TRUE;      
    }
    catch( ClassCastException ec ){}
    catch( NumberFormatException ef){}

    throw new NoSupportException( leftValue.toString()+
                                               "<=" + rightValue.toString() );
  } // method cle
 
  
  private Boolean ceq( Object leftValue, Object rightValue)
                                                   throws NoSupportException {
    if( ( rightValue instanceof Boolean ) && ( leftValue instanceof Boolean ) )
    {
      if( leftValue.equals( rightValue ) ) return TRUE; else return FALSE; 
    }
    else if(   ( leftValue instanceof Integer )
            && ( rightValue instanceof Integer) )
    {
      Integer li = (Integer)leftValue; 
      Integer ri = (Integer)rightValue;
      if( li.intValue() == ri.intValue() ) return TRUE; else return FALSE;
    }    
    else if(   ( leftValue instanceof UnReplaceAbleString )
            && ( rightValue instanceof String ) )
    {
      return( pmatch( leftValue.toString(), rightValue.toString() ) );
    }    
    else if(   ( rightValue instanceof UnReplaceAbleString )
            && ( leftValue instanceof String) )
    {
      return( pmatch( rightValue.toString(), leftValue.toString() ) );
    }
    else if(   ( leftValue instanceof String)
            && ( rightValue instanceof String ) )
    {
      if( leftValue.equals( rightValue.toString() ) ) return TRUE;
      else return FALSE;
    }
    else if(   ( rightValue instanceof String )
            && ( leftValue instanceof String) )
    {
      if( rightValue.equals( leftValue.toString() ) ) return TRUE;
      else return FALSE;
    }
    else if( leftValue instanceof Integer)
    {
      leftValue= new Float( leftValue.toString() );      
    }
    else if( rightValue instanceof Integer)
    {
      rightValue= new Float( rightValue.toString() );
    }        

    try{
      Float lf = (Float)leftValue; 
      if( 0 == lf.compareTo( rightValue ) ) return TRUE; else return FALSE;      
    }
    catch( ClassCastException ec){}
    catch( NumberFormatException ef){}

    throw new NoSupportException(leftValue.toString()+
                                                "=="+ rightValue.toString() );
  } // method ceq


  private Boolean cne( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
   if( ( rightValue instanceof Boolean ) && ( leftValue instanceof Boolean ) )
   {
     if( leftValue.equals( rightValue ) ) return FALSE; else return TRUE; 
   }
   else if(   ( leftValue instanceof Integer )
           && ( rightValue instanceof Integer) )
   {
     Integer li = (Integer)leftValue; 
     Integer ri = (Integer)rightValue;
     if( li.intValue() == ri.intValue() ) return FALSE; else return TRUE;
   }    
   else if(   ( leftValue instanceof UnReplaceAbleString )
           && ( rightValue instanceof String ) )
   {
     if( pmatch( leftValue.toString(), rightValue.toString() ).equals(TRUE) )
     return FALSE; else return TRUE; 
   }    
   else if(   ( rightValue instanceof UnReplaceAbleString )
           && ( leftValue instanceof String) )
   {
     if( pmatch( rightValue.toString(), leftValue.toString() ).equals(TRUE) )
     return FALSE; else return TRUE; 
   }
   else if(   ( leftValue instanceof String )
           && ( rightValue instanceof String ) )
   {
     if( leftValue.equals( rightValue.toString() ) ) return FALSE;
     else return TRUE;
   }
   else if(   ( rightValue instanceof String )
           && ( leftValue instanceof String) )
   {
     if( rightValue.equals( leftValue.toString() ) ) return FALSE;
     else return TRUE;
   }
   else if( leftValue instanceof Integer )
   {
     leftValue= new Float( leftValue.toString() );      
   }
   else if( rightValue instanceof Integer )
   {
     rightValue= new Float( rightValue.toString() );
   }        

   try{
     Float lf = (Float)leftValue; 
     if( 0 == lf.compareTo( rightValue ) ) return FALSE; else return TRUE;      
   }
   catch( ClassCastException ec){}
   catch( NumberFormatException ef){}

   throw new NoSupportException(leftValue.toString()+
                                                 "!="+ rightValue.toString());
  } // method cne


  private Object add( Object leftValue, Object rightValue )
                                                    throws NoSupportException{
    if( ( leftValue instanceof String ) || ( rightValue instanceof String ) )
    {
      try{
        String left= (String)leftValue;
        String right= (String)rightValue;
        return new StringBuffer().append(left).append(right).toString();
      }
      catch( ClassCastException ec ){
        throw new NoSupportException(leftValue.toString()+
                                                 "+"+ rightValue.toString() );
      }
    }  
    else if(   ( leftValue instanceof Integer )
            && ( rightValue instanceof Integer ) )
    {
      Integer li = (Integer)leftValue; 
      Integer ri = (Integer)rightValue; 
      return new Integer( li.intValue() + ri.intValue() ); 
    }
    else if( rightValue instanceof Integer)
    {
      rightValue= new Float( rightValue.toString() ); 
    }
    else if( leftValue instanceof Integer)
    {
      leftValue= new Float( leftValue.toString() );      	
    }

    try{
      Float lf = (Float)leftValue;
      Float rf = (Float)rightValue;
      return new Float(lf.floatValue() + rf.floatValue() );
    }
    catch( ClassCastException ec ){}
    catch( NumberFormatException ef){}
						    
    throw new NoSupportException(leftValue.toString()+
                                                  "+"+ rightValue.toString() );
  } // method add


  private Object sub( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    if( ( leftValue instanceof Integer ) && ( rightValue instanceof Integer ) )
    {
      Integer li = (Integer)leftValue; 
      Integer ri = (Integer)rightValue; 
      return new Integer( li.intValue()- ri.intValue() ); 
    }
    else if( rightValue instanceof Integer)
    {
      rightValue= new Float( rightValue.toString() ); 
    }
    else if( leftValue instanceof Integer)
    {
      leftValue= new Float( leftValue.toString() );      	
    }

    try{
      Float lf = (Float)leftValue;
      Float rf = (Float)rightValue;
      return new Float( lf.floatValue()- rf.floatValue() );
    }
    catch( ClassCastException ec ){}
    catch( NumberFormatException ef){}

    throw new NoSupportException( leftValue.toString() +
                                                "-" + rightValue.toString() );
  } // method sub

  
  private Object mul( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    if( ( leftValue instanceof Integer) && ( rightValue instanceof Integer ) )
    {
      Integer li = (Integer)leftValue; 
      Integer ri = (Integer)rightValue;
      return new Integer( li.intValue()* ri.intValue() ); 
    }
    else if( rightValue instanceof Integer )
    {
      rightValue= new Float( rightValue.toString() ); 
    }
    else if( leftValue instanceof Integer )
    {
      leftValue= new Float( leftValue.toString() );      	
    }

    try{
      Float lf = (Float)leftValue;
      Float rf = (Float)rightValue;
      return new Float(lf.floatValue()* rf.floatValue() );
    }
    catch( ClassCastException ec ){}
    catch( NumberFormatException ef){}

    throw new NoSupportException(leftValue.toString()+
                                                 "*"+ rightValue.toString() );
  } // method mul


  private Object div( Object leftValue, Object rightValue)
                                                    throws NoSupportException{
    if( ( leftValue instanceof Integer ) && ( rightValue instanceof Integer ) )
    {
      Integer li = (Integer)leftValue;
      Integer ri = (Integer)rightValue;
      try{ 
        if((li.intValue()%ri.intValue()) == 0){
          return new Integer( li.intValue()/ ri.intValue() ); 
        }else{
          return new Float( li.floatValue()/ ri.floatValue() ); 
        }
      }
      catch( ArithmeticException ae){
        throw new NoSupportException(leftValue.toString()+
                                                 "/"+ rightValue.toString() );
      }
    }  
    else if( rightValue instanceof Integer )
    {
      rightValue= new Float( rightValue.toString() ); 
    }
    else if( leftValue instanceof Integer )
    {
      leftValue= new Float( leftValue.toString() );      	
    }

    try{
          Float lf = (Float)leftValue;
	  Float rf = (Float)rightValue;
	  if ( rf.equals( new Float(0) ) ) throw new ArithmeticException();
          else return new Float( lf.floatValue()/ rf.floatValue() );
    }
    catch( ClassCastException ec ){}
    catch( NumberFormatException ef){}
    catch( ArithmeticException ea ){}
    
    throw new NoSupportException(leftValue.toString()+
                                                 "/"+ rightValue.toString() );
  } // method div

      
  private Boolean pmatch(String p, String s){
  // Match STRING against the filename pattern PATTERN,
  // returning PATTERN_MATCH if it matches, NO_PATTERN_MATCH if not.

    final Boolean NO_PATTERN_MATCH = Boolean.FALSE;
    final Boolean PATTERN_MATCH = Boolean.TRUE;
    boolean starSeen= false;  
    int pc= 0;
    int sc= 0;
    int endStar= 0;
    int endQuest= 0;    
    int startPosition= 0;
    String substring= null;
 
    if(p.equals(s))return PATTERN_MATCH;
                                                           // try pattern on s    			    
    while( pc < p.length() ){     
      switch( p.charAt(pc) ){
            
        case '?': sc++;                                 // consume a char in s  
		  if( sc > s.length() ) return NO_PATTERN_MATCH; // s is to short
		  pc++;
		  if( pc < p.length() ){}
		  else{
		    sc= s.length();
		    starSeen= false;
		  };
		  break;

        case '*': starSeen= true;                       // there have been a *
	          pc++;                       // is the * the last char in p ?
                  if( pc < p.length() ){}        
		  else{ sc= s.length();
		  };
                  break;

        default :                   // find in p a substring that must match s
	          endStar= p.indexOf( "*", pc );
		  if( endStar < 0 ) endStar= p.length();
		  endQuest= p.indexOf( "?", pc );
		  if( endQuest < 0 ) endQuest= p.length();
                  if( endQuest < endStar )endStar= endQuest;
		                                                // insulate it 
		  substring= p.substring( pc, endStar );
		                                         // does sub match s ?
		  startPosition= s.indexOf( substring, sc );
		                                                  // after a *
		  if( starSeen ){
		    if( startPosition < 0 ) return NO_PATTERN_MATCH;
		                          // s contains not substring                  
		  }                                          // or without a *
		  else{		                              
		    if( startPosition != sc ) return NO_PATTERN_MATCH;		  
		                            // wrong startingPosition
		  };
		  pc= pc+ substring.length();
		  sc= startPosition+ substring.length();
		  break;
      } // switch
    } // while

    if ( sc == s.length() ) return PATTERN_MATCH; // happy ending
    return NO_PATTERN_MATCH; // sad ending
  } // method pmatch
  
  	 
} // class RegFitCheck
