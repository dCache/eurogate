package eurogate.misc.users ;
import java.util.* ;
public interface AcDictionary {

    public Enumeration getPrincipals() ;
    public boolean getPermission(String prinicalName ) 
           throws NoSuchElementException ;
    public boolean isResolved() ;
    public String getInheritance() ;

}
