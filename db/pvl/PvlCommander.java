package  eurogate.db.pvl ;

import   dmg.util.cdb.* ;
import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.protocols.ssh.* ;

import java.util.* ;
import java.io.* ;
import java.net.* ;
import java.text.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class   PvlCommander  {

  private PvlDb               _pvlDb       = null ;
  private Hashtable           _defaultHash = new Hashtable() ;
  private PermissionCheckable _permission  = null ;
  
  public PvlCommander( PvlDb  pvlDb ){
    
       _pvlDb = pvlDb ;
  }
  public void setPermissionCheckable( PermissionCheckable permission ){
      _permission = permission ;
  }
  private void checkPermission( Args args , String acl ) throws AclException{
     if( ( _permission == null ) || ! ( args instanceof Authorizable ) )return ;
     
     _permission.checkPermission( (Authorizable)args , acl ) ;
                                
  }
  //
  //  the pvr
  //
  public String hh_create_pvr = "<pvrName>" ;
  public String ac_create_pvr_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     checkPermission( args , "pvr.*.create" ) ;
     _pvlDb.createPvr( args.argv(0) ) ;
     return "Pvr created : "+args.argv(0) ;
  }
  public String hh_ls_pvr = "[pvrName]" ;
  public String ac_ls_pvr_$_0_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     StringBuffer sb = new StringBuffer() ;
     
     String [] pvrs = _pvlDb.getPvrNames() ;
     for( int i = 0 ; i < pvrs.length ; i++ )
        sb.append( pvrs[i] ).append( "\n" ) ;
        
     return sb.toString() ; 

  }
  //
  //  the drive
  //
  public String hh_create_drive =
         "<driveName> -pvr=<pvr> -dev=<deviceName>"+
         " -spec=<specificName> [-sel=<driveSelection>]" ;
  public String ac_create_drive_$_1(Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )
        throw new IllegalArgumentException( "-pvr=<pvr> not specified" ) ;
     String devName = (String)hash.get( "dev" ) ;
     if( devName == null )
        throw new IllegalArgumentException( "-dev=<deviceName> not specified" ) ;
     String specName = (String)hash.get( "spec" ) ;
     if( specName == null )
        throw new IllegalArgumentException( "-spec=<specName> not specified" ) ;
     String selection = (String)hash.get( "sel" ) ;
     if( selection == null )selection="" ;

     checkPermission( args , "pvr."+pvrName+".modify" ) ;
     
     PvrHandle pvr = _pvlDb.getPvrByName( pvrName ) ;
     
     DriveHandle drive = pvr.createDrive( args.argv(0) ) ;
     drive.open(CdbLockable.WRITE) ;
     drive.setAction("none") ;
     drive.setStatus("disabled") ;
     drive.setCartridge("empty") ;
     drive.setOwner("-") ;
     drive.setSelectionString( selection ) ;
     drive.setSpecificName( specName ) ;
     drive.setDeviceName( devName ) ;
     String info = drive.toLine() ;
     drive.close(CdbLockable.COMMIT) ;
     return "Drive Created : "+args.argv(0)+" "+info  ;
  }
  public String hh_ls_db_drive = "-pvr=<pvr> -s [<driveName>]" ;
  public String ac_ls_db_drive_$_0_1( Args args ) throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     boolean   full = hash.get( "l" ) != null ;
     boolean   sel  = hash.get( "s" ) != null ;
     StringBuffer sb          = new StringBuffer() ;
     String [] pvrNameList    = null ;
     
     if( args.argc() > 0 ){
        
        if( pvrName == null )
          throw new 
          CommandException( "-pvr=<pvr> must be given for <drive>") ;
        String    driveName = args.argv(0) ;
        PvrHandle pvr       = _pvlDb.getPvrByName( pvrName ) ;
        if( pvr == null )
          throw new
          IllegalArgumentException( "pvr not found : "+pvrName) ;        
        DriveHandle drive = pvr.getDriveByName( driveName ) ;
        if( drive == null )
          throw new
          IllegalArgumentException( "drive not found : "+driveName) ;        
        String status = null , cartridge = null , owner = null ,
               selection = null , specific = null , device = null ,
               action    = null ;
        int    idle   = 0 , minimalBlock = 0 , maximalBlock = 0 ,
               bestBlock = 0 ;
        long   time   = 0 ;
        drive.open( CdbLockable.READ ) ;
           status    = drive.getStatus() ;
           cartridge = drive.getCartridge() ;
           owner     = drive.getOwner() ;
           selection = drive.getSelectionString() ;
           idle      = drive.getIdleTime() ;
           specific  = drive.getSpecificName() ;
           device    = drive.getDeviceName() ;
           minimalBlock = drive.getMinimalBlockSize() ;
           maximalBlock = drive.getMaximalBlockSize() ;
           bestBlock    = drive.getBestBlockSize() ;
           action       = drive.getAction() ;
           time         = drive.getTime() ;
        drive.close( CdbLockable.COMMIT ) ;
        DateFormat   df    = new SimpleDateFormat("hh.mm.ss" ) ;
        sb.append("Invariants\n") ;
        sb.append("     Drive Name : ").append(driveName).append("\n") ;
        sb.append("     Robot View : ").append(specific).append("\n") ;
        sb.append("    Device Name : ").append(device).append("\n") ;
        sb.append("      Idle Time : ").append(idle).append("\n") ;
        sb.append("      Selection : ").append(selection).append("\n") ;
        sb.append("       maxBlock : ").append(maximalBlock).append("\n") ;
        sb.append("       minBlock : ").append(minimalBlock).append("\n") ;
        sb.append("      bestBlock : ").append(bestBlock).append("\n") ;
        sb.append("Variants\n");
        sb.append("         Status : ").append(status).append("\n") ;
        sb.append("         Action : ").append(action).append("\n") ;
        sb.append("      Cartridge : ").append(cartridge).append("\n") ;
        sb.append("          Owner : ").append(owner).append("\n") ;
        sb.append("    Last Access : ").
           append(df.format(new Date(time))).
           append("\n") ;
        return sb.toString() ;
     }   
    
    
     if( pvrName == null ){
        pvrNameList = _pvlDb.getPvrNames() ;
     }else{
        pvrNameList = new String[1] ;
        pvrNameList[0] = pvrName ;
     }
     for( int j = 0 ; j < pvrNameList.length ; j++ ){
        pvrName = pvrNameList[j] ;
        PvrHandle    pvr         = _pvlDb.getPvrByName( pvrName ) ;
        String []    driveNames  = pvr.getDriveNames() ;
        DriveHandle  drive       = null ;
        String status = null , cartridge = null , owner = null , selection = null ;
        for( int i = 0 ; i < driveNames.length ; i++ ){
           drive = pvr.getDriveByName( driveNames[i] ) ;
           drive.open( CdbLockable.READ ) ;
           status    = drive.getStatus() ;
           cartridge = drive.getCartridge() ;
           owner     = drive.getOwner() ;
           selection = drive.getSelectionString() ;
           drive.close( CdbLockable.COMMIT ) ;
           if( sel ){
              sb.append( Formats.field( driveNames[i]  , 12 ) ).
                 append( Formats.field( pvrNameList[j] , 8  ) ).
                 append( selection == null ? "none" : selection ).
                 append("\n") ;
           }else{
              sb.append( Formats.field( driveNames[i]  , 12 ) ).
                 append( Formats.field( status         , 12 ) ).
                 append( Formats.field( cartridge      , 12 ) ).
                 append( Formats.field( pvrNameList[j] , 8  ) ).
                 append( Formats.field( owner          , 8  ) ).
                 append("\n") ;
           }
        }
     }
     return sb.toString() ;
  }
  public String hh_set_drive = "<driveName> -pvr=<pvr> <all Options>" ;
  public String fh_set_drive = "mod drive <driveName> -pvr=<pvrName> <options>\n"+
                "   Options : \n"+
                "     -status=<newStatus>\n"+
                "     -action=<newAction>\n"+
                "     -minBlock=<minimalBlockSize>\n"+
                "     -maxBlock=<maximalBlockSize>\n"+
                "     -bestBlock=<bestBlockSize>\n"+
                "     -dev=<deviceName>\n"+
                "     -cart=<cartridgeName>\n"+
                "     -owner=<owner>\n"+
                "     -spec=<robotSecificDriveName>]\n"+
                "     -idle=<idleTime/sec>]\n"+
                "     -sel=<selectionString>\n" ;
  public String ac_set_drive_$_1( Args args ) throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )
        throw new IllegalArgumentException( "-pvr=<pvr> not specified" ) ;
        
     String      driveName = args.argv(0) ;
     PvrHandle   pvr       = _pvlDb.getPvrByName( pvrName ) ;
     DriveHandle drive     = pvr.getDriveByName( driveName ) ;

     checkPermission( args , "pvr."+pvrName+".modify" ) ;
     
     String value = null ;
     drive.open(CdbLockable.WRITE) ;
     if( ( value = (String)hash.get( "status" ) ) != null ){
        drive.setStatus( value ) ;
     }
     if( ( value = (String)hash.get( "action" ) ) != null ){
        drive.setAction( value ) ;
     }
     if( ( value = (String)hash.get( "sel" ) ) != null ){
        drive.setSelectionString( value ) ;
     }
     if( ( value = (String)hash.get( "spec" ) ) != null ){
        drive.setSpecificName( value ) ;
     }
     if( ( value = (String)hash.get( "dev" ) ) != null ){
        drive.setDeviceName( value ) ;
     }
     if( ( value = (String)hash.get( "owner" ) ) != null ){
        drive.setOwner( value ) ;
     }
     if( ( value = (String)hash.get( "cart" ) ) != null ){
        drive.setCartridge( value ) ;
     }
     if( ( value = (String)hash.get( "minBlock" ) ) != null ){
        int size = 0 ;
        try{
           size = Integer.parseInt( value ) ;
        }catch(Exception e){}
        drive.setMinimalBlockSize( size ) ;
     }
     if( ( value = (String)hash.get( "maxBlock" ) ) != null ){
        int size = 0 ;
        try{
           size = Integer.parseInt( value ) ;
        }catch(Exception e){}
        drive.setMaximalBlockSize( size ) ;
     }
     if( ( value = (String)hash.get( "bestBlock" ) ) != null ){
        int size = 0 ;
        try{
           size = Integer.parseInt( value ) ;
        }catch(Exception e){}
        drive.setBestBlockSize( size ) ;
     }
     if( ( value = (String)hash.get( "idle" ) ) != null ){
        int size = 0 ;
        try{
           size = Integer.parseInt( value ) ;
        }catch(Exception e){}
        drive.setIdleTime( size ) ;
     }
     drive.close(CdbLockable.COMMIT) ;
     return "" ;
       
  }
  //
  //  the cartridge descriptor
  //
  public String hh_create_cartridgeDescriptor = 
                "<cartridgeDescriptorName> -type=<cartridgeType>" ;
  public String ac_create_cartridgeDescriptor_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     checkPermission( args , "pvl.*.modify" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String type = (String)hash.get( "type" ) ;
     if( type == null )
        throw new IllegalArgumentException( "-type=<cartridgeType> not specified" ) ;
        
     CartridgeDescriptorHandle cdh = _pvlDb.createCartridgeDescriptor( args.argv(0) ) ;
     cdh.open( CdbLockable.WRITE ) ;
     cdh.setType( type ) ;
     cdh.close( CdbLockable.COMMIT ) ;
     return "CartridgeDescriptor created : "+args.argv(0) ;
  }
  public String hh_ls_cd = "" ;
  public String ac_ls_cd_$_0( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     boolean   full = hash.get( "l" ) != null ;
     String [] cds  = _pvlDb.getCartridgeDescriptorNames() ;
     StringBuffer sb = new StringBuffer() ;
     CartridgeDescriptorHandle cartridge = null ;
     for( int i = 0 ; i < cds.length ; i++ ){
        sb.append( cds[i] ) ;
        if( full ){
           cartridge = _pvlDb.getCartridgeDescriptorByName( cds[i] ) ;
           cartridge.open( CdbLockable.READ) ;
           sb.append(" ").append( cartridge.toLine() ) ;
           cartridge.close( CdbLockable.COMMIT ) ;
        }
        sb.append( "\n") ;
     }
     return sb.toString() ;
  }
  //
  // the volumeDescriptor
  //
  public String hh_create_volumeDescriptor = "<volumeDescriptorName> -size=<size>" ;
  public String ac_create_volumeDescriptor_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     checkPermission( args , "pvl.*.modify" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String sizeStr = (String)hash.get( "size" ) ;
     if( sizeStr == null )
        throw new IllegalArgumentException( "-size=<volumeSize> not specified" ) ;
     long size = Long.parseLong( sizeStr ) ;
     VolumeDescriptorHandle vdh = _pvlDb.createVolumeDescriptor( args.argv(0) ) ;
     vdh.open( CdbLockable.WRITE ) ;
     vdh.setSize( size ) ;
     vdh.close( CdbLockable.COMMIT ) ;
     return "VolumeDescriptor created : "+args.argv(0) ;
  }
  public String hh_ls_vd = "[<volumeDescriptorName>]" ;
  public String ac_ls_vd_$_0_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     boolean   full = hash.get( "l" ) != null ;
     String [] vds = _pvlDb.getVolumeDescriptorNames() ;
     StringBuffer sb = new StringBuffer() ;
     VolumeDescriptorHandle volume = null ;
     for( int i = 0 ; i < vds.length ; i++ ){
        sb.append( vds[i] ) ;
        if( full ){
           volume = _pvlDb.getVolumeDescriptorByName( vds[i] ) ;
           volume.open( CdbLockable.READ) ;
           sb.append(" ").append( volume.toLine() ) ;
           volume.close( CdbLockable.COMMIT ) ;
        }
        sb.append( "\n") ;
     }
     return sb.toString() ;
  }
  //
  // the volume
  //
  public String fh_create_volume =
           "create volume <volumeName>  <options ...>\n"+
           "    -cart=<cartridgeName>  # create volume on this cartridge\n"+
           "    -pvr=<pvrName>         # where to find the cartridge\n"+
           "   [-position=<position>]  # the position of this volume\n"+
           "                           # on the specified tape (def='0')\n" ;       
  public String hh_create_volume = 
      "<volumeName> -pvr=<pvr> -cart=<cartridge> -vd=<volDesc> [-pos=<position>]" ;
  public String ac_create_volume_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )
        throw new IllegalArgumentException( "-pvr=<pvr> not specified" ) ;
     String volDesc = (String)hash.get( "vd" ) ;
     if( volDesc == null )
        throw new 
        IllegalArgumentException( "-vd=<volumeDescriptor> not specified" ) ;
     String cartridge = (String)hash.get( "cart" ) ;
     if( cartridge == null )
        throw new 
        IllegalArgumentException( "-cart=<cartridge> not specified" ) ;
     String position = (String)hash.get( "position" ) ;
       
     checkPermission( args , "pvr."+pvrName+".modify" ) ;
        
     VolumeHandle volume = 
         _pvlDb.createVolume( pvrName , 
                              cartridge , 
                              args.argv(0) , 
                              volDesc ) ;
     if( position != null ){
        volume.open( CdbLockable.WRITE ) ;
        volume.setPosition( position ) ;
        volume.close( CdbLockable.COMMIT ) ;     
     }
     return "Volume created : "+args.argv(0) ;
  }
  public String hh_ls_volumeSet = "" ;
  public String ac_ls_volumeSet_$_0_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     String [] volsets = _pvlDb.getVolumeSetNames() ;
     StringBuffer sb = new StringBuffer() ;
     for( int i = 0 ; i < volsets.length ; i++ )
        sb.append( volsets[i] ).append( "\n" ) ;
     return sb.toString() ;
  }
  public String hh_ls_volume = "[<volumeName>] [-vs=<volumeSet> -pvr=<pvrName>]" ;
  public String ac_ls_volume_$_0_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash  = fillHash( _defaultHash , args ) ;
     boolean full    = hash.get( "l" ) != null ;
     StringBuffer sb       = new StringBuffer() ;
     String []    volNames = null ;
     VolumeHandle volume   = null ;
     //
     // volume name was specified 
     //
     if( args.argc() > 0 ){
         volume = _pvlDb.getVolumeByName( args.argv(0) ) ;
         volume.open( CdbLockable.READ) ;
         String info = volume.toString() ;
         volume.close( CdbLockable.COMMIT ) ;
         return info ;
     }
     if( hash.get( "all" ) != null ){
        volNames = _pvlDb.getVolumeNames() ;
        String info = null ;
        for( int i = 0 ; i < volNames.length ; i++ ){
           sb.append(volNames[i]) ;
           if( full ){
              volume = _pvlDb.getVolumeByName( volNames[i] ) ;
              volume.open( CdbLockable.READ) ;
              info = volume.toLine() ;
              volume.close( CdbLockable.COMMIT ) ;
              sb.append(" ").append(info);
           }
           sb.append( "\n") ;
        }
        return sb.toString() ;
     }
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )pvrName="*" ;
     String vsName = (String)hash.get( "vs" ) ;
     if( vsName == null )vsName="*" ;
     VolumeSetHandle       volSetHandle = null ;
     PvrVolumeSubsetHandle vss          = null ;
     String [] subsetNames = null ;
     String [] volsetNames = null ;
     
     if( vsName.equals( "*" ) ){
     
        volsetNames = _pvlDb.getVolumeSetNames() ;
        
        for( int i = 0 ; i < volsetNames.length ; i++ ){
           sb.append( "Volume Set : " ).append( volsetNames[i] ).append("\n") ;
           if( pvrName.equals("*") ){
           
               volSetHandle = _pvlDb.getVolumeSetByName(volsetNames[i]);
               volSetHandle.open( CdbLockable.READ ) ;
               subsetNames = volSetHandle.getPvrVolumeSubsetNames() ;
               
               for( int j = 0 ; j < subsetNames.length ; j++ ){
                   sb.append( "   Pvr Volume Subset : " ).
                      append( subsetNames[j] ).append("\n") ;
                   vss = volSetHandle.getPvrVolumeSubsetByName( subsetNames[j] ) ;
                   vss.open( CdbLockable.READ ) ;
                   volNames = vss.getVolumeNames() ;
                   vss.close( CdbLockable.COMMIT ) ;
                   
                   for( int l = 0 ; l < volNames.length ; l++ )
                      sb.append("     ").append( volNames[l] ).append( "\n" ) ;
                      
               }
               
               volSetHandle.close( CdbLockable.COMMIT ) ;
               
           }else{


           }
        }
     }else{
     
     }
     return  sb.toString() ;
  }   
  public String hh_create_cartridge = "<cartridgeName> -pvr=<pvr> -cd=<cartDesc>" ;
  public String ac_create_cartridge_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )
        throw new IllegalArgumentException( "-pvr=<pvr> not specified" ) ;
     String cartDesc = (String)hash.get( "cd" ) ;
     if( cartDesc == null )
        throw new 
        IllegalArgumentException( "-cd=<cartridgeDescriptor> not specified" ) ;
        
     checkPermission( args , "pvr."+pvrName+".modify" ) ;
     String cart = args.argv(0) ;
     
     ItemRange range = new ItemRange( cart ) ;
     int fromCount   = range.getFromCount() ;
     int toCount     = range.getToCount() ;
     
     for( int seq =  fromCount ; seq <= toCount ; seq++ ){
     
         _pvlDb.createCartridge( pvrName ,
                                 range.buildName( seq ) , 
                                 cartDesc   ) ;
                                 
     }
     return ""+(toCount-fromCount+1)+" cartridges created "+cart ;
  }
  private class ItemRange  {
     private String _baseName  = null ;
     private int    _digits    = 0 ;
     private int    _fromCount = 0 ;
     private int    _toCount   = 0 ;
     
     public ItemRange( String item ){
        int pos = 0 ;
        if( ( pos = item.indexOf('-') ) < 0 ){
           _digits = countDigits( item ) ;
           if( ( _digits == item.length() ) || ( _digits==0 ) )
              throw new 
              IllegalArgumentException( 
              "No constant name base or no counter" ) ;
           int baseLength = item.length()-_digits ;
           _baseName  = item.substring(0,baseLength) ;
           _fromCount = _toCount = Integer.parseInt( item.substring(baseLength) ) ;
        }else if( ( pos == 0 ) || ( pos == ( item.length()-1 ) ) ){
           throw new 
           IllegalArgumentException( "Syntax Error in range" ) ;

        }else{
        
           _ItemRange( item.substring(0,pos) , item.substring(pos+1) ) ;
           
        }
     }
     public void ItemRange( String from , String to ){
        _ItemRange( from , to ) ;
     }
     public void _ItemRange( String from , String to ){
     
        _digits    = countDigits( from ) ;
        int nameBase  = from.length() - _digits ;
        if( ( from.length() != to.length()        ) ||  
            ( _digits       != countDigits( to )  ) ||
            ( nameBase == 0                       ) ||
            ( ! from.substring(0,nameBase).equals(to.substring(0,nameBase) ) )  )
           throw new 
           IllegalArgumentException( "'<from>'-'<to>' don't match" ) ;
        try{
           _fromCount = Integer.parseInt( from.substring(nameBase) ) ;
           _toCount   = Integer.parseInt( to.substring( nameBase) ) ;
        }catch( Exception eee ){
           throw new 
           IllegalArgumentException( "Problem with number format" ) ;
        }
        if( _fromCount > _toCount )
           throw new 
           IllegalArgumentException( "'<from>'-'<to>' not in order" ) ;
        _baseName = from.substring(0,nameBase) ;
     }
     public String getBaseName(){ return _baseName ; }
     public int    getFromCount(){ return _fromCount ; }
     public int    getToCount(){ return _toCount; }
     private String  buildName( int seq ){ 
        return _buildName( _baseName , _digits , seq ) ;
     }
     private String _buildName( String base , int digitCount , int seq ){
        String seqString = ""+seq ;
        int fill = digitCount - seqString.length() ;
        fill = fill < 0 ? 0 : fill ;
        StringBuffer sb = new StringBuffer() ;
        sb.append( base ) ;
        for( int i = 0 ; i < fill ; i++ )sb.append('0') ;
        sb.append( seqString ) ;
        return sb.toString() ;
     }
     private int countDigits( String name ){
        if( name.length() < 1 )return 0 ;
        int i = 0 ;
        for( i = name.length()-1 ; 
             ( i >= 0 ) && Character.isDigit( name.charAt(i) ); i-- ) ;
        return name.length() - i - 1 ;
     }
  }
  public String hh_ls_cartridge = "-pvr=<pvr>" ;
  public String ac_ls_cartridge_$_0( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     boolean full   = hash.get( "l" ) != null ;
     String pvrName = (String)hash.get( "pvr" ) ;
     if( pvrName == null )
        throw new IllegalArgumentException( "-pvr=<pvr> not specified" ) ;
        
     PvrHandle pvr = _pvlDb.getPvrByName( pvrName ) ;
     
     String [] carts = pvr.getCartridgeNames() ;
     StringBuffer sb = new StringBuffer() ;
     CartridgeHandle cartridge = null ;
     for( int i = 0 ; i < carts.length ; i++ ){
        sb.append( carts[i] ) ;
        if( full ){
           cartridge = pvr.getCartridgeByName( carts[i] ) ;
           cartridge.open(CdbLockable.READ ) ;
           sb.append("  ").append(cartridge.toLine() ) ;
           cartridge.close(CdbLockable.COMMIT ) ;
        }
        sb.append("\n");
     }
     return sb.toString() ;
     
  }
  public String hh_create_volumeSet = "<volumeSetName>" ;
  public String ac_create_volumeSet_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     checkPermission( args , "pvl.*.modify" ) ;
     _pvlDb.createVolumeSet( args.argv(0) ) ;
     return "VolumeSet created : "+args.argv(0) ;
  }
  public String hh_add_volume = "<volumeName> -vs=<volumeSet>" ;
  public String ac_add_volume_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String vsName = (String)hash.get( "vs" ) ;
     if( vsName == null )
        throw new IllegalArgumentException( "-vs=<volumeSet> not specified" ) ;

     checkPermission( args , "pvl.*.modify" ) ;

     VolumeSetHandle volumeSet = _pvlDb.getVolumeSetByName( vsName ) ;
     VolumeHandle    volume    = _pvlDb.getVolumeByName( args.argv(0) ) ;
     
     volumeSet.addVolume( volume ) ;
     
     return "Volume add to "+vsName+" : "+args.argv(0) ;
  }
  public String hh_set_volume = "<volumeName> -status=<status>" ;
  public String ac_set_volume_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     
     VolumeHandle    volume    = _pvlDb.getVolumeByName( args.argv(0) ) ;
     
     String status = args.getOpt("status") ;
     if( status == null )return "Set volume to ?what?" ;
 
     checkPermission( args , "pvl.*.modify" ) ;
    
     volume.open(CdbLockable.WRITE ) ;   
        volume.setStatus( status ) ; 
     volume.close(CdbLockable.COMMIT) ;     
     return "Status of "+args.argv(0)+" set to "+status ;
  }
  //
  // utils 
  //
  private Hashtable fillHash( Args args ){
     return fillHash( null , args ) ;
  }
  private Hashtable fillHash( Hashtable def , Args args ){
     Hashtable hash = def == null ? new Hashtable() : (Hashtable)def.clone() ;
     for( int i = 0 ; i < args.optc() ; i++ ){
        String opt = args.optv(i) ;
        int pos = opt.indexOf( '=' ) ;
        if( pos < 0 ){
          hash.put( opt.substring(1) , "") ;
          continue ;
        }
        String key = opt.substring(1,pos) ;
        String value = opt.substring( pos+1 ) ;
        if( ( key.length() < 1 ) || ( value.length() < 1 ) )continue ;
        hash.put( key , value ) ;
     }
     return hash ;
  }
  //
  // default settings
  //
  public String hh_set_default = "<key> <value>" ;
  public String ac_set_default_$_2( Args args )throws Exception {
     _defaultHash.put( args.argv(0) , args.argv(1) ) ;
     return "Done" ;
  }
  public String hh_ls_default = "[<key>]" ;
  public String ac_ls_default_$_0_1( Args args )throws Exception {
     if( args.argc() > 0 ){
        String value = (String)_defaultHash.get( args.argv(0) ) ;
        if( value == null ){
           return args.argv(0)+" -> (null)" ;
        }else{
           return args.argv(0)+" -> "+value ;
        }
     }else{
        StringBuffer sb = new StringBuffer() ;
        Enumeration e = _defaultHash.keys() ;
        for( ; e.hasMoreElements() ; ){
           String key   = (String)e.nextElement() ;
           String value = (String)_defaultHash.get( key ) ;
           sb.append( key+" -> "+value ).append( "\n" ) ;
        }
        return sb.toString() ;
     }
  }

}
