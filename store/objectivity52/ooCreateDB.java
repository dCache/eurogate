// application to create and initialize all Objectivity related DB objects
// DESY -mg

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


public class ooCreateDB {

  private ooFDObj _fd;
  private ooDBObj _treeBFDB, _miscDB, _hashVDB, _hashSGDB;
  private ooContObj _compBFCont, _compVCont, _compSGCont,
                    _treeBFCont, _treeBFACont;
  private ooTreeSet _bfts;
  private ooHashSet _vhs, _sghs;
  private Session _session;
  private Connection _connection;
  
  private ooBFCompare _ooBFComp;
  private ooVCompare _ooVComp;
  private ooSGCompare _ooSGComp;

  private static final int defMaxNodesPerContainer = 3000;
  private static final int defMaxVArraysPerContainer = 1000;

  
  public ooCreateDB() { }
  
  public void doit(String baseDir, int nlimit, int vlimit) {
    String name = baseDir + "/EuroGate";
    try {
      _connection = Connection.open(name, oo.openReadWrite);
    } catch (DatabaseNotFoundException e1) {
      esay("\nFederated database \"EuroGate\" not found in " + baseDir + "\n" +
           " - use oonewfd to create federated database.");
      return;
    } catch (DatabaseOpenException e) {
      esay("\nConnection to federated database" +
           " \"EuroGate\" already open.");
      return;
    }
    _session = new Session();
    _session.begin();

    say("connection to federated DB EuroGate established");
    
    _fd = _session.getFD();
    
    try {
      _treeBFDB = _fd.newDB("BfidDB");
      _miscDB   = _fd.newDB("MiscDB");
      _hashVDB  = _fd.newDB("VolDB");
      _hashSGDB = _fd.newDB("SGroupDB");
    } catch (ObjyRuntimeException e) {
      e.reportErrors();
      _session.abort();
      return;
    }
    _session.commit();
    say("databases created");
 
    // create and bind container for Comparator classes
    _ooBFComp = new ooBFCompare();
    _ooVComp = new ooVCompare();
    _ooSGComp = new ooSGCompare();
    
    _session.begin();
    
    try {
      _compBFCont = new ooContObj();
      _miscDB.addContainer(_compBFCont, "BFCont", 1, 1, 1);
      //_compBFCont.cluster(_ooBFComp);
      _compBFCont.nameObj(_ooBFComp, "ooBFCompare");
      
      _compVCont = new ooContObj();
      _miscDB.addContainer(_compVCont, "VCont", 1, 1, 1);
      //_compVCont.cluster(_ooVComp);
      _compVCont.nameObj(_ooVComp, "ooVCompare");
      
      _compSGCont = new ooContObj();
      _miscDB.addContainer(_compSGCont, "SGCont", 1, 1, 1);
      //_compSGCont.cluster(_compSGCont);
      _compSGCont.nameObj(_ooSGComp, "ooSGCompare");
    } catch (ObjyRuntimeException e) {
      e.reportErrors();
      _session.abort();
      return;
    } /*catch (ObjectNameNotUniqueException nu) {
      nu.reportErrors();
      _session.abort();
      return;
    }*/
    _session.commit();
    say("container for comparators created");

    // create Volume DB and related stuff
    _session.begin();
    try {
      _vhs = new ooHashSet(_ooVComp, 10);
      _hashVDB.nameObj(_vhs, "VolumeHashSet");
      //_vhs.maxBucketsPerContainer(10);
      
      _sghs = new ooHashSet(_ooSGComp, 10);
      _hashSGDB.nameObj(_sghs, "SGroupHashSet");
      //_sghs.maxBucketsPerContainer(10);
    } catch (ObjyRuntimeException e) {
      e.reportErrors();
      _session.abort();
      return;
    } /*catch (ObjectNameNotUniqueException nu) {
      nu.reportErrors();
      _session.abort();
      return;
    }*/
    _session.commit();
    say("volume and storage group Hash-Sets created");
    
    // create stuff for bitfile records
    _session.begin();
    try {
      _treeBFCont = new ooContObj();
      _treeBFDB.addContainer(_treeBFCont, null, 0, 1000, 1000);
      
      _treeBFACont = new ooContObj();
      _treeBFDB.addContainer(_treeBFACont, null, 0, 1000, 1000);
      
      _bfts = new ooTreeSet(_ooBFComp, 100, _treeBFCont, _treeBFACont);
      _treeBFDB.nameObj(_bfts, "BitfileTreeSet");
      _bfts.maxNodesPerContainer(nlimit);
      _bfts.maxVArraysPerContainer(vlimit);
    } catch (ObjyRuntimeException e) {
      e.reportErrors();
      _session.abort();
      return;
    } /*catch (ObjectNameNotUniqueException nu) {
      nu.reportErrors();
      _session.abort();
      return;
    }*/
    _session.commit();
    say("bitfile Tree-Set created");
    say("all done");
  }

  private static void say(String s) { System.out.println(s); }
  private static void esay(String s) { System.err.println(s); }


  public static void main(String arg[]) {
    Args args = new Args(arg);
    String dbPath = null;
    int mNodes, mVArrays;
    
    if (args.argc() <= 0) {
      esay("usage: ooCreateDB [-maxNodesPerContainer=X] [-maxVArraysPerContainer=X] dbpath");
      return;
    }
    dbPath = args.argv(0);
    //say("dbpath set to: " + dbPath);
    String maxNodes = args.getOpt("maxNodesPerContainer");
    String maxVArrays = args.getOpt("maxVArraysPerContainer");
    //say("maxNodes: " + maxNodes + "  maxVArrays: " + maxVArrays);
    if (maxNodes != null) {
      mNodes = Integer.parseInt(maxNodes, 10);
      if (mNodes <= 0) {
        esay("illegal maxNodesPerContainer: " + mNodes);
        return;
      }
    } else {
      mNodes = defMaxNodesPerContainer;
    }
    // do it again Sam
    if (maxVArrays != null) {
      mVArrays = Integer.parseInt(maxVArrays, 10);
      if (mVArrays <= 0) {
        esay("illegal maxVArraysPerContainer: " + mVArrays);
        return;
      }
    } else {
      mVArrays = defMaxVArraysPerContainer;
    }
  
    ooCreateDB c = new ooCreateDB();
  
    c.doit(dbPath, mNodes, mVArrays);
  }
}
