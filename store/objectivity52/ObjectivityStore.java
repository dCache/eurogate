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

public class ObjectivityStore implements EuroStoreable {

  private static Connection _connection = null;
  private static ooHelper[] _ssessions;
  private Logable _log = null;
  
  private static final int numSessions = 1;
  private static final int _dbMajor = 0;
  private static final int _dbMinor = 1;

  public ObjectivityStore(Args arg, Dictionary dict, Logable log) throws
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
    Connection.setFileCount(64);
    _connection = Connection.open(name, oo.openReadWrite);
    //_connection.setDeploymentMode(true);
    _ssessions = new ooHelper[numSessions];
    try {
      for(int i=0; i<numSessions; i++) {
        _ssessions[i] = new ooHelper(log, _dbMajor, _dbMinor);
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
    _log.log("Store DB opened: " + _ssessions[0]._dbVersion);
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
    String sgroup, volume, error, location, parameter;
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
	parameter = bf._parameter;
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
      o._session.abort();
      bfreq.setReturnValue(13, "Objectivity DB runtime error");
      return;
    }
    o._session.commit();
    
    // fill request structure
    bfreq.setFileSize(size);
    bfreq.setStorageGroup(sgroup);
    bfreq.setFilePosition(location);
    bfreq.setVolume(volume);
    bfreq.setParameter(parameter);
    
  }
  
  public void initialRemoveRequest( StorageSessionable session ,
                                    BitfileRequest bfreq ) {
    ooHelper o   = (ooHelper) session;
    String bfid   = bfreq.getBfid();
    String sgroup, volume, error, location, parameter;
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
	parameter = bf._parameter;
      } else {   // bitfile not found
        esay(error = "bitfile not found (" + bfid + ")");
        bfreq.setReturnValue(14, error);
        o._session.abort();
        return;
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      bfreq.setReturnValue(13, "Objectivity DB runtime error");
      return;
    }
    o._session.commit();
    
    // fill request structure
    bfreq.setFileSize(size);
    bfreq.setStorageGroup(sgroup);
    bfreq.setFilePosition(location);
    bfreq.setVolume(volume);
    bfreq.setParameter(parameter);
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
    bf._parameter = bfreq.getParameter();
    bf._created = new Timestamp(System.currentTimeMillis());
    bf._lastAccessed = new Timestamp(bf._created.getTime());
    bf._storingDevice = "no idea";
    bf.setStatePermanent();

//    if (o._ts == null || bf._bfid == null) {
//      esay("tree set is null !!!!");
//      bfreq.setReturnValue(22, "corrupted DB");
//      return;
//    }

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
    // update lastAccessed timestamp for bitfile
    ooHelper o   = (ooHelper) session;
    String bfid   = bfreq.getBfid();
    String error = null;
    ooBfid bf = null;

    o._session.join();
    
    o._session.begin();    
    try {
      if ((bf = (ooBfid) o._ts.get(bfid)) != null) {
        bf.markModified();
        bf._lastAccessed = new Timestamp(System.currentTimeMillis());
        if (bfreq.getReturnCode() != 0) { // some error happens
          bf._readsFailed++;
          bf._readsFailedInRow++;
        } else {
          bf._readsDone++;
        }
      } else {   // bitfile not found
        esay(error = "bitfile not found (" + bfid + ")");
        if (bfreq.getReturnCode() == 0)
          bfreq.setReturnValue(14, error);
        o._session.abort();
        return;
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      bfreq.setReturnValue(13, "Objectivity DB runtime error");
      o._session.abort();
      return;
    }
    o._session.commit();
  }
  
  public void finalRemoveRequest( StorageSessionable session ,
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
        bf.markModified();
        //vol = bf.getVolume();
        //vol.fetch();
        //sg = bf.getSGroup();
        //sg.fetch();
        //bf.removeVRelation(vol);
        //bf.removeSGRelation(sg);
        if (o._ts.ooRemove(bf)) {
          bf.delete();
        } else {
          esay(error = "error removing bitfile " + bfid + " from Tree-Set");
          bfreq.setReturnValue(15, error);
          o._session.abort();
          return;
        }
      } else {   // bitfile not found
        esay(error = "bitfile not found (" + bfid + ")");
        bfreq.setReturnValue(14, error);
        o._session.abort();
        return;
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      bfreq.setReturnValue(13, "Objectivity DB runtime error");
      return;
    }
    o._session.commit();
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

