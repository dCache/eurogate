// helper class to log Objectivity Exceptions into Logable DESY -mg

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

public class LogDB {
  public static void Log(Logable l, Vector v) {
    Enumeration e = v.elements();
    
    l.elog("Objectivity Exception Caught");
    while(e.hasMoreElements()) {
      ExceptionInfo ei = (ExceptionInfo) e.nextElement();
      l.elog("Id: " + ei.getId() + " - Level: " + ei.getLevel() +
             " - Message: " + ei.getMessage());
    }
  }
}
