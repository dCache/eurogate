package eurogate.misc.users ;

public class AclPermissionException 
       extends Exception 
       implements java.io.Serializable {
       
    public AclPermissionException( String message ){
        super( message ) ;    
    }      
}
