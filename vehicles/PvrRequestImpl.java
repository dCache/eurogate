package  eurogate.vehicles ;



public class      PvrRequestImpl 
       extends    EurogateRequestImpl 
       implements PvrRequest {

    private String _cartridgeName = "" ;
    private String _genericDriveName  = "" ;
    private String _specificDriveName = "" ;
    private String _pvrName = "" ;
    public PvrRequestImpl( String command ,
                           String pvr ,
                           String volumeName ,
                           String genericDrive ,
                           String specificDrive  ){
        super( command ) ;
        _cartridgeName     = volumeName ;
        _genericDriveName  = genericDrive ;
        _specificDriveName = specificDrive ;  
        _pvrName           = pvr ;                    
    }
    //
    // the interface implementation 
    //
    public String getPvr(){ return _pvrName ; }
    public void   setPvr( String pvr ){ _pvrName = pvr ; }
    public String getCartridge(){ return _cartridgeName ; }
    public String getGenericDrive(){ return _genericDriveName ; }
    public String getSpecificDrive(){ return _specificDriveName ; }
    public void   setDrive( String generic , String specific ){
       _genericDriveName  = generic ;
       _specificDriveName = specific ;
    }
    public void   setCartridge( String cartridgeName ){
       _cartridgeName = cartridgeName ;
    }
    public String toString(){
       return super.toString()+
               ";v="+_cartridgeName+
               ";gd="+_genericDriveName+
               ";sd="+_specificDriveName ;
    }                                          
    
} 
