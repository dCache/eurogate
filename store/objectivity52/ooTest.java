// test app for the EuroStore Objectivity Store

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;
import java.sql.Timestamp;
import dmg.util.*;
import eurogate.misc.*;
import eurogate.vehicles.*;
import eurogate.store.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;

public class ooTest implements Logable {

  private ObjectivityStore _os = null;
  private BitfileRequest _bfr = null;
  private RequestImpl _r = null;
  private StorageSessionable _ses[] = null;
  
  public ooTest() {
    Args arg = new Args("-dbpath=/export/home/martin/es-work/eurogate/store/objectivity52");
    try {
      _os = new ObjectivityStore(arg, null, this);
      _ses = _os.getStorageSessionable();
    } catch (Exception e) {
      elog("Exception caught");
      e.printStackTrace();
    }
  }
  
  public void close() {
    _os.close();
  }

  public void printReq(RequestImpl r) {
    log("BFID:      " + r.getBfid());
    log("PARAMETER: " + r.getParameter());
    log("SIZE:      " + r.getFileSize());
    log("POSITION:  " + r.getPosition());
    log("S-GROUP:   " + r.getStorageGroup());
    log("VOLUME:    " + r.getVolume());
  }


  public void doPut(String sgroup, String vol, long size) {
    RequestImpl r = new RequestImpl("Test", "PUT");
    r.setStorageGroup(sgroup);
    r.setFileSize(size);
    r.setParameter("dummyParameter");

    _os.initialPutRequest(_ses[0], r);
    r.setVolume(vol);
    r.setPosition("0x0:0x560", "eor");
    
    printReq(r);

    _os.finalPutRequest(_ses[0], r);

    log("PUT done bfid: " + r.getBfid());
  }

  public void doGet(String bfid) {
    RequestImpl r = new RequestImpl("Test", "GET");

    r.setBfid(bfid);

    _os.initialGetRequest(_ses[0], r);

    printReq(r);

    _os.finalGetRequest(_ses[0], r);

    log("GET done");
  }

  public void doRemove(String bfid) {
    RequestImpl r = new RequestImpl("Test", "REMOVE");

    r.setBfid(bfid);

    _os.initialRemoveRequest(_ses[0], r);

    printReq(r);

    _os.finalRemoveRequest(_ses[0], r);

    log("REMOVE done");
  }

  public void doShow(String bfid) {

    BfRecordable bfr = null;
    try {
      bfr = _os.getBitfileRecord(_ses[0], bfid);
    } catch (Exception e) {
      log(e.toString());
      return;
    }

    log("BFID:        " + bfr.getBfid());
    log("PARAMETER:   " + bfr.getParameter());
    log("SIZE:        " + bfr.getFileSize());
    log("POSITION:    " + bfr.getFilePosition());
    log("S-GROUP:     " + bfr.getStorageGroup());
    log("VOLUME:      " + bfr.getVolume());
    log("ACCESSCOUNT: " + bfr.getAccessCounter());
    log("STATUS:      " + bfr.getStatus());

    Timestamp t = (Timestamp) bfr.getCreationDate();
    log("CREATED:     " + t.toString());

    t = (Timestamp) bfr.getLastAccessDate();
    log("LAST-ACCESS: " + t.toString());
    
  }


  public void doListVol(String vol, boolean lListing) {
    CookieEnumeration ce = null;

    try {
      ce = _os.getBfidsByVolume(_ses[0], vol, 0);
    } catch (Exception e) {
      log(e.toString());
      return;
    }

    while(ce.hasMoreElements()) {
      String bfid = (String) ce.nextElement();
      if (lListing)
	doShow(bfid);
      else
	log("BFID: " + bfid);
    }
  }

  public void doListGroup(String group, boolean lListing) {
    CookieEnumeration ce = null;

    try {
      ce = _os.getBfidsByStorageGroup(_ses[0], group, 0);
    } catch (Exception e) {
      log(e.toString());
      return;
    }

    while(ce.hasMoreElements()) {
      String bfid = (String) ce.nextElement();
      if (lListing)
	doShow(bfid);
      else
	log("BFID: " + bfid);
    }
  }

  public void doListGroups() {
    CookieEnumeration ce = null;

    try {
      ce = _os.getStorageGroups(_ses[0], 0);
    } catch (Exception e) {
      log(e.toString());
      return;
    }
    while(ce.hasMoreElements())
      log("Storage-Group: " + (String) ce.nextElement());
  }


  // to implement Logable interface
  public void log(String s) { System.out.println(s); }
  public void elog(String s) { System.out.println(s); }
  public void plog(String s) { elog(s); }
  
  public String getString(String prompt) {
        String  line = "";

        System.out.print(prompt);
        BufferedReader in = new
                    BufferedReader(new InputStreamReader(System.in));
        try { line = in.readLine(); }
        catch(java.io.IOException e) {
            System.out.println("\nError reading input, exiting...");
            return null;
        }
        if (line == null)
            return null;
        else if (line.length() == 0)
            return null;

        return line;
  }


public static void main(String[] args) {
  String cmd = null;
  ooTest ot = new ooTest();

  while((cmd = ot.getString("giveit: ")) != null) {
    if (cmd.length() == 0)
      break;
    StringTokenizer st = new StringTokenizer(cmd, " ");
    if (st.countTokens() == 0)
      continue;
    String tok = st.nextToken();
    if (tok.compareTo("put") == 0) {
      ot.log("PUT");
      if (st.countTokens() != 3) {
	ot.log("usage: put <group> <volume> <size>");
	continue;
      }
      String group = st.nextToken();
      String vol = st.nextToken();
      long size = Long.parseLong(st.nextToken());
      ot.doPut(group, vol, size);
    } else if (tok.compareTo("get") == 0) {
      ot.log("GET");
      if (st.countTokens() != 1) {
	ot.log("usage: get <bfid>");
	continue;
      }
      String bfid = st.nextToken();
      ot.doGet(bfid);
    } else if (tok.compareTo("remove") == 0) {
      ot.log("REMOVE");
      if (st.countTokens() != 1) {
        ot.log("usage: remove <bfid>");
        continue;
      }
      String bfid = st.nextToken();
      ot.doRemove(bfid);
    } else if (tok.compareTo("show") == 0) {
      ot.log("SHOW");
      if (st.countTokens() != 1) {
	ot.log("usage: show <bfid>");
	continue;
      }
      String bfid = st.nextToken();
      ot.doShow(bfid);
    } else if (tok.compareTo("listvol") == 0) {
      ot.log("LISTVOL");
      if (st.countTokens() < 1) {
	ot.log("usage: listvol <volume> [<l>]");
	continue;
      }
      String vol = st.nextToken();
      ot.doListVol(vol, st.countTokens() > 0);
    } else if (tok.compareTo("listgroup") == 0) {
      ot.log("LISTGROUP");
      if (st.countTokens() < 1) {
        ot.log("usage: listgroup <storagegroup> [<l>]");
        continue;
      }
      String vol = st.nextToken();
      ot.doListGroup(vol, st.countTokens() > 0);
    } else if (tok.compareTo("listgroups") == 0) {
      ot.log("LISTGROUPS");
      ot.doListGroups();
    } else if (tok.compareTo("quit") == 0) {
      ot.log("bye bye...");
      break;
    }
  }
  ot.close();
}

}
