package  eurogate.pvr ;

import java.net.* ;
import java.io.* ;
import java.util.* ;

import dmg.util.* ;

public class Robotic implements Runnable {
   private Fifo     _fifo   = new Fifo() ;
   private Thread   _worker = null ;
   public Robotic(){
       _worker = new Thread( this ) ;
       _worker.start() ;
   
   }
   public void waitForRobotic(){
      Gate gate = new Gate() ;
      gate.close() ;
      _fifo.push( gate ) ;
      gate.check() ;
   
   }
   public void run(){
      while( true ){
          Gate gate = null ;
          try{
             gate = (Gate)_fifo.pop() ;
             Thread.currentThread().sleep(5000) ; 
             gate.open() ;
          }catch(InterruptedException iae ){
             break ;
          }
      }
   }

}
