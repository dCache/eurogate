package  eurogate.pvl ;

import java.util.*;

import dmg.cells.nucleus.*; 
import dmg.util.*;

import eurogate.vehicles.* ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  * 
 */
public class      CommandPvl 
       extends    CellAdapter {

  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private Dictionary   _context ;
  private Args         _args ;
  private String       _pvrName = null ;
  private Hashtable    _driveHash = new Hashtable() ;
  /**
  */
  public CommandPvl( String name , String args  ) throws Exception {
       super( name , args , false ) ;
       
       _nucleus       = getNucleus() ;
       _cellName      = name ;
       _context       = getDomainContext() ;
       _args          = getArgs() ;
       
       try{
       
       }catch( Exception e ){
          //
          // something went wrong, so we start, kill and exit
          //
          start() ;
          kill() ;
             
          throw e ;
       }
       //
       // start the listener thread
       //
       
//       useInterpreter(false);
       _nucleus.setPrintoutLevel( 0xf ) ;
       
       start() ;
  }
  public void messageToForward( CellMessage msg ){
     Object req = msg.getMessageObject() ;
     if( req instanceof PvrRequest ){
        msg.setMessageObject( req.toString() ) ;
        try{
            msg.nextDestination() ;
            sendMessage( msg ) ;
        }catch( Exception e ){
            esay( "can't forward message : "+msg ) ;
        }
     }else if( req instanceof MoverRequest ){
        msg.setMessageObject( req.toString() ) ;
        try{
            msg.nextDestination() ;
            sendMessage( msg ) ;
        }catch( Exception e ){
            esay( "can't forward message : "+msg ) ;
        }
     }else{
        esay( "Unidentified message to forward : "+req.getClass().getName() ) ;
        return ;
     }
  }
  public void messageArrived( CellMessage msg ){
     Object req = msg.getMessageObject() ;
     if( req instanceof PvrRequest ){
        say( "ANSWER : "+req.toString() ) ;
     }else if( req instanceof String ){
        processRequest( msg , (String) req ) ;
     }else{
        esay( "Unidentified message arrived : "+req.getClass().getName() ) ;
        return ;
     }
  }
  private void processRequest( CellMessage msg , String req ){
     Args args = new Args( req ) ;
     if( args.argc() < 1 ){
        
     }
  }
  private class DriveFrame {
     private String _pvr  ;
     private String _drive ;
     public DriveFrame(  String drive , String pvr  ){
        _pvr = pvr ; _drive = drive ;
     }
     public String getSpecificDrive(){ return _drive ; }
     public String getPvr(){ return _pvr ; }
  }
  public String hh_mount     = "<volumeName> <genericDriveName>" ;
  public String hh_dismount  = "<volumeName> <genericDriveName>" ;
  public String hh_newdrive  = "<genericDriveName>" ;
  public String hh_terminate = "<pvrName>" ;
  
  
  public String hh_load       = "<genericDriveName>" ;
  public String hh_unload     = "<genericDriveName>" ;
  public String hh_checklabel = "<genericDriveName> <store> <cartridgeName>" ;
  public String hh_writelabel = "<genericDriveName> <store> <cartridgeName>" ;
  public String hh_set_position   = "<genericDriveName> <position>" ;
  
  public String ac_load_$_1( Args args ) throws Exception { 
       loadUnload( args , "load" ) ;
       return null ; 
  } 
  public String ac_unload_$_1( Args args ) throws Exception { 
       loadUnload( args , "unload" ) ;
       return null ; 
  } 
  public String ac_checklabel_$_3( Args args ) throws Exception { 
       doLabel( args , "checkLabel" ) ;
       return null ; 
  } 
  public String ac_writelabel_$_3( Args args ) throws Exception { 
       doLabel( args , "writeLabel" ) ;
       return null ; 
  } 
  public String ac_set_position_$_2( Args args ) throws Exception { 
     String driveName = args.argv(0) ;
     String position  = args.argv(1) ;
     DriveFrame drive = (DriveFrame)_driveHash.get( driveName ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+driveName ) ;
         
     MoverRequestImpl request = new MoverRequestImpl( "position" ) ;
     request.setPosition( position ) ;
     sendMoverRequest( driveName , request ) ;
     return null ; 
  } 
  private void doLabel( Args args , String command ) throws Exception{
        
     String driveName = args.argv(0) ;
     String store     = args.argv(1) ;
     String cartridge = args.argv(2) ;
     DriveFrame drive = (DriveFrame)_driveHash.get( driveName ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+driveName ) ;
         
     sendMoverRequest( driveName , 
                       new MoverRequestImpl(
                                command , 
                                store , 
                                cartridge ) 
                      );
   
  }
  private void loadUnload( Args args , String command ) throws Exception{
     String driveName = args.argv(0) ;
     DriveFrame drive = (DriveFrame)_driveHash.get( driveName ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+driveName ) ;
         
     sendMoverRequest( driveName , new MoverRequestImpl(command ) ) ;
  }
  private void sendMoverRequest( String driveName , MoverRequest request )
          throws Exception {
          
     CellMessage msg = getThisMessage() ;
     msg.setMessageObject( request ) ;
     msg.getDestinationPath().add( driveName ) ;  
     msg.nextDestination() ;                                  
     sendMessage( msg ) ;     
          
  }
  public String ac_ls_drives( Args args ){
      StringBuffer sb = new StringBuffer() ;
      Enumeration e = _driveHash.keys() ;
      while( e.hasMoreElements() ){
         String name = (String)e.nextElement() ;
         DriveFrame sp   = (DriveFrame)_driveHash.get( name ) ;
         sb.append( " "+name+" -> "+
                    sp.getSpecificDrive()+" ("+
                    sp.getPvr()+")\n" ) ;
      }
      return sb.toString() ;
  }
  public String ac_terminate_$_1( Args args )throws Exception{
     PvrRequest pvr = new PvrRequestImpl( "terminate" ,
                                          "" , 
                                          "" ,
                                          "" ,
                                          "" ) ;
     
     CellMessage msg = getThisMessage() ;
     msg.setMessageObject( pvr ) ;
     msg.getDestinationPath().add( args.argv(0) ) ;  
     msg.nextDestination() ;                                  
     sendMessage( msg ) ;     
          
     return null ;
  }
  public String hh_add_drive = "<genericDriveName> <specificDriveName>|null <pvr>" ;
  public String ac_add_drive_$_3( Args args )throws CommandException {
      if( args.argv(1).equals("null") ){
          _driveHash.remove( args.argv(0) ) ;
          return args.argv(0) + " removed" ;
      }else{
          _driveHash.put( args.argv(0) , new DriveFrame( args.argv(1) , args.argv(2) ) ) ;
          return args.argv(0) + " -> "+args.argv(1)+" ("+args.argv(2)+")" ;
      }
  }
  public String ac_mount_$_2( Args args )throws Exception {
     
     DriveFrame drive = (DriveFrame)_driveHash.get( args.argv(1) ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+args.argv(1) ) ;
     
     PvrRequest pvr = new PvrRequestImpl( "mount" , 
                                          drive.getPvr() ,
                                          args.argv(0) ,
                                          args.argv(1) ,
                                          drive.getSpecificDrive() ) ;
     
     CellMessage msg = getThisMessage() ;
     msg.setMessageObject( pvr ) ;
     msg.getDestinationPath().add( drive.getPvr() ) ;  
     msg.nextDestination() ;                                  
     sendMessage( msg ) ;     
          
     return null ;
  }
  public String ac_dismount_$_2(Args args )throws Exception {
     
     DriveFrame drive = (DriveFrame)_driveHash.get( args.argv(1) ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+args.argv(1) ) ;
     
     PvrRequest pvr = new PvrRequestImpl( "dismount" , 
                                          drive.getPvr() ,
                                          args.argv(0) ,
                                          args.argv(1) ,
                                          drive.getSpecificDrive() ) ;
                                          
     CellMessage msg = getThisMessage() ;
     msg.setMessageObject( pvr ) ;
     msg.getDestinationPath().add( drive.getPvr() ) ;                                    
     msg.nextDestination() ;                                  
     sendMessage( msg ) ;     
          
     return null ;
  }
  public String ac_newdrive_$_1( Args args )throws Exception {
     
     DriveFrame drive = (DriveFrame)_driveHash.get( args.argv(0) ) ;
     if( drive == null )
         throw new CommandException( "Drive not known : "+args.argv(0) ) ;
         
     PvrRequest pvr = new PvrRequestImpl( "newdrive" , 
                                          drive.getPvr() ,
                                          "" , 
                                          args.argv(0) , 
                                          drive.getSpecificDrive() ) ;
                                          
     CellMessage msg = getThisMessage() ;
     msg.setMessageObject( pvr ) ;
     msg.getDestinationPath().add( drive.getPvr() ) ;                                    
     msg.nextDestination() ;                                  
     sendMessage( msg ) ;     
          
     return null ;
  }


}
