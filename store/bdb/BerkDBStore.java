// class handling persistent Store objects in Objectivity DESY -mg

package eurogate.store.bdb;

import java.util.*;
import java.io.*;
import dmg.util.*;
import java.sql.Timestamp;
import eurogate.misc.*;
import eurogate.vehicles.*;
import eurogate.store.*;

// Berkeley DB stuff
import com.sleepycat.db.*;

public class BerkDBStore implements EuroStoreable {

  private static final int numSessions = 1;
  private static final int _dbMajor = 0;
  private static final int _dbMinor = 1;

  private Session _sessions[] = null;
  private Logable _log = null;


  private class Session implements StorageSessionable {
    private Db _BFDB = null;
    private Db _SGDB = null;
    private Db _VDB  = null;
    private DbEnv _env = null;
    public Session(Db bfdb, Db sgdb, Db vdb, DbEnv env) {
      _BFDB = bfdb;
      _SGDB = sgdb;
      _VDB  = vdb; 
      _env  = env;
    }
    public Db getBFDB() { return(_BFDB); }
    public Db getSGDB() { return(_SGDB); }
    public Db getVDB()  { return(_VDB); }
    public DbEnv getDbEnv() { return(_env); }
  }

  // class to create Dbt objects for the database
  private class BfidDbt extends Dbt {

    public BfidDbt() {
      set_flags(Db.DB_DBT_MALLOC);
    }
    public BfidDbt(bdbBfid bf) {
      setBfid(bf);
      set_flags(Db.DB_DBT_MALLOC);      
    }
    void setBfid(bdbBfid bf) {
      try {
	ByteArrayOutputStream ao = new ByteArrayOutputStream();
	ObjectOutputStream os = new ObjectOutputStream(ao);
	os.writeObject(bf);
	os.flush();
	os.close();
	set_data(ao.toByteArray());
	set_size(ao.size());
	//say("BF-size: " + ao.size());
      } catch (Exception e) {
	e.printStackTrace();
	return;
      }
    }
    public bdbBfid getBfid() {
      bdbBfid bf = null;
      ByteArrayInputStream ai;
      ObjectInputStream os;

      try {
	ai = new ByteArrayInputStream(get_data(), 0, get_size());
	os = new ObjectInputStream(ai);
	bf = (bdbBfid) os.readObject();
	os.close();
      } catch (Exception e) {
	e.printStackTrace();
	return(null);
      }
      return(bf);
    }

  }

  // class to create Volume Dbt objects for the database
  private class VolumeDbt extends Dbt {
    public VolumeDbt() {
      set_flags(Db.DB_DBT_MALLOC);
    }
    public VolumeDbt(bdbVolume v) {
      setVolume(v);
      set_flags(Db.DB_DBT_MALLOC);
    }
    void setVolume(bdbVolume v) {
      try {
        ByteArrayOutputStream ao = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(ao);
        os.writeObject(v);
        os.flush();
        os.close();
        set_data(ao.toByteArray());
        set_size(ao.size());
        //say("VOL-size: " + ao.size());
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
    }
    public bdbVolume getVolume() {
      bdbVolume v = null;
      ByteArrayInputStream ai;
      ObjectInputStream os;
      try {
        ai = new ByteArrayInputStream(get_data(), 0, get_size());
        os = new ObjectInputStream(ai);
        v = (bdbVolume) os.readObject();
        os.close();
      } catch (Exception e) {
        e.printStackTrace();
        return(null);
      }
      return(v);
    }
  }

  // class to create Volume Dbt objects for the database
  private class SGroupDbt extends Dbt {
    public SGroupDbt() {
      set_flags(Db.DB_DBT_MALLOC);
    }
    public SGroupDbt(bdbSGroup sg) {
      setSGroup(sg);
      set_flags(Db.DB_DBT_MALLOC);
    }
    void setSGroup(bdbSGroup sg) {
      try {
        ByteArrayOutputStream ao = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(ao);
        os.writeObject(sg);
        os.flush();
        os.close();
        set_data(ao.toByteArray());
        set_size(ao.size());
        //say("SG-size: " + ao.size());
      } catch (Exception e) {
        e.printStackTrace();
        return;
      }
    }
    public bdbSGroup getSGroup() {
      bdbSGroup sg = null;
      ByteArrayInputStream ai;
      ObjectInputStream os;
      try {
        ai = new ByteArrayInputStream(get_data(), 0, get_size());
        os = new ObjectInputStream(ai);
        sg = (bdbSGroup) os.readObject();
        os.close();
      } catch (Exception e) {
        e.printStackTrace();
        return(null);
      }
      return(sg);
    }
  }

