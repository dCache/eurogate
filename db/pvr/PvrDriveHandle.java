package eurogate.db.pvr ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>cartridge</td><td>cartridge name in drive, or 'empty'</td></tr>
  *   </table>
  */ 
 
public class  PvrDriveHandle extends PvrObjectHandle {

   public PvrDriveHandle( String  name ,
                       CdbContainable container  ,
                       CdbElementable element ){
    
        super( name , container , element ) ; 
        

   }
   public void setCartridge( String name ){
      setAttribute( "cartridge" , name ) ;
   }
   public String getCartridge(){
      return (String)getAttribute("cartridge") ;
   }
} 
 
