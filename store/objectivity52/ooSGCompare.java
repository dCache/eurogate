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
    String s = null;
    ooSGroup sg1 = (ooSGroup) obj1;
    sg1.fetch();
    
    if (obj2 instanceof ooSGroup) {
      ooSGroup sg2 = (ooSGroup) obj2;
      sg2.fetch();
      s = sg2._sgName;
    } else {  // must be the bfid directly as String
      s = (String) obj2;
    }
    return(sg1._sgName.compareTo(s));
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