  // Dbt subclass for the key (bfid)
  static class StringDbt extends Dbt {
    StringDbt() {
      set_flags(Db.DB_DBT_MALLOC); // tell Db to allocate on retrieval
    }
    StringDbt(String value) {
      setString(value);
      set_flags(Db.DB_DBT_MALLOC); // tell Db to allocate on retrieval
    }
    void setString(String value) {
      set_data(value.getBytes());
      set_size(value.length());
    }
    String getString() {
      return new String(get_data(), 0, get_size());
    }
  }

  public BerkDBStore(Args arg, Dictionary dict, Logable log)
    throws Exception {

    String dbPath = arg.getOpt("dbpath");
    String name;
    Db bfdb = null;
    Db sgdb = null;
    Db vdb = null;
    DbEnv env = null;

    if (dbPath != null) {
      name = dbPath + "/EuroGate";
    } else {
      throw new IllegalArgumentException("no dbpath argument specified");
    }

    env = new DbEnv(0);
    bfdb = new Db(null, 0);
    bfdb.set_error_stream(System.err);
    bfdb.set_errpfx("BerkDBStore-BFID");
    bfdb.open(name + "BFID.DB", null, Db.DB_BTREE, Db.DB_CREATE, 0644);

    sgdb = new Db(null, 0);
    sgdb.set_error_stream(System.err);
    sgdb.set_errpfx("BerkDBStore-SG");
    sgdb.open(name + "SGROUP.DB", null, Db.DB_BTREE, Db.DB_CREATE, 0644);

    vdb = new Db(null, 0);
    vdb.set_error_stream(System.err);
    vdb.set_errpfx("BerkDBStore-V");
    vdb.open(name + "VOL.DB", null, Db.DB_BTREE, Db.DB_CREATE, 0644);

    _sessions = new Session[1];
    _sessions[0] = new Session(bfdb, sgdb, vdb, env);
    
    _log = log;
    //_log.log("Store DB opened: " + _ssessions[0]._dbVersion);
  }

  public StorageSessionable[] getStorageSessionable() {
    return(_sessions);
  }
  
  private void say( String msg ){ _log.log( msg ) ; }
  private void esay( String msg ){ _log.elog( msg ) ; }

  // and now the real stuff
  
  public void initialPutRequest( StorageSessionable session ,
                                 BitfileRequest bfreq ) {
    //say("initialPutRequest() in");
    bfreq.setBfid(new Bfid().toString());
    //say("initialPutRequest() out");
  }
  
  public void initialGetRequest( StorageSessionable sessions ,
                                 BitfileRequest bfreq ) {
    Session session = (Session) sessions;
    String bfid   = bfreq.getBfid();
    bdbBfid bf = null;

    //say("initialGetRequest() in");

    try {
      bf = fetchBfid(session.getBFDB(), bfid);
    } catch (DbException dbe) {
      dbe.printStackTrace();
      bfreq.setReturnValue(14, dbe.toString());
      return;
    } catch (IllegalArgumentException e) {
      bfreq.setReturnValue(13, e.toString());
      return;
    }
    // fill request structure
    bfreq.setFileSize(bf._size);
    bfreq.setStorageGroup(bf._sGroup);
    bfreq.setFilePosition(bf._location);
    bfreq.setVolume(bf._volume);
    bfreq.setParameter(bf._parameter);

    //say("initialGetRequest() out");
  }
  
  public void initialRemoveRequest( StorageSessionable sessions ,
                                    BitfileRequest bfreq ) {
    Session session = (Session) sessions;
    String bfid   = bfreq.getBfid();
    bdbBfid bf = null;

    //say("initialRemoveRequest() in");
    
    try {
      bf = fetchBfid(session.getBFDB(), bfid);
    } catch (DbException dbe) {
      dbe.printStackTrace();
      bfreq.setReturnValue(14, dbe.toString());
      return;
    } catch (IllegalArgumentException e) {
      bfreq.setReturnValue(13, e.toString());
      return;
    }

    // fill request structure
    bfreq.setFileSize(bf._size);
    bfreq.setStorageGroup(bf._sGroup);
    bfreq.setFilePosition(bf._location);
    bfreq.setVolume(bf._volume);
    bfreq.setParameter(bf._parameter);

    //say("initialRemoveRequest() out");
  }

