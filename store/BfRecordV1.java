package eurogate.store ;

import eurogate.vehicles.* ;
import java.util.* ;

public class BfRecordV1 
       implements BfRecordable {

   String _bfid  = null ;
   String _group = "" ;
   long   _size  = 0 ;
   String _volume = null ;
   String _filePosition = null ;
   String _parameter    = null ;
   Date   _lastDate     = null ;
   Date   _creationDate = null ;
   int    _counter      =  0 ;
   String _status       = null ;

   BfRecordV1( String bfid ){ _bfid = bfid ; }
   public String getBfid(){ return _bfid ; }
   public String getStorageGroup(){ return _group ; }
   public long   getFileSize(){ return _size ; }
   public String getVolume(){ return _volume ; }
   public String getFilePosition(){ return _filePosition ; }
   public String getParameter(){ return _parameter ; }

   public Date   getLastAccessDate(){ return _lastDate ; }
   public Date   getCreationDate(){ return _creationDate ; }
   public int    getAccessCounter(){ return _counter ; }

   public String getStatus(){ return _status ; }

}
