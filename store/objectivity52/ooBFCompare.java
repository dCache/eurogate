// class dependent compare class for Objectivity

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;


public class ooBFCompare extends ooCompare {
  public ooBFCompare() { }
  
  public int compare(Object obj1, Object obj2) {
    // String bf1 = ((ooBfid) obj1)._bfid;
    ooBfid bf1 = (ooBfid) obj1;
    String bfid;
    bf1.fetch();
    
    //System.err.println("obj1: " + obj1 + " obj2: " + obj2);
    if (obj2 instanceof ooBfid) {
      ooBfid bf2 = (ooBfid) obj2;
      bf2.fetch();
      bfid = bf2._bfid;
    } else {  // must be the bfid directly as String
      bfid = (String) obj2;
    }
//System.err.println("obj1: " + obj1 + " obj1._bfid: " + ((ooBfid) obj1)._bfid);
    return(bf1._bfid.compareTo(bfid));
  }
  
  public int hash(Object key) {
    String bf = null;
    
    if (key instanceof ooBfid) {
      return(((ooBfid) key)._bfid.hashCode());
    } else {
      return(((String) key).hashCode());
    }
  }
  
}
