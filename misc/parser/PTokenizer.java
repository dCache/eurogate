package eurogate.misc.parser ;

import java.util.* ;
import java.text.* ;
import java.io.* ;

public class PTokenizer implements PTokenizable {
   private Reader _reader = null ;
   private final static int IDLE     = 0 ;
   private final static int QUOTED   = 1 ;
   private final static int DOLLARED = 2 ;
   private final static int STRINGED = 3 ;
   private final static int OPER     = 4 ;
   public PTokenizer( String line ){
       _reader = new StringReader(line) ;
   }
   
   private boolean _undo = false ;
   private char    _res  = 0 ;
   private char nextChar() throws IOException { 
      if( _undo ){ _undo = false  ; return _res ; }
      else return (char)_reader.read() ;
   }
   private void undo( char r ){ _res = r ; _undo = true ; }
   
   private final static char   END_OF_INFO = (char)-1 ;
   private final static String OPERS       = "/+-*=&|!" ;
   
   public synchronized PObject nextToken() throws ParseException, IOException {
      char c = 0 ;
      int state = IDLE ;
      StringBuffer sb = new StringBuffer() ;
      do{
         c = nextChar() ;
//         System.out.println( "state="+state+";c="+c) ;
         switch( state ){
            case IDLE :
              if( c == ' ' ){
                 continue ;
              }else if( c == '\"' ){
                 state = QUOTED ;
              }else if( OPERS.indexOf(c) > -1 ){
                 sb.append(c) ;
                 state = OPER ;
              }else if( c == '$' ){
                 state = DOLLARED ;
              }else if( c == END_OF_INFO ){
                 return null ;
              }else{
                 state = STRINGED ;
                 sb.append(c) ;
              }
            break ;
            case  QUOTED : 
              if( c == '\"' ){
                 return new QuotedString(sb.toString());
              }else if( c == END_OF_INFO ){
                 undo(c);
                 return new QuotedString(sb.toString());
              }else{
                 sb.append(c) ;
              }
            break ;
            case DOLLARED :
            case STRINGED :
              if( ( c == ' '         ) || 
                  ( c == '$'         ) || 
                  ( c == END_OF_INFO ) ||
                  ( OPERS.indexOf(c) > -1 ) ){
                 undo(c);
                 return state == DOLLARED ?
                        (PObject)new VariableString(sb.toString()) :
                        (PObject)new PlainString(sb.toString());
              }else{
                 sb.append(c);
              }
            break ;
            case OPER :
              if( OPERS.indexOf(c) > -1 ){
                 sb.append(c) ;
              }else{
                 undo(c);
                 return new OperatorString(sb.toString());
              }
            break ; 
         
         }
      }while( c != END_OF_INFO ) ;
      return null ;
   }
   public static void main( String [] args )throws Exception {
       if( args.length < 1 ){
          System.err.println( "Usage : ... <parseLine>" ) ;
          System.exit(4);
       }
       PObject po = null ;
       try{
          PTokenizer pt = new PTokenizer( args[0] ) ;
          while( ( po = pt.nextToken() ) != null ){
             System.out.println( po.getClass().getName()+" : "+po ) ;
          }
       }catch( ParseException pe ){
          System.err.println( "error at "+pe.getErrorOffset()+
                              " : "+pe.getMessage() ) ;
       }
   
   }

}
