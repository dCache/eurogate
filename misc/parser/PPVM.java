package eurogate.misc.parser ;

import java.util.* ;
import java.text.* ;
import java.io.* ;

public class PPVM {

   static private class DummyDictionary extends Dictionary {
      public int size(){ return 0 ; }
      public boolean isEmpty(){ return true ; }
      public Enumeration keys(){ return null ; }
      public Enumeration elements(){ return null ; }
      public Object get( Object key ){ return "x" ; }
      public Object put( Object key , Object value ){ return null ;}
      public Object remove( Object key ){ return null ;}
   }
   public static void check( PTokenizable tokens )
          throws IOException , 
                 ParseException,
                 EmptyStackException  {
                 
       execute( tokens , new DummyDictionary() ) ;
   }
   public static class CompiledTokens implements PTokenizable {
      private Enumeration _e = null ;
      private CompiledTokens( Vector v ){
         _e = v.elements() ;
      }
      public PObject nextToken() throws ParseException , IOException {
         return _e.hasMoreElements() ? (PObject)_e.nextElement() : null ;
      }
   
   }
   public static PCode compile( PTokenizable tokens )
          throws IOException , 
                 ParseException,
                 EmptyStackException  {
          
       PObject t   = null ;
       PCode   v   = new PCode() ;
       while( ( t = tokens.nextToken() ) != null )
         v.addElement(t) ;
       
       check( v.getTokenizer() ) ;
       
       return v ;          
   }
   public static boolean execute( PTokenizable tokens , Dictionary dictionary )
          throws IOException , 
                 ParseException,
                 EmptyStackException  {
          
       Stack stack = new Stack() ;  
       PObject t   = null ;
       
       while( ( t = tokens.nextToken() ) != null ){
          String s = t.toString() ;
          if( t instanceof PlainString ){
             String r = (String)dictionary.get(s) ;
             stack.push( r == null ? s : r ) ;
          }else if( t instanceof QuotedString ){
             stack.push(s) ;
          }else if( t instanceof VariableString ){
             Object r = dictionary.get(s) ;
             stack.push( r == null ? "" : r ) ;
          }else if( t instanceof OperatorString ){
             Object o1 = stack.pop() ;
             Class  c1 = o1.getClass() ;
             
             if( s.equals( "!" ) ){
                if( c1 != java.lang.Boolean.class )
                   throw new
                   IllegalArgumentException("can only invert Boolean" ) ;
                stack.push( new Boolean( ! ((Boolean)o1).booleanValue() ) ) ;
                continue ;
             }
             Object o2 = stack.pop() ;
             Class  c2 = o2.getClass() ;
             boolean r ;
             if( c1 != c2 )
                throw new
                IllegalArgumentException("Can't compare "+c1.getName()+
                                         " with "+c2.getName() ) ;
             if( s.equals("==") ){
                stack.push( 
                    new Boolean( o1.toString().equals( o2.toString() ) ) );
             }else if( s.equals("===") ){
                stack.push( 
                    new Boolean( 
                       o1.toString().equalsIgnoreCase( o2.toString() ) ) );
             }else if( s.equals("!=") ){
                stack.push( 
                    new Boolean( ! o1.toString().equals( o2.toString() ) ) );
             }else if( s.equals("!==") ){
                stack.push( 
                    new Boolean( 
                       ! o1.toString().equalsIgnoreCase( o2.toString() ) ) );
             }else if( s.equals("+") ){
                if( c1 != java.lang.String.class )
                  throw new
                  IllegalArgumentException(
                     "Can only add Strings, not "+c1.getName());
                stack.push( o1.toString()+o2.toString() ) ;
                          
             }else if( s.equals("&&") ){
                if( ! ( c1 == java.lang.Boolean.class ) )
                  throw new
                  IllegalArgumentException(
                     "Can only compare Boolean, not "+c1.getName());
                stack.push(
                   new Boolean( ((Boolean)o1).booleanValue() &&
                                ((Boolean)o2).booleanValue()    )
                          ) ;
             }else if( s.equals("||") ){
                if( ! ( c1 == java.lang.Boolean.class ) )
                  throw new
                  IllegalArgumentException(
                      "Can only compare Boolean, not "+c1.getName());
                stack.push(
                   new Boolean( ((Boolean)o1).booleanValue() ||
                                ((Boolean)o2).booleanValue()    )
                          ) ;
             }else{
                throw new
                IllegalArgumentException("Unknown operation : "+s ) ;
             }
          }
       }
       if( stack.size() != 1 )
         throw new
         IllegalArgumentException( "Stack not empty" ) ;
       Object res = stack.pop() ;
       if( ! ( res instanceof Boolean ) )
         throw new
         IllegalArgumentException( "Top stack element not boolean" ) ;
       return ((Boolean)res).booleanValue() ;
   }
   
   public static void main( String [] args )throws Exception {
      if( args.length < 2 ){
         System.err.println( "Usage : ... <drive> <request>" ) ;
         System.exit(4);
      }
//      System.out.println( "Drive : >"+args[0]+"<" ) ;
      PTokenizable tokens = new PTokenizer( args[0] ) ;
      Dictionary   dict   = new PTokens( args[1] ) ;     
      PCode        code   = PPVM.compile( tokens ) ; 
      boolean result = PPVM.execute( code.getTokenizer() , dict ) ;
      System.out.println( "Result : "+result);
   }

}
