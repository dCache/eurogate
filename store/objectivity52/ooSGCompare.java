// class dependent compare class for Objectivity

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;


public class ooSGCompare extends ooCompare {

  public ooSGCompare() { }
  
  public int compare(Object obj1, Object obj2) {
    // String bf1 = ((ooBfid) obj1)._bfid;
    String s = null;
    
    if (obj2 instanceof ooSGroup) {
      s = ((ooSGroup) obj2)._sgName;
    } else {  // must be the bfid directly as String
      s = (String) obj2;
    }
    return(((ooSGroup) obj1)._sgName.compareTo(s));
  }
  
  public int hash(Object key) {
    String v = null;
    
    if (key instanceof ooSGroup) {
      return(((ooSGroup) key)._sgName.hashCode());
    } else {
      return(((String) key).hashCode());
    }
  }
  
}
