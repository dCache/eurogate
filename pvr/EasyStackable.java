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
public interface EasyStackable {
    public void mount( String driveName , String driveLocation , 
                       String cartridgeName , String cartridgeLocation )
              throws EurogatePvrException , InterruptedException  ;
    public void dismount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation )
              throws EurogatePvrException  , InterruptedException ;
    public boolean hasCapability( String capability ) ;
    public int getNumberOfArms() ;
    public void getInfo( PrintWriter pw ) ;
}
