package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>type</td><td>cartridge type (9840, dlt, exabyte)</td></tr>
  *   <tr><td>mode</td><td>type dependent (dlt7000, mammoth)</td></tr>
  *   </table>
  */ 
 
public class  CartridgeDescriptorHandle extends ElementDescriptorHandle {

   public CartridgeDescriptorHandle( String  name ,
                                     CdbContainable container  ,
                                     CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   /**
     *  set the cartridge type.
     */
   public void setType( String type ){
      setAttribute( "type" , type ) ;
   }
   /**
     *  get the cartridge type.
     */
   public String getType(){
      return (String)getAttribute( "type" ) ;
   }
   /**
     *  set the type dependent cartridge mode .
     */
   public void setMode( String type ){
      setAttribute( "mode" , type ) ;
   }
   /**
     *  get the type dependent cartridge mode .
     */
   public String getMode(){
      return (String)getAttribute( "mode" ) ;
   }
} 
