package eurogate.vehicles ;

import java.io.* ;
import java.util.* ;

public class PvrUpRequest implements Serializable {
   private String _name ;
   private Vector _list = new Vector() ;
   public PvrUpRequest( String name ){
      _name = name ;
   }
   public String getName(){ return _name ; }
   public void addEmptyDrive( String generic , String specific ){
       String [] drive = new String[2] ;
       drive[0] = generic ;
       drive[1] = specific ;
       _list.addElement( drive ) ;
   }
   public String [][] getEmptyDrives(){
      String [] [] res = new String [_list.size()][] ;
      for( int i = 0 ; i < res.length ; i++ )
         res[i] = (String [])_list.elementAt(i) ;
      return res ;
   }

}
