package eurogate.misc.users ;
import java.util.* ;
public interface UserRelationable extends TopDownUserRelationable {

   public Enumeration getParentsOf( String element )
          throws NoSuchElementException ;
   public boolean     isParentOf( String element , String container ) 
          throws NoSuchElementException ;       
} 