  private bdbVolume fetchVolume(Db db, String name) throws DbException {
    VolumeDbt voldbt = new VolumeDbt();
    StringDbt sdbt = new StringDbt(name);
    String error = null;

    try {
      int err;
      if ((err = db.get(null, sdbt, voldbt, 0)) != 0) {
        esay(error = "Volume " + name + " not exists.");
	throw new IllegalArgumentException(error);
      }
    } catch (DbException dbe) {
      dbe.printStackTrace();
      throw dbe;
    }
    return(voldbt.getVolume());
  }

  private void storeVolume(Db db, bdbVolume v) throws DbException {
    String error = null;
    VolumeDbt voldbt = new VolumeDbt(v);
    StringDbt sdbt = new StringDbt(v._name);

    try {
      int err;
      if ((err = db.put(null, sdbt, voldbt, 0)) != 0) {
        esay(error = "DB error while storing volume " + v._name);
	throw new DbException(error);
      }
    } catch (DbException dbe) {
      dbe.printStackTrace();
      throw dbe;
    }
  }

  private void storeBfid(Db db, bdbBfid bf) throws DbException {
    String error = null;
    BfidDbt bfdbt = new BfidDbt(bf);
    StringDbt sdbt = new StringDbt(bf._bfid);

    try {
      int err;
      if ((err = db.put(null, sdbt, bfdbt, 0)) != 0) {
        esay(error = "DB error while storing bfid " + bf._bfid);
        throw new DbException(error);
      }
    } catch (DbException dbe) {
      dbe.printStackTrace();
      throw dbe;
    }
  }

  private bdbSGroup fetchSGroup(Db db, String name) throws DbException {
    SGroupDbt sgdbt = new SGroupDbt();
    StringDbt sdbt = new StringDbt(name);
    String error = null;

    try {
      int err;
      if ((err = db.get(null, sdbt, sgdbt, 0)) != 0) {
        esay("SGroup " + name + " not exists.");
        throw new IllegalArgumentException(error);
      }
    } catch (DbException dbe) {
      dbe.printStackTrace();
      throw dbe;
    }
    return(sgdbt.getSGroup());
  }


  private void storeSGroup(Db db, bdbSGroup sg) throws DbException {
    String error = null;
    SGroupDbt sgdbt = new SGroupDbt(sg);
    StringDbt sdbt = new StringDbt(sg._name);

    try {
      int err = 0;
      if ((err = db.put(null, sdbt, sgdbt, 0)) != 0) {
        esay(error = "DB error while storing storage group " + sg._name);
        throw new DbException(error);
      }
    } catch (DbException dbe) {
      dbe.printStackTrace();
      throw dbe;
    }
  }



  public void finalPutRequest( StorageSessionable sessions ,
                               BitfileRequest bfreq ) {
    
    if (bfreq.getReturnCode() != 0)  // easy going
      return;

    //say("finalPutRequest() in");
    
    Session session = (Session) sessions;
    bdbVolume v = null;
    bdbSGroup sg = null;
    
    String group  = bfreq.getStorageGroup();
    String volume = bfreq.getVolume();
    String bfid   = bfreq.getBfid();
    String error = null;

    bdbBfid bf = new bdbBfid();
    bf._bfid = bfreq.getBfid();
    bf._size = bfreq.getFileSize();
    bf._location = bfreq.getFilePosition();
    bf._parameter = bfreq.getParameter();
    //bf._created = new Timestamp(System.currentTimeMillis());
    //bf._lastAccessed = new Timestamp(bf._created.getTime());
    bf._lastAccessed = bf._created = System.currentTimeMillis();
    bf._storingDevice = "no idea";
    bf._volume = volume;
    bf._sGroup = group;
    bf._state = "Permanent";

    BfidDbt bfdbt = new BfidDbt(bf);
    StringDbt sdbt = new StringDbt(bf._bfid);

    try {
      int err;
      if ((err = session.getBFDB().put(null, sdbt, bfdbt,
				       Db.DB_NOOVERWRITE)) == Db.DB_KEYEXIST) {
	esay(error = "Bfid " + bf._bfid + " already exists.");
	bfreq.setReturnValue(18, error);
	return;
      } else {
	try {
	  v = fetchVolume(session.getVDB(), volume);
	} catch (IllegalArgumentException e) {
	  v = new bdbVolume();
	  v._name = volume;
	  v._created = v._lastAccessed = bf._created;
	}
	v._files++;
	v.addBfid(bf);
	storeVolume(session.getVDB(), v);

	// same procedure for the storage group
	try {
	  sg = fetchSGroup(session.getSGDB(), group);
	} catch (IllegalArgumentException e) {
	  sg = new bdbSGroup();
	  sg._name = group;
	  sg._created = sg._lastAccessed = bf._created;
	}
	sg._files++;
	sg.addBfid(bf);
	storeSGroup(session.getSGDB(), sg);
      }
    } catch (DbException dbe) {
      esay(dbe.toString());
      dbe.printStackTrace();
      bfreq.setReturnValue(22, dbe.toString());
      return;
    }
    //say("finalPutRequest() out" + bf._bfid);
  }
  
