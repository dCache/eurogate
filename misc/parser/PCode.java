package eurogate.misc.parser ;

import java.util.* ;

public class PCode extends Vector {
    public PTokenizable getTokenizer(){
        return new PTokenizable(){
            private Enumeration _e = elements() ;
            public PObject nextToken(){          
                return _e.hasMoreElements() ? 
                       (PObject)_e.nextElement() : 
                       null ;
            }
        } ;
    }

    
}
