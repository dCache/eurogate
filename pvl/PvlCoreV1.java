package  eurogate.pvl ;

import   eurogate.db.pvl.* ;
import   eurogate.vehicles.* ;
import   eurogate.misc.* ;

import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.util.cdb.* ;

import java.util.* ;
import java.lang.reflect.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 9 Feb 1999
  */
public class      PvlCoreV1
       extends    CellAdapter
       implements Runnable               {
 
   private PvlDb     _pvlDb           = null ;
   private FifoY     _fifo            = new FifoY() ;
   private Thread    _schedulerThread = null ;  
   private Gate      _finishGate      = new Gate() ; 
   private Hashtable _pending         = new Hashtable() ;
   private Object    _sendLock        = new Object() ;
   private Pinboard  _status          = new Pinboard(200) ;
   private PvlResourceScheduler    _scheduler = null ; 
   private PvlResourceRequestQueue _queue     = new PvlResourceRequestQueue() ;
   private PvlCommanderV1          _commander = null ;
                                 
   private Class [] schedulerConstructorArgs = {       
                        eurogate.db.pvl.PvlDb.class  } ;
                        
   public PvlCoreV1( String name , String args ){
       super( name , args , true ) ;
    
 //      setPrintoutLevel( 0xff ) ;
       try{
           //
           // 
           //
           Dictionary dict = getDomainContext() ;
           _pvlDb = (PvlDb) dict.get( "database" ) ;
           if( _pvlDb == null )
              throw new 
              IllegalArgumentException( "database not defined" ) ;
          
           String schedulerClassName = (String)dict.get("scheduler") ;
           if( schedulerClassName == null )
              throw new 
              IllegalArgumentException( "scheduler not defined" ) ;
              
           _finishGate.close() ;
           
           _scheduler = initiateScheduler( schedulerClassName ) ;
           
           _schedulerThread  = new Thread( this ) ; 
           _schedulerThread.start() ;      
       }catch( Exception e ){
          say( "Problem in <init> : "+e ) ;
          kill() ;
          return ;
       }
       _commander = new PvlCommanderV1( this , _pvlDb , _fifo , _queue ) ;
       say( "Commander created" ) ;
       addCommandListener( _commander ) ;
       say( "Clearing all drive states ( db only )" ) ;
       resetDrives() ;
       say( "Started" ) ;    
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
   private PvlResourceScheduler initiateScheduler( String name )
        throws Exception {
   
        Class schedulerClass = Class.forName( name ) ;
        Constructor con = 
             schedulerClass.getConstructor( schedulerConstructorArgs ) ;
        
        Object [] args = new Object[1] ;
        args[0] = _pvlDb ;
        
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
   public void cleanUp(){
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
   private void runScheduler() throws InterruptedException {
   
      PvlResourceModifier [] inList   = new PvlResourceModifier[1] ;      
      PvlResourceModifier [] outList  = null  ; 
      PvlResourceModifier    modifier = null ;   
      while( true ){
      
         modifier = (PvlResourceModifier)_fifo.pop() ;
         
         say( "runScheduler : processing : "+modifier ) ;
         
         try{
         
            if( modifier instanceof PvlResourceRequest )
                _queue.addRequest( (PvlResourceRequest)modifier ) ;
            
            inList[0] = modifier ;
            outList   = _scheduler.nextEvent( _queue , inList ) ;
            
         }catch( Exception e ){
            e.printStackTrace() ;
            esay( "runScheduler : Problem in scheduler.nextEvent : "+e ) ;
            continue ;
         }

      
         if( outList == null ){
            say( "runScheduler : scheduler returned null" ) ;
            continue ;
         }
         for( int i = 0 ; i < outList.length ; i++ ){
         
             say( "runScheduler : done : "+outList[i] ) ;
             try{
             
                processModifier( outList[i] ) ;
                
             }catch(Exception e ){
                e.printStackTrace() ;
                esay( "runScheduler : Problem in processModifier : "+e ) ;
             }
         
         }

      
      }
   
   }
   public void messageToForward( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       
       say( "messageToForward : "+obj ) ;
       
       if( obj instanceof EurogateRequest ){
       
           EurogateRequest req  = (EurogateRequest) obj ;
           String actionCommand = req.getActionCommand() ;
           
           if( actionCommand.equals("i/o-ready") ){
           
              String type = req.getType() ;
              if( type.equals( "get" ) ){
                  processGetReply( msg , (PvrRequest)req ) ;
              }else if( type.equals( "put" ) ){
                  processPutReply( msg , (PvrRequest)req ) ;
              }else if( type.equals( "remove" ) ){
//                  processRemoveReply( msg , (PvlRequest)req ) ;
              }else
                  esay( "Unsupported Direction : "+type) ;
                  
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
   private void processGetReply( CellMessage msg , PvrRequest pvrReq ){
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
   public void say( String msg ){
       _status.pin( msg ) ;
       super.say( msg ) ;
   }
   public void esay( String msg ){
       _status.pin( "ERROR : "+msg ) ;
       super.say( msg ) ;
   }
   private void processPutReply( CellMessage msg , PvrRequest pvrReq ){
   
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
   public void messageArrived( CellMessage msg ){
   
       Object obj = msg.getMessageObject() ;
       
       say( "Message arrived : "+obj ) ;
       
       if( obj instanceof EurogateRequest ){
       
           EurogateRequest req = (EurogateRequest) obj ;
           
           if( req.getActionCommand().equals("i/o") ){
           
              String type = req.getType() ;
              if( type.equals( "get" ) )
                  processGet( msg , (PvlRequest)req ) ;
              else if( type.equals( "put" ) )
                  processPut( msg , (PvlRequest)req ) ;
              else if( type.equals( "remove" ) )
                  processRemove( msg , (PvlRequest)req ) ;
              else
                  esay( "Unsupported Direction : "+type) ;
                  
           }else if( req.getActionCommand().equals("mount-ready") ){
              //
              // the mount finished ( ok ? ) ;
              //
              mountFinished( msg , (PvrRequest)req ) ;
              
           }else if( req.getActionCommand().equals("dismount-ready") ){
              //
              // the mount finished ( ok ? ) ;
              //
              dismountFinished( msg , (PvrRequest)req ) ;
              
           }else if( req.getActionCommand().equals("load-ready") ){
              //
              // the mount finished ( ok ? ) ;
              //
              loadFinished( msg , (MoverRequest)req ) ;
              
           }else if( req.getActionCommand().equals("unload-ready") ){
              //
              // the mount finished ( ok ? ) ;
              //
              unloadFinished( msg , (MoverRequest)req ) ;
              
           }else if( req.getActionCommand().equals("checkLabel-ready") ||
                     req.getActionCommand().equals("writeLabel-ready")   ){
              //
              // the mount finished ( ok ? ) ;
              //
              labelFinished( msg , (MoverRequest)req ) ;
              
           }else
              esay( "Unsupported action command : "+req.getActionCommand() ) ;
              
       }else{
           esay( "Unknown message object arrived : "+obj.getClass().getName() ) ;
       }
   }
   private void replyMessage( CellMessage msg ){
      try{
          msg.revertDirection() ;
          sendMessage( msg ) ;
      }catch(Exception eee ){
          esay( "PANIC : can't reply to door : "+eee ) ;
      }
   }
   private void labelFinished( CellMessage msg , MoverRequest moverReq ){
       CellMessage storedMsg = null ;
       say( "labelFinished : "+moverReq ) ;
       synchronized( _sendLock ){
           storedMsg = (CellMessage)_pending.remove( msg.getLastUOID() ) ;
       }
       if( storedMsg == null ){
          esay( "PANIC : "+moverReq.getActionCommand()+" arrived unexpectedly" ) ;
          return ;
       }
       if(  moverReq.getReturnCode() != 0 ){
          esay( "Label failed : "+moverReq.getReturnMessage() ) ;
          storedMsg.setMessageObject( moverReq ) ;
          replyMessage( storedMsg ) ;
          return ;
       }
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
       storedMsg.setMessageObject( moverReq ) ;
       storedMsg.getDestinationPath().
                 add(((PvrRequest)moverReq).getGenericDrive() ) ;
       storedMsg.nextDestination() ;
       say( "labelFinished : sending to "+storedMsg.getDestinationPath()+
            " : "+moverReq ) ;
       try{
          sendMessage( storedMsg ) ;      
       }catch(Exception eee  ){
          String problem = "Can't forward to mover : "+eee  ;
          esay( problem ) ;
          moverReq.setReturnValue( 33 , problem ) ;
          replyMessage(storedMsg) ;
       }
   }
   private void unloadFinished( CellMessage msg , MoverRequest moverReq ){
       CellMessage storedMsg = null ;
       say( "unloadFinished : "+moverReq ) ;
       synchronized( _sendLock ){
           storedMsg = (CellMessage)_pending.remove( msg.getLastUOID() ) ;
       }
       if( storedMsg == null ){
          esay( "PANIC : unload-ready arrived unexpectedly" ) ;
          return ;
       }
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
              _pending.put( msg.getUOID() , msg ) ;
              
          }catch( Exception ee ){
             String problem = "pvr not available : "+ee  ;
             esay( problem ) ; 
          }
       }
       
   }
   private void loadFinished( CellMessage msg , MoverRequest moverReq ){
       CellMessage storedMsg = null ;
       say( "loadFinished : "+moverReq ) ;
       synchronized( _sendLock ){
           storedMsg = (CellMessage)_pending.remove( msg.getLastUOID() ) ;
       }
       if( storedMsg == null ){
          esay( "PANIC : load-ready arrived unexpectedly" ) ;
          return ;
       }
       if(  moverReq.getReturnCode() != 0 ){
          esay( "Load failed : "+moverReq.getReturnMessage() ) ;
          storedMsg.setMessageObject( moverReq ) ;
          replyMessage( storedMsg ) ;
          return ;
       }
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
           return ;
       }
       String newAction = usageCount > 0 ? "checkLabel" : "writeLabel" ;
       moverReq.setActionCommand( newAction ) ;
       storedMsg.setMessageObject( moverReq ) ;
       msg = new CellMessage( 
                       new CellPath( pvrReq.getGenericDrive() ) ,
                       pvrReq ) ;
                       
       say( "mountFinished : sending to "+
            pvrReq.getGenericDrive()+" : "+pvrReq ) ;
            
       try{
          updateDriveAction( pvrReq , newAction ) ;
       }catch(Exception ee ){
           esay( "PANIC : internal problem 23733 : "+ee ) ;
           return ;
       }
       
       synchronized( _sendLock ){
          try{
               
              sendMessage( msg ) ;
              _pending.put( msg.getUOID() , storedMsg ) ;
              
          }catch( Exception ee ){
             String problem = "Can't pvr not available : "+ee  ;
             esay( problem ) ; 
             pvrReq.setReturnValue( 124 , problem ) ;
             replyMessage( storedMsg ) ;
          }
       }
   }
   private void dismountFinished( CellMessage msg , PvrRequest pvrRequest ){
       CellMessage storedMsg = null ;
       say( "dismountFinished : "+pvrRequest ) ;
       synchronized( _sendLock ){
           storedMsg = (CellMessage)_pending.remove( msg.getLastUOID() ) ;
       }
       if( storedMsg == null ){
          esay( "PANIC : dismount-ready arrived unexpectedly" ) ;
          return ;
       }
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
   private void mountFinished( CellMessage msg , PvrRequest pvrRequest ){
       CellMessage storedMsg = null ;
       say( "mountFinished : "+pvrRequest ) ;
       synchronized( _sendLock ){
           storedMsg = (CellMessage)_pending.remove( msg.getLastUOID() ) ;
       }
       if( storedMsg == null ){
          esay( "PANIC : mount-ready arrived unexpectedly" ) ;
          return ;
       }
       if(  pvrRequest.getReturnCode() != 0 ){
          esay( "Mount failed : "+pvrRequest.getReturnMessage() ) ;
          storedMsg.setMessageObject( pvrRequest ) ;
          replyMessage( storedMsg ) ;
          return ;
       }
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
       storedMsg.setMessageObject( pvrRequest ) ;
       msg = new CellMessage( new CellPath( pvrRequest.getGenericDrive() ) ,
                              pvrRequest ) ;
       say( "mountFinished : sending to "+pvrRequest.getGenericDrive()+
            " : "+pvrRequest ) ;
       synchronized( _sendLock ){
          try{
               
              sendMessage( msg ) ;
              _pending.put( msg.getUOID() , storedMsg ) ;
              
          }catch( Exception ee ){
             String problem = "Can't pvr not available : "+ee  ;
             esay( problem ) ; 
             pvrRequest.setReturnValue( 124 , problem ) ;
             replyMessage( storedMsg ) ;
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
          drive.close( CdbLockable.COMMIT ) ;
   }
   private void processGet( CellMessage msg , PvlRequest req ){
   
      say( "processGet <init> : "+req ) ;
      
      String       volumeName = req.getVolume() ;
      VolumeHandle volume     = null ;


      try{
         volume = _pvlDb.getVolumeByName( volumeName ) ;
      }catch( Exception e ){
         String problem = "Volume not found : "+volumeName  ;
         esay( problem ) ;
         try{
            msg.revertDirection() ;
            req.setReturnValue( 22 , problem ) ;
            sendMessage( msg ) ;
         }catch(Exception e2 ){
            esay( "processGet : PANIC : can't return problem state to door" ) ;
            return ;
         }
      }
      String pvrName       = null ;
      String cartridgeName = null ;
      String volumeId      = null ;
      try{
         volume.open( CdbLockable.READ ) ;
         pvrName       = volume.getPvr() ;
         cartridgeName = volume.getCartridge() ;
         volumeId      = volume.getPosition() ;
         volume.close( CdbLockable.COMMIT ) ;
      }catch( Exception ee ){
         esay( "processGet : Database Exception in processGet : "+ee ) ;
         return ;
      }
      say( "processGet : pvr="+pvrName+";cart="+cartridgeName) ;
      
      PvlResourceRequest pvlReq = new PvlResourceRequest( msg , req ) ;
      pvlReq.setPvr( pvrName ) ;
      pvlReq.setCartridge( cartridgeName ) ;
      pvlReq.setVolumeId( volumeId ) ;

      say( "processGet : request added to fifo") ;
      _fifo.push( new PvlResourceRequest( msg , req ) ) ;
   }
   private void processPut( CellMessage msg , PvlRequest req ){
   
      say( "processPut <init> : "+req ) ;

      String volumeSetName = req.getVolumeSet() ;
      
      VolumeSetHandle volumeSet = null ;
      try{
         volumeSet = _pvlDb.getVolumeSetByName( volumeSetName ) ;
      }catch( Exception e ){
         String problem = "VolumeSet not found : "+volumeSetName  ;
         esay( problem ) ;
         try{
            msg.revertDirection() ;
            req.setReturnValue( 22 , problem ) ;
            sendMessage( msg ) ;
         }catch(Exception e2 ){
            esay( "PANIC : can't return problem state to door" ) ;
            return ;
         }
      }
   
     say( "processPut : request added to fifo") ;
     _fifo.push( new PvlResourceRequest( msg , req ) ) ;
      
   }
   private void processRemove( CellMessage msg , PvlRequest req ){
      say( "processRemove <init> : "+req ) ;
      
      String       volumeName = req.getVolume() ;
      VolumeHandle volume     = null ;


      try{
         volume = _pvlDb.getVolumeByName( volumeName ) ;
      }catch( Exception e ){
         String problem = "Volume not found : "+volumeName  ;
         esay( problem ) ;
         try{
            msg.revertDirection() ;
            req.setReturnValue( 22 , problem ) ;
            sendMessage( msg ) ;
         }catch(Exception e2 ){
            esay( "processGet : PANIC : can't return problem state to door" ) ;
            return ;
         }
      }
      String pvrName       = null ;
      String cartridgeName = null ;
      String volumeId      = null ;
      int    fileCount     = 0 ;
      try{
         volume.open( CdbLockable.WRITE ) ;
            pvrName       = volume.getPvr() ;
            cartridgeName = volume.getCartridge() ;
            volumeId      = volume.getPosition() ;
            fileCount     = volume.getFileCount() ;

            fileCount -- ;
            say( "volume "+volumeName+" : new filecount : "+fileCount ) ;

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
               say("volume "+volumeName+" : freed" ) ;
            }else{
                volume.setFileCount( fileCount ) ;
            }
         volume.close( CdbLockable.COMMIT ) ;
      }catch( Exception ee ){
         String problem =
              "!!! processGet : Database Exception in processGet : "+ee ;
         esay( problem ) ;
         req.setReturnValue( 34 , problem ) ;         
      }
      msg.revertDirection() ;
      try{
          sendMessage( msg ) ;
      }catch( Exception red ){
          esay( "PANIC : can't report result to store "+red ) ;
          return ;
      }
   }
   private void  processModifier( PvlResourceModifier modifier )
           throws Exception {
           
      say( "processModifier <init> : "+modifier ) ;
      
      if( modifier.getActionEvent().equals("newRequest" ) ){
      
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
           wasInDrive = drive.getCartridge().equals( cart ) ;
           if( ! wasInDrive )drive.setCartridge( cart  ) ;
           specific   = drive.getSpecificName() ;
           drive.setOwner( "OWNED" ) ;
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
         
         if( wasInDrive ){
            path        = driveName ;
            command     = "i/o" ;
            drive.open( CdbLockable.WRITE ) ;
              drive.setAction( "i/o" ) ;
            drive.close( CdbLockable.COMMIT ) ;
         }else{
            path        = req.getPvr() ;
            command     = "mount" ;
            drive.open( CdbLockable.WRITE ) ;
              drive.setAction( "mounting" ) ;
            drive.close( CdbLockable.COMMIT ) ;
         }
         say( "Initiating "+command+" : "+pvrReq ) ;
         //
         // now we only have to send it to 'whereever'
         //
         pvlRequest.setActionCommand( command ) ;
         synchronized( _sendLock ){
            CellMessage msg = null ;
            say( "Sending to "+path+" : "+pvlRequest ) ;
            try{
               msg = new CellMessage( 
                           new CellPath( path ) ,
                           pvlRequest            )  ;
               sendMessage( msg ) ;
               _pending.put( msg.getUOID() ,req.getMessage()  ) ;
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
         
      }else if( modifier.getActionEvent().equals("dismounted" ) ){
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
               _pending.put( msg.getUOID() , msg ) ;
            }
         }catch(Exception eee ){
            esay( "PANIC : internal send problem 54938 : "+eee ) ;
            return ;
         }
         return ;
      }else if( modifier.getActionEvent().equals("xxxx" ) ){
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
               drive.setAction( "dismounting" ) ;
            drive.close( CdbLockable.COMMIT ) ;
         }catch( Exception ee ){
            esay( "PANIC : internal DB problem 54738 : "+ee ) ;
            return ;
         }
         //
         // create the dismount request, the corresponding
         // message and send it to the pvrProxy.
         //
         PvrRequest pvrReq = new PvrRequestImpl(
                                    "dismount" ,
                                    pvrName ,
                                    cartridge ,
                                    driveName ,
                                    specific     ) ;
          
         CellMessage msg = new CellMessage( 
                           new CellPath( pvrName ) , pvrReq ) ; 
         
         try{
            synchronized( _sendLock ){
               sendMessage( msg ) ;
               _pending.put( msg.getUOID() , msg ) ;
            }
         }catch(Exception eee ){
            esay( "PANIC : internal send problem 54938 : "+eee ) ;
            return ;
         }
         return ;
      }
      esay( "processModifier Panic : "+ modifier.getActionEvent() ) ;
   }
   public String ac_show_pinboard = "<last n lines>" ;
   public String ac_show_pinboard_$_0_1( Args args )throws CommandException {
       StringBuffer sb = new StringBuffer(); ;
       int count = 20 ;
       try{
          if( args.argc() > 0 )count = Integer.parseInt( args.argv(0) ) ;
       }catch( Exception eee ){ }
       sb.append( " ---- Pinboard at "+ new Date().toString()+"\n" ) ;
       _status.dump( sb , count ) ;
       return sb.toString() ;
   }
  
}