  public void finalGetRequest( StorageSessionable sessions ,
                               BitfileRequest bfreq ) {
    Session session = (Session) sessions;
    String bfid   = bfreq.getBfid();
    String error = null;


    //say("finalGetRequest() in");

    try {
      bdbBfid bf = fetchBfid(session.getBFDB(), bfid);

      if (bfreq.getReturnCode() != 0) { // some error happens
	bf._readsFailed++;
	bf._readsFailedInRow++;
      } else {
	bf._readsDone++;
	bf._readsFailedInRow = 0;
      }
      bf._lastAccessed = System.currentTimeMillis();
      storeBfid(session.getBFDB(), bf);
    } catch (DbException dbe) {
      dbe.printStackTrace();
      bfreq.setReturnValue(14, dbe.toString());
      return;
    } catch (IllegalArgumentException e) {
      bfreq.setReturnValue(13, e.toString());
      return;
    }

    //say("finalGetRequest() out");
  }
  
  public void finalRemoveRequest( StorageSessionable sessions ,
                                  BitfileRequest bfreq ) {
    Session session = (Session) sessions;
    String bfid   = bfreq.getBfid();
    bdbBfid bf = null;
    bdbVolume v = null;
    bdbSGroup sg = null;

    //say("finalRemoveRequest() in");

    if (bfreq.getReturnCode() != 0)  // some error happens
      return;

    try {
      bf = fetchBfid(session.getBFDB(), bfid);
      v  = fetchVolume(session.getVDB(), bf._volume);
      sg = fetchSGroup(session.getSGDB(), bf._sGroup);

      v._files--;
      if (v._bfids.removeElement(bf._bfid) == false)
	throw new DbException("remove bfid from volume failed");

      sg._files--;
      if (sg._bfids.removeElement(bf._bfid) == false)
        throw new DbException("remove bfid from sgroup failed");
 
      if (v._files <= 0) {
	StringDbt sdbt = new StringDbt(v._name);
	session.getVDB().del(null, sdbt, 0);
      } else {
	storeVolume(session.getVDB(), v);
      }

      if (sg._files <= 0) {
        StringDbt sdbt = new StringDbt(sg._name);
        session.getSGDB().del(null, sdbt, 0);
      } else {
	storeSGroup(session.getSGDB(), sg);
      }

      // delete the actual bitfile
      StringDbt sdbt = new StringDbt(bf._bfid);
      session.getBFDB().del(null, sdbt, 0);


    } catch (DbException dbe) {
      bfreq.setReturnValue(16, dbe.toString());
      return;
    } catch (IllegalArgumentException e) {
      bfreq.setReturnValue(13, e.toString());
      return;
    }

    //say("finalRemoveRequest() out");
  }

  private class BfRecord implements BfRecordable {
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

    BfRecord( String bfid ) { _bfid = bfid ; }
    public String getBfid() { return _bfid ; }
    public String getStorageGroup() { return _group ; }
    public long   getFileSize() { return _size ; }
    public String getVolume() { return _volume ; }
    public String getFilePosition() { return _filePosition ; }
    public String getParameter() { return _parameter ; }
    public Date   getLastAccessDate() { return _lastDate ; }
    public Date   getCreationDate() { return _creationDate ; }
    public int    getAccessCounter() { return _counter ; }
    public String getStatus() { return _status ; }
  }

  private bdbBfid fetchBfid(Db db, String bfid) throws DbException {
    String error = null;
    BfidDbt bfdbt = new BfidDbt();
    StringDbt sdbt = new StringDbt(bfid);

    try {
      int err = 0;
      if ((err = db.get(null, sdbt, bfdbt, 0)) != 0) {
        esay(error = "Bfid " + bfid + " not exists. (" + err + ")");
        throw new IllegalArgumentException(error);
      }
    } catch (DbException dbe) {
      esay(error = "bfid db error" + dbe.toString());
      dbe.printStackTrace();
      throw dbe;
    }

    return(bfdbt.getBfid());
  }

