package eurogate.db.pvl ;

import  dmg.util.cdb.* ;
import  eurogate.misc.parser.* ;
import java.util.* ;
import java.io.* ; 

/**
  *   <table border=1>
  *   <tr><th>Key</th><th>Meaning</th></tr>
  *   <tr><td>action</td><td>none, mounting, dismounting</td></tr>
  *   <tr><td>status</td><td>enabled, disabled</td></tr>
  *   <tr><td>owner</td><td>'owned by' or -</td></tr>
  *   <tr><td>cartridge</td><td>cartridge name in drive, or 'empty'</td></tr>
  *   <tr><td>specific</td><td>drive name seen by robotics</td></tr>
  *   <tr><td>device</td><td>drive name seen by I/O host</td></tr>
  *   <tr><td>selection</td><td>Drive selection string</td></tr>
  *   <tr><td>minimalBlock</td><td>minimal I/O Block size</td></tr>
  *   <tr><td>maximalBlock</td><td>maximal I/O Block size</td></tr>
  *   <tr><td>bestBlock</td><td>recommended I/O Block size</td></tr>
  *   <tr><td>accessTime</td><td>last setAction time / msec</td></tr>
  *   <tr><td>idleTime</td><td>idle time in seconds</td></tr>
  *   </table>
  */ 
 
public class      DriveHandle extends CdbFileRecordHandle {

   public DriveHandle( String  name ,
                       CdbContainable container  ,
                       CdbElementable element ){
    
        super( name , container , element ) ; 

   }
   public void setAction( String name ){
      setAttribute( "action" , name ) ;
      setTime( System.currentTimeMillis() ) ;
   }
   public void setStatus( String name ){
      setAttribute( "status" , name ) ;
      setTime( System.currentTimeMillis() ) ;
   }
   public void setCartridge( String name ){
      setAttribute( "cartridge" , name ) ;
   }
   public void setSpecificName( String name ){
      setAttribute( "specific" , name ) ;
   }
   public void setDeviceName( String name ){
      setAttribute( "device" , name ) ;
   }
   public void setOwner( String owner ){
      setAttribute( "owner" , owner ) ;
   }
   public String getOwner(){
      return (String)getAttribute("owner") ;
   }
   public String getCartridge(){
      return (String)getAttribute("cartridge") ;
   }
   public String getAction(){
      return (String)getAttribute("action") ;
   }
   public String getStatus(){
      return (String)getAttribute("status") ;
   }
   public String getDeviceName(){
      return (String)getAttribute("device") ;
   }
   public String getSpecificName(){
      return (String)getAttribute("specific") ;
   }
   public void setMinimalBlockSize( int size ){
      setAttribute( "minimalBlock" , ""+size ) ;
   }
   public void setMaximalBlockSize( int size ){
      setAttribute( "maximalBlock" , ""+size ) ;
   }
   public void setBestBlockSize( int size ){
      setAttribute( "bestBlock" , ""+size ) ;
   }
   public void setTime( long time ){
      setAttribute( "accessTime" , ""+time ) ;
   }
   public void setIdleTime( int idleTime ){
      setAttribute( "idleTime" , ""+idleTime ) ;
   }
   public int getMinimalBlockSize(){ 
      return getInteger("minimalBlock") ; 
   }
   public int getMaximalBlockSize(){ 
      return getInteger("maximalBlock") ; 
   }
   public int getBestBlockSize(){ 
      return getInteger("bestBlock") ; 
   }
   public long getTime(){ 
      return getLong("accessTime") ; 
   }
   public int getIdleTime(){ 
      return getInteger("idleTime") ; 
   }
  
   public int getInteger( String name ){
      String str = (String)getAttribute( name ) ;
      if( str == null )return 0 ;
      try{
         return Integer.parseInt( str ) ;
      }catch( NumberFormatException ee ){
         return 0 ;
      }
   }
   public long getLong( String name ){
      String str = (String)getAttribute( name ) ;
      if( str == null )return 0 ;
      try{
         return Long.parseLong( str ) ;
      }catch( NumberFormatException ee ){
         return 0 ;
      }
   }
   //
   // the selection stuff is a bit more complicated
   //
   private PCode _selectionCode = null ;
   public void setSelectionString( String selection ) throws Exception {
      if( ( selection == null ) || selection.equals("") || selection.equals("none") ){
         setAttribute( "selection" , "1 1 ==" ) ;
         return ;
      }
      PTokenizable pt = new PTokenizer( selection ) ;
      _selectionCode = PPVM.compile( pt ) ;
      setAttribute( "selection" , selection ) ;
   }
   public String getSelectionString(){
      return (String)getAttribute("selection") ;
   }
   public PCode getSelectionCode() throws Exception {
      if( _selectionCode != null )return _selectionCode ;
      
      String selection = getSelectionString() ;
      if( ( selection == null )  || 
            selection.equals("") || 
            selection.equals("none")   )
         selection = "1 1 ==" ;
         
      PTokenizable pt = new PTokenizer( selection ) ;
      return _selectionCode = PPVM.compile( pt ) ;
   }
} 
