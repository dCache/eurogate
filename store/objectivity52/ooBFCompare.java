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
    String bf2 = null;
    
    if (obj2 instanceof ooBfid) {
      bf2 = ((ooBfid) obj2)._bfid;
    } else {  // must be the bfid directly as String
      bf2 = (String) obj2;
    }
    return(((ooBfid) obj1)._bfid.compareTo(bf2));
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