  public BfRecordable getBitfileRecord(
				       StorageSessionable sessions ,
				       String bfid
				       ) {
    
    Session session = (Session) sessions;
    String error = null;
    BfidDbt bfdbt = new BfidDbt();
    StringDbt sdbt = new StringDbt(bfid);
    bdbBfid bf = null; 

    try {
      bf = fetchBfid(session.getBFDB(), bfid);
    } catch (DbException dbe) {
      esay(error = dbe.toString());
      dbe.printStackTrace();
      throw new IllegalArgumentException(error);
    }

    BfRecord bfr = new BfRecord(bfid);

    bfr._group = bf._sGroup;
    bfr._size = bf._size;
    bfr._volume = bf._volume;
    bfr._filePosition = bf._location;
    bfr._parameter = bf._parameter;
    bfr._lastDate = new Timestamp(bf._lastAccessed);
    bfr._creationDate = new Timestamp(bf._created);
    bfr._counter = (int) bf._readsDone;
    bfr._status = bf._state;

    return(bfr);
  }

  private class ListCookieEnumeration implements CookieEnumeration {
    private Object [] _list = null ;
    private int       _p = 0 ;
    
    private ListCookieEnumeration( Object [] list , long cookie ){
      _p    = (int)cookie ;
      _list = new Object[list.length] ;
      System.arraycopy( list  , 0 , _list , 0 , list.length ) ;
    }
    public boolean hasMoreElements(){
      return _p < _list.length ;
    }
    public Object nextElement() throws NoSuchElementException {
      if( ! hasMoreElements() )
        throw new NoSuchElementException( "No more elements" ) ;
      return _list[_p++] ;
    }
    public long getCookie(){
      return _p ;
    }
  }

  public CookieEnumeration getBfidsByVolume( StorageSessionable sessions ,
                                             String volume , long cookie ) {
    Session session = (Session) sessions;
    String error = null;
    bdbVolume v = null;
 
    try {
      v = fetchVolume(session.getVDB(), volume);
    } catch (IllegalArgumentException e) {
      esay(error = "volume " + volume + " not exists");
      throw e;
    } catch (DbException dbe) {
      esay(error = "fetch error in volume DB " + dbe.toString());
      dbe.printStackTrace();
      throw new IllegalArgumentException(error);
    }
    return(new ListCookieEnumeration(v._bfids.toArray(), cookie));
  }

    
  public CookieEnumeration getBfidsByStorageGroup(
                                 StorageSessionable sessions ,
                                 String storageGroup , long cookie) {
    Session session = (Session) sessions;
    String error = null;
    bdbSGroup sg = null;

    try {
      sg = fetchSGroup(session.getSGDB(), storageGroup);
    } catch (IllegalArgumentException e) {
      esay(error = "storage group " + storageGroup + " not exists");
      throw e;
    } catch (DbException dbe) {
      esay(error = "fetch error in SGroup DB " + dbe.toString());
      dbe.printStackTrace();
      throw new IllegalArgumentException(error);
    }
    return(new ListCookieEnumeration(sg._bfids.toArray(), cookie));
  }
  
  public CookieEnumeration getStorageGroups(
                                 StorageSessionable sessions , 
                                 long cookie ) {
    Session session = (Session) sessions;
    String error = null;
    Vector ve = new Vector();
    SGroupDbt sgdbt = new SGroupDbt();
    StringDbt sdbt = new StringDbt();

    try {
      int ret;
      Dbc dbc = session.getSGDB().cursor(null, 0);
      for(ret = dbc.get(sdbt, sgdbt, Db.DB_FIRST);
	  ret != Db.DB_NOTFOUND;
	  ret = dbc.get(sdbt, sgdbt, Db.DB_NEXT)) {
	ve.addElement(sgdbt.getSGroup()._name);
      }
    } catch (DbException dbe) {
      esay(error = "db error while scanning SGroup DB " + dbe.toString());
      throw new IllegalArgumentException(error);
    }

    return(new ListCookieEnumeration(ve.toArray(), cookie));
  }
  
  public void close() {
    say("shutting down BerkeleyDB Store");

    try {
      _sessions[0].getBFDB().close(0);
      _sessions[0].getSGDB().close(0);
      _sessions[0].getVDB().close(0);
    } catch (DbException dbe) {
      dbe.printStackTrace();
    }
  }

}
