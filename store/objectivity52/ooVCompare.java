// class dependent compare class for Objectivity

package eurogate.store.objectivity52;

import java.util.*;
import java.io.*;

// Objectivity stuff
import com.objy.db.*;
import com.objy.db.app.*;
import com.objy.db.util.*;


public class ooVCompare extends ooCompare {

  public ooVCompare() { }
  
  public int compare(Object obj1, Object obj2) {
    // String bf1 = ((ooBfid) obj1)._bfid;
    String s = null;
    ooVolume v1 = (ooVolume) obj1;
    v1.fetch();
    
    if (obj2 instanceof ooVolume) {
      ooVolume v2 = (ooVolume) obj2;
      v2.fetch();
      s = v2._volName;
    } else {  // must be the bfid directly as String
      s = (String) obj2;
    }
    return(v1._volName.compareTo(s));
  }
  
  public int hash(Object key) {
    String v = null;
    
    if (key instanceof ooVolume) {
      return(((ooVolume) key)._volName.hashCode());
    } else {
      return(((String) key).hashCode());
    }
  }
  
}
