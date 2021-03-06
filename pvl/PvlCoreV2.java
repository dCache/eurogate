package  eurogate.pvl ;

import   eurogate.db.pvl.* ;
import   eurogate.vehicles.* ;
import   eurogate.misc.* ;
import   eurogate.misc.users.* ;
import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.util.cdb.* ;

import java.util.* ;
import java.lang.reflect.* ;
import java.io.File ;

/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 9 Feb 1999
  */
public class      PvlCoreV2
       extends    CellAdapter
       implements Runnable,
                  Logable                {
 
   private PvlDb     _pvlDb           = null ;
   private FifoY     _fifo            = new FifoY() ;
   private Thread    _schedulerThread = null ;  
   private Gate      _finishGate      = new Gate() ; 
   private Hashtable _pending         = new Hashtable() ;
   private Object    _sendLock        = new Object() ;
   
   private PvlResourceScheduler    _scheduler = null ; 
   private PvlResourceRequestQueue _queue     = new PvlResourceRequestQueue() ;
   private PvlCommanderV1          _commander = null ;
   private PvlCommander            _dbCommander = null ;
   private CellNucleus             _nucleus   = null ;
   private GateKeeper              _dbGate    = new GateKeeper() ;
   private Dictionary              _context   = null ;
   private Hashtable               _env       = new Hashtable() ;
   private Args                    _args      = null ;
   private Thread                  _timer     = null ;   
   private class ResourceTimer implements Runnable {
       public void run(){
         while(true){
           try{
              Thread.currentThread().sleep(30000) ;
              _fifo.push( new PvlResourceTimer() ) ;
           }catch(InterruptedException ee){
              break ;
           }
         }
       }
   }   
   public PvlCoreV2( String name , String args ) throws Exception{
       super( name , args , true ) ;
    
       try{
           //
           // 
           _nucleus = getNucleus() ;
           _context = getDomainContext() ;
           _args    = getArgs() ;
           
           if( _args.argc() < 1 )
              throw new
              IllegalArgumentException( "Usage : ... <schedulerClass>" ) ;
              
           _pvlDb   = (PvlDb) _context.get( "database" ) ;
           if( _pvlDb == null )
              throw new 
              IllegalArgumentException( "database not defined" ) ;
          
          
           String schedulerClassName = (String)_args.argv(0) ;
              
           _finishGate.close() ;
           
           //
           // prepare the hashtable argument for the scheduler.
           //
           _env.put( "args"    , _args ) ;
           _env.put( "context" , _context ) ;
           _env.put( "logable" , this ) ;
           
           _scheduler = initiateScheduler( schedulerClassName ) ;
           
           _schedulerThread  = _nucleus.newThread( this , "scheduler" ) ; 
           _schedulerThread.start() ;  
               
       }catch( Exception e ){
          esay( "Problem in <init> : "+e ) ;
          esay(e);
          kill() ;
          throw e ;
       }
       _commander = new PvlCommanderV1( this , _pvlDb , _fifo , _queue ) ;
       _dbCommander = new PvlCommander( _pvlDb ) ;
       
       String aclCell = _args.getOpt("acl") ;
       if( aclCell != null ){
          say( "Using Security Cell : "+aclCell ) ;
          RemotePermission rp = 
                 new  RemotePermission( this , new CellPath( aclCell ) ) ;
          _commander.setPermissionCheckable( rp ) ; 
          _dbCommander.setPermissionCheckable( rp ) ;
       }
       say( "Commander created" ) ;
       addCommandListener( _commander ) ;
       addCommandListener( _dbCommander ) ;
       say( "Clearing all drive states ( db only )" ) ;
       resetDrives() ;
       
       _timer = _nucleus.newThread( new ResourceTimer() , "timer" );
       _timer.start() ;
       setCommandExceptionEnabled( true ) ;
       say( "Started" ) ;    
   }
   ///////////////////////////////////////////////////////////
   //
   // and the cleanup
   //
   public void cleanUp(){
      //
      // timer no longer needed
      //
      say( "Stopping timer" ) ;
      try{
         _timer.interrupt() ;
      }catch( Exception ee ){
         esay( "Problem stopping timer : "+ee ) ;
      }
      //
      // we could dump the request queue here
      //
      // stop the scheduler thread
      //
      say( "Closing scheduler thread" ) ;
      _fifo.close() ;
      
      //
      // and wait for it to finish
      //
      say( "Waiting for final gate to open" ) ;
      _finishGate.check() ;
   }
   ///////////////////////////////////////////////////////////
   //
   // Logable interface implementations
   //
   public void log(String s)  { say(s) ; }
   public void elog(String s) { esay(s); }
   public void plog(String s) { plog(s); }
   public void say( String msg ){ pin( msg ) ; super.say( msg ) ; }
   public void esay( String msg ){ pin( "(E) "+msg ) ; super.say( msg ) ;}
   //
   //
   /////////////////////////////////////////////////////////////////////////////
   //
   //   try to load the scheduler
   //    first we asume that there is on with a logable argument constructor
   //    but we don't mind if not.
   //
   private Class [][] schedulerConstructorArgs = {       
       { eurogate.db.pvl.PvlDb.class  } ,
       { eurogate.db.pvl.PvlDb.class , java.util.Dictionary.class  }
   }  ;
   private PvlResourceScheduler initiateScheduler( String name )
        throws Exception {
   
        Class       schedulerClass = Class.forName( name ) ;
        Constructor con            = null ;
        Object  []  args           = null ;
        try{
           con     = schedulerClass.getConstructor( schedulerConstructorArgs[1] ) ;
           args    = new Object[2] ;
           args[0] = _pvlDb ;
           args[1] = _env ;
        }catch( Exception e ){
           con     = schedulerClass.getConstructor( schedulerConstructorArgs[0] ) ;       
           args    = new Object[1] ;
           args[0] = _pvlDb ;
        }
        
        try{
           return (PvlResourceScheduler)con.newInstance( args ) ;
        }catch( InvocationTargetException ee ){
           Throwable t = ee.getTargetException() ;
           if( t instanceof Exception ){
              throw (Exception)t ;
           }else{
              throw new Exception( "Problem : "+t ) ;
           }
            
        } 
   }
   /////////////////////////////////////////////////////////////////
   //
   //   runnabled :
   //    _shedulerThread 
   //           runs forever ( is stopped by an interrupted exception )
   //
   public void run(){
     if( Thread.currentThread() == _schedulerThread ){
     
        try{ 
           runScheduler() ;
        }catch( InterruptedException ie ){
           say( "The scheduler Thread was interrupted" ) ;
        }
        
        _finishGate.open() ;
     }
   }
   private void runScheduler() throws InterruptedException {
   
      PvlResourceModifier [] inList   = new PvlResourceModifier[1] ;      
      PvlResourceModifier [] outList  = null  ; 
      PvlResourceModifier    modifier = null ;   
      while( ! Thread.currentThread().interrupted() ){
      
         //
         // get next event
         //
         modifier = (PvlResourceModifier)_fifo.pop() ;
         //
         // get access to the database.
         //
         try{
         
             _dbGate.open(GateKeeper.LOW) ;
             
         }catch( InterruptedException ie ){
             //
             // we need to give it back ( will screw up the order )
             //
             _fifo.push( modifier ) ;
             //
             throw ie ;         
         }
         
         say( "runScheduler : processing : "+modifier ) ;
         
         try{
         
            //
            //  if the modifier is a new I/O request we
            //  have to store it in the queue.
            //  
            if( modifier instanceof PvlResourceRequest )
                _queue.addRequest( (PvlResourceRequest)modifier ) ;
            //
            //  we currently only support on modifier/event
            //
            inList[0] = modifier ;
            //
            // run the scheduler 
            //
            outList   = _scheduler.nextEvent( _queue , inList ) ;
            
         }catch( Exception e ){
            
            esay( "runScheduler : Problem in scheduler.nextEvent : "+e ) ;
            esay( e ) ;
            _dbGate.close() ;
            continue ;
         }

      
         if( outList == null ){
            say( "runScheduler : no more decisions yet" ) ;
            _dbGate.close() ;
            continue ;
         }
         for( int i = 0 ; i < outList.length ; i++ ){
         
             say( "runScheduler : scheduled : "+outList[i] ) ;
             try{
             
                processModifier( outList[i] ) ;
                
             }catch(Exception e ){
                esay( "runScheduler : Problem in processModifier : "+e ) ;
                esay( e ) ;
             }
         
         }
         _dbGate.close() ;

      
      }
      say( "Scheduler thread done" ) ;
   }
   public class PvlException extends Exception {
       private int _code = 0 ;
       public PvlException( int code , String msg ){
          super( msg ) ;
          _code = code ;
       }
       public int getReturnCode(){ return _code ; }
       public String getReturnMessage(){ return getMessage() ; }
       
   }
   //
   // r0  : pvl-request
   // r1  : username 
   // r2  : command
   // r3  : pvr
   // r4  : drive/volume
   //(r5  : new status )
   //
   private void executeOutOfBand( CellMessage msg , Object [] request ){
       if( ( request.length < 3 ) ||
           ( ! ( request[2] instanceof String) )  ){
         esay( "Illegal request in OutOfBand" ) ;
         
         return ;
      }
      String command = (String)request[2] ;
      Object answer = null ;
      try{
         if( command.equals( "get-volume-status" ) ){
            if( request.length < 5 )
            throw new Exception( "Not enough arguments" ) ;
            String pvrName    = (String)request[3] ;
            String volumeName = (String)request[4] ;
            VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
            volume.open(CdbLockable.READ) ;
               String status = volume.getStatus() ;
            volume.close(CdbLockable.COMMIT) ;
            Object [] a = new Object[6] ;
            for( int i = 0 ; i < 5 ; i++ )a[i] = request[i] ;
            a[5] = status ;
            answer = a ;
         }else if(command.equals( "set-volume-status" ) ){
            if( request.length < 6 )
            throw new Exception( "Not enough arguments" ) ;
            String pvrName    = (String)request[3] ;
            String volumeName = (String)request[4] ;
            String status     = (String)request[5] ;
            VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
            volume.open(CdbLockable.WRITE) ;
               volume.setStatus(status) ;
               status = volume.getStatus() ;
            volume.close(CdbLockable.COMMIT) ;
            request[5] = status ;
            answer = request ;
         }else if(command.equals( "get-drive-status" ) ){
            if( request.length < 5 )
            throw new Exception( "Not enough arguments" ) ;
            String pvrName = (String)request[3] ;
            String driveName = (String)request[4] ;
            PvrHandle pvr = _pvlDb.getPvrByName( pvrName ) ;
            pvr.open(CdbLockable.READ) ;
               DriveHandle drive = pvr.getDriveByName( driveName ) ;
               drive.open(CdbLockable.READ) ;
               String status = drive.getStatus() ;
               drive.close(CdbLockable.COMMIT) ;
            pvr.close(CdbLockable.COMMIT) ;
            Object [] a = new Object[6] ;
            for( int i = 0 ; i < 5 ; i++ )a[i] = request[i] ;
            a[5] = status ;
            answer = a ;
         }else if(command.equals( "set-drive-status" ) ){
            if( request.length < 6 )
            throw new Exception( "Not enough arguments" ) ;
            String pvrName   = (String)request[3] ;
            String driveName = (String)request[4] ;
            PvrHandle pvr    = _pvlDb.getPvrByName( pvrName ) ;
            String status    = (String)request[5] ;
            pvr.open(CdbLockable.WRITE) ;
               DriveHandle drive = pvr.getDriveByName( driveName ) ;
               drive.open(CdbLockable.WRITE) ;
               drive.setStatus(status) ;
                  status = drive.getStatus() ;
               drive.close(CdbLockable.COMMIT) ;
            pvr.close(CdbLockable.COMMIT) ;
            answer = request ;
         }else{
           throw new Exception( "Unknown request : "+command ) ;
         }
      
      }catch(Exception e ){
          esay( "Problem in out or band with '"+command+"' : "+e ) ;
          answer = e ;
      }
      try{
         msg.setMessageObject( answer ) ;
         msg.revertDirection() ;
         sendMessage( msg ) ;
      }catch( Exception ioe ){
         esay( ioe ) ;
      }
   
   }
   public void messageArrived( CellMessage msg ){
   
       Object obj = msg.getMessageObject() ;
       
       say( "MA "+obj ) ;
       
       if( obj instanceof Object [] ){
         
          if( ((Object[])obj).length < 3 )return ;
          executeOutOfBand( msg , (Object[])obj ) ;
          
       }else if( obj instanceof EurogateRequest ){
       
           EurogateRequest req     = (EurogateRequest) obj ;
           String          command = req.getActionCommand() ;
           //
           //  the io action will initail a client i/o 
           //  operation .
           //
           if( command.equals("i/o") ){
           
              String type = req.getType() ;
              if( type.equals( "get" ) )
                  processGet( msg , (PvlRequest)req ) ;
              else if( type.equals( "put" ) )
                  processPut( msg , (PvlRequest)req ) ;
              else if( type.equals( "remove" ) )
                  processRemove( msg , (PvlRequest)req ) ;
              else
                  esay( "Unsupported Direction : "+type) ;
              
              return ;
                  
           }
           //
           //
           PvlCompanion companion = null ;
           synchronized( _sendLock ){
               companion = (PvlCompanion)_pending.remove( msg.getLastUOID() ) ;
           }
           if( companion == null ){
              esay( "PANIC : no companion found for '"+command+"'" ) ;
              //
              //  we should the appropriate cleanups 
              //
              return ;
           }
           if( companion instanceof IoCompanion ){
              //
              // this is a regular IoRequest
              //
              IoCompanion ioComp  = (IoCompanion)companion ;
              try{
                 if( req.getReturnCode() != 0 ){
                    ioComp.setRequest( req ) ;
                    throw new PvlException( req.getReturnCode() ,
                                            req.getReturnMessage() ) ;
                 }
                 try{
                     _dbGate.open(GateKeeper.LOW) ;
                 }catch( InterruptedException ie ){
                     throw new PvlException( 55 , "sendMessage was interruped" ) ;
                 }
                 if( command.equals("mount-ready") ){

                    mountFinished( ioComp , msg , (PvrRequest)req ) ;

                 }else if( command.equals("load-ready") ){

                    loadFinished( ioComp , msg , (MoverRequest)req ) ;

                 }else if( command.equals("checkLabel-ready") ||
                           command.equals("writeLabel-ready")   ){

                    labelFinished( ioComp , msg , (MoverRequest)req ) ;

                 }else{
                    String error = "PANIC : '"+command+
                                   "' found for companion : "+
                                   companion.getClass().getName() ;
                    throw new PvlException( 5 , error ) ;
                 }
                 synchronized( _sendLock ){
                      CellMessage m = companion.getNextMessage() ;
                      sendMessage( m ) ;
                      _pending.put( m.getUOID() , companion ) ;
                 }
              }catch( Throwable t ){
                 int    code = 66 ;
                 if( t instanceof PvlException ){
                      code = ((PvlException)t).getReturnCode() ;
                 }
                 esay( "Problem in messageArrived : "+t ) ;
                 esay( t ) ;
                 sendProblem( ioComp.getMessage() , code , t.getMessage() ) ;
                 //
                 // do the system cleanups
                 // ??
                 
              }finally{
                 try{ _dbGate.close() ; }catch(IllegalArgumentException iae ){}
              }
              return ;
           }else if( companion instanceof DismountCompanion ){
              DismountCompanion disComp = (DismountCompanion)companion ;
              try{
                  _dbGate.open(GateKeeper.LOW) ;
              }catch( InterruptedException ie ){
//                  throw new PvlException( 56 , "sendMessage was interruped" ) ;
                  esay( "Panic : "+"sendMessage was interruped"  ) ;
                  return ;
              }
              try{
                 if( command.equals("dismount-ready") ){

                    dismountFinished( disComp ,
                                      msg     ,
                                     (PvrRequest)req ) ;

                 }else if( command.equals("unload-ready") ){
                    //
                    // the unload finished ( ok ? ) ;
                    //
                    unloadFinished( disComp,
                                    msg , 
                                    (MoverRequest)req ) ;

                 }else
                    esay( "Unsupported action command : "+req.getActionCommand() ) ;
              }finally{
                 _dbGate.close() ;
              }
           }  
       }else{
           esay( "Unknown message object arrived : "+obj.getClass().getName() ) ;
       }
   }
   public void messageToForward( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       
       say( "TF : "+obj ) ;
       
       if( obj instanceof EurogateRequest ){
       
           EurogateRequest req  = (EurogateRequest) obj ;
           String actionCommand = req.getActionCommand() ;
           
           if( actionCommand.equals("i/o-ready") ){
              //
              PvlCompanion companion = null ;
              synchronized( _sendLock ){
                  companion = (PvlCompanion)_pending.remove( msg.getLastUOID() ) ;
              }
              if( companion == null ){
                 esay( "PANIC : no companion found for '"+actionCommand+"'" ) ;
                 //
                 //  we should the appropriate cleanups 
                 //
                 return ;
              }
              if( companion instanceof IoCompanion ){
                 IoCompanion ioComp = (IoCompanion)companion ;
                 String type = req.getType() ;
                 if( type.equals( "get" ) ){
                     processGetReply( ioComp , msg , (PvrRequest)req ) ;
                 }else if( type.equals( "put" ) ){
                     processPutReply( ioComp , msg , (PvrRequest)req ) ;
                 }else if( type.equals( "remove" ) ){
   //                  processRemoveReply( ioComp , msg , (PvlRequest)req ) ;
                 }else
                     esay( "Unsupported Direction : "+type) ;
              }    
           }else if( actionCommand.equals("dismount-ready") ){
              processDismountReady( msg , (PvrRequest)req ) ;
           }else if( actionCommand.equals("mount-ready") ){
              processMountReady( msg , (PvrRequest)req ) ;
           }else if( actionCommand.equals("newdrive-ready") ){
              processNewdriveReady( msg , (PvrRequest)req ) ;
           }else if( actionCommand.equals("unload-ready") ){
              processUnloadReady( msg , (MoverRequest)req ) ;
           }else
              esay( "Unsupported action command : "+req.getActionCommand() ) ;
              
       }else{
           esay( "Unknown message object arrived : "+obj.getClass().getName() ) ;
       }
       
       super.messageToForward( msg ) ;
   }
   /////////////////////////////////////////////////////////////////
   //
   //    the initial request part
   //          process  Get   Put    Remove
   //
   private void processGet( CellMessage msg , PvlRequest req ){
   
      say( "processGet <init> : "+req ) ;
      
      String       volumeName = req.getVolume() ;
      VolumeHandle volume     = null ;
      String       problem    = "" ;
      try{
         volume = _pvlDb.getVolumeByName( volumeName ) ;
      }catch( Exception e ){
         esay( problem = "Volume not found : "+volumeName ) ;
         sendProblem( msg , 22 , problem ) ;
         return ;
      }
      String pvrName       = null ;
      String cartridgeName = null ;
      String volumeId      = null ;
      //
      // get the lock
      //
      try{
         _dbGate.open(GateKeeper.MEDIUM);
      }catch( InterruptedException ie ){
         esay( problem = "processGet : Interruped while waiting for global database lock" ) ;
         sendProblem( msg , 23 , problem ) ;
         return;
      }
      try{
         volume.open( CdbLockable.READ ) ;
         pvrName       = volume.getPvr() ;
         cartridgeName = volume.getCartridge() ;
         volumeId      = volume.getPosition() ;
         volume.close( CdbLockable.COMMIT ) ;
      }catch( Exception ee ){
         esay( problem = "processGet : Database Exception in processGet : "+ee ) ;
         sendProblem( msg , 24 , problem ) ;
         return ;
      }finally{
         _dbGate.close() ;
      }
      say( "processGet : pvr="+pvrName+";cart="+cartridgeName) ;
      
      PvlResourceRequest pvlReq = new PvlResourceRequest( msg , req ) ;
      pvlReq.setPvr( pvrName ) ;
      pvlReq.setCartridge( cartridgeName ) ;
      pvlReq.setVolumeId( volumeId ) ;

      say( "processGet : request added to fifo ") ;
      _fifo.push( pvlReq ) ;
   }
   private void processPut( CellMessage msg , PvlRequest req ){
   
      say( "processPut <init> : "+req ) ;

      String volumeSetName = req.getVolumeSet() ;
      //
      // there is no real need to get the db gateKeeper
      // because we only check if the volumeSet exists.
      //
      VolumeSetHandle volumeSet = null ;
      try{
         volumeSet = _pvlDb.getVolumeSetByName( volumeSetName ) ;
      }catch( Exception e ){
         String problem = "VolumeSet not found : "+volumeSetName  ;
         esay( problem ) ;
         sendProblem( msg , 25 , problem ) ;
         return ;
      }
   
     say( "processPut : request added to fifo") ;
     _fifo.push( new PvlResourceRequest( msg , req ) ) ;
      
   }
   private void processRemove( CellMessage msg , PvlRequest req ){
      say( "processRemove <init> : "+req ) ;
      
      String       volumeName = req.getVolume() ;
      VolumeHandle volume     = null ;
      String       problem    = null ;

      try{
         volume = _pvlDb.getVolumeByName( volumeName ) ;
      }catch( Exception e ){
         esay( problem = "Volume not found : "+volumeName ) ;
         sendProblem( msg , 22 , problem ) ;
         return ;
      }
      String pvrName       = null ;
      String cartridgeName = null ;
      String volumeId      = null ;
      int    fileCount     = 0 ;
      //
      // get the lock
      //
      try{
         _dbGate.open(GateKeeper.MEDIUM);
      }catch( InterruptedException ie ){
         esay( problem = "processRemove : Interruped while waiting for global lock" ) ;
         sendProblem( msg , 23 , problem ) ;
         return;
      }
      try{
         volume.open( CdbLockable.WRITE ) ;
            pvrName       = volume.getPvr() ;
            cartridgeName = volume.getCartridge() ;
            volumeId      = volume.getPosition() ;
            fileCount     = volume.getFileCount() ;

            fileCount -- ;
            say( "processRemove : volume "+volumeName+
                 " : new filecount : "+fileCount ) ;
          
            if( fileCount <= 0 ){
               //
               // the tape is free again.
               //    set the filecount, the eor position and the
               //    new residualBytes count ( from the vdes)
               //
               volume.setFileCount(0) ;
               volume.setEOR( "-" ) ;
               String volDesc = volume.getVolumeDescriptor() ;
               VolumeDescriptorHandle vd = 
                    _pvlDb.getVolumeDescriptorByName(
                         volume.getVolumeDescriptor()  ) ;
               vd.open(CdbLockable.READ) ;
                   volume.setResidualBytes( vd.getSize() ) ;          
               vd.close(CdbLockable.COMMIT) ;
               say("processRemove : volume "+volumeName+" : freed" ) ;
            }else{
                volume.setFileCount( fileCount ) ;
            }
         volume.close( CdbLockable.COMMIT ) ;
         //
         // if we are simulating the robot we have to
         // remove the file from disk. ( should be done
         // by the mover )
         String disk = (String)_context.get( "robotSpace" ) ;
         if( disk != null ){
            String filename = cartridgeName+":"+volumeId+":"+fileCount ;
            new File( filename ).delete() ;
            if( fileCount <= 0 ){
                filename = cartridgeName+":"+volumeId+":label" ;
                new File( filename ).delete() ;
            }
         }
      }catch( Exception ee ){
         problem = "processRemove : Database Exception : "+ee ;
         esay( problem ) ;
         sendProblem( msg , 26 , problem ) ;
      }finally{
         _dbGate.close() ;
      }
      sendBack( msg ) ;
   }
   /////////////////////////////////////////////////////////////////////
   //
   //   process the result from the scheduler
   //       i)  newRequest
   //      ii)  dismount
   //
   //    NOTE : the processModifiers are dbGate locked 
   //           within runScheduler. ( is the same thread ) 
   //
   private void  processModifier( PvlResourceModifier modifier )
           throws Exception {
           
      say( "processModifier <init> : "+modifier ) ;
      
      if( modifier.getActionEvent().equals("newRequest" ) ){
      
         processNewRequestModifier( modifier ) ;
         
      }else if( modifier.getActionEvent().equals("dismounted" ) ){
      
         processDismountModifier( modifier ) ;
         
      }else{
      
         esay( "processModifier Panic : "+ modifier.getActionEvent() ) ;
         
      }
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   scheduler
   //                     | 
   //                     +--->   mount
   //                     +--->   i/o
   //
   private void processNewRequestModifier( PvlResourceModifier modifier )
           throws Exception {

      PvlResourceRequest req = (PvlResourceRequest)modifier ;
      PvlRequest  pvlRequest = (PvlRequest)req.getRequest() ;
      PvrHandle   pvr        = _pvlDb.getPvrByName(req.getPvr()) ;
      String      driveName  = req.getDrive() ;
      DriveHandle drive      = pvr.getDriveByName( driveName ) ;
      String      cart       = req.getCartridge() ;
      boolean     wasInDrive = false ;
      String      specific   = null ;
      //
      // is the required cartridge already in the drive.
      // assign it to the drive and set the owner.
      //
      drive.open( CdbLockable.WRITE ) ;
        say( "Selected cartridge : "+cart ) ;
        say( "Cartridge in drive : "+drive.getCartridge() ) ;
        wasInDrive = drive.getCartridge().equals( cart ) ;
        if( ! wasInDrive )drive.setCartridge( cart  ) ;
        specific   = drive.getSpecificName() ;
        drive.setOwner( "OWNED" ) ;
        drive.setTime( System.currentTimeMillis() ) ;
        drive.setAction( "scheduler" ) ;
      drive.close( CdbLockable.COMMIT ) ;

      //
      // get the volume 
      //
      VolumeHandle volume = _pvlDb.getVolumeByName( req.getVolume() ) ;
      volume.open( CdbLockable.WRITE ) ;
         //
         // set the cartridge name ( taken from the volume db) 
         //
         pvlRequest.setCartridge( volume.getCartridge() ) ;

         if( req.getDirection().equals("put") ){
            //
            // adjust the filesize 
            //
            long size  = volume.getResidualBytes() ;
            size -= req.getFileSize() ;
            volume.setResidualBytes( size ) ;
            //
            // insert into the request :
            //              .
            //              .pvr-mount  mvr-load mvr-label mvr-io
            // ....................................................
            //  volumeId    .               *
            //  eorPos      .                                 *
            //  cartridge   .     *                  *
            //  pvr         .             helper
            //  drive       .             helper
            //  spec. drive .     *
            //
            pvlRequest.setVolumeId( volume.getPosition() ) ;
            pvlRequest.setPosition( 
                  volume.getEOR() ,
                  volume.getEOR()  ) ;

         }else if( req.getDirection().equals("get") ){
            //
            // nothing to do for reads
            //

         }
      volume.close( CdbLockable.COMMIT ) ;

      PvrRequest pvrReq = (PvrRequest)pvlRequest ;
      pvrReq.setDrive( driveName , specific ) ;
      pvrReq.setPvr(req.getPvr() ) ; // looks strange, but is needed.

      String  path , command ;
      CellMessage msg = null ;
      if( wasInDrive ){
         path        = driveName ;
         command     = "i/o" ;
         drive.open( CdbLockable.WRITE ) ;
           drive.setAction( "i/o" ) ;
         drive.close( CdbLockable.COMMIT ) ;
         msg = req.getMessage() ;
         msg.setMessageObject( pvlRequest ) ;
         msg.getDestinationPath().add( path ) ;
         msg.nextDestination() ;
      }else{
         path        = req.getPvr() ;
         command     = "mount" ;
         drive.open( CdbLockable.WRITE ) ;
           drive.setAction( "mounting" ) ;
         drive.close( CdbLockable.COMMIT ) ;
         msg = new CellMessage( new CellPath(path) , pvlRequest ) ;
      }
      say( "Initiating "+command+" : "+pvrReq ) ;
      //
      // now we only have to send it to 'whereever'
      //
      pvlRequest.setActionCommand( command ) ;
      synchronized( _sendLock ){
         say( "Sending to "+path+" : "+pvlRequest ) ;
         try{
            sendMessage( msg ) ;
            _pending.put( msg.getUOID() ,new IoCompanion( req.getMessage() )  ) ;
         }catch(Exception me ){
            String problem = "Can't forward message : "+me  ;
            esay( problem ) ;
            pvlRequest.setReturnValue( 123 , problem ) ; 
            replyMessage( req.getMessage() ) ;
         }
      }
      //
      // remove the request from the queue.
      //
      int pos = 0 ;
      if( ( pos = req.getPosition() ) < 0 )
                pos = _queue.getRequestCount() - 1 ;
      _queue.removeRequestAt( pos ) ;

      return  ;
           
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   scheduler
   //                     | 
   //                     +--->   unload
   //
   private void processDismountModifier( PvlResourceModifier modifier )
           throws Exception {
         //
         //  the scheduler wants us to dismount the 
         //  drive /cartridge pair.
         //
         String pvrName    = modifier.getPvr() ;
         String cartridge  = modifier.getCartridge() ;
         String driveName  = modifier.getDrive() ;
         
         PvrHandle   pvr      = null ;
         DriveHandle drive    = null ;
         String      specific = null ;
         //
         // extract the specific name from the database
         //
         try{
            pvr   = _pvlDb.getPvrByName(pvrName) ;
            drive = pvr.getDriveByName( driveName ) ;
            drive.open( CdbLockable.WRITE ) ;
               specific = drive.getSpecificName() ;
               drive.setAction( "unloading" ) ;
            drive.close( CdbLockable.COMMIT ) ;
         }catch( Exception ee ){
            esay( "PANIC : internal DB problem 54738 : "+ee ) ;
            return ;
         }
         //
         // create the unload/dismount request, the corresponding
         // message and send it to the drive first.
         //
         MoverRequest moverReq = 
              new UnloadDismountRequest( 
                     pvrName , 
                     cartridge ,
                     driveName ,
                     specific     ) ;
          
         CellMessage msg = new CellMessage( 
                               new CellPath( driveName ) , 
                               moverReq ) ; 
         
         try{
            synchronized( _sendLock ){
               sendMessage( msg ) ;
               _pending.put( msg.getUOID() , new DismountCompanion(msg) ) ;
            }
         }catch(Exception eee ){
            esay( "PANIC : internal send problem 54938 : "+eee ) ;
            return ;
         }
         return ;
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MA mount-ready
   //                     | 
   //                     +--->   load
   //
   private void mountFinished( IoCompanion companion ,
                               CellMessage msg ,
                               PvrRequest  pvrRequest )
           throws PvlException {
   
       say( "mountFinished : "+pvrRequest ) ;
       
       //
       // update the drive info
       //
       try{
          updateDriveAction( pvrRequest , "loading" ) ;
       }catch( Exception dbeee ){
          esay( "Problem updating drive info : "+dbeee ) ;
          return ;
       }
       pvrRequest.setActionCommand( "load" ) ;
       companion.getMessage().setMessageObject( pvrRequest ) ;
       //
       // create the message to proceed with
       //
       msg = new CellMessage( 
                     new CellPath( pvrRequest.getGenericDrive() ) ,
                     pvrRequest 
                            ) ;

       companion.setNextMessage( msg ) ;

       say( "mountFinished : sending to "+
            pvrRequest.getGenericDrive()+
            " : "+pvrRequest ) ;
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MA load-ready
   //                     | 
   //                     +--->   checkLabel
   //                     +--->   writeLabel
   //
   private void loadFinished( IoCompanion companion ,
                              CellMessage msg , 
                              MoverRequest moverReq )
       throws Exception {
       
       say( "loadFinished : "+moverReq ) ;
       
       PvrRequest  pvrReq = (PvrRequest)moverReq ;
       PvrHandle   pvr    = null ;
       int     usageCount = 0 ;
       CartridgeHandle cartridge  = null ;
       try{
           pvr        = _pvlDb.getPvrByName( pvrReq.getPvr() ) ;
           cartridge  = pvr.getCartridgeByName( pvrReq.getCartridge() ) ;
           cartridge.open( CdbLockable.WRITE ) ;
              usageCount = cartridge.getUsageCount() ; 
           cartridge.close( CdbLockable.COMMIT ) ;
       }catch(Exception ee ){
           esay( "PANIC : internal problem 23643 : "+ee ) ;
           throw ee ;
       }
       
       String newAction = usageCount > 0 ? "checkLabel" : "writeLabel" ;
       moverReq.setActionCommand( newAction ) ;
       companion.getMessage().setMessageObject( moverReq ) ;
       msg = new CellMessage( 
                       new CellPath( pvrReq.getGenericDrive() ) ,
                       pvrReq ) ;

       companion.setNextMessage( msg ) ;
       
       say( "mountFinished : sending to "+
            pvrReq.getGenericDrive()+" : "+pvrReq ) ;

       try{
          updateDriveAction( pvrReq , newAction ) ;
       }catch(Exception ee ){
           esay( "PANIC : internal problem 23733 : "+ee ) ;
           throw ee ;
       }

   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MA checkLabel/writeLabel-ready
   //                     | 
   //                     +--->   i/o
   //
   private void labelFinished( IoCompanion companion ,
                               CellMessage msg , 
                               MoverRequest moverReq ){
       say( "labelFinished : "+moverReq ) ;
       
        PvrRequest  pvrReq = (PvrRequest)moverReq ;
        if( moverReq.getActionCommand().equals("writeLabel-ready") ){
            PvlRequest  pvlReq = (PvlRequest)moverReq ;
            //
            //   correct the position and EOR
            //
            String eor = moverReq.getEorPosition() ; // is eor from mover
	    moverReq.setPosition( eor , eor ) ;
            String volumeName = pvlReq.getVolume() ;
            try{
               VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
               volume.open( CdbLockable.WRITE );
                  volume.setEOR( eor ) ;
               volume.close( CdbLockable.COMMIT ) ;
            }catch( Exception eeee ){
               esay( "PANIC : internal problem 473734 : "+eeee ) ;
               return ;
            }
        }
        try{
           updateDriveAction( pvrReq , "i/o-"+pvrReq.getType() ) ;
        }catch(Exception ee ){
            esay( "PANIC : internal problem 23734 : "+ee ) ;
            return ;
        }
        moverReq.setActionCommand( "i/o" ) ;
        
        CellMessage storedMsg = companion.getMessage() ;
        storedMsg.setMessageObject( moverReq ) ;
        storedMsg.getDestinationPath().
                  add(((PvrRequest)moverReq).getGenericDrive() ) ;
        storedMsg.nextDestination() ;
        say( "labelFinished : sending to "+storedMsg.getDestinationPath()+
             " : "+moverReq ) ;
             
        companion.setNextMessage( storedMsg ) ; // myself

   }
   ///////////////////////////////////////////////////////////////////////
   //
   //     and now the    process  Get Put   Remove   Reply
   //
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MF i/o-ready
   //                     | 
   //                     +--->   unload
   //
   private void processGetReply( IoCompanion companion ,
                                 CellMessage msg , 
                                 PvrRequest pvrReq ){
      //
      // set the drive to 'action=none'
      //
      int problem = pvrReq.getReturnCode() ;
      try{
         updateDriveAction( pvrReq , "none" ) ;
         updateDriveOwner( pvrReq , "-" ) ;
      }catch(Exception ee ){
          esay( "PANIC : internal problem 23739 : "+ee ) ;
          return ;
      }

      //
      // we need to generate a dealloating request for the scheduler
      //
      String pvrName       = pvrReq.getPvr() ;
      String driveName     = pvrReq.getGenericDrive() ;
      String cartridgeName = pvrReq.getCartridge() ;
      PvlResourceModifier modifier = 
           new PvlResourceModifier( 
                   "deallocated" , 
                   pvrName, 
                   driveName , 
                   cartridgeName ) ;
               
      say( "processGetReply : request added to fifo "+pvrReq ) ;
      _fifo.push( modifier ) ;
   }
   private void processPutReply( IoCompanion companion ,
                                 CellMessage msg , 
                                 PvrRequest pvrReq ){
   
      say( "processPutReady <init> : "+pvrReq ) ;
      //
      // error recovery preparation
      // we have to do most of the things problem or not .
      //
      int problem = pvrReq.getReturnCode() ;
      //
      // increment the cartridge usageCount
      //
      String pvrName = pvrReq.getPvr() ;
      
      try{
          PvrHandle       
              pvr        = _pvlDb.getPvrByName( pvrName ) ;
          CartridgeHandle 
              cartridge  = pvr.getCartridgeByName( pvrReq.getCartridge() ) ;
              
          cartridge.open( CdbLockable.WRITE ) ;
             cartridge.setUsageCount(cartridge.getUsageCount() + 1) ;
          cartridge.close( CdbLockable.COMMIT ) ;
          
      }catch(Exception ee ){
          esay( "PANIC : internal problem 23643 : "+ee ) ;
          return ;
      }
      //
      //    update the volume information.
      //          end of recording
      //          real number of  bytes written
      //          number of files
      //
      //          residual bytes on tape ( in case of EOV error ) 
      //
      PvlRequest  pvlReq     = (PvlRequest)pvrReq ;
      String      eor        = pvlReq.getEorPosition() ; // is eor from mover
      String      volumeName = pvlReq.getVolume() ;
      try{
         VolumeHandle volume = _pvlDb.getVolumeByName(volumeName) ;
         volume.open( CdbLockable.WRITE );
            long residual  = volume.getResidualBytes() ;
	    if( problem == 0 ){
                //
                // nothing wrong
                //
        	volume.setEOR( eor ) ;
        	long realBytes = pvlReq.getRealBytes() ;
        	if( realBytes > 0 ){
        	   residual += ( pvlReq.getFileSize() - realBytes ) ;
        	   volume.setResidualBytes( residual ) ;
        	} 
        	volume.setFileCount( volume.getFileCount() + 1 ) ;
            }else if( problem == 999 ){
                //
                // reached premature eoTape (recoverable)
                //
                if( ( residual = pvlReq.getResidualBytes() ) > 0 ){
                   residual -= ( residual / 10 ) ;
                   volume.setResidualBytes( residual ) ;
                }else{
                   residual = 0 ;
                }
                String error = "Unexpected EOT reached,"+
                     " correcting residual bytes to : "+residual ;
                esay( error ) ;
                volume.setResidualBytes( residual ) ;
            }else{
                //
                // fatal (non recoverable)
                //
                residual += pvlReq.getFileSize() ;
                volume.setResidualBytes( residual ) ;
            }
         volume.close( CdbLockable.COMMIT ) ;
      }catch( Exception eeee ){
         esay( "PANIC : internal problem 473734 : "+eeee ) ;
         return ;
      }
      //
      // set the drive to 'action=none'
      //
      try{
         updateDriveAction( pvrReq , "none" ) ;
         updateDriveOwner( pvrReq , "-" ) ;
      }catch(Exception ee ){
          esay( "PANIC : internal problem 23739 : "+ee ) ;
          return ;
      }

      //
      // we need to generate a dealloating request for the scheduler
      //
      String driveName     = pvrReq.getGenericDrive() ;
      String cartridgeName = pvrReq.getCartridge() ;
      PvlResourceModifier modifier = 
           new PvlResourceModifier( 
                   "deallocated" , 
                   pvrName, 
                   driveName , 
                   cartridgeName ) ;
               
      say( "processPutReply : request added to fifo "+pvrReq ) ;
      _fifo.push( modifier ) ;
      
      
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MF unload-ready
   //                     | 
   //                     +--->   dismount
   //
   private void unloadFinished( DismountCompanion companion,
                                CellMessage msg , 
                                MoverRequest moverReq ){

       say( "unloadFinished : "+moverReq ) ;
       if(  moverReq.getReturnCode() != 0 ){
          esay( "Unload failed : "+moverReq.getReturnMessage() ) ;
          //
          // so the cartridge might be already waiting for the robotic.
          // => dismount it anyway.
       }
       moverReq.setActionCommand( "dismount" ) ;
       PvrRequest pvrReq = (PvrRequest)moverReq ;
  
       try{
         updateDriveAction( pvrReq , "dismount" ) ;
       }catch(Exception e ){
           esay( "PANIC : internal problem 27643 : "+e ) ;
           return ;
       }
       msg = new CellMessage( 
                    new CellPath( pvrReq.getPvr() ) ,
                                  pvrReq ) ;
                                  
       say( "unmountFinished : sending to "+
            pvrReq.getGenericDrive()+" : "+pvrReq ) ;
            
       synchronized( _sendLock ){
          try{
               
              sendMessage( msg ) ;
              _pending.put( msg.getUOID() , companion ) ;
              
          }catch( Exception ee ){
             String problem = "pvr not available : "+ee  ;
             esay( problem ) ; 
          }
       }
       
   }
   ///////////////////////////////////////////////////////////////////////
   //
   //   called by :   MF dismount-ready
   //                     | 
   //                     +--->   scheduler
   //
   private void dismountFinished( DismountCompanion companion,
                                  CellMessage msg , 
                                  PvrRequest pvrRequest ){

       say( "dismountFinished : "+pvrRequest ) ;
       
       int problem = pvrRequest.getReturnCode() ;
                                     
       try{
          PvrHandle   pvr   = _pvlDb.getPvrByName( pvrRequest.getPvr() ) ;
          DriveHandle drive = pvr.getDriveByName( pvrRequest.getGenericDrive() ) ;
          drive.open( CdbLockable.WRITE ) ;
            if( problem == 0 ){
               drive.setCartridge( "empty" ) ;
               drive.setOwner( "-" ) ;
               drive.setAction( "none" ) ;
            }else{
               drive.setStatus( "disabled" ) ;
               drive.setAction( "dism-failed" ) ;
            }
          drive.close( CdbLockable.COMMIT ) ;
       }catch( Exception dbe ){
          esay( "dismountFinished : internal db error : "+dbe ) ;
          return ;
       }
       
       if(  problem != 0 ){
          esay( "DisMount failed : "+pvrRequest.getReturnMessage() ) ;
          return ;
       }
       
       PvlResourceModifier modifier =
             new PvlDismountModifier( 
                 pvrRequest.getPvr() ,
                 pvrRequest.getGenericDrive() ,
                 pvrRequest.getCartridge()  ) ;

       _fifo.push( modifier ) ;
         
       return ;
   }
   private void processUnloadReady( CellMessage msg , MoverRequest moverReq ){
   
      if(! ( moverReq instanceof PvrRequest ) ){
        String info = "internal problem 6843866 in processMountReady" ;
        esay( info ) ;
        moverReq.setReturnValue( 45 , info ) ;
        return ;
      }
      PvrRequest pvrReq = (PvrRequest)moverReq ;
      
      int problem          = pvrReq.getReturnCode() ;
      String pvrName       = pvrReq.getPvr() ;
      String driveName     = pvrReq.getGenericDrive() ;
      String cartridgeName = pvrReq.getCartridge() ;
      try{
      
         PvrHandle   pvr   = _pvlDb.getPvrByName( pvrName ) ;         
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         
         drive.open( CdbLockable.WRITE ) ;
            if( problem == 0 ){
               drive.setOwner("-") ;
               drive.setAction( "none" ) ;
            }else{
               drive.setStatus( "disabled" ) ;
               drive.setAction( "unload-failed" ) ;
            }
         drive.close( CdbLockable.COMMIT ) ;
      
      }catch( Exception ee ){
         esay( "PANIC : internal database error 8535438 : "+ee ) ;
         String info = "Internal database problem : "+ee ;
         if( problem != 0 ){
             pvrReq.setReturnValue( 46 ,info +
               " (initial problem : "+pvrReq.getReturnMessage()+")" );
         }else{
             pvrReq.setReturnValue( 46 ,info ) ;
         }
      }
   
   }
   private void processNewdriveReady( CellMessage msg , PvrRequest pvrReq ){
   
      int mode             = pvrReq.getReturnCode() ;
      String pvrName       = pvrReq.getPvr() ;
      String driveName     = pvrReq.getGenericDrive() ;
      String cartridgeName = mode == 3 ? pvrReq.getCartridge() : "" ;
      try{
      
         PvrHandle   pvr   = _pvlDb.getPvrByName( pvrName ) ;         
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         
         drive.open( CdbLockable.WRITE ) ;
            switch( mode ){
               case 0 :
                  pvrReq.setReturnValue( 1 , 
                    "Pvr can't determine drive status" ) ;
               break ;
               case 1 :
                  drive.setCartridge( "empty" ) ;
               break ;
               case 2 :
                  cartridgeName = drive.getCartridge() ;
                  drive.setCartridge( "?"+cartridgeName+"?" ) ;
               break ;
               case 3 :
                  drive.setCartridge( cartridgeName ) ;
               break ;
               default :
                  pvrReq.setReturnValue( 1 , 
                    "Unknown return code from pvr : "+mode ) ;
            }
         drive.close( CdbLockable.COMMIT ) ;
      
      }catch( Exception ee ){
         esay( "PANIC : internal database error 8595438 : "+ee ) ;
         String info = "Internal database problem : "+ee ;
         pvrReq.setReturnValue( 46 ,info ) ;
         
      }
   
   }
   private void processMountReady( CellMessage msg , PvrRequest pvrReq ){
      int problem          = pvrReq.getReturnCode() ;
      String pvrName       = pvrReq.getPvr() ;
      String driveName     = pvrReq.getGenericDrive() ;
      String cartridgeName = pvrReq.getCartridge() ;
      try{
      
         PvrHandle   pvr   = _pvlDb.getPvrByName( pvrName ) ;         
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         
         drive.open( CdbLockable.WRITE ) ;
            if( problem == 0 ){
               drive.setCartridge( cartridgeName ) ;
               drive.setOwner("-") ;
               drive.setAction( "none" ) ;
            }else{
               drive.setStatus( "disabled" ) ;
               drive.setAction( "mount-failed" ) ;
            }
         drive.close( CdbLockable.COMMIT ) ;
      
      }catch( Exception ee ){
         esay( "PANIC : internal database error 8595438 : "+ee ) ;
         String info = "Internal database problem : "+ee ;
         if( problem != 0 ){
             pvrReq.setReturnValue( 46 ,info +
               " (initial problem : "+pvrReq.getReturnMessage()+")" );
         }else{
             pvrReq.setReturnValue( 46 ,info ) ;
         }
      }
   
   }
   private void processDismountReady( CellMessage msg , PvrRequest pvrReq ){
      int problem          = pvrReq.getReturnCode() ;
      String pvrName       = pvrReq.getPvr() ;
      String driveName     = pvrReq.getGenericDrive() ;
      try{
      
         PvrHandle   pvr   = _pvlDb.getPvrByName( pvrName ) ;         
         DriveHandle drive = pvr.getDriveByName( driveName ) ;
         
         drive.open( CdbLockable.WRITE ) ;
            if( problem == 0 ){
               drive.setCartridge( "empty" ) ;
               drive.setOwner("-") ;
               drive.setAction( "none" ) ;
            }else{
               drive.setStatus( "disabled" ) ;
               drive.setAction( "dism-failed" ) ;
            }
         drive.close( CdbLockable.COMMIT ) ;
      
      }catch( Exception ee ){
         esay( "PANIC : internal database error 8595438 : "+ee ) ;
         String info = "Internal database problem : "+ee ;
         if( problem != 0 ){
             pvrReq.setReturnValue( 46 ,info +
               " (initial problem : "+pvrReq.getReturnMessage()+")" );
         }else{
             pvrReq.setReturnValue( 46 ,info ) ;
         }
      }
   
   }
   private void updateDriveAction( PvrRequest pvrRequest , String action )
                throws Exception {
          String pvrName = pvrRequest.getPvr() ;
          PvrHandle pvr  = _pvlDb.getPvrByName( pvrName ) ;
          DriveHandle drive = pvr.getDriveByName( pvrRequest.getGenericDrive() ) ;
          drive.open( CdbLockable.WRITE ) ;
            drive.setAction( action ) ;
          drive.close( CdbLockable.COMMIT ) ;
   }
   private void updateDriveOwner( PvrRequest pvrRequest , String owner )
                throws Exception {
          String pvrName = pvrRequest.getPvr() ;
          PvrHandle pvr  = _pvlDb.getPvrByName( pvrName ) ;
          DriveHandle drive = pvr.getDriveByName( pvrRequest.getGenericDrive() ) ;
          drive.open( CdbLockable.WRITE ) ;
            drive.setOwner( owner ) ;
            drive.setTime( System.currentTimeMillis() ) ;
          drive.close( CdbLockable.COMMIT ) ;
   }
   private void sendBack( CellMessage msg ){
      sendProblem( msg , 0 , "O.K." ) ;
   }
   private void sendProblem( CellMessage msg , int rc , String rmsg ){
       Object o = msg.getMessageObject() ;
       if( o instanceof EurogateRequest ){
          EurogateRequest egr = (EurogateRequest)o ; 
          egr.setReturnValue( rc , rmsg ) ;
       }
       replyMessage( msg ) ;
   }
   private void replyMessage( CellMessage msg ){
      try{
          msg.revertDirection() ;
          sendMessage( msg ) ;
      }catch(Exception eee ){
          esay( "PANIC : can't reply message : "+eee ) ;
      }
   }
   private void setDriveState( String pvrName,
                               String driveName ,
                               String cartridge ,
                               String action ,
                               String status ,
                               String owner      ) throws Exception {
                               
         PvrHandle   pvr   = _pvlDb.getPvrByName( pvrName ) ;         
         DriveHandle drive = pvr.getDriveByName( driveName ) ;

         drive.open( CdbLockable.WRITE ) ;
            if( cartridge != null )drive.setCartridge( cartridge ) ;
            if( action != null )drive.setAction( action ) ;
            if( status != null )drive.setStatus( status ) ;
            if( owner  != null )drive.setOwner( owner ) ;
         drive.close( CdbLockable.COMMIT ) ;
                                  
   }
   private void resetDrives(){
   
      String []   pvrNames   = _pvlDb.getPvrNames() ;
      PvrHandle   pvr        = null ;
      String []   driveNames = null ;
      DriveHandle drive      = null ;
      
      for( int pvrIndex = 0 ; pvrIndex < pvrNames.length ; pvrIndex++ ){
        try{
        
            pvr = _pvlDb.getPvrByName( pvrNames[pvrIndex] ) ;
            driveNames = pvr.getDriveNames() ;
            for( int driveIndex = 0 ; 
                 driveIndex < driveNames.length ; 
                 driveIndex ++                       ){
                 
                try{
                   drive = pvr.getDriveByName( driveNames[driveIndex] ) ;

                   drive.open( CdbLockable.WRITE ) ;
                      drive.setAction( "none" ) ;
                      drive.setCartridge( "empty" ) ;
                      drive.setStatus( "enabled" ) ;
                      drive.setOwner( "-" ) ;
                   drive.close( CdbLockable.COMMIT ) ;    
                }catch( Exception ee ){
                  say( "resetDrive : "+ee ) ;
                }
            }
        }catch( Exception e ){
          say( "resetDrive : "+e ) ;
        }
      }
   }
   public String ac_ls_companions( Args args )throws Exception {
      StringBuffer sb = new StringBuffer() ;
      Enumeration   e = _pending.keys() ;
      PvlCompanion  c = null ;
      Object        k = null ;
      synchronized( _sendLock ){
         while( e.hasMoreElements() ){
            k = e.nextElement() ;
            c = (PvlCompanion)_pending.get(k) ;
            sb.append(k.toString()).append("->").
               append(c.toString()).append("\n") ; 
         }
      }
      return sb.toString() ;
   
   }
  
}

