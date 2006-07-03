package  eurogate.pvr ;

import   eurogate.db.pvr.* ;

import java.util.*;
import java.io.* ;
import java.lang.reflect.* ;
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
   private int         _availableArms     = 0 ;
   private int         _maxAvailableArms  = 0 ;
   private Map         _requestMap        = new HashMap() ;
   private Map         _cartridgeLocation = new HashMap() ;
   private Map         _driveLocation     = new HashMap() ;
   private boolean     _isNewDatabase     = false ;
   private EasyStackable _stackable       = null ;
   private Thread        _scheduler       = null ;
   
   private static int    __requestId = 0 ;
   private static Object __idLock    = new Object() ;
   
   private class Request {
       private CellMessage message = null ;
       private PvrRequest  request = null ;
       private String      driveLocation     = null ;
       private String      cartridgeLocation = null ;
       private String      _command = null ;
       private int         _requestId = 0 ;
       private long        _started   = 0 ;
       private Thread      _worker    = null  ;
       
       private Request( CellMessage msg , PvrRequest request ){
          this.message  = msg ;
          this.request  = request ;
          this._command = request.getActionCommand() ;
          this._started = System.currentTimeMillis() ;
          synchronized( __idLock ){ _requestId = __requestId ++ ;}
       }
       public String toString(){
           return "["+_requestId+"] "+request.toString() ;
       }
       public String getId(){ return ""+_requestId ; }
   }
   public StackerPvrV0( String name , String args ) throws Exception {
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          
          if( _args.argc() < 1 ) 
              throw new
              IllegalArgumentException( "Usage : ... <pvrDbName> [-stackerDriver=<className>]" ) ;
              
          _dbName = _args.argv(0) ;
                          
          try{
             _pvrDb = new PvrDb( new File( _dbName ) , false ) ;
             _isNewDatabase = false ;
          }catch( Exception ee ){
             _pvrDb = new PvrDb( new File( _dbName ) , true ) ;
             _isNewDatabase = true ;
          }
          say( "Database ok at "+_dbName ) ;
          
          String stackableName = _args.getOpt("stackerDriver") ;
          if( ( stackableName != null ) && ( ! stackableName.equals("") ) ){
              loadStackerDriver( stackableName ) ;
              say( "Stacker Drive "+stackableName+" succesfully loaded");
          }
              
              
          if( _stackable != null ){
             _scheduler = _nucleus.newThread( new Scheduler() , "scheduler" ) ;
             _scheduler.start() ;
          }
          
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
  private class Worker implements Runnable {
     private Request _request = null ;
     private Worker( Request request ){
        _request = request ;
     }
     public void run(){
        int errorCode = 0 ;
        String errorMessage = null ;
        try{
           say("Starting worker for : "+_request);
           processRequest( _request ) ;
        }catch(InterruptedException ee ){
           errorCode    = 1 ;
           errorMessage = "Interrupted" ;
        }catch(EurogatePvrException ee ){
           errorCode    = ee.getErrorCode() ;
           errorMessage = ee.getMessage() ;
        }catch(Throwable te ){
           errorMessage = te.toString() ;
           errorCode    = 666 ;
        }
        say("Worker done for "+_request.getId()+" ["+errorCode+"] "+(errorMessage==null?"":errorMessage));
        synchronized( _requestMap ){
           _requestMap.remove( _request.getId() ) ;
           commitRequest( _request , errorCode , errorMessage ) ;
           _activeArms -- ;
           _requestMap.notifyAll();
        }
     }
  }
  private void processRequest( Request _request ) throws InterruptedException , EurogatePvrException {
  
      String command           = _request.request.getActionCommand() ;
      String cartridgeName     = _request.request.getCartridge() ;
      String cartridgeLocation = _request.cartridgeLocation ;
      String driveName         = _request.request.getGenericDrive() ;
      String driveLocation     = _request.driveLocation ;
      
      if( command.equals("mount") ){
         _stackable.mount( driveName , driveLocation , cartridgeName , cartridgeLocation ) ;
      }else if( command.equals("dismount") ){
         _stackable.dismount( driveName , driveLocation , cartridgeName , cartridgeLocation ) ;
      }
  }
  private class Scheduler implements Runnable {
  
     public void run(){
         say("Scheduler start delayed ..." );
         try{
            Thread.sleep(5000L) ;
         }catch(InterruptedException ie ){
            esay("Scheduler thread interrupted prematurely");
            return;
         }
         say("Scheduler finally started");
         try{
             runScheduler() ;
         }catch(Exception ee ){
             esay("Scheduler got an exception and terminated : "+ee);
             esay(ee);
         }finally{
             say("Scheduler finished");
         }
     }
     
  }
  private void runScheduler() throws Exception {
     synchronized( _requestMap ){
     
        ArrayList pendingRequests = new ArrayList() ;
        Request   request = null ;
        while( ! Thread.interrupted() ){
           
           
            say( "Scheduler : pending : "+pendingRequests.size()+
                 " ; arms = "+_availableArms+
                 " ; active arms "+_activeArms ) ;

            //
            // if there is nothing to do we go to sleep.
            //
            if( ( pendingRequests.size() == 0   ) ||
                ( _activeArms >= _availableArms )    )_requestMap.wait();            
            //
            // still no arms available ?
            //
            if( _activeArms >= _availableArms ){
               say("Scheduler : No arms available" ) ;
               continue ;
            }
            //
            // count current requests and select the best
            //
            pendingRequests.clear();
            
            for( Iterator i = _requestMap.values().iterator() ; i.hasNext() ; ){
                request = (Request)i.next() ;
                if( request._worker != null )continue ; 
                pendingRequests.add(request);
            }
            //
            // no requests ?
            //
            if( pendingRequests.size() == 0 ){
               say("Scheduler : no requests pending");
               continue ;
            }
            say("Scheduler : chosing 1 out of "+pendingRequests.size() );
            //
            // find pending 'dismounts' first
            //
            int j = 0 , n = pendingRequests.size() ;
            for(  ; j < n ; j++ ){
               request = (Request)pendingRequests.get(j);
               if( request.request.getActionCommand().equals("dismount") )break ;
            }
            if( j < n ){
               process( (Request)pendingRequests.remove(j) ) ;
               continue ;
            }
            //
            // no dismount found, just take the first one in the row.
            //
            process( (Request)pendingRequests.remove(0) ) ;
            
        }
     }
  }
  private void process( Request request ){
     say("Scheduler processing request : "+request ) ;
     request._worker = _nucleus.newThread(   new Worker(request) , "Worker-"+request.getId()  ) ;
     _activeArms ++ ;
     request._worker.start() ;
  }
  private Class [] _callClasses = {
      java.lang.String.class ,
      dmg.cells.nucleus.CellAdapter.class 
  } ;
  private Class [] _callClasses2 = {
      java.lang.String.class ,
  } ;
  private void loadStackerDriver( String className ) throws Exception {
      Class sc = Class.forName( className ) ;
      Object stacker = null ;
      try{
          stacker =
          sc.getConstructor( _callClasses ).newInstance( 
              new Object[]{  this.getCellName() , this  }
          ) ;
      }catch(Exception eee ){
          try{
              stacker =
              sc.getConstructor( _callClasses ).newInstance( 
                  new Object[]{  this.getCellName() }
              ) ;        
          }catch(Exception ee ){
              stacker = sc.newInstance();
          }
      }
      if( ! ( stacker instanceof EasyStackable ) )
         throw new
         IllegalArgumentException(className+" is not eurogate.pvr.EasyStackable" ) ;
         
     _stackable = (EasyStackable)stacker ;
     _maxAvailableArms = _availableArms = _stackable.getNumberOfArms() ;
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
    String  error         = null ;

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
       synchronized( _requestMap ){
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
       request.driveLocation    = drive.getLocation() ;
       drive.close( CdbLockable.COMMIT );   
       PvrCartridgeHandle cartridge = null ;
       try{
          cartridge = _pvrDb.getCartridgeByName( newCartridge ) ;
       }catch(Exception ee ){
          throw new Exception("Cartridge not found : "+newCartridge ) ;
       }
       cartridge.open(CdbLockable.WRITE) ;
       try{
          request.cartridgeLocation = cartridge.getLocation() ;
       }finally{
          cartridge.close( CdbLockable.COMMIT ) ;
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
            //requestCountChanged() ;
        }
    }catch(Exception ee ){
       esay( error = ee.getMessage() ) ;
       newRequest.setReturnValue(  39 ,  error ) ;
       sendBack( request ) ;
    }
  }
  private void commitRequest( Request request , int errorCode , String errorMessage ) {
  
     PvrRequest pvr = request.request ;
     String action  = pvr.getActionCommand() ;
     
     if( errorCode != 0 ){
        pvr.setReturnValue( errorCode , errorMessage ) ;
        sendBack( request ) ;
        return ;
     }
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
        pvr.setReturnValue( errorCode , errorMessage ) ;
     }catch(Exception iie){
        String error = "Error in commit request : "+iie.getMessage() ;
        esay( error ) ;
        pvr.setReturnValue(  40 ,  error ) ;
     }
     sendBack( request ) ;     
  }
  public void getInfo( PrintWriter pw ){
     pw.println( "          Database : "+_dbName ) ;
     pw.println( "Max Number of Arms : "+_maxAvailableArms ) ;
     pw.println( "    Number of Arms : "+_availableArms ) ;
     pw.println( "       Active Arms : "+_activeArms ) ;
     if( _stackable == null ){
        pw.println( "    Stacker Driver : None" ) ;
     }else{
        pw.println( "Stacker Driver");
        pw.println( "  Driver  : "+_stackable.getClass().getName() ) ;
        pw.println( "  Comment : "+_stackable.toString() ) ;
        pw.println( "  Details") ;
        _stackable.getInfo(pw);
     }
  }
  public String hh_x_ls_queue = "" ;
  public Object ac_x_ls_queue(Args args ){
     List list = new ArrayList() ;
     synchronized( _requestMap ){
        
        for( Iterator i = _requestMap.values().iterator() ; i.hasNext() ; ){
           Request request = (Request)i.next() ;
           PvrRequest pvr  = request.request ;
           
           Object [] reply = new Object[5] ;
           reply[0] = ""+request._requestId ;
           reply[1] = pvr.getActionCommand() ;
           reply[2] = pvr.getSpecificDrive() ;
           reply[3] = pvr.getCartridge() ;
           reply[4] = new Long(request._started) ;
           list.add( reply ) ;
        }
     }
     return list ;
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
     if( _stackable != null )
        throw new
        Exception("Not a manual library") ;
        
     synchronized( _requestMap ){
        Request request = (Request)_requestMap.remove(requestId) ;
        if( request == null )
          throw new
          IllegalArgumentException("RequestId not found : "+requestId);
       
        if( args.getOpt("failed") != null ){
           request.request.setReturnValue( 41 , "Operator Intervention" ) ;
        }else{ 
           commitRequest( request , 0 , null  ) ;
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
  public String hh_set_available_arms = "<numberOfArms>" ;
  public String ac_set_available_arms_$_1( Args args ){
     int arms = Integer.parseInt( args.argv(0) ) ;
     if( ( arms < 0 ) || ( arms > _maxAvailableArms ) )
       throw new
       IllegalArgumentException("0 <= <arms> <= "+_maxAvailableArms ) ;
       
     synchronized( _requestMap ){
         _availableArms = arms ;
         _requestMap.notifyAll() ;
     }
     return "" ;
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
