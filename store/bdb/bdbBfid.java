//
// class definition for Bitfile record within Berkeley DB  DESY -mg
//

package eurogate.store.bdb;

import java.io.*;


public class bdbBfid implements java.io.Serializable {

  public String _bfid = null;
  public long _created = 0;
  public long _lastAccessed = 0;
  public long _size = 0;
  public String _location = null;
  public String _storingDevice = null;
  public String _state = null;
  public String _parameter = null;
  public long _readsDone = 0;
  public long _readsFailed = 0;
  public int _readsFailedInRow = 0;
  public String _sGroup = null;
  public String _volume = null;


  private void writeObject(ObjectOutputStream s) throws IOException {
    s.writeUTF(_bfid);
    s.writeUTF(_sGroup);
    s.writeUTF(_volume);
    s.writeUTF(_location);
    s.writeUTF(_storingDevice);
    s.writeUTF(_state);
    s.writeUTF(_parameter);
    s.writeLong(_created);
    s.writeLong(_lastAccessed);
    s.writeLong(_size);
    s.writeLong(_readsDone);
    s.writeLong(_readsFailed);
    s.writeInt(_readsFailedInRow);
  }

  private void readObject(ObjectInputStream s) throws IOException {
    _bfid             = s.readUTF();
    _sGroup           = s.readUTF();
    _volume           = s.readUTF();
    _location         = s.readUTF();
    _storingDevice    = s.readUTF();
    _state            = s.readUTF();
    _parameter        = s.readUTF();
    _created          = s.readLong();
    _lastAccessed     = s.readLong();
    _size             = s.readLong();
    _readsDone        = s.readLong();
    _readsFailed      = s.readLong();
    _readsFailedInRow = s.readInt();
  }

}

