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
  * @version 0.1, 15 Feb 1998
  * 
 */
 
public class      DummyPvrCell 
       extends    CellAdapter   {

   private Args        _args    = null ;
   private CellNucleus _nucleus = null ;
   private String      _dbName  = null ;
   private PvrDb       _pvrDb   = null ;
   private long        _minWait = 1000 ;
   private long        _maxWait = 5000 ;
   private boolean     _isNewDatabase = false ;
   
   public DummyPvrCell( String name , String args ) throws Exception {
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          
          if( _args.argc() < 1 ) 
              throw new
              IllegalArgumentException( "Usage : ... <pvrDbName>" ) ;
              
          _dbName = _args.argv(0) ;
          
          try{
             String t = null ;
             if( ( t = _args.getOpt("minWait") ) != null ){
                _minWait = Integer.parseInt( t ) * 1000 ;             
             }
             if( ( t = _args.getOpt("maxWait") ) != null ){
                _maxWait = Integer.parseInt( t ) * 1000 ;             
             }else{
                _maxWait = _minWait * 2 ;
             }
          
          }catch(Exception e ){
          }
                
          try{
             _pvrDb = new PvrDb( new File( _dbName ) , false ) ;
             _isNewDatabase = false ;
          }catch( Exception ee ){
             _pvrDb = new PvrDb( new File( _dbName ) , true ) ;
             _isNewDatabase = true ;
          }
          say( "Database ok." ) ;
          say( "Delay "+_minWait+" ... "+_maxWait ) ;
       }catch( Exception e ){
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
      long waitTime = 
        (long)
        (((double)(_maxWait - _minWait ) * Math.random() ) +
         ((double)_minWait)) ;
      new RequestTask( msg , waitTime ) ;
  }
  public void getInfo( PrintWriter pw ){
     pw.println( "Database : "+_dbName ) ;
     pw.println( "Min Wait : "+_minWait+" seconds" ) ;
     pw.println( "Max Wait : "+_maxWait+" seconds" ) ;
  }
  private class RequestTask implements Runnable {
     private Thread      _thread   = null ;
     private CellMessage _message  = null ;
     private long        _waitTime = 0L ;
     private RequestTask( CellMessage msg , long waitTime ){
        _thread   = _nucleus.newThread(this,"requestTask") ;
        _message  = msg ;
        _waitTime = waitTime ;
        _thread.start() ;
     }
     public void run(){
        Object req = _message.getMessageObject() ;
        if( req instanceof PvrRequest ){

           
           if( processPvrRequest( _message , (PvrRequest)req  ) ){
             try{
             
                 Thread.currentThread().sleep(_waitTime) ;
                 
             }catch( InterruptedException ie ){
                 say( "requestTast interrupted" ) ;
             }           
           
           }

           sendBack( _message ) ;

        }else{
           esay( "Unidentified message arrived : "+req.getClass().getName() ) ;
           return ;
        }
     }
  }
  private boolean processPvrRequest( CellMessage msg , PvrRequest request ){
      String             command  = request.getActionCommand() ;
      PvrDriveHandle     drive    = null ;
      PvrCartridgeHandle cart     = null ;
      boolean            doWait   = true ;
      
      say( "processPvrRequest : Req : "+request ) ;
      if( command.equals("newdrive") ){
         try{
            drive = _pvrDb.getDriveByName( request.getSpecificDrive() ) ;
            drive.open( CdbLockable.READ ) ;
              String cartridge  =  drive.getCartridge()  ;
            drive.close( CdbLockable.COMMIT ) ;
            request.setCartridge( cartridge  ) ;
            request.setReturnValue(
                 cartridge.equals("empty")?1:3,
                 cartridge
            ) ;
         }catch( Exception ee ){
            esay(ee);
            request.setReturnValue(33,ee.toString()) ;
         }
         doWait = false ;            
      }else if( command.equals( "terminate" ) ){
         doWait = false ;
      }else if( command.equals( "mount" ) ){
        try{
            
            drive = _pvrDb.getDriveByName( request.getSpecificDrive() ) ;
            //
            // check if the cartridge exists...
            //
            cart = _pvrDb.getCartridgeByName(request.getCartridge());
            try{cart.close( CdbLockable.COMMIT ) ;}catch(Exception ie){}
            //
            drive.open( CdbLockable.WRITE ) ;
              String cartridge = drive.getCartridge()  ;
              if( cartridge.equals(request.getCartridge() ) ){
                 // is ok.
                 doWait = false ;
              }else if( cartridge.equals( "empty" )   ){
                 
                 
                 drive.setCartridge( request.getCartridge() ) ;
                 doWait = true ;
              }else{
                 request.setReturnValue(2,"Drive not empty") ;
                 doWait = false ;
              }
         }catch( Exception ee ){
            esay(ee);
            request.setReturnValue(33,ee.toString()) ;
            doWait = false ;
         }finally{
            try{drive.close( CdbLockable.COMMIT ) ;}catch(Exception ie){}
         }
      }else if( command.equals( "dismount" ) ){
        try{
            drive = _pvrDb.getDriveByName( request.getSpecificDrive() ) ;
            drive.open( CdbLockable.WRITE ) ;
              String cartridge = drive.getCartridge()  ;
              if( ! cartridge.equals( "empty" ) ){
                 request.setCartridge( cartridge ) ;
                 drive.setCartridge( "empty" ) ;
                 doWait = true ;
              }else{
                 doWait = false ;
              }         
         }catch( Exception ee ){
            esay(ee);
            request.setReturnValue(33,ee.toString()) ;
            doWait = false ;
         }finally{
            try{drive.close( CdbLockable.COMMIT ) ;}catch(Exception ie){}
         }
      }else{
         String problem = "Illegal request received : "+command ;
         esay( problem ) ;
         request.setReturnValue( 999 , problem ) ;
         doWait = true ;
      }
      say( "processPvrRequest : Done : "+request ) ;
      return doWait ;
      
  }
  public String ac_set_minWait_$_1( Args args ) throws Exception {
      _minWait = Integer.parseInt( args.argv(0) ) * 1000 ;
      return "Delay "+_minWait+" ... "+_maxWait+ "msec" ;
  }
  public String ac_set_maxWait_$_1( Args args ) throws Exception {
      _maxWait = Integer.parseInt( args.argv(0) ) * 1000 ;
      return "Delay "+_minWait+" ... "+_maxWait+ "msec" ;
  }
  public String hh_create_drive = "-pvr=<pvrName> <driveName>";
  public String ac_create_drive_$_1( Args args ) throws Exception {
      String pvrName  = args.getOpt( "pvr" ) ;
      if( ( pvrName == null ) || ( ! pvrName.equals(getCellName()) ) )
         return "Not for Us" ;
      PvrDriveHandle drive = _pvrDb.createDrive( args.argv(0) ) ;
       drive.open( CdbLockable.WRITE ) ;
         drive.setCartridge("empty")  ;
       drive.close( CdbLockable.COMMIT ) ;
      return "" ;
  }
  public String hh_create_cartridge = "-pvr=<pvrName> <cartridgeName>";
  public String ac_create_cartridge_$_1( Args args ) throws Exception {
      String pvrName  = args.getOpt( "pvr" ) ;
      if( ( pvrName == null ) || ( ! pvrName.equals(getCellName()) ) )
         return "Not for Us" ;
      _pvrDb.createCartridge( args.argv(0) ) ;
      return "" ;
  }
  public void say( String str ){
     super.say( str ) ;
     pin( str ) ;
  }
  public void esay( String str ){
     super.esay( str ) ;
     pin( str ) ;
  }
  private void sendBack( CellMessage msg ){
     msg.revertDirection() ;
     Object returnObject = msg.getMessageObject() ;
     if( returnObject instanceof EurogateRequest ){
        EurogateRequest req = (EurogateRequest)returnObject ;
        req.setActionCommand(req.getActionCommand()+"-ready") ;
     }
     try{
        sendMessage( msg ) ;
     }catch( Exception msge ){
        esay( "PANIC : can't reply message : "+msge ) ;
     }
  }
   
   
}
