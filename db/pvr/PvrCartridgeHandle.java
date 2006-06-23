package eurogate.db.pvr ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   </table>
  */ 
 
public class  PvrCartridgeHandle extends PvrObjectHandle {

   public PvrCartridgeHandle( String  name ,
                       CdbContainable container  ,
                       CdbElementable element ){
    
        super( name , container , element ) ; 

   }
} 
  
