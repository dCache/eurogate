package  eurogate.vehicles ;

import   java.util.* ;

public class      UnloadDismountRequest
       extends    EurogateRequestImpl 
       implements MoverRequest, PvrRequest {

    private String _cartridge ;
    private String _pvr ;
    private String _drive ;
    private String _specific ;
    
    public UnloadDismountRequest( String pvr ,
                                  String cartridge,
                                  String drive ,
                                  String specific    ){
        super( "unload" ) ;
       _cartridge = cartridge ;
       _pvr       = pvr ;
       _drive     = drive ;
       _specific  = specific  ;
    }
    public String toString(){
       StringBuffer sb = new StringBuffer() ;
       sb.append(super.toString()) ;
       if( _cartridge != null )sb.append(";c="+_cartridge) ;
       if( _drive != null )sb.append(";dr="+_drive) ;
       if( _specific != null )sb.append(";sdr="+_specific) ;
       if( _pvr != null )sb.append(";pvr="+_pvr) ;
       sb.append(";") ;
       return sb.toString() ;    
    }
    public String getCartridge(){ return _cartridge ; }
    public String getPvr(){ return _pvr ; }
    public String getGenericDrive(){ return _drive ; }
    public String getSpecificDrive(){ return _specific ; }

    public void   setDrive( String generic , String specific ){ 
       _drive    = generic ;
       _specific = specific ;
    }
    public void   setCartridge( String cartridgeName ){
       _cartridge = cartridgeName ;
    }
    public void   setPvr( String pvr ){
       _pvr = pvr ;
    }
    
    public String getHostName(){ return null ; }
    public int    getHostPort(){ return 0 ; }
    public long   getFileSize(){ return 0 ; }
    public long   getServerReqId(){ return 0 ; }
    public String getClientReqId(){ return null ; }
    public String getBfid(){ return null ; }
    public String getStorageGroup(){ return null ; }
    public String getStore(){ return null ; }
    public String getParameter(){ return null ; }
    public String getPosition(){ return null ; }
    public String getEorPosition(){ return null ; }
    public String getVolumeId(){ return null ; }
    public void setResidualBytes( long size ){}
    public void setRealBytes( long size ){}
    public void setPosition( String filePosition , String eorPosition ){}
    public Dictionary getParameterDictionary(){ 
        return new Hashtable() ;
    }

} 
