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
 public class EasyStackerExec extends EasyStackerAdapter  {
    public EasyStackerExec( String ourName , CellAdapter cellAdapter ){
        super( ourName , cellAdapter );
    }
    public void mount( String driveName , String driveLocation , 
                       String cartridgeName , String cartridgeLocation )
                       throws InterruptedException {
         say("Mount  called in EasyStackerExec "+driveName+"["+driveLocation+"] "+
                    cartridgeName+"["+cartridgeLocation+"]" ) ;
         Thread.sleep(5000L); 
         say("Mount finished in EasyStackerExec");
    }
    public void dismount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation )

                       throws InterruptedException {
         say("Mount  called in EasyStackerExec "+driveName+"["+driveLocation+"] "+
                    cartridgeName+"["+cartridgeLocation+"]" ) ;
         Thread.sleep(5000L); 
         say("Dismount finished in EasyStackerExec");
    }   
 }
