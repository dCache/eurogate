package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>volumeList</td><td>list of all volumes in this subset</td></tr>
  *   <tr><td>windowSize</td><td>number of concurrent volumes</td></tr>
  *   <tr><td>windowStart</td><td>First window volume</td></tr>
  *   <tr><td>windowEnd/td><td>Last window volume</td></tr>
  *   </table>
  */ 
 
public class  PvrVolumeSubsetHandle extends CdbFileRecordHandle {

   public PvrVolumeSubsetHandle( String  name ,
                                 CdbContainable container  ,
                                 CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   void addVolumeName( String volumeName ){
       addListItem( "volumeList" , volumeName ) ;
   }
   void removeVolumeName( String volumeName ){
       removeListItem( "volumeList" , volumeName ) ;
   }
   /**
     *  returns the list of all volumes within this subset.
     */
   public String [] getVolumeNames(){ 
       return (String [])getAttribute( "volumeList" ) ;
   }
   /**
     *  sets the window size for this subset.
     */
   public void setWindowSize( int windowSize ){
       setAttribute( "windowSize" , windowSize ) ;
   }
   /**
     *  gets the window size for this subset.
     */
   public int getWindowSize(){
       return getIntAttribute("windowSize") ;
   }
   /**
     *  sets the first and last volume of the current window.
     */
   public void setWindow( String first , String last ){
       setAttribute( "windowStart" , first ) ;
       setAttribute( "windowEnd"   , last  ) ;
   }
   /**
     *  gets the first volume of the current window.
     */
   public String getWindowStart(){ 
       return (String)getAttribute( "windowStart" ) ;
   }
   /**
     *  gets the last volume of the current window.
     */
   public String getWindowEnd(){ 
       return (String)getAttribute( "windowEnd" ) ;
   }
   /**
     *  gets all volumes of the current window.
     */
   public String [] getWindow(){ 
      String start   = (String)getAttribute( "windowStart" ) ;
      String end     = (String)getAttribute( "windowEnd" ) ;
      String [] list = (String [])getAttribute("volumeList") ;
      int i = 0 , j = 0 ;
      for( ; ( i < list.length ) && ( ! list.equals(start) ) ; i++ ) ;                       
      if( i  == list.length )
         throw new 
         IllegalArgumentException( "Database incon. (start not found)" ) ;
      int s = i ;
      for( ; ( i < list.length ) && ( ! list.equals(end) ) ; i++ ) ;
      if( i  == list.length )
         throw new 
         IllegalArgumentException( "Database incon. (end not found)" ) ;
      int e = i ;
      String [] res  = new String[e-s+1] ;
      int l = 0 ;
      for( i = s ; i <= e ; i++ )res[l++] = list[i] ;
      return res ;
   }
} 
