package eurogate.mover ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

public class Mover extends CellAdapter implements Runnable {

   private CellNucleus _nucleus ;
   private String      _pvlPath ;
   private String      _devBase ;
   private Args        _args ;
   private StateInfoUpdater _spray = null ;
   private StateInfo        _info ;
   private boolean          _weAreOnline = false ;
   private boolean          _weAreBusy   = false ;
   private Object           _busyLock  = new Object() ;
   private Thread           _moverThread ;
   private MoverRequest   _activeRequest  = null ;
   private CellMessage      _activeMessage  = null ;
   private boolean          _havingProblems = false ;
   private String           _fakeHost       = null ;
   private long             _fileSize       = 0 ;
   private long             _rest           = 0 ;
   private double           _rate           = 0.0 ;
   private long             _problemCounter = 1 ;
   private Exception        _lastException  = null ;
   private boolean          _stopOnError    = false ;
   private int              _ioCounter      = 0 ;
   private int              _delay          = 0 ; // artifitial delay (debug only)
   
   public Mover( String name , String args ){
      super( name , args , false ) ;
      _nucleus = getNucleus() ;
      _args    = getArgs() ;
      
      if( _args.argc() < 2 ){
         start() ;
         kill() ;
         throw new 
         IllegalArgumentException( "Usage : ... <pvlPath> <devBase>" ) ;
      }
      _pvlPath = _args.argv(0) ;
      _devBase = _args.argv(1) ;
      _info    = new StateInfo( getCellName() , true ) ;
      _spray   = new StateInfoUpdater( _nucleus , 20 ) ;
      _spray.addTarget( _pvlPath ) ;
      _spray.setInfo( _info ) ;
      _weAreOnline = true ;
      _weAreBusy   = false ;
      start() ;
      pin( "Started" ) ;
   }
   public void cleanUp(){
      unregister() ;
   }
   private void unregister(){
      if( _spray == null )return ;
      _spray.setInfo( _info = new StateInfo( getCellName() , false ) ) ;
      _weAreOnline = false ;
      pin( "Forced Unregistering" ) ;
   }
   public void getInfo( PrintWriter pw ){
      super.getInfo( pw ) ;
      pw.println( "  pvlPath      : "+_pvlPath ) ;
      pw.println( "  devBase      : "+_devBase ) ;
      pw.println( "  online       : "+_weAreOnline ) ;
      pw.println( "  busy         : "+_weAreBusy ) ;
      pw.println( "  problem      : "+_havingProblems ) ;
      pw.println( "  stopOnErrors : "+_stopOnError ) ;
      pw.println( "  rate         : "+_rate+" Kbytes/sec" ) ;
      pw.println( "  total        : "+_fileSize+" Kbytes" ) ;
      pw.println( "  rest         : "+_rest+" Kbytes" ) ;
      pw.println( "  I/O Counter  : "+_ioCounter ) ;
      pw.println( "  Delay        : "+_delay+" seconds" ) ;
   
   }
   public String toString(){
      return "State="+
          (_weAreOnline?"Online":"Offline") +"/"+
          (_weAreBusy?"Busy":"Idle") ;
   }
   public void messageArrived( CellMessage msg ){
      say( "message arrived from : "+msg.getSourcePath() ) ;
      MoverRequest req = (MoverRequest)msg.getMessageObject() ;
      synchronized( _busyLock ){
         if( _weAreBusy ){
            req.setReturnValue( 44 , "We are busy" ) ;
            try{
               msg.revertDirection() ;
               sendMessage( msg ) ;
            }catch(Exception eee ){
               esay( "Can't return message to StarGate" ) ;
            }
         }
         _weAreBusy = true ;
      }
      _activeRequest = req ;
      _activeMessage = msg ; 
      _moverThread = new Thread( this ) ;
      _moverThread.start() ;
   
   }
   public void run(){
   
      if( Thread.currentThread() == _moverThread ){
      
         String ioType = _activeRequest.getType() ;

         if(      ioType.equals("put")    )runPutMover() ;
         else if( ioType.equals("get")    )runGetMover() ;
         else if( ioType.equals("remove") )runRemoveMover() ;
            
      }
   }
   private File filepathFromRequest( MoverRequest req )
           throws Exception {
       String storageGroup = req.getStorageGroup() ;
       File dir        = new File( _devBase ) ;
       if( ! dir.isDirectory() )
          throw new 
          Exception( "Mover directory not found for "+req ) ;
       return  new File( dir , req.getBfid() ) ;
   }
   private void runRemoveMover(){
       File outputFile = null ;
       try{
          outputFile = filepathFromRequest( _activeRequest ) ;
          if( ! outputFile.delete() )
             throw new 
             Exception( "Couldn't delete "+_activeRequest.getBfid() ) ;
       }catch( Exception pe ){
          String er = "PANIC : "+pe.getMessage() ;
          esay( er ) ;
          _activeRequest.setActionCommand( "Mover-OK" ) ;
          _activeRequest.setReturnValue( 44 , er ) ;
          sendBack() ;
          return ;
       }
       _activeRequest.setActionCommand( "Mover-OK" ) ;
       _activeRequest.setReturnValue( 0 , "Done" ) ;
       
       sendBack() ;
       
       return ;
   }
   private void runPutMover(){
       Socket             moverSocket   = null ;
       DataInputStream    inputStream   = null ;
       DataOutputStream   outputStream  = null ;
       DataOutputStream   controlStream = null ;
       File               dataFile      = null ;
       byte [] dataBuffer = new byte[0x10000] ;
       String error = null ;
       int rc = 0 , r ;
       //
       // extract what we need for connecting the client
       //
       String host      = _fakeHost ==  null ? 
                          _activeRequest.getHostName() :
                          _fakeHost ;
       int    port      = _activeRequest.getHostPort() ;
             _fileSize  = _activeRequest.getFileSize() ;
       String clientId  = _activeRequest.getClientReqId() ;
       pin(">>> PUT "+_activeRequest) ;
       
       try{
          dataFile = filepathFromRequest( _activeRequest ) ;
       }catch( Exception pe ){
	  if( _stopOnError )unregister() ;
          _lastException = pe ;
          esay( error = "PANIC : "+pe.getMessage() ) ;
          returnFinal( 44 , error ) ;
	  pin( "<<< "+error ) ;
          return ;
       }
       try{
          if( _delay > 0 ){
	     pin( "Warning : delaying by "+_delay+" seconds" ) ;
	     try{ Thread.currentThread().sleep(_delay*1000) ;}
	     catch(InterruptedException ie ){}
	     pin( "Woken up" ) ;
	  }
          outputStream   = new DataOutputStream(
                                new FileOutputStream( dataFile ) 
                           ) ;
          pin( "Trying to connect to "+host+":"+port+")" ) ;
          moverSocket  = new Socket( host , port ) ;
          pin( "Connected ... waiting for "+_fileSize+
                       " bytes ; id="+clientId ) ;
          inputStream   = new DataInputStream( moverSocket.getInputStream() ) ;
          controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
          controlStream.writeUTF( "Hello-EuroStore-Client "+clientId ) ;
          controlStream.flush() ;
             _rest = _fileSize ;
          int pos  = 0 ;
          pin( "Starting data tranfer" ) ;
          if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	      pin( error = "Simulated IOException" ) ;
	      throw new IOException( error ) ;
	  }
          long start = System.currentTimeMillis() ;
	  _ioCounter = 0 ;
          while( _rest > 0 ){
             pos = 0 ; 
             r = ((long)dataBuffer.length) < _rest ? dataBuffer.length : (int)_rest ;
             while( r > 0 ){
                rc = inputStream.read( dataBuffer , pos , r ) ;
                pin( "inputStream.read : "+rc ) ;
		_ioCounter ++ ;
                if( rc <= 0 )break ;
                pos  += rc ;
               _rest -= rc ;
                r    -= rc ;
             }
             if( rc <= 0 )break ;
             outputStream.write( dataBuffer , 0 , pos ) ;
              
          }
	  if( _fileSize == 0 ){
             pin( "Zero byte file transfered" );
	  }else if( rc > 0 ){
             long   now  = System.currentTimeMillis() ;
                   _rate = ( (double)_fileSize / (double)(now-start) )  ;
             pin( ""+_fileSize+" Bytes transferred with "+_rate+" KBytes/sec" );
          }
       }catch(Exception e ){
	  if( _stopOnError )unregister() ;
          try{ moverSocket.close() ; }catch(Exception eee){}
          esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
          returnFinal( 11 , error ) ;
          dataFile.delete() ;
	  _lastException = e ;
	  pin( "<<< "+error ) ;
          return ;
       }finally{
          pin( "Closing file output streams" ) ;
          try{ outputStream.close()  ; }catch(Exception eee){} 
       }
       if( rc <= 0 ){
	  if( _stopOnError )unregister() ;
          esay( error = "PANIC : unexpected end of stream rc="+rc ) ;
	  pin( error ) ;
          returnFinal( 12 , error ) ;
          dataFile.delete() ;
       }else{
          try{
             controlStream.writeUTF( "MACK "+clientId+" 0 "+_fileSize ) ;       
             pin( "Data Mover finished successfully" ) ;
             returnFinal( 0 , "Done" ) ;
          }catch( Exception eee ){
	     _lastException = eee ;
             esay( error = "IOException in sending MACK : "+eee) ;
             pin( error ) ;
             returnFinal( 13 , error ) ;
          }
       }
       pin( "Closing net input stream" ) ;
       try{ moverSocket.close() ; }catch(Exception eee){}
       pin( "<<< Done" ) ;
       return ;
      
   }
   private void runGetMover(){
       Socket             moverSocket   = null ;
       DataInputStream    inputStream   = null ;
       DataOutputStream   outputStream  = null ;
       DataOutputStream   controlStream = null ;
       File               dataFile      = null ;
       byte [] dataBuffer = new byte[0x10000] ;
       String error = null ;
       int rc = 0 , r ;
       //
       // extract what we need for connecting the client
       //
       String host      = _fakeHost ==  null ? 
                          _activeRequest.getHostName() :
                          "localhost" ;
       int    port      = _activeRequest.getHostPort() ;
             _fileSize  = _activeRequest.getFileSize() ;
       String clientId  = _activeRequest.getClientReqId() ;

       pin(">>> GET "+_activeRequest) ;
       try{
          dataFile = filepathFromRequest( _activeRequest ) ;
       }catch( Exception pe ){
	  if( _stopOnError )unregister() ;
          _lastException = pe ;
          esay( error = "PANIC : "+pe.getMessage() ) ;
          returnFinal( 44 , error ) ;
	  pin( "<<< "+error ) ;
          return ;
       }
       try{
          if( _delay > 0 ){
	     pin( "Warning : delaying by "+_delay+" seconds" ) ;
	     try{ Thread.currentThread().sleep(_delay*1000) ; }
	     catch(InterruptedException ie ){}
	     pin( "Woken up" ) ;
	  }
          inputStream   = new DataInputStream(
                                new FileInputStream( dataFile ) 
                           ) ;
          pin( "Trying to connect to "+host+":"+port+")" ) ;
          moverSocket  = new Socket( host , port ) ;
          pin( "Connected ... will send "+_fileSize+" bytes" ) ;
          controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
          outputStream  = controlStream ;
          controlStream.writeUTF( "Hello-EuroStore-Client "+clientId ) ;
             _rest = (int) _fileSize ;
          int pos  = 0 ;
          pin( "Starting data tranfer" ) ;
          if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	      pin( error = "Simulated IOException" ) ;
	      throw new IOException( error ) ;
	  }
          long start = System.currentTimeMillis() ;
	  _ioCounter = 0 ;
          while( _rest > 0 ){
             pos = 0 ; 
             r = ((long)dataBuffer.length) < _rest ? dataBuffer.length : (int)_rest ;
             while( r > 0 ){
                rc = inputStream.read( dataBuffer , pos , r ) ;
		_ioCounter++ ;
                if( rc <= 0 )break ;
                pos  += rc ;
               _rest -= rc ;
                r    -= rc ;
             }
             if( rc <= 0 )break ;
             outputStream.write( dataBuffer , 0 , pos ) ;
              
          }
	  if( _fileSize == 0 ){
             pin( "Zero byte file transfered" );
	  }else if( rc > 0 ){
             long   now  = System.currentTimeMillis() ;
                   _rate = ( (double)_fileSize / (double)(now-start) )  ;
             pin( ""+_fileSize+" Bytes transferred with "+_rate+" KBytes/sec" );
          }
       }catch(Exception e ){
	  if( _stopOnError )unregister() ;
          try{ moverSocket.close() ; }catch(Exception eee){}
          esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
          returnFinal( 11 , error ) ;
	  _lastException = e ;
	  pin( "<<< "+error ) ;
          return ;
       }finally{
          pin( "Closing file input streams" ) ;
          try{ inputStream.close()  ; }catch(Exception eee){} 
       }
       if( rc <= 0 ){
	  if( _stopOnError )unregister() ;
          esay( error = "PANIC : unexpected end of stream" ) ;
	  pin( error ) ;
          returnFinal( 12 , error ) ;
       }else{
          try{
             controlStream.writeUTF( "MACK "+clientId+" 0 "+_fileSize ) ;       
             pin( "Data Mover finished successfully" ) ;
             returnFinal( 0 , "Done" ) ;
          }catch( Exception eee ){
	     _lastException = eee ;
             esay( error= "IOException in sending MACK : "+eee ) ;
	     pin( error ) ;
             returnFinal( 13 , error ) ;
          }
       }
       pin( "Closing net input stream" ) ;
       try{ moverSocket.close() ; }catch(Exception eee){}
       pin( "<<< Done" ) ;
       return ;
      
   }
   private void returnFinal( int rc , String msg ){
       _activeRequest.setActionCommand( "Mover-OK" ) ;
       _activeRequest.setReturnValue( rc , msg ) ;
       
       sendBack() ;
   }
   private void sendBack(){
       _activeMessage.revertDirection() ;
       try{
          sendMessage( _activeMessage ) ;
       }catch(Exception e2 ){
          esay( "Can't return message to Door "+e2 ) ;
       }
       synchronized( _busyLock ){
          _weAreBusy = false ;
       }
   }
   public String ac_show_delay( Args args ){
      return "Delay is "+_delay+" seconds" ;
   }
   public String hh_set_delay = "<seconds>" ;
   public String ac_set_delay_$_1( Args args )throws CommandException {
       _delay = 0 ;
       try{
          _delay = Integer.parseInt( args.argv(0) ) ;
       }catch(Exception e){}
       return "Delay set to "+_delay+" seconds" ;
   }
   public String hh_set_state = 
                 "online|offline|problem|noproblem|stoponerror|dontstoponerror" ;
   
   public String ac_set_state_$_1( Args args )throws CommandException {
      if( args.argv(0).equals( "online" ) ){
         if( _weAreOnline ){
            throw new CommandException( 3 , "Already Online" ) ;
         }else{
           _info    = new StateInfo( getCellName() , true ) ;
           _spray.setInfo( _info ) ;
           _weAreOnline = true ;
           return "Mover : "+getCellName()+" set online" ;
         }
      }else if( args.argv(0).equals( "offline" ) ){
         if( ! _weAreOnline ){
            throw new CommandException( 3 , "Already Offline" ) ;
         }else{
            _spray.setInfo( _info = new StateInfo( getCellName() , false ) ) ;
           _weAreOnline = false ;
            return "Mover : "+getCellName()+" set offline" ;
         }
      }else if( args.argv(0).equals( "problem" ) ){
         _havingProblems = true ;
      }else if( args.argv(0).equals( "noproblem" ) ){
         _havingProblems = false ;
      }else if( args.argv(0).equals( "stoponerror" ) ){
         _stopOnError = true ;
      }else if( args.argv(0).equals( "dontstoponerror" ) ){
         _stopOnError = false ;
      }else
        throw new CommandException(3 , "Unknown state : "+args.argv(0) ) ;
      return "Done" ;
   }
   public String hh_set_destinationhost = "true|<hostname>" ;
   public String ac_set_destinationhost_$_1( Args args ){
       _fakeHost = args.argv(0).equals("true") ? null : args.argv(0) ;
       return "" ; 
   }
}
