package eurogate.misc.users ;
import java.util.* ;
import eurogate.misc.* ;
public interface TopDownUserRelationable {

    public Enumeration getContainers() ;
    public void        createContainer( String container )
        throws DatabaseException ;
    public Enumeration getElementsOf( String container ) 
        throws NoSuchElementException ;
    public boolean     isElementOf( String container , String element )
        throws NoSuchElementException ;
    public void        addElement( String container , String element )
        throws NoSuchElementException ;
    public void     removeElement( String container , String element )
        throws NoSuchElementException ;
    public void     removeContainer( String container )
        throws NoSuchElementException ,
               DatabaseException ;   
}
