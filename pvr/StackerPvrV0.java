package  eurogate.pvr ;

import   eurogate.db.pvr.* ;

import java.util.*;
import java.io.* ;

import dmg.cells.nucleus.*; 
import dmg.util.*;
import dmg.util.cdb.* ;

import eurogate.vehicles.* ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 20 June 2007
  * 
 */
 
public class      StackerPvrV0 
       extends    CellAdapter   {

   private Args        _args    = null ;
   private CellNucleus _nucleus = null ;
   private String      _dbName  = null ;
   private PvrDb       _pvrDb   = null ;
   private int         _arms    = 0 ;
   private int         _activeArms        = 0 ;
   private Map         _requestMap        = new HashMap() ;
   private Map         _cartridgeLocation = new HashMap() ;
   private Map         _driveLocation     = new HashMap() ;
   private boolean     _isNewDatabase     = false ;
   private EasyStackable _stackable       = null ;
   private static int __requestId = 0 ;
   private static Object __idLock = new Object() ;
   private class Request {
       private CellMessage message = null ;
       private PvrRequest  request = null ;
       private String      _command = null ;
       private int         _requestId = 0 ;
       private Request( CellMessage msg , PvrRequest request ){
          this.message  = msg ;
          this.request  = request ;
          this._command = request.getActionCommand() ;
          synchronized( __idLock ){ _requestId = __requestId ++ ;}
       }
       public String toString(){
           return "["+_requestId+"] "+request.toString() ;
       }
       public String getId(){ return ""+_requestId ; }
   }
   public interface EasyStackable {
       public void mount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation ) ;
       public void dismount( String driveName , String driveLocation , 
                             String cartridgeName , String cartridgeLocation ) ;
                          
   }
   private class EasyStackerAdapter implements EasyStackable {
       public void mount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation ){
                          
       }
       public void dismount( String driveName , String driveLocation , 
                             String cartridgeName , String cartridgeLocation ){
                             
       }
    
   }
   public StackerPvrV0( String name , String args ) throws Exception {
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          
          if( _args.argc() < 1 ) 
              throw new
              IllegalArgumentException( "Usage : ... <pvrDbName>" ) ;
              
          _dbName = _args.argv(0) ;
                          
          try{
             _pvrDb = new PvrDb( new File( _dbName ) , false ) ;
             _isNewDatabase = false ;
          }catch( Exception ee ){
             _pvrDb = new PvrDb( new File( _dbName ) , true ) ;
             _isNewDatabase = true ;
          }
          say( "Database ok." ) ;
       }catch( Exception e ){
          esay(e);
          start() ;
          kill() ;
          throw e ;
       }
       start() ;
       if( ! _isNewDatabase )return ;
       String auto = null ;
       try{
           auto = _args.getOpt("autoinstall") ;
           if( ( auto != null ) && ( auto.length() > 0 ) ){
               say( "Running autoinstall : "+auto ) ;
               executeDomainContext( auto ) ;
           }
       }catch( Exception ee ){
           esay( "Problem executing : "+auto+" : "+ee ) ;
       }
   }
  public void messageArrived( CellMessage msg ){
  
      Object req = msg.getMessageObject() ;
      say("Message arrived : "+req.getClass().getName());
      if( req instanceof EurogateRequest ){
      
         EurogateRequest euro = (EurogateRequest) req ;
         
         if( euro instanceof PvrRequest ){
            Request request = new Request( msg , (PvrRequest) euro)  ;
            try{
               pvrRequest( request ) ;
            }catch(Throwable t ){
               String error = "Problem in processing request : "+t ;
               esay( error ) ;
               esay(t);
               euro.setReturnValue( 24 , error );
               sendBack( request ) ;
            }
            
         }else{
             euro.setReturnValue( 23 , "Pvr can't handle this request" ) ;
             sendBack( msg ) ;
         }
      }else{
         esay("Unexpected request arrived : "+req.getClass().getName());
      }
  }
  private void pvrRequest( Request request ){
    //
    // is there already a similiar request.
    //
    PvrRequest newRequest = request.request ;
    String  actionCommand = newRequest.getActionCommand() ;
    String  newDriveName  = newRequest.getGenericDrive() ;
    String  newCartridge  = newRequest.getCartridge() ;
    String error = null ;
    
    try{
    
       if( actionCommand.equals("newdrive") ){
       
            PvrDriveHandle drive = _pvrDb.getDriveByName( newRequest.getSpecificDrive() ) ;
            drive.open( CdbLockable.READ ) ;
            String cartridge  =  drive.getCartridge()  ;
            drive.close( CdbLockable.COMMIT ) ;
            newRequest.setCartridge( cartridge  ) ;
            newRequest.setReturnValue(
                 cartridge.equals("empty")?1:3,
                 cartridge
            ) ;
            sendBack( request ) ;
            return  ;
       }
       say("Checking request : "+request) ;
       for( Iterator i = _requestMap.values().iterator() ; i.hasNext() ; ){

          Request    cursor        = (Request)i.next() ;
          PvrRequest requestCursor = cursor.request ;

          if( requestCursor.getActionCommand().equals( actionCommand ) ){
             if( ( actionCommand.equals("mount") &&
                   ( requestCursor.getCartridge().equals( newCartridge) ||
                     requestCursor.getGenericDrive().equals( newDriveName ) )
                 )||
                 ( actionCommand.equals("dismount") &&
                   requestCursor.getGenericDrive().equals( newDriveName ) ) 
                ){

                //
                // this is not correct
                //   
                throw new Exception(  "Similiar request already in queue" ) ;
             }   
          }
       }
       say("No similiar request found for : "+request);
       //
       // check if the request conflicts with the current drive situation.
       //
       //
       // check if drive and cartridge exists.
       //
       PvrDriveHandle drive = null ;
       try{
          drive = _pvrDb.getDriveByName( newDriveName ) ;
       }catch(Exception ee ){
          throw new Exception("Drive not found : "+newDriveName ) ;
       }
       drive.open(CdbLockable.WRITE) ;
       String cartridgeInDrive = drive.getCartridge() ;
       drive.close( CdbLockable.COMMIT );   
       PvrCartridgeHandle cartridge = null ;
       try{
          cartridge = _pvrDb.getCartridgeByName( newCartridge ) ;
       }catch(Exception ee ){
          throw new Exception("Cartridge not found : "+newCartridge ) ;
       }
       say("Drive and cartridge exists for : "+request);
       //
       // check drive to mount is empty
       //
       if( actionCommand.equals("mount" ) && 
           ! cartridgeInDrive.equals("empty") )
           throw new
           Exception("Drive not empty (Still "+cartridgeInDrive+" loaded");
        //
        // check if this cartridge is not already mounted.
        //
        say("Requested cartridge is empty for : "+request);
        if( actionCommand.equals("mount") ){
           String [] drives = _pvrDb.getDriveNames() ;
           for( int i = 0 ; i < drives.length ; i++ ){
              drive = _pvrDb.getDriveByName( drives[i] ) ;          
              drive.open(CdbLockable.WRITE) ;
              cartridgeInDrive = drive.getCartridge() ;
              drive.close( CdbLockable.COMMIT );   
              if( cartridgeInDrive.equals(newCartridge) )
                 throw new
                 Exception("Cartridge already loaded in "+drive.getName() ) ;
           }
           //
           // dismount of a dismounted drive is always ok.
           //
        }else if( actionCommand.equals("dismount") && cartridgeInDrive.equals("empty") ){
           sendBack( request ) ;
           return ;
        }
        say("Requested Drive is ok for : "+request);
        say("Adding to action list : "+request);
        synchronized( _requestMap ){
            _requestMap.put( request.getId() , request ) ;
            _requestMap.notifyAll() ;
            requestCountChanged() ;
        }
    }catch(Exception ee ){
       esay( error = ee.getMessage() ) ;
       newRequest.setReturnValue(  39 ,  error ) ;
       sendBack( request ) ;
    }
  }
  private void requestCountChanged(){
 
     for( Iterator i = _requestMap.values().iterator() ; 
          ( _activeArms < _arms ) && i.hasNext() ;       ){
           Request request = (Request)i.next() ;
           if( request.request.getActionCommand().equals("dismount") ){
               _activeArms ++ ;
               new StackerHandler( request ) ;    
           }          
     }
     for( Iterator i = _requestMap.values().iterator() ; 
          ( _activeArms < _arms ) && i.hasNext() ;       ){
           Request request = (Request)i.next() ;
           _activeArms ++ ;
           new StackerHandler( request ) ;    
     }
  }
  private class StackerHandler implements Runnable {
      private Request _request = null ;
      private StackerHandler( Request request ){
         _request = request ;
         _nucleus.newThread( this , "STACKER-"+request.getId() ).start() ;
      }
      public void run(){
          //
          //
          try{ 
          
             Thread.sleep(10000L) ;
             //
             // do mount/dismount here
             //
          }catch(Exception ee){
          
          }finally{
             synchronized( _requestMap ){
                 _activeArms -- ;
                 commitRequest( _request ) ;
             }
          }
      }
  } 
  private void commitRequest( Request request ) {
  
     PvrRequest pvr = request.request ;
     String action = pvr.getActionCommand() ;
     try{
        PvrDriveHandle drive = _pvrDb.getDriveByName( pvr.getSpecificDrive() ) ;
        drive.open( CdbLockable.WRITE ) ;
        try{
           if( action.equals("mount") ){
              drive.setCartridge( pvr.getCartridge() ) ;
           }else if( action.equals("dismount") ){
              drive.setCartridge( "empty" ) ;        
           }
        }finally{
            drive.close( CdbLockable.COMMIT ) ;
        }
     }catch(Exception iie){
        String error = "Error in commit request : "+iie.getMessage() ;
        esay( error ) ;
        pvr.setReturnValue(  40 ,  error ) ;
     }
     sendBack( request ) ;     
  }
  public void getInfo( PrintWriter pw ){
     pw.println( "Database : "+_dbName ) ;
  }
  public String hh_ls_queue = "" ;
  public String ac_ls_queue(Args args ){
     StringBuffer sb = new StringBuffer() ;
     synchronized( _requestMap ){
        for( Iterator i = _requestMap.values().iterator() ; i.hasNext() ; ){
           sb.append( i.next().toString() ).append("\n");
        }
     }
     return sb.toString() ;
  }
  public String hh_do = "<requestId> [-failed]" ;
  public String ac_do_$_1( Args args ) throws Exception {
     String requestId = args.argv(0) ;
     synchronized( _requestMap ){
        Request request = (Request)_requestMap.remove(requestId) ;
        if( request == null )
          throw new
          IllegalArgumentException("RequestId not found : "+requestId);
       
        if( args.getOpt("failed") != null ){
           request.request.setReturnValue( 41 , "Operator Intervention" ) ;
        }else{ 
           commitRequest( request ) ;
        }
        _requestMap.notifyAll();
     }
     return "";
  }
  public String hh_create_cartridge = "<cartridgeName> <location>";
  public String ac_create_cartridge_$_2( Args args ) throws Exception {
  
      String cartridgeName = args.argv(0) ;
      String location      = args.argv(1) ;
             
      PvrCartridgeHandle cartridge = _pvrDb.createCartridge( cartridgeName ) ;
      
      setObject( cartridge , _cartridgeLocation , location , "online" ) ;

      return "" ;
  }
  public String hh_create_drive = "<driveName> <location>";
  public String ac_create_drive_$_2( Args args ) throws Exception {
  
      String driveName = args.argv(0) ;
      String location  = args.argv(1) ;
             
      PvrDriveHandle drive = _pvrDb.createDrive( driveName ) ;
      
      setObject( drive , _driveLocation , location , "online" ) ;
      
      drive.open( CdbLockable.WRITE ) ;
      try{
         drive.setCartridge("empty")  ;
      }finally{
         drive.close( CdbLockable.COMMIT ) ;
      }

      return "" ;
  }
  public String hh_set_cartridge = "<cartridgeName> [-mode=online|offline] [-location=<location>]" ;
  public String ac_set_cartridge_$_1( Args args ) throws Exception {
  
      String cartridgeName = args.argv(0) ;
      PvrCartridgeHandle cartridge = _pvrDb.getCartridgeByName( cartridgeName ) ;
      
      setObject( cartridge , _cartridgeLocation , args.getOpt("location" ) , args.getOpt("mode") ) ;
      
      return "" ;
  }
  public String hh_set_drive = "<drive> [-mode=online|offline] [-location=<location>]" ;
  public String ac_set_drive_$_1( Args args ) throws Exception {
  
      String driveName = args.argv(0) ;
      PvrCartridgeHandle drive = _pvrDb.getCartridgeByName( driveName ) ;
      
      setObject( drive , _driveLocation , args.getOpt("location" ) , args.getOpt("mode") ) ;
      
      return "" ;
  }
  public void setObject( PvrObjectHandle handle , Map locationMap , String location , String mode ) 
         throws Exception {
  
      handle.open( CdbLockable.WRITE ) ;
      
      try{
      
         if( ( location != null ) && ( ! location.equals("") ) ){
         
            String objectFound = (String)locationMap.get(location) ;
            if( objectFound != null )
              throw new 
              IllegalArgumentException("Location <"+location+"> already occupied by : "+objectFound);

              handle.setLocation( location )  ;
              locationMap.put( location , handle.getName() ) ;
            
         }
 
         if( ( mode != null ) && ( ! mode.equals("") ) ){
            handle.setMode( mode )  ;
         }

      }finally{
         handle.close( CdbLockable.COMMIT ) ;
      }
  
  }
  public void getObjectString( PvrObjectHandle handle , StringBuffer sb )
        throws Exception {
         sb.append(handle.getName()).append("  ");
         handle.open( CdbLockable.WRITE ) ;
         try{
            String tmp = handle.getLocation()  ;
            if( tmp != null )sb.append(tmp).append(" ");
            tmp = handle.getMode()  ;
            if( tmp != null )sb.append(tmp).append(" ");           
         }finally{
            handle.close( CdbLockable.COMMIT ) ;
         }
         return ;
  }
  public void getDriveObjectString( PvrDriveHandle handle , StringBuffer sb )
         throws Exception {
         getObjectString( handle , sb ) ;

         handle.open( CdbLockable.WRITE ) ;
         try{
            String tmp = handle.getCartridge()  ;
            if( tmp != null )sb.append(tmp).append(" ");
         }finally{
            handle.close( CdbLockable.COMMIT ) ;
         }
         return ;
  }
  public String hh_ls_cartridge = "<cartridgeName>" ;
  public String ac_ls_cartridge_$_0_1( Args args ) throws Exception {
  
     StringBuffer sb = new StringBuffer() ;
     if( args.argc() > 0 ){
     
         String cartridgeName = args.argv(0) ;
         PvrCartridgeHandle cartridge = _pvrDb.getCartridgeByName( cartridgeName ) ;
         getObjectString( cartridge , sb )  ;
         return sb.toString();
         
     }else{
        String [] cartridges = _pvrDb.getCartridgeNames() ;
        for( int i = 0 ; i < cartridges.length ; i++ ){
           sb.append(cartridges[i]).append("  ") ;
           try{

                 PvrCartridgeHandle cartridge = _pvrDb.getCartridgeByName( cartridges[i] ) ;          
                 getObjectString( cartridge , sb ) ;
                 sb.append("\n");

           }catch(Exception ee ){
              sb.append("Problem : ").append(ee.getMessage()).append("\n");
           }
        }
        return sb.toString() ;
     }
  }
  public String hh_ls_drive = "<driveName>" ;
  public String ac_ls_drive_$_0_1( Args args ) throws Exception {
  
     StringBuffer sb = new StringBuffer() ;
     if( args.argc() > 0 ){
     
         String driveName = args.argv(0) ;
         PvrDriveHandle drive = _pvrDb.getDriveByName( driveName ) ;
         getDriveObjectString( drive , sb )  ;
         
     }else{
        String [] drives = _pvrDb.getDriveNames() ;
        for( int i = 0 ; i < drives.length ; i++ ){
           sb.append(drives[i]).append("  ") ;
           try{

                 PvrDriveHandle drive = _pvrDb.getDriveByName( drives[i] ) ;          
                 getDriveObjectString( drive , sb ) ;
                 sb.append("\n");

           }catch(Exception ee ){
              sb.append("Problem : ").append(ee.getMessage()).append("\n");
           }
        }
     }
     return sb.toString() ;
  }
  public void say( String str ){
     super.say( str ) ;
     pin( str ) ;
  }
  public void esay( String str ){
     super.esay( str ) ;
     pin( str ) ;
  }
  private void sendBack( Request request ){
     say("Returning request id "+request.getId());
     sendBack( request.message ) ;
  }
  private void sendBack( CellMessage msg ) {
     msg.revertDirection() ;
     Object returnObject = msg.getMessageObject() ;
     if( returnObject instanceof EurogateRequest ){
        EurogateRequest req = (EurogateRequest)returnObject ;
        req.setActionCommand(req.getActionCommand()+"-ready") ;
     }
     try{
        sendMessage( msg ) ;
     }catch( Exception msge ){
        esay(msge);
        esay( "PANIC : can't reply message : "+msge ) ;
     }
  }
   
   
}
