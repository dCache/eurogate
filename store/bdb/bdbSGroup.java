//
// class definition for StorageGroup record within Berkeley DB  DESY -mg
//

package eurogate.store.bdb;

import java.io.*;
import java.util.*;


public class bdbSGroup implements java.io.Serializable {

  public String _name = null;
  public long _created = 0;
  public long _lastAccessed = 0;
  public long _files = 0;
  public Vector _bfids = null;


  private void writeObject(ObjectOutputStream s) throws IOException {
    s.writeUTF(_name);
    s.writeLong(_created);
    s.writeLong(_lastAccessed);
    s.writeLong(_files);
    _files = _bfids.size();

    for (Enumeration e = _bfids.elements() ; e.hasMoreElements() ;) {
      s.writeUTF((String) e.nextElement());
    }

  }

  private void readObject(ObjectInputStream s) throws IOException {
    _name         = s.readUTF();
    _created      = s.readLong();
    _lastAccessed = s.readLong();
    _files        = s.readLong();

    _bfids = new Vector((int) _files);
    for(int i = 0; i < _files; i++) {
      _bfids.add(s.readUTF());
    }
  }

  public void addBfid(bdbBfid bf) {
    if (bf._sGroup.compareTo(_name) != 0) {
      throw new IllegalArgumentException("SGroup bfid corruption");
    }
    if (_bfids == null)
      _bfids = new Vector();
    _bfids.addElement(bf._bfid);
  }

}

