package eurogate.mover ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;
import eurogate.mover.drivers.scsi.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.nio.* ;
import java.net.* ;
import java.util.* ;

public class MoverEasy 
       extends CellAdapter
       implements Runnable, 
                  Logable {
  
  // EAGLE specific stuff
  private final int MINSIZE = (2 * 1024);
  private final int MAXSIZE = (64 * 1024);

  private CellNucleus _nucleus ;
  private String      _pvlPath ;
  private String      _deviceName ;
  private Args        _args ;
  private StateInfoUpdater _spray = null ;
  private StateInfo        _info ;
  private boolean          _weAreOnline = false ;
  private boolean          _weAreBusy   = false ;
  private Object           _busyLock    = new Object() ;
  private String           _owner       = "none" ;
  private Thread           _moverThread ;
  private MoverRequest     _activeRequest  = null ;
  private CellMessage      _activeMessage  = null ;
  private boolean          _havingProblems = false ;
  private String           _fakeHost       = null ;
  private long             _fileSize       = 0 ;
  private long             _rest           = 0 ;
  private double           _rate           = 0.0 ;
  private String           _cartLoaded     = null;
  private long             _problemCounter = 1 ;
  private Exception        _lastException  = null ;
  private boolean          _stopOnError    = false ;
  private int              _ioCounter      = 0 ;
  private int              _delay          = 0 ; // artifitial delay
  private ByteBuffer       _byteBuffer     = ByteBuffer.allocateDirect(MAXSIZE);
  private byte []          _byteArray      = new byte[MAXSIZE];
  private final String     __version       = "V0.1";
  

  // Logable interface implementations
  public void log(String s) {
    pin(s);
    super.say(s);
  }
  public void elog(String s) { log(s); }
  public void plog(String s) { log(s); }
 
  public void say( String s ){ pin(s) ; super.say(s) ; }
  public void esay( String s ){ pin(s) ; super.esay(s) ; }
   
   public MoverEasy( String name , String args ){
      super( name , args , false ) ;
      _nucleus = getNucleus() ;
      _args    = getArgs() ;
      
      if( _args.argc() < 2 ){
         start() ;
         kill() ;
         throw new 
         IllegalArgumentException( "Usage : ... <pvlPath> <deviceName>" ) ;
      }
      _pvlPath    = _args.argv(0);
      _deviceName = _args.argv(1);
      //      _info    = new StateInfo( getCellName() , true ) ;
      //      _spray   = new StateInfoUpdater( _nucleus , 20 ) ;
      //      _spray.addTarget( _pvlPath ) ;
      //      _spray.setInfo( _info ) ;
      _weAreOnline = true ;
      _weAreBusy   = false ;
      export();
      start() ;
      say( "Started " + __version) ;
   }
   public void cleanUp(){
      unregister() ;
   }
   private void unregister(){
      if( _spray == null )return ;
      _spray.setInfo( _info = new StateInfo( getCellName() , false ) ) ;
      _weAreOnline = false ;
      say( "Forced Unregistering" ) ;
   }
   public void getInfo( PrintWriter pw ){
      super.getInfo( pw ) ;
      pw.println( "  pvlPath      : "+_pvlPath ) ;
      pw.println( "  deviceName   : "+_deviceName ) ;
      pw.println( "  Cartridge    : " + ((_cartLoaded == null) ? "<none>" :
                                      _cartLoaded));
      pw.println( "  online       : "+_weAreOnline ) ;
      pw.println( "  busy         : "+_weAreBusy ) ;
      pw.println( "  problem      : "+_havingProblems ) ;
      pw.println( "  stopOnErrors : "+_stopOnError ) ;
      pw.println( "  rate         : "+_rate+" Kbytes/sec" ) ;
      pw.println( "  total        : "+_fileSize+" Kbytes" ) ;
      pw.println( "  rest         : "+_rest+" Kbytes" ) ;
      pw.println( "  I/O Counter  : "+_ioCounter ) ;
      pw.println( "  Delay        : "+_delay+" seconds" ) ;
      pw.println( "  Owner        : "+_owner ) ;
   
   }
   public String toString(){
      return "State="+
          (_weAreOnline?"Online":"Offline") +"/"+
          (_weAreBusy?"Busy":"Idle") ;
   }
   public void messageArrived( CellMessage msg ){
      say( "message arrived from : "+msg.getSourcePath() ) ;
      MoverRequest req = (MoverRequest) msg.getMessageObject() ;
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
         _owner     = "system" ;
      }
      _activeRequest = req ;
      _activeMessage = msg ; 
      _moverThread   = new Thread( this ) ;
      _moverThread.start() ;
   
   }

  public void run() {
    if( Thread.currentThread() == _moverThread ) {
      String cmd = _activeRequest.getActionCommand();
      String type = _activeRequest.getType();
      
      if (cmd.equals("i/o")) {  // io command put/get
	if( type.equals("put") ){
	  runPutMover();
	}else if( type.equals("get") ){
	  runGetMover();
        }else if( type.equals("remove") ){
          say("got remove command - ignored");
          returnFinal(0, "Done");
	}else {
	  esay("unknown I/O command-type '" + type + "'");
	  _activeRequest.setActionCommand("Mover-BAD");
	  _activeRequest.setReturnValue(1, "unknown command");
	  sendBack();
 	}
        
      }else if( cmd.equals("load") ){
	runLoadMover();
      }else if( cmd.equals("unload") ){
	runUnloadMover();
      }else if( cmd.equals("checkLabel") ){
	runCheckLabelMover();
      }else if( cmd.equals("writeLabel") ){
	runWriteLabelMover();
      }else{
	say("unknown command '" + cmd + "'");
	_activeRequest.setActionCommand("panic");
	_activeRequest.setReturnValue(1, "unknown command");
	sendBack();
      }
    }
  }
  private void runLoadMover() {
    String cart = _activeRequest.getCartridge();
    if (cart.length() > 0)
      _cartLoaded = "UnChecked: <" + cart + ">";
    returnFinal(0, "Done");
  }

  private void runUnloadMover() {
    String          error = null;
    String          cart  = _activeRequest.getCartridge();
    say("Unloading cartridge : "+cart+" (NOP)");

    _cartLoaded = null;
    returnFinal(0, "Done");
  }
  private Hashtable readLabel() throws IOException {
     String error       = null ;
     int bRead ;

     TapeDrive td = new TapeDrive( new ScsiDevice( _deviceName ) ) ;

     try {
        td.rewind();
        bRead = (int)td.read(_byteBuffer,(int)MAXSIZE);
	_byteBuffer.position(0);
	_byteBuffer.limit(bRead);
	_byteBuffer.get( _byteArray , 0 , bRead ) ;
     }catch( IOException e ){
        esay(error = "Tape error while reading label " + e);
        throw e ;
     }finally{
        //
        // try to close it
        //
        //try{ td.close() ; }catch( Exception e ){}
     }

     if( bRead <= 0 ){
       esay( error = "emty label" ) ;
       throw new IllegalArgumentException( error ) ;
     }

     String cont = new String(_byteArray);

     if( cont.length() <= 1 ){
       esay( error = "labeldata has no string contents");
       throw new IllegalArgumentException( error );
     }
     Hashtable hash = new Hashtable() ;

     StringTokenizer line = new StringTokenizer( cont, "\n");
     while( line.hasMoreTokens() ){
       String          lineElem = line.nextToken();
       StringTokenizer keyVal   = new StringTokenizer(lineElem, "=");

       if( keyVal.countTokens() != 2 ){
         //
         // the next easy will dump several KBytes into the 
         // pinboard ( very bad idea ) .
	 // esay("invalid label line - " + lineElem);
	 continue;
       }

       say("read label line - " + lineElem);

       hash.put( keyVal.nextToken() , keyVal.nextToken() ) ;
     }
     return hash ;
  }
  private void runCheckLabelMover() {
    String cart         = _activeRequest.getCartridge();
    String store        = _activeRequest.getStore();
    String storageGroup = _activeRequest.getStorageGroup();
    Hashtable keyHash   = null ;
    String    error     = null ;
    
    try{
    
       keyHash = readLabel() ;
       
    }catch( Exception e ){
       e.printStackTrace() ;
       esay(error = "Tape error while reading label " + e);
       returnFinal(11, error);
       return;
    }

    String cartname = (String)keyHash.get( "CARTNAME" ) ;
    if( cartname == null ){
        returnFinal(11, "Unknown Label type");
        return ;
    }else if( ! cartname.equals( cart ) ){
        returnFinal(11, "Label mismatch");
        _cartLoaded = cartname ;
        return ;
    }else{
        _cartLoaded = cartname ;
	returnFinal(0, "Done");
	return;
    }
  }

  private void runWriteLabelMover() {
    String store        = _activeRequest.getStore();
    String cart         = _activeRequest.getCartridge();
    String storageGroup = _activeRequest.getStorageGroup();
    String error        = null;
    String eorPos       = null;


    String label = "CARTNAME=" + cart + "\n" +
                   "STORE=" + store + "\n" +
                   "STORAGEGROUP=" + storageGroup + "\n" +
                   "CREATOR=EuroStore-HSMV0.1\n" +
                   "DATE="+( new  Date() ).toString()+"\n" ;


       byte[] info = label.getBytes();
       _byteBuffer.clear() ;
       _byteBuffer.put(info) ;

       try{

         TapeDrive td = new TapeDrive( new ScsiDevice( _deviceName ) ) ;
	 td.rewind();
	 td.write(_byteBuffer, MAXSIZE);
	 td.newFileMark();
	 TapePosition position = td.position();
	 eorPos = ""+position.position();

       }catch(Exception e) {
	 esay(error = "Tape exception on Label-Create: " + e);
	 returnFinal(1, error);
      }finally{
         // try{ to.close() ; }catch(Exception ee ){}
      }
    say(error = "Writing Label on " + _deviceName + 
                " cartridge " + cart + " done");
    _activeRequest.setPosition("", eorPos);
    returnFinal(0, "Done");
  }


  // fill complete buffer with bytes from net
  private void fillNetBuffer( DataInputStream in, 
                              byte[] buf,
			      int from, int num   ) 
               throws IOException {
    int rb; 
    try {
      while(num > 0) {
	if ((rb = in.read(buf, from, num)) <= 0)
	  throw new IOException("EOF/EIO on data socket (" + rb + ")");
	from += rb;
	num  -= rb;
      }
    }catch(IOException e){
      esay( "Problem in fillNetBuffer : "+e ) ;
      throw(e);
    }
  }

  private int padding(int number, int bound) {
    return((number == 0) ? 0 : ((bound - 1) - (((number) - 1) % (bound))));
  }

  private void runPutMover() {
    Socket               moverSocket   = null ;
    DataInputStream      inputStream   = null ;
    DataOutputStream     controlStream = null ;
    cpio                 cpio = null;
    BufferedOutputStream fo   = null;
    byte [] trailer    = new byte[MAXSIZE];
    String  error      = null ;
    File    f          = null;
    //
    // extract what we need for connecting the client
    //
    String host  = _fakeHost ==  null ? 
                   _activeRequest.getHostName() :
                   "localhost" ;
                   
    int    port  = _activeRequest.getHostPort() ;
    _fileSize    = _activeRequest.getFileSize() ;
    
    String clientId  = _activeRequest.getClientReqId() ;
    say(">>> PUT "+_activeRequest) ;
    String fileName = _activeRequest.getBfid();
    String eorPos   = _activeRequest.getEorPosition();
    String cart     = _activeRequest.getCartridge();
    String filePos  = null;  // location of new file to write
    long   mediaBytes = 0;
    long   totalBytes = 0;


    _ioCounter = 0 ;
    try {   // here comes the BIG... try
    
      if ( _delay > 0 ) {
	say( "Warning : delaying by "+_delay+" seconds" ) ;
	try { Thread.currentThread().sleep(_delay*1000) ;}
	catch (InterruptedException ie ) {}
	say( "Woken up" ) ;
      }

     TapeDrive td = new TapeDrive( new ScsiDevice( _deviceName ) ) ;

     cpio = new cpio(fileName, 
                      "PATHNAMES/PATHNAME." + fileName,
		      "FSINFO/PNFS_ID." + fileName, _fileSize, 0, 0);


      say( "Trying to connect to "+host+":"+port+")" ) ;
      moverSocket  = new Socket( host , port ) ;
      say( "Connected ... waiting for "+_fileSize+" bytes" ) ;
      inputStream   = new DataInputStream(  moverSocket.getInputStream() ) ;
      controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
      controlStream.writeUTF( "Hello-EuroStore-Client " + clientId ) ;

      say( "Starting data tranfer" ) ;

        try{
	    td.position(Long.parseLong(eorPos));
         }catch( Exception devIoE ){
            say( "PositioningEOR : ("+devIoE.getMessage()+") rewind/retry" ) ;
            td.rewind() ;
	    td.position(Long.parseLong(eorPos));
         }

	filePos = ""+td.position().position();

      say("positioned to " + filePos);

      if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	say( error = "Simulated IOException" ) ;
	throw new IOException( error ) ;
      }

      long start = System.currentTimeMillis() ;

      int pos  = 0 ;
      if( (pos = cpio.createHeader(_byteArray) ) < 0 ){ 
	throw new DevIOException("cpio header create failed", 1);
      }

      _rest = _fileSize;
      int toRead  = MAXSIZE - pos;  /* for the initial block */
      int toWrite = pos;

	
	// move data !
	while (_rest > 0) {
	  toRead = ( toRead <= _rest ) ? toRead : (int) _rest;
	  fillNetBuffer(inputStream, _byteArray, pos, toRead);
	  _ioCounter++;
	  toWrite += toRead;
	  
	  if ((_rest -= toRead) <= 0)  //  later buffer flush
	    break;
	  
	  if(toWrite != MAXSIZE)say("_toWrite garbled " + toWrite);
	 
	  _byteBuffer.clear() ;
	  _byteBuffer.put(_byteArray,0,toWrite); 
	  td.write(_byteBuffer, toWrite);
	  
	  totalBytes += toRead;
	  
	  toRead = MAXSIZE;
	  toWrite = pos = 0;
	}
        

      // create trailer cpio stuff
      if ((pos = cpio.createTrailer(trailer)) < 0)
	throw new DevIOException("cpio trailer create failed", 1);

      //
      // check if the trailer stuff fits into databuffer
      //
      if ((toWrite + pos) > MAXSIZE) {  
        //
        // doesn't fit
        //
	int left = MAXSIZE - toWrite;
	System.arraycopy(trailer, 0, _byteArray, toWrite, left);
        
	_byteBuffer.clear() ;
	_byteBuffer.put(_byteArray); 
        td.write(_byteBuffer,  MAXSIZE);
          
        totalBytes += MAXSIZE;
	toWrite     = pos - left;
        
	System.arraycopy(trailer, left, _byteArray, 0, toWrite);
			 
      }else{
        // 
        // fit within _byteArray
        //
	System.arraycopy(trailer, 0, _byteArray, toWrite, pos);
	toWrite += pos;
      }

      if (toWrite > 0) { 
        //
        // and the last one :-)
        //
	int freeBytes = MAXSIZE  - toWrite;
	int lastFlush = (MAXSIZE - freeBytes) +
	                padding((MAXSIZE - freeBytes), MINSIZE);
        //
	// zero remaining stuff
        //
	//for(int i=toWrite; i<lastFlush; i++) _byteArray[i] = 0;

	_byteBuffer.clear() ;
	_byteBuffer.put(_byteArray); 
        td.write(_byteBuffer, lastFlush);
        totalBytes += lastFlush;
      }
	 td.newFileMark();
	 eorPos = ""+td.position().position();
	 mediaBytes = _fileSize;

      say("moved bytes: " + _fileSize + " on media: " + mediaBytes);

      if( _fileSize == 0 ){
	say( "Zero byte file transfered" );
      }
      long  now  = System.currentTimeMillis() ;
      _rate = ( (double)_fileSize / (double)(now-start) )  ;
      say( ""+_fileSize+
           " Bytes transferred with "+_rate+" KBytes/sec" );

    } catch (DevIOException dioe) {
    
       if (_stopOnError) unregister();
       try { moverSocket.close() ; } catch (Exception eee) {}
       esay( error = "Exception for Tape access (" 
	     + _deviceName + ") " + dioe) ;
//       dioe.printStackTrace();
       _activeRequest.setResidualBytes(totalBytes);

       // check EOM condition
       if (dioe.getErrorCode() == 666) {
	 returnFinal(999, "EOM detected: " + dioe);
       } else {
	 returnFinal( 11 , error );
       }
       _lastException = dioe ;
       say( "<<< "+error ) ;
       return;

    }catch (Exception e ) {
       e.printStackTrace(); 
       if( _stopOnError )unregister() ;
       try { moverSocket.close() ; } catch(Exception eee){}
       esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
       e.printStackTrace();
       returnFinal( 11 , error ) ;
       _lastException = e ;
       say( "<<< "+error ) ;
       return ;
       
    } finally {
    
       say( "Closing tape output stream" );
       // try{ td.close()  ; }catch(Exception eee){} 
    }

    // sent ACK to client
    try {
       controlStream.writeUTF( "MACK "+clientId+" 0 "+_fileSize ) ;       
       say( "Data Mover finished successfully" ) ;
       _activeRequest.setPosition(filePos, eorPos);
       _activeRequest.setRealBytes(mediaBytes);
       returnFinal( 0 , "Done" ) ;
    } catch(Exception eee) {
       _lastException = eee ;
       esay( error = "IOException in sending MACK : "+eee) ;
       returnFinal( 13 , error ) ;
    }
    
    say( "Closing net input stream" ) ;
    try{ moverSocket.close() ; }catch(Exception eee){}
    say( "<<< Done" ) ;
    return ;
    
  }

  private void runGetMover() {
    Socket             moverSocket   = null ;
    DataOutputStream   outputStream  = null ;
    DataOutputStream   controlStream = null ;
    File               dataFile      = null ;
    //
    // NOTE: the blocksize should be stored in BB's db
    //
    String  error      = null ;
    int     rc   = 0 , r ;
    cpio    cpio = null;
    BufferedInputStream fi = null;
    File                f  = null;
    //
    // extract what we need for connecting the client
    //
    String host      = _fakeHost ==  null ? 
                       _activeRequest.getHostName() :
                       "localhost" ;
    int    port      = _activeRequest.getHostPort();
    String clientId  = _activeRequest.getClientReqId();    
    String fileName  = _activeRequest.getBfid();
    String filePos   = _activeRequest.getPosition();
    String cart      = _activeRequest.getCartridge();
    _fileSize        = _activeRequest.getFileSize();
    
    
    say(">>> GET "+_activeRequest) ;

    try {
     TapeDrive td = new TapeDrive( new ScsiDevice( _deviceName ) ) ;
    
    
      if ( _delay > 0 ) {
	say( "Warning : delaying by "+_delay+" seconds" ) ;
	try{ Thread.currentThread().sleep(_delay*1000) ; }
	catch(InterruptedException ie ){}
	say( "Woken up" ) ;
      }
      
      say( "Trying to connect to "+host+":"+port+")" ) ;
      moverSocket  = new Socket( host , port ) ;
      say( "Connected ... will send "+_fileSize+" bytes" ) ;
      controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
      outputStream  = controlStream ;
      controlStream.writeUTF( "Hello-EuroStore-Client "+clientId ) ;

      say("open tape and locate to '" + filePos + "'");

         try{
	    td.position(Long.parseLong(filePos));
         }catch( Exception devIoE ){
            say( "Positioning : ("+devIoE.getMessage()+") rewind/retry" ) ;
            td.rewind() ;
	    td.position(Long.parseLong(filePos));
         }
      
      _rest = _fileSize ;
      int pos  = 0 ;
      say( "Starting data tranfer" ) ;
      
      
      if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	say( error = "Simulated IOException" ) ;
	throw new IOException( error ) ;
      }
      
      long start = System.currentTimeMillis();
      
      _ioCounter = 0 ;
      boolean headerDone = false;
      int     rDone;
      
      cpio = new cpio(fileName, 
                      "PATHNAMES/PATHNAME." + fileName,
		      "FSINFO/PNFS_ID." + fileName, 
                      _fileSize, 0, 0); 

	
	while(_rest > 0) {
	  rDone = (int)td.read(_byteBuffer,MAXSIZE);
          _byteBuffer.position(0);
	  _byteBuffer.limit(rDone);
	  _byteBuffer.get( _byteArray , 0 , rDone ) ;

	  _ioCounter++;
	  if( ! headerDone  ) { 
            //
            // process cpio header
            //
	    // 
	    System.out.print("Header : ");
	    for( int i= 0 ;  i < 128 ; i++ )System.out.print(" "+_byteArray[i]);
	    System.out.println("");
	    if( (pos = cpio.checkHeader(_byteArray) ) < 0 ||
          	pos > rDone  )
	      throw new DevIOException("invalid CPIO header found : pos="+pos+";rDone="+rDone);
              
	    say("cpio headersize: " + pos);
	    headerDone = true;
	    rDone     -= pos;    // adjust user byte count
	  }
	  if( rDone > _rest )rDone = (int) _rest;
	  
	  outputStream.write(_byteArray, pos, rDone);
          
	  pos    = 0;
	  _rest -= rDone;
	}
      
      if( _fileSize == 0 )say( "Zero byte file transfered" );
      
      long   now  = System.currentTimeMillis() ;
      _rate = ( (double)_fileSize / (double)(now-start) )  ;
      say( ""+_fileSize+
           " Bytes transferred with "+_rate+" KBytes/sec" );

      //long mediaBytes = ti.bytesOnMedia();
      
    } catch (DevIOException dioe) {
       if (_stopOnError) unregister() ;
       try { moverSocket.close(); } catch (Exception eee) {}
       esay( error = "Exception in tape handling " + dioe);
       returnFinal( 11 , error ) ;
       _lastException = dioe ;
       say( "<<< "+error ) ;
       return;
    } catch (Exception e ) {
       e.printStackTrace() ;
       if ( _stopOnError ) unregister() ;
       try { moverSocket.close() ; } catch (Exception eee) {}
       esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
       returnFinal( 11 , error ) ;
       _lastException = e ;
       say( "<<< "+error ) ;
       return ;
    } finally {
       say( "Closing file input streams" ) ;
      //  try { ti.close(); } catch (Exception eee) {} 
    }
    
    try {
       controlStream.writeUTF(error = "MACK "+clientId+" 0 "+_fileSize ) ;
       say("sent to client '" + error + "'");
       say( "Data Mover finished successfully" ) ;
       returnFinal( 0 , "Done" ) ;
    } catch ( Exception eee ) {
       _lastException = eee ;
       esay( error= "IOException in sending MACK : "+eee ) ;
       returnFinal( 13 , error ) ;
    }

    say( "Closing net input stream" ) ;
    try { moverSocket.close() ; } catch (Exception eee) {}
    say( "<<< Done" ) ;
    return ;
  }

  private void returnFinal( int rc , String msg ){
    _activeRequest.setReturnValue( rc , msg ) ;
    String cmd = _activeRequest.getActionCommand();
    _activeRequest.setActionCommand(cmd+"-ready");
    sendBack() ;
  }

  private void sendBack(){
    synchronized ( _busyLock ) {
      _weAreBusy = false ;
      _owner     = "none" ;
    }
    _activeMessage.revertDirection() ;
    try{
      sendMessage( _activeMessage ) ;
    }catch(Exception e2 ){
      esay( "Can't return message to PVL "+e2 ) ;
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
   public String ac_show_cartridge( Args args ){
       String cart = _cartLoaded ;
       if( cart == null ){
          return "No cartridge loaded" ;
       }else{
          return "Cartridge : "+cart  ;
       }
   }
   public String ac_set_busy(Args args ){
      synchronized( _busyLock ){
          if( _weAreBusy )return "Failed : mover is busy : "+_owner ;
          _weAreBusy = true ;
          _owner     = "com-set-busy" ;
          return "O.K" ;
      }
   }
   public String ac_unset_busy(Args args ){
      synchronized( _busyLock ){
          _weAreBusy = false ;
          _owner     = "none" ;
          return "O.K" ;
      }
   }
   private class AsyncCheckLabel implements Runnable {
       private CellMessage _msg    = null ;
       private Thread      _worker = null ;
       public AsyncCheckLabel( CellMessage msg ){
          _msg = msg ;
          _worker = new Thread(this) ;
          _worker.start() ;
       }
       public void run(){
          if( Thread.currentThread() == _worker ){
             say( "Starting check-label thread" ) ;
             try{
                reply( dumpLabel( readLabel()  ) ) ;               
             }catch( Exception e ){
                reply( "Exception : "+e ) ;
             }
             synchronized( _busyLock ){
                _weAreBusy = false ;
                _owner     = "none" ;
             }
             say( "Check-label thread done" ) ;
          }
       }
       private void reply( String str ){
          _msg.setMessageObject( str ) ;
          _msg.revertDirection() ;
          try{
             sendMessage( _msg ) ;
          }catch(Exception ee ){
             esay( "Problem replying : "+ee ) ;
          }
       }
       private String dumpLabel( Hashtable hash ){
          Enumeration  e   = hash.keys() ;
          StringBuffer sb  = new StringBuffer() ;
          String       key = null ;
          while( e.hasMoreElements() ){
             key = (String)e.nextElement() ;
             sb.append( key ).append( " -> " ).
                append( (String)hash.get(key) ).append("\n") ;          
          }
          return sb.toString()  ;
       }
   }
   public String ac_check_label(Args args ){
      synchronized( _busyLock ){
          if( _weAreBusy )return "Failed : mover is busy" ;
          _weAreBusy = true ;
          _owner     = "com-check-label" ;
      }
      new AsyncCheckLabel( getThisMessage() ) ;
      return null ;
      
   }
}
