package eurogate.misc ;
public class Version {
    public static void main( String [] args ){
       try{
           Class c = Class.forName( "dmg.util.Args" ) ;
       }catch(Exception iee){
           System.err.println("Cells not found") ;
           System.exit(4);
       }
       System.out.println("1.2") ;
       System.exit(0);
    }
}
