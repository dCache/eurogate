package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

public class VolumeSetElement extends CdbDirectoryContainer {


   public VolumeSetElement( CdbLockable lockable ,
                            File file ,
                            boolean create )
      throws CdbException , InterruptedException {
 
      super( lockable , 
             dmg.util.cdb.CdbFileRecord.class ,
             eurogate.db.pvl.PvrVolumeSubsetHandle.class ,
             file ,
             create ) ;
   }
   public String [] getPvrVolumeSubsetNames(){
      return getElementNames() ;
   }
} 
