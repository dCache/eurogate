package eurogate.db.pvr ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   </table>
  */ 
 
public class  PvrObjectHandle extends CdbFileRecordHandle {

   public PvrObjectHandle( String  name ,
                          CdbContainable container  ,
                          CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   public void setLocation( String name ){
      setAttribute( "location" , name ) ;
   }
   public String getLocation(){
      return (String)getAttribute("location") ;
   }
   public void setMode( String name ){
      setAttribute( "mode" , name ) ;
   }
   public String getMode(){
      return (String)getAttribute("mode") ;
   }


} 
  
