package eurogate.vehicles ;

public interface MoverRequest extends EurogateRequest {


    public String getHostName() ;
    public int    getHostPort() ;
    public long   getFileSize() ;
    public long   getServerReqId() ;
    public String getClientReqId() ;
    public String getBfid() ;                  
    public String getStorageGroup() ;
    public String getStore() ;
    public String getCartridge() ;
    public String getParameter() ;
    /**
      *  Sets the position of the new file and the new 
      *  'end of recording' position.
      */
    public void setPosition( String filePosition , String eorPosition ) ;
    /**
      *  Returns the bf position for read and the EOR position
      *  for write. The initial EOR position is a hyphen '-' .
      */
    public String getEorPosition() ;
    /**
      *  Returns the bf position for read and the EOR position
      *  for write. The initial EOR position is a hyphen '-' .
      */
    public String getPosition() ;
    /**
      *  Returns the unique identification for this
      *  volume on this cartridge.
      */
    public String getVolumeId() ;
    /**
      *  Has to be the number of free bytes, the mover expects on the
      *  tape, after a write operation failed because EOT
      *  was reached. 
      */
    public void setResidualBytes( long size ) ;
    /**
      *  Has to be the actual number of bytes the mover has written to
      *  tape.
      */
    public void setRealBytes( long size ) ;

} 
