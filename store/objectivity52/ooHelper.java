// helper class to manage Objectivity Sessions and other
// thread bounded state  DESY -mg

package eurogate.store.objectivity52;

import java.util.*;
import dmg.util.*;
import eurogate.misc.*;
import eurogate.vehicles.*;
import eurogate.store.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class ooHelper implements StorageSessionable {

  public Session _session = null;
  public ooFDObj _fd = null;
  public ooDBObj _miscDB = null;
  public ooDBObj _bfidDB = null;
  public ooDBObj _volDB = null;
  public ooDBObj _sgDB = null;
  public ooTreeSet _ts = null;        // the bitfile collection
  public ooHashSet _vhs = null;       // the volume collection
  public ooHashSet _sghs = null;      // the storagegroup collection
  public ooBFCompare _BFComp = null;
  public ooVCompare _VComp = null;
  public ooSGCompare _SGComp = null;
  public String _dbVersion = null;

  public void clear() {
    _session = null;
    _fd = null;
    _ts = null;
    _vhs = _sghs = null;
    _miscDB = _volDB = _sgDB = null;
  }
    

  // create helper instance - auto-fill state
  public ooHelper(Logable l, int dbMajor, int dbMinor) 
         throws ObjyRuntimeException, ObjyException, Exception {
    _session = new Session(200, 1000);
    _fd = _session.getFD();
    _session.begin();
    try {
      _miscDB = _fd.lookupDB("MiscDB");
      // fetch the DBVersion object and check compatibility
      DBVersion dbv = (DBVersion) _miscDB.lookupContainer("DBVCont").lookupObj("DBVersion");
      _dbVersion = "EuroStore Objectivity/DB version " + dbv + " created " +
                    dbv.created().toString();
      if (!dbv.check(dbMajor, dbMinor))
        throw new Exception("Objectivity Store DB wrong version (" + dbv + ") expect (" + dbMajor + "." + dbMinor + ")");
    
      _ts = (ooTreeSet) (_bfidDB = _fd.lookupDB("BfidDB")).lookupObj("BitfileTreeSet");
      _vhs = (ooHashSet) (_volDB = _fd.lookupDB("VolDB")).lookupObj("VolumeHashSet");
      _sghs = (ooHashSet) (_sgDB = _fd.lookupDB("SGroupDB")).lookupObj("SGroupHashSet");

      if (_ts == null)
        throw new Exception("Tree-Set is null");
      if (_vhs == null)
        throw new Exception("Volume Hash-Set is null");
      if (_sghs == null)
        throw new Exception("Storage-Group Hash-Set is null");
      if (_miscDB == null)
        throw new Exception("MiscDB is null");
    } catch (ObjyRuntimeException e) {
      //LogDB.Log(l, e.errors());
      _session.abort();
      throw(e);
    } catch (ObjectNameNotFoundException nf) {
      //LogDB.Log(l, nf.errors());
      _session.abort();
      throw(nf);
    } catch (Exception e) {
      _session.abort();
      throw(e);
    }
    _session.commit();
    
    // get the Comparator classes
    _session.begin();
    try {
      _BFComp = (ooBFCompare) _miscDB.lookupContainer("BFCont").lookupObj("ooBFCompare");
      _VComp = (ooVCompare) _miscDB.lookupContainer("VCont").lookupObj("ooVCompare");
      _SGComp = (ooSGCompare) _miscDB.lookupContainer("SGCont").lookupObj("ooSGCompare");
    } catch (ObjyRuntimeException e) {
      //LogDB.Log(l, e.errors());
      _session.abort();
      throw(e);
    } /*catch (ObjectNameNotFoundException nf) {
      //LogDB.Log(l, nf.errors());
      _session.abort();
      throw(nf);
    }*/
    _session.commit();
  }
  
}
