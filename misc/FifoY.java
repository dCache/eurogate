package eurogate.misc ;
import  java.util.Vector ;
 
/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
 public class FifoY extends Vector {
   private boolean _finished = false ;
   public synchronized void push( Object o ){
      if( _finished )throw new IllegalArgumentException( "Fifo finished" ) ;
      addElement( o ) ;
      notifyAll() ;
   } 
   public synchronized Object pop() throws InterruptedException {
      while( true ){ 
         if( ! isEmpty() ){
            Object tmp = firstElement() ;
            removeElementAt(0) ;
            return tmp ;
         }else if( _finished ){
            throw new InterruptedException("FINISHED") ;
         }else{
            wait() ;
         }
      }
   } 
   public synchronized Object pop( long timeout )throws InterruptedException {
      long start = System.currentTimeMillis() ;
      while( true ){ 
         if( ! isEmpty() ){
            Object tmp = firstElement() ;
            removeElementAt(0) ;
            return tmp ;
         }else if( _finished ){
            throw new InterruptedException("FINISHED") ;
         }else{
            long rest = timeout - ( System.currentTimeMillis() - start ) ;
            if( rest <= 0L )return null ;
            wait( rest ) ;
           
            
         }
      }
   } 
   public synchronized void close(){
      _finished = true ;
      notifyAll() ;
   }
   public Object spy(){
      return firstElement() ;
   }
 }
