package eurogate.simulation.graphics.pvr ;

import eurogate.vehicles.* ;
import java.util.* ;
import dmg.cells.nucleus.* ;
import dmg.util.* ;

public class PvrRequestShell {

    private Args        _args ;
    private CellNucleus _nucleus ;
    private String      _user ;
    
    public PvrRequestShell( String user , CellNucleus nucleus , Args args ){
       _user    = user ;
       _nucleus = nucleus ;
       _args    = args ;
    
    }
    public void say( String s ){ _nucleus.say( "PvrRequestShell:"+s ) ; }
    public void esay( String s ){ _nucleus.esay( "PvrRequestShell:"+s ) ; }
    
    public String hh_mount = 
    "[-pvr=<pvr>] <cartridge> <genericDrive> <specificDrive>" ;
    public String ac_mount_$_3( Args args )throws Exception {
        String pvr = args.getOpt("pvr") ;
        pvr = pvr == null ? "stk" : pvr ;
        PvrRequest req = new PvrRequestImpl( 
                               "mount" , pvr ,
                                         args.argv(0),
                                         args.argv(1),
                                         args.argv(2)   ) ;
                             
       _nucleus.sendMessage( new CellMessage(
                             new CellPath( pvr ) ,
                             req ) ) ;
       return "Done" ;
    }
    public String ac_dismount_$_3( Args args )throws Exception {
        String pvr = args.getOpt("pvr") ;
        pvr = pvr == null ? "stk" : pvr ;
        PvrRequest req = new PvrRequestImpl( 
                               "dismount" , pvr ,
                                         args.argv(0),
                                         args.argv(1),
                                         args.argv(2)   ) ;
                             
       _nucleus.sendMessage( new CellMessage(
                             new CellPath( pvr ) ,
                             req ) ) ;
       return "Done" ;
    }
    public String ac_show_options( Args args ){
        Enumeration e = _args.options().keys() ;
        StringBuffer sb = new StringBuffer() ;
        while( e.hasMoreElements() ){
            String key = (String)e.nextElement() ;
            sb.append( key ).
               append(" -> ").
               append( _args.getOpt(key) ).
               append( "\n" ) ;
        }
        return sb.toString() ;
    }
    public String ac_show_me( Args args ){
       return "Done" ;
    }
}
