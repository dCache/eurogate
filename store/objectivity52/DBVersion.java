// helper class to store the EuroStore DB version  DESY -mg

package eurogate.store.objectivity52;


import java.util.*;
import java.io.*;
import java.sql.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class DBVersion extends ooObj {

  private int _major;
  private int _minor;
  private Timestamp _created;
  private Timestamp _lastAccessed;
  
  public DBVersion(int major, int minor) {
    _major = major;
    _minor = minor;
    _created = new Timestamp(System.currentTimeMillis());
    _lastAccessed = new Timestamp(_created.getTime());
  }

  public DBVersion(DBVersion dv) {
    _major = dv.major();
    _minor = dv.minor();
    _created = new Timestamp(dv.created().getTime());
    _lastAccessed = new Timestamp(dv.lastAccessed().getTime());
  }
  
  public String toString() {
    fetch();
    return(_major + "." + _minor);
  }
  
  public boolean check(int major, int minor) {
    fetch();
    return((_major == major) && (_minor <= minor));
  }

  public int major() {
    fetch();
    return(_major);
  }
  
  public int minor() {
    fetch();
    return(_minor);
  }

  public Timestamp created() {
    fetch();
    return(_created);
  }
  
  public Timestamp lastAccessed() {
    fetch();
    return(_lastAccessed);
  }
  
  public void updateAccess(long time) {
    markModified();
    _lastAccessed = new Timestamp(time);
  }
}
