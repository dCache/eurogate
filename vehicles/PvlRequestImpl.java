package  eurogate.vehicles ;

import java.util.* ;

public class      PvlRequestImpl 
       extends    EurogateRequestImpl 
       implements PvlRequest {

    private long   _fileSize  ;
    private String _volume ;
    private String _volumeSet ;
    private String _cartridge ;
    private String _volumeId ;
    private String _eorPosition ;
    private String _position ;
    public PvlRequestImpl( String volumeSet ,
                           long   fileSize   ){
       super( "put" , "" ) ;
       _fileSize  = fileSize ;
       _volumeSet = volumeSet ;
       _volume    = "" ;                      
    }
    public PvlRequestImpl( String volume   ){
       super( "get" , "" ) ;
       _volume    = volume;                      
    }
    public String getVolume(){
       return _volume ;
    }
    public long   getFileSize(){
       return _fileSize ;
    }
    public long   getRealBytes(){
       return -1 ;
    }
    public long getResidualBytes(){ 
       return 0 ; 
    }
    public String getVolumeSet(){
       return _volumeSet ;
    }
    public void   setVolume( String volumeName ) {
       _volume = volumeName ;
    }
    public void   setCartridge( String cartridgeName ) {
       _cartridge = cartridgeName ;
    }
    public Dictionary getParameterDictionary(){ return new Hashtable() ; }
    public String getCartridge(){ return _cartridge ; }
    public String getPvr(){ return null ; }
    public String getDrive(){ return null ; }
    public void  setDrive( String drive ){} 
    public void  setPvr( String pvr ){} 
    public void  setVolumeId( String volId ){
       _volumeId = volId ;
    }
    public void setPosition( String position , String eor ){
       _position = position ;
       _eorPosition = eor ;
    }
    public String getEorPosition(){ return _eorPosition ; }
 
}
