package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>status</td><td>empty, used, full</td></tr>
  *   <tr><td>cartridge</td><td>The cartridge this volume resides on</td></tr>
  *   <tr><td>volumeDescriptor</td><td>The corresponding VolumeDescriptor</td></tr>
  *   <tr><td>pvr</td><td>The responsible pvr</td></tr>
  *   <tr><td>residualBytes</td><td>Number of unused bytes</td></tr>
  *   <tr><td>eor</td><td>End Of Recording (initial value '-')</td></tr>
  *   <tr><td>position</td><td>Position of this volume on its cartridge</td></tr>
  *   <tr><td>files</td><td>Number of files on this volume</td></tr>
  *   </table>
  */ 
public class  VolumeHandle extends CdbFileRecordHandle {

   public VolumeHandle( String  name ,
                        CdbContainable container  ,
                        CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   void setVolumeDescriptor( String name ){
      setAttribute( "volumeDescriptor" , name ) ;
   }
   /**
     * set the position of this volume on its cartridge
     */
   public void setPosition( String position ){
      setAttribute( "position" , position ) ;
   }
   /**
     * get the position of this volume on its cartridge.
     * The initial value is a zero '0'.
     */
   public String getPosition(){
      String position = (String)getAttribute("position") ;
      return position == null ? "0" : position ;
   }
   /**
     * set the current 'end of recording position'
     */
   public void setEOR( String eor ){
      setAttribute( "eor" , eor ) ;
   }
   /**
     * get the current 'end of recording position'
     * The initial value is a hyphen '-'.
     */
   public String getEOR(){
      String eor = (String)getAttribute("eor") ;
      return eor == null ? "-" : eor ;
   }
   /**
     *  gets the file counter.
     */
   public int getFileCount(){
       return getIntAttribute("files") ;
   }
   /**
     *  sets the file counter.
     */
   public void setFileCount( int count ){
       setAttribute("files" , ""+count ) ;
   }
   /**
     *  set the number of unused bytes on this volume
     */
   public void setResidualBytes( long residualBytes ){
      setAttribute( "residualBytes" , ""+residualBytes ) ; 
   }
   /**
     *  get the number of unused bytes on this volume
     */
   public long getResidualBytes(){
      String x = (String)getAttribute( "residualBytes" ) ;
      if( x == null )return 0 ;
      try{
         return  Long.parseLong( x ) ;
      }catch( NumberFormatException nfe ){
         return 0 ;
      }
   }
   /**
     *  get the current status of this volume (empty, used, full)
     */
   public String getStatus(){
      return (String)getAttribute( "status" ) ;
   }
   /**
     *  set the current status of this volume (empty, used, full)
     */
   public void setStatus( String status ){
      setAttribute( "status" , status ) ;
   }
   /**
     *  get the responsible Pvr Name
     */
   public String getPvr(){
      return (String)getAttribute( "pvr" ) ;
   }
   /**
     *  get the cartridge name, this volume resides on.
     */
   public String getCartridge(){
      return (String)getAttribute( "cartridge" ) ;
   }
   /**
     *  get the corresponding volumeDescriptor.
     */
   public String getVolumeDescriptor(){
      return (String)getAttribute( "volumeDescriptor" ) ;
   }
} 
