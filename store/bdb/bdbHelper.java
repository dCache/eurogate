// helper class to manage Objectivity Sessions and other
// thread bounded state  DESY -mg

package eurogate.store.bdb;

import java.util.*;
import dmg.util.*;
import eurogate.misc.*;
import eurogate.vehicles.*;
import eurogate.store.*;

import com.sleepycat.db.*;

public class bdbHelper implements StorageSessionable {

  public String _dbVersion = null;

  public void clear() {
  }
    

  // create helper instance - auto-fill state
  public bdbHelper(Logable l, int dbMajor, int dbMinor) 
         throws Exception {

    _dbVersion = "Berkeley/DB version 0.1";


//     } catch (Exception e) {
//       _session.abort();
//       _session.terminate();
//       clear();
//       throw(e);
//     }

  }
  
}
