package eurogate.vehicles ;

import  java.util.Date ;

/**
  * The BfRecord interface hides the access to the actual
  * bitfilerecord. The interface is for query purposes only.
  * It can be interfaced by 'immutable' objects (getX only)
  * or by the real database bitfile record. NOTE : it 
  * enforces Serializable which may restict the use of
  * this interface.
  */
public interface BfRecordable extends java.io.Serializable {

    /**
      * Returns the unique representation of the bitfilerecord.
      */
    public String getBfid() ;
    /**
      * Returns the name of the storage group, this bitfile
      * belongs to.
      */
    public String getStorageGroup()  ;
    /**
      * Returns the size of the bitfile in bytes.
      */
    public long   getFileSize() ;
    /**
      * Returns the name of the volume this bitfile resides on.
      */
    public String getVolume() ;
    /**
      * Returns the transient volume position of this
      * bitfile on the volume. The string value can only
      * be interpreted by the related mover.
      */
    public String getFilePosition() ;
    /**
      * Returns the parameter string the request contained 
      * when first sent to the store.
      */
    public String getParameter() ;
    /**
      * Returns the last access date of this bitfile.
      */
    public Date   getLastAccessDate() ;
    /**
      * Returns the creation date of this bitfile.
      */
    public Date   getCreationDate() ;
    /**
      * Returns the number this bitfile has been accessed yet.
      */
    public int    getAccessCounter() ;
    /**
      *  Returns the string representation of the status of
      *  this bitfile. (transient/persistant)
      */
    public String getStatus() ;
}
