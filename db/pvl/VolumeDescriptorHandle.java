package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>size</td><td>maximum number of bytes on this volume type</td></tr>
  *   </table>
  */ 
   
public class  VolumeDescriptorHandle extends ElementDescriptorHandle {

   public VolumeDescriptorHandle( String  name ,
                                  CdbContainable container  ,
                                  CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   /**
     * set the maximum number of bytes for this volume type
     */
   public void setSize( long size ){
     setAttribute( "size" , ""+size ) ;
   }
   /**
     * get the maximum number of bytes for this volume type
     */
   public long getSize(){
      String s = (String)getAttribute("size") ;
      if( s == null )return 0 ;
      try{
         return Long.parseLong( s ) ;
      }catch( NumberFormatException nfe ){
         return 0 ;
      }
   }
} 
