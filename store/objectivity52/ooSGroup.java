// Volume persistent object (Objectivity) DESY -mg

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;
import java.sql.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class ooSGroup extends ooObj {

  public String _sgName = null;
  public Timestamp _created = null;
  public Timestamp _lastAccessed = null;
  public long _fileCount = 0;
  // relation to bitfile records
  private ToManyRelationship _bitfiles;
  public static OneToMany _bitfiles_Relationship() {
    return new OneToMany(
      "_bitfiles",
      "eurogate.store.objectivity52.ooBfid",
      "_storagegroup",
      Relationship.COPY_MOVE,
      Relationship.VERSION_MOVE,
      false,
      false,
      Relationship.INLINE_NONE);
  }
  
  public ooSGroup() { }
  public ooSGroup(String sgName) {
    _sgName = sgName;
  }
}
