package  eurogate.pvr ;

import   eurogate.db.pvr.* ;

import java.util.*;
import java.io.* ;

import dmg.cells.nucleus.*; 
import dmg.util.*;
import dmg.util.cdb.* ;

import eurogate.vehicles.* ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 20 June 2007
  * 
 */
 public class EasyStackerAdapter implements EasyStackable {
    private CellAdapter _adapter = null ;
    private String      _ourName = null ;
    public EasyStackerAdapter( String ourName , CellAdapter cellAdapter ){
        _ourName = ourName ;
        _adapter = cellAdapter ;
        say("Started : ["+this.getClass().getName()+",] "+_ourName ) ;
    }
    public EasyStackerAdapter( String ourName ){
        _ourName = ourName ;
        say("Started : ["+this.getClass().getName()+"] "+_ourName ) ;
    }
    public EasyStackerAdapter(){
        _ourName = this.getClass().getName() ;
        say("Started : ["+this.getClass().getName()+"] "+_ourName ) ;
    }
    public void say( String message ){
       if( _adapter != null )_adapter.say("["+_ourName+"] "+message) ;
    }
    public void esay( String message ){
       if( _adapter != null )_adapter.esay("["+_ourName+"] "+message) ;
    }
    public void esay( Throwable t ){
       if( _adapter != null )_adapter.esay(t) ;
    }
    public void mount( String driveName , String driveLocation , 
                       String cartridgeName , String cartridgeLocation )
           throws InterruptedException, EurogatePvrException            {
       say("mount "+driveName+"["+driveLocation+"] "+
                    cartridgeName+"["+cartridgeLocation+"]" ) ;
    }
    public void dismount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation )
           throws InterruptedException, EurogatePvrException            {
       say("dismount "+driveName+"["+driveLocation+"] "+
                       cartridgeName+"["+cartridgeLocation+"]" ) ;

    }   
    public boolean hasCapability( String capability ){
       say("hasCapability "+capability);
       return false ;
    } 
    public int getNumberOfArms(){ return 1 ; }
 }
