package  eurogate.vehicles ;

/**
  *   this interface does no longer make any sense
  *   and should be removed as soon as possibe.
  */

public interface PvlRequest extends EurogateRequest {

    public String getVolume() ;
    public long   getFileSize() ;
    public long   getRealBytes() ;
    public long   getResidualBytes() ;
    public String getVolumeSet() ;
    public void   setVolume( String volumeName ) ;
    public void   setVolumeId( String volumeName ) ;
    public void   setPosition( String newFile , String eor ) ;
    public String getEorPosition() ;
    public String getDrive() ;
    public String getCartridge() ;
    public String getPvr() ;
    public void   setPvr( String pvr ) ;
    public void   setCartridge( String cartridge ) ;
    public void   setDrive( String drive ) ;
    
}
 
