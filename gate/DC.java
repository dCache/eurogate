package eurogate.gate ;

import eurogate.vehicles.* ;
import eurogate.misc.* ;

import dmg.util.* ;
import dmg.cells.nucleus.* ;
import java.net.* ;
import java.util.* ;
import java.io.* ;

public class DC extends CellAdapter implements Runnable {

    private Thread       _readerThread = null ;
    private CellNucleus  _nucleus      = null ;
    private Args         _args         = null ;
    private String       _mode         = "Unknown" ;
    private int          _counter      = 0 ;
    
    public DC( String name , String args ){
        super( name , args , false ) ;
        
        _nucleus = getNucleus() ;
        _args    = getArgs() ;
        
            if( _args.argc() < 1 )_mode = "client" ;
            else _mode = "master" ;
                
        _readerThread = _nucleus.newThread( this ) ;
        _readerThread.start() ;
        start() ;
    }
    public String toString(){
      return "Mode="+_mode ;
    }
    public void getInfo( PrintWriter pw ){
       super.getInfo( pw ) ;
       pw.println( "Mode : "+_mode ) ;
    }
    public void run(){
       if( Thread.currentThread() == _readerThread ){
          if( _mode.equals( "master" ) ){
              runMaster() ;
          }else{
              runClient() ;
          }          
       }
    
    }
    private boolean _finished = false ;
    private void runClient(){
       say( "Going into client" ) ;
       try{
          Thread.currentThread().sleep(2000) ;
       }catch( Exception ee ){
       }
       say( "Closing client thread" ) ;
       kill() ;
    }
    private void runMaster(){
       say( "Going into master loop" ) ;
       while( true ){
          new DC( "client*" , "" ) ;
          if( _finished )break ;
          try{
             Thread.currentThread().sleep(200) ;
          }catch( Exception ee ){
             say( "Master thread interrupted" ) ;
             break ;
          }
       }
       say( "Closing master thread" ) ;
    }
    public void cleanUp(){
       _readerThread.interrupt() ;
    }
}
