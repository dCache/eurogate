package eurogate.vehicles ;

public interface BitfileRequest extends EurogateRequest {

    public void setServerReqId( long serverReqId ) ;
    public void setBfid( String bfid ) ;
    public void setStorageGroup( String storageGroup ) ;
    public void setFileSize( long fileSize ) ;
    public void setVolume( String volume ) ;
    public void setFilePosition( String position ) ;
    public void setParameter( String parameter ) ;
    
    public long   getServerReqId() ;
    public String getBfid() ;
    public String getStorageGroup()  ;
    public long   getFileSize() ;
    public String getVolume() ;
    public String getFilePosition() ;
    public String getParameter() ;
}
