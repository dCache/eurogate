public class X {

   public X(){

      try{

          System.out.println( "Start" ) ;
          return ;

      }finally{
         System.out.println( "Finally" ) ;
      }
   }

   public static void main( String [] args ){
   
       new X() ;
   
   }
}
