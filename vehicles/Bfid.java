package eurogate.vehicles ;

import java.util.* ;
import java.net.* ;
import java.io.* ;

public class Bfid implements Serializable {
   private static Integer      __portPart ;
   private static Date         __datePart ;
   private static long         __counterPart = 0 ;
   private static Object       __lock  = new Object() ;
   private static Long         __hostPart ;
   private static boolean      __initReady  = false ;
   
   private static void     __initId() {
         ServerSocket ss = null ;
         try{
           ss = new ServerSocket(0) ;
           __portPart = new Integer( ss.getLocalPort() ) ;
           byte [] addr = InetAddress.getLocalHost().getAddress() ;
           long l = 0 ;
           for( int i = 0 ; i < addr.length ; i++ ){
              l <<= 8 ;
              byte b = addr[i] ;
              int  j = ( b < 0 ) ? ( 255 + b) : b ;
              l |= ( j & 0xff ) ;
           }
           __hostPart = new Long( l ) ;
         }catch( IOException ioe ){
           __portPart    = new Integer( 5555 ) ;
           __hostPart    = null ;
         }
         __datePart    = new Date() ;
         try{ ss.close() ; }catch( IOException itoe ){}
         __counterPart  = 1 ;
         __initReady    = true ;
   }
   private static long __nextId() {
      synchronized( __lock ){
          if( ! __initReady )__initId() ;
          return __counterPart ++  ;
      }
   
   }
   //
   // object part
   //
   private Long    _counterPart = null ;
   private Long    _hostPart    = null ;
   private Integer _portPart    = null ;
   private Long    _datePart    = null ;
   public Bfid(){
      _counterPart = new Long( __nextId() ) ;
      _hostPart    = __hostPart ;
      _portPart    = __portPart ;
      _datePart    = new Long( __datePart.getTime() ) ;
   }
   public Bfid( String bfid ){
      StringTokenizer st = new StringTokenizer( bfid , "." ) ;
      if( st.countTokens() != 4 )
           throw new IllegalArgumentException("not a legal bfid");
      try{
         _hostPart = new Long( st.nextToken() ) ;
         _portPart = new Integer( st.nextToken() ) ;
         _datePart = new Long( st.nextToken() ) ;
         _counterPart = new Long( st.nextToken() ) ;
      }catch( Exception e ){
           throw new IllegalArgumentException("not a legal bfid");
      }
   }
   public int hashCode(){
      return _hostPart.hashCode() |
             _portPart.hashCode() |
             _datePart.hashCode() |
             _counterPart.hashCode() ;
   }
   public boolean equals( Object other ){
      return _hostPart.equals( ((Bfid)other)._hostPart ) &
             _portPart.equals( ((Bfid)other)._portPart ) &
             _datePart.equals( ((Bfid)other)._datePart ) &
             _counterPart.equals( ((Bfid)other)._counterPart ) ;
   }
   public String toString(){
      return         _hostPart.toString()+"."+
                     _portPart.toString()+"."+
                     _datePart.toString()+"."+
                     _counterPart.toString()   ;
   }
   //
   // test part
   //
   public static void main( String [] args ){
       Hashtable h = new Hashtable() ;
       if( args.length > 0 ){
       
           Bfid bfid = new Bfid( args[0] ) ;
           System.out.println( ""+bfid.toString() ) ;
           System.exit(0);
       }
       int n = 10 ;
       Bfid [] bfids = new Bfid[n] ;
       for( int i = 0 ; i < bfids.length ; i++ ){
           bfids[i]   = new Bfid() ;
           Integer c  = new Integer( i ) ;
           h.put( bfids[i] , c ) ;
           System.out.println( "num "+i+" -> "+bfids[i] ) ;
       }   
       Object o = h.get( bfids[n/2] ) ;
       System.out.println( " Finding "+bfids[n/2]+" in "+o ) ;
   }
} 
