
package eurogate.misc ;

import java.util.* ;

public class FifoX {

   private int       _maxSize = 0 , 
                     _nextIn  = 0 ,
                     _nextOut = 0  ;
   private Object [] _array   = null ;
   
   public FifoX(){ this(0) ; }
   public FifoX( int maxSize ){
      _maxSize = maxSize ;
      if( maxSize < 0 ){
        throw new 
        IllegalArgumentException( "maxSize < 0 not supported" ) ;
      }else{
        _array = new Object[_maxSize==0?64:_maxSize] ;
      }
   }
   public synchronized void put( Object obj ){
      if( (_nextIn - _nextOut) >= _maxSize ){
         if( _maxSize > 0 )
            throw new IllegalArgumentException("maxSize exceeded" ) ;
         //
         // extend the array
         //
         Object [] newArray = new Object[_array.length+16] ;
         int i = 0 ;
         for( i = 0 ; 
              ( obj = get() ) != null ; i++ )newArray[i] = obj ;
         _nextOut = 0 ;
         _nextIn  = i ;
         _array   = newArray ;
      }
      _array[_nextIn++%_array.length] = obj ;
   
   }
   public synchronized Object get(){
     if( ( _nextIn - _nextOut ) <= 0 )return null ;
     Object rc = _array[_nextOut%_array.length] ; 
     _array[_nextOut++%_array.length] = null ;
     return rc ;
   }
   public synchronized  int size(){ return  _nextIn - _nextOut ; }
   public synchronized  Enumeration elements(){
      Vector v = new Vector() ;
      for( int i = _nextOut ; i < _nextIn ; i++ )
         v.addElement( _array[i%_array.length] ) ;
      return v.elements() ;
   }
}
