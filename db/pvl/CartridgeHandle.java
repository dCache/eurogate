package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ;

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>status</td><td>ok, readOnly, bad</td></tr>
  *   <tr><td>mode</td><td>online, offline</td></tr>
  *   <tr><td>cartridgeDescriptor</td><td>corresponding cartridgeDescriptor</td></tr>
  *   <tr><td>volumeList</td><td>list of volumes on this cartridge</td></tr>
  *   <tr><td>usageCount</td><td>number of times the cartridge has been used</td></tr>
  *   </table>
  */ 
 
public class CartridgeHandle extends CdbFileRecordHandle {

   public CartridgeHandle( String  name ,
                           CdbContainable container  ,
                           CdbElementable element ){
    
        super( name , container , element ) ;                    
   }
   /**
     *  get the corresponding cartridgeDescriptor.
     */
   public String getCartridgeDesciptor(){
      return (String)getAttribute( "cartridgeDescriptor" ) ;
   }
   /**
     * set the status of this cartridge (ok, readOnly, bad)
     *
     */
   public void setStatus( String status ){
      setAttribute( "status" , status ) ; 
   }
   /**
     * get the status of this cartridge (ok, readOnly, bad)
     *
     */
   public String getStatus(){
      return (String)getAttribute( "status" ) ;
   }
   /**
     * set the mode of this cartridge (online, offline)
     *
     */
   public void setMode( String mode ){
      setAttribute( "mode" , mode ) ; 
   }
   /**
     * get the mode of this cartridge (online, offline)
     *
     */
   public String getMode(){
      return (String)getAttribute( "mode" ) ;
   }
   /**
     * get list of all volumes on this cartridge
     *
     */
   public String [] getVolumeNames(){
      String [] list = (String [])getAttribute("volumeList") ;
      return list == null ? new String[0] : list ;
   }
   /**
     *  gets the usage counter.
     */
   public int getUsageCount(){
       return getIntAttribute("usageCount") ;
   }
   /**
     *  sets the usage counter.
     */
   public void setUsageCount( int count ){
       setAttribute("usageCount" , ""+count ) ;
   }


} 
