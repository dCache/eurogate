package eurogate.misc ;


import java.util.* ;
import java.text.* ;

public class Pinboard {

   private Vector       _pin = new Vector() ;
   private DateFormat   _df  = new SimpleDateFormat("hh.mm.ss " ) ;
   private int          _size = 0 ;
   private class PinEntry {
      private String _message ;
      private Date   _date ;
      public PinEntry( String message ){
         _message = message ;
	 _date = new Date() ;
      }
      public String toString(){
         return _df.format(_date)+" "+_message ;
      }
   }
   public Pinboard(){
      this( 20 ) ;
   }
   public Pinboard( int size ){
      _size = size ;
   }
   public synchronized void pin( String note ){
      _pin.addElement( new PinEntry( note ) ) ;
      if( _pin.size() > _size )_pin.removeElementAt(0) ;
   }
   public synchronized void dump( StringBuffer sb ){
       dump( sb , _size ) ;
   }
   public synchronized void dump( StringBuffer sb , int last ){
       int i = _pin.size() - last + 1 ;
       for( i =i<0?0:i ; i < _pin.size() ; i++)
         sb.append(_pin.elementAt(i).toString()).append("\n") ;
   }

}
