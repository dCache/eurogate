package  eurogate.vehicles ;

import java.io.* ;


public interface EurogateRequest extends Serializable {


    /**
      *   The type is the I/O direction, which can
      *   be put, get or remove.
      *
      */
    public String getType() ;
    public void   setType( String requestType ) ;
    /**
      *   The action command depends on the specific
      *   message target.
      *
      * <table border=1>
      * <tr><th>Target</th><th>Actions</th></tr>
      * <tr><td>Pvr</td><td>mount, dismount, newDrive</td></tr>
      * <tr><td>Mover</td><td>checkLabel, writeLabel, load, unload, i/o</td><tr>
      * </table>
      */
    public String getActionCommand() ;
    public void   setActionCommand( String command ) ;
    public String getReturnMessage() ;
    public int    getReturnCode() ;
    public void   setReturnValue( int retCode , String retMessage ) ;
    public int    getSerialId() ;

}
