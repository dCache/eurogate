// bitfile ID persistent object (Objectivity) DESY -mg

package eurogate.store.objectivity52;


import java.util.*;
import java.io.*;
import java.sql.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class ooBfid extends ooObj {

  public String _bfid = null;
  public Timestamp _created = null;
  public Timestamp _lastAccessed = null;
  public long _size = 0;
  public String _location = null;
  public String _storingDevice = null;
  public String _state = null;
  public String _parameter = null;
  public long _readsDone = 0;
  public long _readsFailed = 0;
  public int _readsFailedInRow = 0;
  // _state could be
  //     "Undefined"
  //     "Transient"
  //     "Permanent"
  
  // relations to volume and storagegroup records
  private ToOneRelationship _volume;
  public static ManyToOne _volume_Relationship() {
    return new ManyToOne(
      "_volume",
      "eurogate.store.objectivity52.ooVolume",
      "_bitfiles",
      Relationship.COPY_MOVE,
      Relationship.VERSION_MOVE,
      false,
      false,
      Relationship.INLINE_LONG);
  } 
  
  private ToOneRelationship _storagegroup;
  public static ManyToOne _storagegroup_Relationship() {
    return new ManyToOne(
      "_storagegroup",
      "eurogate.store.objectivity52.ooSGroup",
      "_bitfiles",
      Relationship.COPY_MOVE,
      Relationship.VERSION_MOVE,
      false,
      false,
      Relationship.INLINE_LONG);
  }
  
  public void attachVolume(ooVolume v) {
    fetch();
    _volume.form(v);
  }
  
  public ooVolume getVolume() {
    fetch();
    return((ooVolume) _volume.get());
  }

  public void attachSGroup(ooSGroup sg) {
    fetch();
    _storagegroup.form(sg);
  }
  
  public ooSGroup getSGroup() {
    fetch();
    return((ooSGroup) _storagegroup.get());
  }

  public ooBfid() { _state = "Undefined"; }
  public ooBfid(String bfid) {
    _bfid = bfid;
    _state = "Undefined";
  }
  
  public void setStateTransient() {
    _state = "Transient";
  }
  
  public void setStatePermanent() {
    _state = "Permanent";
  }
  
  public boolean isStateTransient() {
    return(_state.compareTo("Transient") == 0);
  }
  
  public boolean isStatePermanent() {
    return(_state.compareTo("Permanent") == 0);
  }
}
