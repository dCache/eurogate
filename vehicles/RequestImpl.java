package eurogate.vehicles ;

import eurogate.misc.* ;
import java.io.* ;

public class      RequestImpl 
       extends    EurogateRequestImpl
       implements MoverRequest, PvlRequest, PvrRequest , BitfileRequest {
       
    private String _hostName     = null ;
    private int    _hostPort     = 0 ; 
    private String _store        = null ;
    private String _storageGroup = null ;
    private String _volume      = null ;
    private String _cartridge   = null ;
    private String _volumeSet   = null ;
    private long   _fileSize    = 0 ;
    private String _clientReqId = null ;
    private long   _serverReqId = 0 ;
    private String _bfid        = null ;
    private String _parameter   = "" ;
    private String _volumePosition = null ;
    private String _eorPosition    = null ;
    private String _volumeId       = null ; // volume position on cartridge
    private String _driveName         = null ;
    private String _specificDriveName = null ;
    private String _pvrName           = null ;
    private long   _residualBytes     = -1 ;
    private long   _realBytes         = -1 ;
    
   public String toString(){
       StringBuffer sb = new StringBuffer() ;
       sb.append(super.toString()) ;
       if( _hostName != null )sb.append(";hn=").append(_hostName) ;
       if( _hostPort != 0 )sb.append(";hp="+_hostPort) ;
       if( _store != null )sb.append(";s="+_store) ;
       if( _storageGroup != null )sb.append(";sg="+_storageGroup) ;
       if( _volume != null )sb.append(";v="+_volume) ;
       if( _cartridge != null )sb.append(";c="+_cartridge) ;
       if( _volumeSet != null )sb.append(";vs="+_volumeSet) ;
       if( _fileSize != 0 )sb.append(";fs="+_fileSize) ;
       if( _realBytes >= 0 )sb.append(";rs="+_realBytes) ;
       if( _residualBytes >= 0 )sb.append(";rss="+_residualBytes) ;
       if( _clientReqId != null )sb.append(";cid="+_clientReqId) ;
       if( _serverReqId != 0 )sb.append(";sid="+_serverReqId) ;
       if( _bfid != null )sb.append(";bfid="+_bfid) ;
       if( _volumePosition != null )sb.append(";vp="+_volumePosition) ;
       if( _eorPosition != null )sb.append(";eor="+_eorPosition) ;
       if( _volumeId != null )sb.append(";vid="+_volumeId) ;
       if( _driveName != null )sb.append(";dr="+_driveName) ;
       if( _specificDriveName != null )sb.append(";sdr="+_specificDriveName) ;
       if( _pvrName != null )sb.append(";pvr="+_pvrName) ;
       sb.append(";") ;
       return sb.toString() ;    
    }
    public RequestImpl( String type , String action ){
       super( type , action ) ;
    }
    public RequestImpl(    String type ,
                           String hostName ,
                           int    hostPort ,
                           String store ,
                           String storageGroup ,
                           String migrationPath  ,
                           long   fileSize ,
                           String clientReqId  ){
        super( type , "i/o" ) ;                  
        _store         = store ;
        _hostName      = hostName ;
        _hostPort      = hostPort ;
        _fileSize      = fileSize ;
        _clientReqId   = clientReqId ;
        
       setStorageGroup( storageGroup ) ;

        
    }
    public RequestImpl(    String type ,
                           String hostName ,
                           int    hostPort ,
                           String store ,
                           String bfid ,
                           String clientReqId  ){
                           
        super( type , "i/o" ) ;                  
        _store         = store ;
        _hostName      = hostName ;
        _hostPort      = hostPort ;
        _clientReqId   = clientReqId ;
        _bfid          = bfid ;
        
    }
    public RequestImpl(    String type ,
                           String store ,
                           String bfid      ){
                           
        super( type , "i/o" ) ;                  
        _store         = store ;
        _bfid          = bfid ;
        
    }
    public RequestImpl(    String type ,
                           String store ,
                           String bfid ,
                           String clientReqId     ){
                           
        super( type , "i/o" ) ;                  
        _store         = store ;
        _bfid          = bfid ;
        _clientReqId   = clientReqId ;
        
    }
    public String getHostName(){ return _hostName ; }
    public int    getHostPort(){ return _hostPort ; }
    public long   getFileSize(){ return _fileSize ; }
    public long   getServerReqId(){ return _serverReqId ; }
    public String getClientReqId(){ return _clientReqId ; }
    public String getBfid(){ return _bfid ; }                  
    public String getStorageGroup(){ return _storageGroup  ; }
    public String getStore(){ return _store ; }
    public String getParameter(){ return _parameter ; }
    public String getVolume(){ return _volume ; }
    public String getVolumeSet(){ return _volumeSet ; }
    public String getCartridge(){ return _cartridge ; }
    //
    // setter
    //
    public void   setParameter( String parameter ){
       _parameter = parameter ;
    }
    public void   setFileSize( long size ){
       _fileSize = size ;
    }
    public void   setStorageGroup( String group ){
       _storageGroup = group ; 
       _volumeSet    = group ;
    }
    public void   setBfid( String bfid ){
       _bfid = bfid ;
    }
    public void   setServerReqId( long serverReqId ){
       _serverReqId = serverReqId ;
    }
    public void   setClientReqId( String clientReqId ){
       _clientReqId = clientReqId ;
    }
    public void   setVolume( String volumeName ) {
       _volume = volumeName ;
    }
    public void   setCartridge( String cartridgeName ) {
       _cartridge = cartridgeName ;
    }
    public void   setStore( String storeName ) {
       _store = storeName ;
    }
    public void setResidualBytes( long size ){
       _residualBytes = size ;
    }
    public void setRealBytes( long size ){
       _realBytes = size ;
    }
    public long getResidualBytes(){ return _residualBytes ; }
    public long getRealBytes(){ return _realBytes ; }
    public void setPosition( String volumePosition , String eorPosition ){
       _volumePosition = volumePosition ;
       _eorPosition    = eorPosition ;
    }
    public void setFilePosition( String volumePosition ){
       _volumePosition = volumePosition ;
    }
    public String getEorPosition(){ return _eorPosition ; }
    public String getPosition(){ return _volumePosition ; }
    public String getFilePosition(){ return _volumePosition ; }
    public String getVolumeId(){ return _volumeId ; }
    public void   setVolumeId( String volId ){ _volumeId = volId ; }
    public String getGenericDrive(){ return _driveName ; }
    public String getDrive(){ return _driveName ; }
    public String getSpecificDrive(){ return _specificDriveName ; }
    public void   setDrive( String generic ){
       _driveName         = generic ;
    }
    public void   setDrive( String generic , String specific ){
       _driveName         = generic ;
       _specificDriveName = specific ;
    }
    public void setPvr( String pvr ){ _pvrName = pvr ; }
    public String getPvr(){ return _pvrName ; }
}

 
