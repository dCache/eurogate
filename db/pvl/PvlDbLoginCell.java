package  eurogate.db.pvl ;

import   dmg.util.cdb.* ;
import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.protocols.ssh.* ;

import java.util.* ;
import java.io.* ;
import java.net.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      PvlDbLoginCell 
       extends    CellAdapter
       implements Runnable  {

  private StreamEngine   _engine ;
  private BufferedReader _in ;
  private PrintWriter    _out ;
  private InetAddress    _host ;
  private String         _user ;
  private Thread         _workerThread ;
  private CellShell      _shell ; 
  private String         _destination = null ;
  private boolean        _syncMode    = true ;
  private Gate           _readyGate   = new Gate(false) ;
  private int            _syncTimeout = 10 ;
  private int            _commandCounter = 0 ;
  private String         _lastCommand    = "<init>" ;
  private Reader         _reader = null ;
  private Hashtable      _defaultHash = new Hashtable() ;
  private String         _container = "/home/patrick/eurogate/db/pvl/database" ;
  private PvlDb          _pvlDb     = null ;
  
  public PvlDbLoginCell( String name , StreamEngine engine ){
     super( name ) ;
     _engine  = engine ;
     
     _reader = engine.getReader() ;
     _in   = new BufferedReader( _reader ) ;
     _out  = new PrintWriter( engine.getWriter() ) ;
     _user = engine.getUserName() ;
     _host = engine.getInetAddress() ;
      
     _destination  = getCellName() ;
     _workerThread = new Thread( this ) ;         
     
     _workerThread.start() ;
     setPrintoutLevel( 11 ) ;
     useInterpreter(false) ;
     //
     // 
     // create the database if not yet done
     //
     Dictionary dict = getDomainContext() ;
     _pvlDb = (PvlDb) dict.get( "database" ) ;
     if( _pvlDb == null ){
        kill() ;
        throw new IllegalArgumentException( "database not defined" ) ;
     }
  }
  public void run(){
    if( Thread.currentThread() == _workerThread ){
        print( prompt() ) ;
        while( true ){
           try{
               if( ( _lastCommand = _in.readLine() ) == null )break ;
               _commandCounter++ ;
               if( execute( _lastCommand ) > 0 ){
                  //
                  // we need to close the socket AND
                  // have to go back to readLine to
                  // finish the ssh protocol gracefully.
                  //
                  try{ _out.close() ; }catch(Exception ee){} 
               }else{
                  print( prompt() ) ;
               }       
           }catch( IOException e ){
              esay("EOF Exception in read line : "+e ) ;
              break ;
           }catch( Exception e ){
              esay("I/O Error in read line : "+e ) ;
              break ;
           }
        
        }
        say( "EOS encountered" ) ;
        _readyGate.open() ;
        kill() ;
    
    }
  }
  //
  //   the database
  //
  public String hh_create_database = "[<path>]" ;
  public String ac_create_database_$_0_1( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = new PvlDb( new File( _container ) , true ) ;     
     return "Done (db="+_container+")" ;
  }
  public String hh_open_database = "[<path>]" ;
  public String ac_open_database_$_0_1( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = new PvlDb( new File( _container ) , false ) ;     
     return "Done (db="+_container+")" ;
  }
  public String hh_release_database = "" ;
  public String ac_release_database( Args args )throws Exception {
     if( args.argc() > 0 )_container = args.argv(0) ;
     _pvlDb = null ;     
     return "Done" ;
  }
  //
  //  the pvr
  //
  public String hh_create_pvr = "<pvrName>" ;
  public String ac_create_pvr_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
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
  public String hh_ls_drive = "-pvr=<pvr>" ;
  public String ac_ls_drive_$_0_1( Args args ) throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
     Hashtable hash = fillHash( _defaultHash , args ) ;
     String pvrName = (String)hash.get( "pvr" ) ;
     boolean   full = hash.get( "l" ) != null ;
        
     StringBuffer sb          = new StringBuffer() ;
     String [] pvrNameList    = null ;
     
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
        String status = null , cartridge = null , owner = null ;
        for( int i = 0 ; i < driveNames.length ; i++ ){
           drive = pvr.getDriveByName( driveNames[i] ) ;
           drive.open( CdbLockable.READ ) ;
           status    = drive.getStatus() ;
           cartridge = drive.getCartridge() ;
           owner     = drive.getOwner() ;
           drive.close( CdbLockable.COMMIT ) ;
           sb.append( Formats.field( driveNames[i]  , 12 ) ).
              append( Formats.field( status         , 12 ) ).
              append( Formats.field( cartridge      , 12 ) ).
              append( Formats.field( pvrNameList[j] , 8  ) ).
              append( Formats.field( owner          , 8  ) ).
              append("\n") ;
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
     
     String info = drive.toLine() ;
     drive.close(CdbLockable.COMMIT) ;
     return "Drive Modified : "+args.argv(0)+" "+info  ;
       
  }
  //
  //  the cartridge descriptor
  //
  public String hh_create_cartridgeDescriptor = 
                "<cartridgeDescriptorName> -type=<cartridgeType>" ;
  public String ac_create_cartridgeDescriptor_$_1( Args args )throws Exception {
     if( _pvlDb == null )throw new IllegalArgumentException( "Database not open" ) ;
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

     VolumeSetHandle volumeSet = _pvlDb.getVolumeSetByName( vsName ) ;
     VolumeHandle    volume    = _pvlDb.getVolumeByName( args.argv(0) ) ;
     
     volumeSet.addVolume( volume ) ;
     
     return "Volume add to "+vsName+" : "+args.argv(0) ;
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
  //
  //    this and that
  //
   public void   cleanUp(){
   
     say( "Clean up called" ) ;
     println("");
     try{ _out.close() ; }catch(Exception ee){} 
     _readyGate.check() ;
     say( "finished" ) ;

   }
  public void println( String str ){ 
     _out.print( str ) ;
     if( ( str.length() > 0 ) &&
         ( str.charAt(str.length()-1) != '\n' ) )_out.print("\n") ;
     _out.flush() ;
  }
  public void print( String str ){
     _out.print( str ) ;
     _out.flush() ;
  }
   public String prompt(){ 
      return _destination == null ? " .. > " : (_destination+" > ")  ; 
   }
   public int execute( String command ) throws Exception {
      if( command.equals("") )return 0 ;
      
         try{
             println( command( command ) ) ;
             return 0 ;
         }catch( CommandExitException cee ){
             return 1 ;
         }
   
   }
   private void printObject( Object obj ){
      if( obj == null ){
         println( "Received 'null' Object" ) ;
         return ;
      }  
      String output = null ;    
      if( obj instanceof Object [] ){
         Object [] ar = (Object []) obj ;
         for( int i = 0 ; i < ar.length ; i++ ){
            if( ar[i] == null )continue ;
             
            print( output = ar[i].toString() ) ;
            if(  ( output.length() > 0 ) &&
                 ( output.charAt(output.length()-1) != '\n' ) 

               )print("\n") ;
         }
      }else{
         print( output =  obj.toString() ) ;
         if( ( output.length() > 0 ) &&
             ( output.charAt(output.length()-1) != '\n' ) )print("\n") ;
      }
   
   }
  //
  // the cell implemetation 
  //
   public String toString(){ return _user+"@"+_host ; }
   public void getInfo( PrintWriter pw ){
     pw.println( "            Stream LoginCell" ) ;
     pw.println( "         User  : "+_user ) ;
     pw.println( "         Host  : "+_host ) ;
     pw.println( " Last Command  : "+_lastCommand ) ;
     pw.println( " Command Count : "+_commandCounter ) ;
   }
   public void   messageArrived( CellMessage msg ){
   
        Object obj = msg.getMessageObject() ;
        println("");
        println( " CellMessage From   : "+msg.getSourceAddress() ) ; 
        println( " CellMessage To     : "+msg.getDestinationAddress() ) ; 
        println( " CellMessage Object : "+obj.getClass().getName() ) ;
        printObject( obj ) ;
     
   }
   ///////////////////////////////////////////////////////////////////////////
   //                                                                       //
   // the interpreter stuff                                                 //
   //                                                                       //
   public String ac_set_timeout_$_1( Args args ) throws Exception {
      _syncTimeout = new Integer( args.argv(0) ).intValue() ;
      return "" ;
   }
   public String ac_exit( Args args ) throws CommandExitException {
      throw new CommandExitException( "" , 0 ) ;
   }
      


}
