package eurogate.vehicles ;

import  java.util.Date ;

public interface BfRecordable extends java.io.Serializable {


    public String getBfid() ;
    public String getStorageGroup()  ;
    public long   getFileSize() ;
    public String getVolume() ;
    public String getFilePosition() ;
    public String getParameter() ;
 
    public Date   getLastAccessDate() ;
    public Date   getCreationDate() ;
    public int    getAccessCounter() ;
    
    public String getStatus() ;
}
