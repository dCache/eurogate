package eurogate.db.pvl ;

import  dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ; 

 
public class      PvrContainer 
       extends    CdbDirectoryContainer 
       implements CdbContainable , CdbElementable  {

   public PvrContainer( CdbLockable lockable ,
                        File file ,
                        boolean create ) throws CdbException {
      super( lockable ,
             eurogate.db.pvl.PvrElement.class ,
             eurogate.db.pvl.PvrHandle.class ,
             file ,
             create ) ;
   }

   
   public static void main( String [] args ) throws Exception {
   
       if( args.length < 1 ){
          System.out.println( "USAGE : ... <datbase>" ) ;
          System.exit(4);
       }
       PvrContainer container = 
            new PvrContainer( null , new File( args[0] ) , false ) ;
       
       PvrHandle pvr = (PvrHandle)container.createElement( "pvr1" ) ;
       
       DriveHandle drive = pvr.createDrive( "drive1" ) ;
       
       drive.open( CdbLockable.WRITE ) ;
       drive.setCartridge( "U0000" ) ;
       drive.close( CdbLockable.COMMIT ) ;
       
       pvr.removeDrive( "drive1" ) ;
       
   }
}
 
