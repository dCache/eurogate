// class handling persistent Store objects in Objectivity DESY -mg

package eurogate.store.objectivity52;

import java.util.*;
import dmg.util.*;
import java.sql.Timestamp;
import eurogate.misc.*;
import eurogate.vehicles.*;
import eurogate.store.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class BitfileHandler implements EuroStoreable {

  private static Connection _connection = null;
  private static ooHelper[] _ssessions;
  private Logable _log = null;
  private static final int numSessions = 1;

  public BitfileHandler(Args arg, Dictionary dict, Logable log) throws
         ObjyException, ObjyRuntimeException, Exception {
    if (_connection != null)
      throw new IllegalArgumentException("federation already connected");
    // get federation path
    String dbPath = arg.getOpt("dbpath");
    String name;
    if (dbPath != null) {
      name = dbPath + "/EuroGate";
    } else {
      throw new IllegalArgumentException("no dbpath argument specified");
    }
    _connection = Connection.open(name, oo.openReadWrite);
    _ssessions = new ooHelper[numSessions];
    try {
      for(int i=0; i<numSessions; i++) {
        _ssessions[i] = new ooHelper();
      } 
    } catch (ObjyException oe) {
      LogDB.Log(log, oe.errors());
      oe.printStackTrace();
      _connection.close(); _connection = null;
      throw oe;
    } catch (ObjyRuntimeException or) {
      LogDB.Log(log, or.errors());
      or.printStackTrace();
      _connection.close(); _connection = null;
      throw or;
    } catch (Exception e) {
      e.printStackTrace();
      _connection.close(); _connection = null;
      throw e;
    }
    _log = log;
  }

  public StorageSessionable[] getStorageSessionable() {
    return(_ssessions);
  }
  
  private void say( String msg ){ _log.log( msg ) ; }
  private void esay( String msg ){ _log.elog( msg ) ; }

  // and now the real stuff
  
  public void initialPutRequest( StorageSessionable session ,
                                 BitfileRequest bfreq ) {
    bfreq.setBfid(new Bfid().toString());
  }
  
  public void initialGetRequest( StorageSessionable session ,
                                 BitfileRequest bfreq ) {
    ooHelper o   = (ooHelper) session;
    String bfid   = bfreq.getBfid();
    String sgroup, volume, error, location;
    ooBfid bf = null;
    ooVolume vol = null;
    ooSGroup sg = null;
    long size;

    o._session.join();
    
    o._session.begin();
    try {
      bf = (ooBfid) o._ts.get(bfid);
      if (bf != null) {
        bf.fetch();
        vol = bf.getVolume();
        vol.fetch();
        sg = bf.getSGroup();
        sg.fetch();
        volume = vol._volName;
        sgroup = sg._sgName;
        location = bf._location;
        size = bf._size;
      } else {   // bitfile not found
        esay(error = "bitfile not found (" + bfid + ")");
        bfreq.setReturnValue(14, error);
        o._session.abort();
        return;
      }
    } /* catch (ObjyException oe) {
      LogDB.Log(_log, oe.errors());
      o._session.abort();
      bfreq.setReturnValue(12, "Objectivity DB error");
      return;
    }*/ catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      bfreq.setReturnValue(13, "Objectivity DB runtime error");
      return;
    }
    o._session.commit();
    
    // fill request structure
    bfreq.setFileSize(size);
    bfreq.setStorageGroup(sgroup);
    bfreq.setFilePosition(location);
    bfreq.setVolume(volume);
    
  }
  
  public void initialRemoveRequest( StorageSessionable session ,
                                    BitfileRequest bfreq ) {
  }
  
  public void finalPutRequest( StorageSessionable session ,
                               BitfileRequest bfreq ) {
    
    if (bfreq.getReturnCode() != 0)  // easy going
      return;
                                   
    ooHelper o   = (ooHelper) session;
    String group  = bfreq.getStorageGroup();
    String volume = bfreq.getVolume();
    String bfid   = bfreq.getBfid();
    String error = null;

    ooBfid bf = new ooBfid(bfid);
    bf._size = bfreq.getFileSize();
    bf._location = bfreq.getFilePosition();
    bf._created = new Timestamp(System.currentTimeMillis());
    bf._lastAccessed = new Timestamp(bf._created.getTime());
    bf._storingDevice = "no idea";
    bf.setStatePermanent();

    if (o._ts == null || bf._bfid == null) {
      esay("tree set is null !!!!");
      bfreq.setReturnValue(22, "corrupted DB");
      return;
    }

    o._session.join();
    o._session.begin();
    try {  
    
      o._ts.add(bf);   // add bitfile record to TreeSet collection
    
      // handle storage group
      ooSGroup sg = null;
      sg = (ooSGroup) o._sghs.get(group);
      if (sg == null) {           // create new one
        say("storage group " + group + " not exist - create new");
        sg = new ooSGroup(group);
        sg._created = new Timestamp(bf._created.getTime());
        sg._lastAccessed = new Timestamp(sg._created.getTime());
        o._sghs.add(sg);
      } else {
        sg.fetch();
      }
      // bf.attachSGroup(sg);
      sg.attachBitfile(bf);
    
      // handle volume stuff
      ooVolume v = null;
      v = (ooVolume) o._vhs.get(volume);
      if (v == null) {
        say("volume " + volume + " not exist - create new");
        v = new ooVolume(volume);
        v._created = new Timestamp(bf._created.getTime());
        v._lastAccessed = new Timestamp(v._created.getTime());
        o._vhs.add(v);
      } else {
        v.fetch();
      }
      //bf.attachVolume(v);
      v.attachBitfile(bf);
      
    /*} catch (ObjyException oe) {
      LogDB.Log(_log, oe.errors());
      o._session.abort();
      String error = "Objectivity Store DB error";
      bfreq.setReturnValue(12, error);
      return; */
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      ore.printStackTrace();
      o._session.abort();
      error = "Objectivity Store DB runtime error";
      bfreq.setReturnValue(13, error);
      return;
    } catch (Exception e) {
      esay(error = "Exception in create bitfile");
      e.printStackTrace();
      o._session.abort();
      bfreq.setReturnValue(15, error);
      return;
    }
    o._session.commit();
  }
  
  public void finalGetRequest( StorageSessionable session ,
                               BitfileRequest bfreq ) {
  }
  
  public void finalRemoveRequest( StorageSessionable session ,
                                  BitfileRequest bfreq ) {
  }
  
  public BfRecordable getBitfileRecord( StorageSessionable session ,
                                        String bfid ) {
    return(null);
  }
  
  public CookieEnumeration getBfidsByVolume( StorageSessionable session ,
                                             String volume , long cookie ) {
    return(null);
  }
  
  public CookieEnumeration getBfidsByStorageGroup(
                                 StorageSessionable session ,
                                 String storageGroup , long cookie) {
    return(null);
  }
  
  public CookieEnumeration getStorageGroups(
                                 StorageSessionable session , 
                                 long cookie ) {
    return(null);
  }
  
  public void close() {
    say("shutting down Objectivity DB");
    for(int i=0; i<numSessions; i++) {
      ooHelper o = _ssessions[i];
      o.clear();
      _ssessions[i] = null;
    }
    try {
      _connection.close();
    } catch (DatabaseClosedException e) {
      LogDB.Log(_log, e.errors());
    }
  }

}