    BfRecord( String bfid ){ _bfid = bfid ; }
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

  public BfRecordable getBitfileRecord( StorageSessionable session ,
                                        String bfid ) {
    ooHelper o   = (ooHelper) session;
    String error = null;
    ooBfid bf = null;
    ooSGroup sg = null;
    ooVolume v = null;

    o._session.join();
    
    o._session.begin();    
    try {
      if ((bf = (ooBfid) o._ts.get(bfid)) != null) {
        bf.fetch();
        BfRecord bfr = new BfRecord(bfid);
        sg = bf.getSGroup();
        sg.fetch();
        bfr._group = sg._sgName;
        bfr._size = bf._size;
        v = bf.getVolume();
        v.fetch();
        bfr._volume = v._volName;
        bfr._filePosition = bf._location;
        bfr._parameter = bf._parameter;
        bfr._lastDate = bf._lastAccessed;
        bfr._creationDate = bf._created;
        bfr._counter = (int) bf._readsDone;
        bfr._status = bf._state;
        o._session.commit();
        return(bfr);
      } else {   // bitfile not found
        esay(error = "bitfile not found (" + bfid + ")");
        o._session.abort();
        throw new IllegalArgumentException(error);
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      throw(ore);
    }
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

  public CookieEnumeration getBfidsByVolume( StorageSessionable session ,
                                             String volume , long cookie ) {
    ooHelper o   = (ooHelper) session;
    String error = null;
    ooBfid bf = null;
    ooVolume v = null;
    Vector ve = null;
    com.objy.db.app.Iterator itr = null;
    
    o._session.join();
    
    o._session.begin();
    try {
      if ((v = (ooVolume) o._vhs.get(volume)) != null) {
        v.fetch();
        itr = v.getBitfiles();
	ve = new Vector(100, 100);
        while(itr.hasNext()) {
          bf = (ooBfid) itr.next();
          bf.fetch();
          ve.addElement(bf._bfid);
        }
        o._session.commit();
        return(new ListCookieEnumeration(ve.toArray(), 0));
      } else { // volume not found
        error = "volume " + volume + " not found";
        esay(error);
        o._session.abort();
        throw new IllegalArgumentException(error);
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      throw(ore);
    }
  }
  
  public CookieEnumeration getBfidsByStorageGroup(
                                 StorageSessionable session ,
                                 String storageGroup , long cookie) {
    ooHelper o   = (ooHelper) session;
    String error = null;
    ooBfid bf = null;
    ooSGroup sg = null;
    Vector ve = null;
    com.objy.db.app.Iterator itr = null;
    
    o._session.join();
    
    o._session.begin();
    try {
      if ((sg = (ooSGroup) o._sghs.get(storageGroup)) != null) {
        sg.fetch();
        itr = sg.getBitfiles();
	ve = new Vector(100, 100);
        while(itr.hasNext()) {
          bf = (ooBfid) itr.next();
          bf.fetch();
          ve.addElement(bf._bfid);
        }
        o._session.commit();
        return(new ListCookieEnumeration(ve.toArray(), 0));
      } else { // storage group not found
        error = "storage group " + storageGroup + " not found";
        esay(error);
        o._session.abort();
        throw new IllegalArgumentException(error);
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      throw(ore);
    }
  }
  
  public CookieEnumeration getStorageGroups(
                                 StorageSessionable session , 
                                 long cookie ) {
    ooHelper o   = (ooHelper) session;
    String error = null;
    Vector ve = null;
    ooCollectionIterator itr = null;
    ooSGroup sg = null;
    
    o._session.join();
    
    o._session.begin();
    try {
      if ((itr = (ooCollectionIterator) o._sghs.iterator()) != null) {
        ve = new Vector(100, 100);
        while(itr.hasNext()) {
          sg = (ooSGroup) itr.next();
          sg.fetch();
          ve.addElement(sg._sgName);
        }
	o._session.commit();
        return(new ListCookieEnumeration(ve.toArray(), 0));
      } else {
        o._session.abort();
        esay(error = "empty Hash-Set for storage groups");
        throw new IllegalArgumentException(error);
      }
    } catch (ObjyRuntimeException ore) {
      LogDB.Log(_log, ore.errors());
      o._session.abort();
      throw(ore);
    }
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
