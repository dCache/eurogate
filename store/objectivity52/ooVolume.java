// Volume persistent object (Objectivity) DESY -mg

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;
import java.sql.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class ooVolume extends ooObj {

  public String _volName = null;
  public Timestamp _created = null;
  public Timestamp _lastAccessed = null;
  public long _fileCount = 0;
  // relation to bitfile records
  private ToManyRelationship _bitfiles;
  public static OneToMany _bitfiles_Relationship() {
    return new OneToMany(
      "_bitfiles",
      "eurogate.store.objectivity52.ooBfid",
      "_volume",
      Relationship.COPY_MOVE,
      Relationship.VERSION_MOVE,
      false,
      false,
      Relationship.INLINE_LONG);
  }
  
  public void attachBitfile(ooBfid bf) {
    _bitfiles.add(bf);
  }
  
  public com.objy.db.app.Iterator getBitfiles() {
    return(_bitfiles.scan());
  }
  
  public ooVolume() { }
  public ooVolume(String volName) {
    _volName = volName;
  }
}
