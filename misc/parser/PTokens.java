package eurogate.misc.parser ;

import java.util.* ;
import java.text.* ;

public class PTokens extends Hashtable {
   private static final int IDLE        = 0 ;
   private static final int KEY         = 1 ;
   private static final int GOT_EQ      = 2 ;
   private static final int IN_QUOTES   = 3 ;
   private static final int END_QUOTES  = 4 ;
   private static final int IN_VALUE    = 5 ;
   private static final char END_OF_INFO = '\0' ;
   public  PTokens( String line )throws ParseException {
   
       scanLine( line ) ;
   }
   private void scanLine( String line )throws ParseException{
     int state = IDLE ;
     int len   = line.length() ;
     StringBuffer key    = null ;
     StringBuffer value  = null ;
     char c ;
     for( int i = 0 ; i <= len ; i ++ ){
        if( i == len )c = END_OF_INFO ;
        else c = line.charAt(i) ;
        switch( state ){
          case IDLE :
            if( ( c == ' ' ) || ( c == ';' ) ){
               continue ;
            }else if( c == END_OF_INFO ){
               return ;
            }else if( c == '=' ){
               throw new 
               ParseException("no key before = sign",i) ;
            }else{
               key   = new StringBuffer() ;
               key.append(c) ;
               state = KEY ;
            }
          break ;
          case KEY :
            if( c == ' ' )continue ;
            if( ( c == END_OF_INFO ) || ( c == ';' ) ){
               put( key.toString() , "" ) ;
               state = IDLE ;
               break ;
            }
            if( c == '=' ){
               value = new StringBuffer() ;
               state = GOT_EQ ;
               break ;
            }
            key.append(c) ;
          break ;
          case GOT_EQ :
            if( c == ' ' ){
               continue ;
            }else if( ( c == END_OF_INFO ) || ( c == ';' ) ){
               put( key.toString() , "" ) ;
               state = IDLE ;
            }else if( c == '"' ){
               state = IN_QUOTES ;
               value = new StringBuffer() ;
            }else{
               value = new StringBuffer() ;
               value.append(c) ;
               state = IN_VALUE ;
            }
          break ;
          case IN_QUOTES :
            if( c == '"' ){
               put( key.toString() , value.toString() ) ;
               state = END_QUOTES ;
            }else if( c == END_OF_INFO ){
               throw new
               ParseException( "EOL in quotes (end quote expected)" , i);
            }else{
               value.append(c) ;
            }
          break ;
          case END_QUOTES :
            if( c == ' ' ){
               continue ;
            }else if(  ( c == END_OF_INFO ) || ( c == ';' ) ){
               state = IDLE ;
            }else{
               throw new
               ParseException( "; or EOL expected, found "+c , i ) ;
            }
          break ;
          case IN_VALUE :
            if( c == ' ' ){
               continue ;
            }else if( ( c == END_OF_INFO ) || ( c == ';' ) ){
               put( key.toString() , value.toString() ) ;
               state = IDLE ;
            }else{
               value.append(c) ;
            }
          break ;

        }


     }
   }
   public String toList(){
      StringBuffer sb = new StringBuffer() ;
      Enumeration keys = keys() ;
      while( keys.hasMoreElements() ){
         String key = (String)keys.nextElement() ;
         String val = (String)get(key) ;
         System.out.println( key+"="+val ) ;
      }
      return sb.toString();
   }
   public static void main( String [] args )throws Exception {
       if( args.length < 1 ){
          System.err.println( "Usage : ... <parseLine>" ) ;
          System.exit(4);
       }
//       System.out.println( "Line >"+args[0]+"<" ) ;
       try{
          PTokens pt = new PTokens( args[0] ) ;
          System.out.print( pt.toList() ) ;
       }catch( ParseException pe ){
          System.err.println( "error at "+pe.getErrorOffset()+
                              " : "+pe.getMessage() ) ;
       }
   }

}
