package eurogate.mover ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

public class MoverEagle extends CellAdapter
  implements Runnable, dmg.util.Logable {
  
  private CellNucleus _nucleus ;
  private String      _pvlPath ;
  private String      _deviceName ;
  private Args        _args ;
  private StateInfoUpdater _spray = null ;
  private StateInfo        _info ;
  private boolean          _weAreOnline = false ;
  private boolean          _weAreBusy   = false ;
  private Object           _busyLock  = new Object() ;
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
  //   private Pinboard         _status         = new Pinboard(200) ;
  private boolean          _stopOnError    = false ;
  private int              _ioCounter      = 0 ;
  private int              _delay          = 0 ; // artifitial delay
  private boolean          _disk           = false; // root dir for disk mover
  private final String version = "V0.1";
  
  // EAGLE specific stuff
  private final int MINSIZE = (2 * 1024);
  private final int MAXSIZE = (64 * 1024);

  // Logable interface implementations
  public void log(String s) {
    pin(s);
    say(s);
  }
  public void elog(String s) { log(s); }
  public void plog(String s) { log(s); }

   
   public MoverEagle( String name , String args ){
      super( name , args , false ) ;
      _nucleus = getNucleus() ;
      _args    = getArgs() ;
      
      if( _args.argc() < 2 ){
         start() ;
         kill() ;
         throw new 
         IllegalArgumentException( "Usage : ... <pvlPath> <deviceName>" ) ;
      }
      _pvlPath = _args.argv(0);
      _deviceName = _args.argv(1);
      if (_args.argc() > 2) {
	_disk = _args.argv(2).compareTo("disk") == 0;
	if (_disk) {
	  File _f = new File(_deviceName);
	  if (!_f.exists() ||
	      !_f.isDirectory() ||
	      !_f.canWrite() ||
	      !_f.canRead()) {
	    throw new IllegalArgumentException(
		 "<deviceName> is not a directory or accessible");
	  }
	}
      }
      //      _info    = new StateInfo( getCellName() , true ) ;
      //      _spray   = new StateInfoUpdater( _nucleus , 20 ) ;
      //      _spray.addTarget( _pvlPath ) ;
      //      _spray.setInfo( _info ) ;
      _weAreOnline = true ;
      _weAreBusy   = false ;
      export();
      start() ;
      pin( "Started " + version) ;
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
      }
      _activeRequest = req ;
      _activeMessage = msg ; 
      _moverThread = new Thread( this ) ;
      _moverThread.start() ;
   
   }

  public void run() {
    if( Thread.currentThread() == _moverThread ) {
      String cmd = _activeRequest.getActionCommand();
      String type = _activeRequest.getType();
      
      if (cmd.equals("i/o")) {  // io command put/get
	if (type.equals("put"))
	  runPutMover();
	else if (type.equals("get"))
	  runGetMover();
        else if (type.equals("remove")) {
          pin("got remove command - ignored");
          returnFinal(0, "Done");
	}
	else {
	  esay("unknown I/O command type " + type);
	  pin("unknown I/O command-type '" + type + "'");
	  _activeRequest.setActionCommand("Mover-BAD");
	  _activeRequest.setReturnValue(1, "unknown command");
	  sendBack();
 	}
      } else if (cmd.equals("load")) {
	runLoadMover();
      } else if (cmd.equals("unload")) {
	runUnloadMover();
      } else if (cmd.equals("checkLabel")) {
	runCheckLabelMover();
      } else if (cmd.equals("writeLabel")) {
	runWriteLabelMover();
      } else {
	pin("unknown command '" + cmd + "'");
	_activeRequest.setActionCommand("panic");
	_activeRequest.setReturnValue(1, "unknown command");
	sendBack();
      }
    }
  }

  //   private File filepathFromRequest( RequestShuttle req )
  //        throws Exception {
  //   String storageGroup = req.getStorageGroup() ;
//       File dir = new File( _devBase , storageGroup ) ;
  //   File dir        = new File( _devBase ) ;
  //   if( ! dir.isDirectory() )
  //      throw new 
  //      Exception( "Mover directory not found for "+req ) ;
  //   return  new File( dir , req.getBfid() ) ;
  // }

  private void runLoadMover() {
    String cart = _activeRequest.getCartridge();
    if (cart.length() > 0)
      _cartLoaded = new String("UnChecked: <" + cart + ">");
    returnFinal(0, "Done");
  }

  private void runUnloadMover() {
    if (_disk) {
      _cartLoaded = null;
      returnFinal(0, "Done");
      return;
    }
    String error = null;
    TapeInputStream ti = new TapeInputStream(_deviceName);
    String _cart = _activeRequest.getCartridge();

    try {
      ti.OpenReady(10);
      ti.Display("Unload", _cart);
      ti.Unload();
      ti.close();
    } catch(DevIOException e) {
      esay(error = "unload failed " + e);
      returnFinal(1, error);
      return;
    }
    _cartLoaded = null;
    returnFinal(0, "Done");
  }

  private void runCheckLabelMover() {
    String _cart = _activeRequest.getCartridge();
    String _store = _activeRequest.getStore();
    String _storageGroup = _activeRequest.getStorageGroup();

    if (_disk) {
      _cartLoaded = new String(_cart);
      returnFinal(0, "Done");
      return;
    }

    byte[] dataBuffer = new byte[MAXSIZE];
    String error = null;
    TapeInputStream ti = null;
    int _bRead;

    ti = new TapeInputStream(_deviceName);

    try {
      ti.OpenReady(200);
      ti.Display("LabelCHK", _cart);
      ti.Rewind();
      _bRead = ti.read(dataBuffer, 0, MAXSIZE);
      ti.close();
    } catch (DevIOException e) {
      esay(error = "Tape error while reading label " + e);
      pin(error);
      returnFinal(11, error);
      return;
    }

    if (_bRead <= 0) {
      esay(error = "empty label");
      pin(error);
      returnFinal(11, error);
      return;
    }

    String _cont = new String(dataBuffer);

    if (_cont.length() <= 1) {
      esay(error = "labeldata has no string contents");
      pin(error);
      returnFinal(11, error);
      return;
    }

    StringTokenizer _line = new StringTokenizer(_cont, "\n");
    while(_line.hasMoreTokens()) {
      String _lineElem = _line.nextToken();
      StringTokenizer _keyVal = new StringTokenizer(_lineElem, "=");
      if (_keyVal.countTokens() != 2) {
	esay("invalid label line - " + _lineElem);
	continue;
      }

      say("read label line - " + _lineElem);
      
      String _key = _keyVal.nextToken();
      String _val = _keyVal.nextToken();

      if (_key.equals("CARTNAME") &&
	  _val.equals(_cart)) {       // get it
	say(error = "Checking Label on " + _deviceName + " cartridge " +
	    _val + " done");
	pin(error);
        _cartLoaded = new String(_val);
	returnFinal(0, "Done");
	return;
      }
    }
    returnFinal(11, "Label mismatch");
  }

  private void runWriteLabelMover() {
    byte[] dataBuffer = new byte[MAXSIZE];
    String _store = _activeRequest.getStore();
    String _cart = _activeRequest.getCartridge();
    String _storageGroup = _activeRequest.getStorageGroup();
    String error = null;
    String _eorPos = null;


    String _label = "CARTNAME=" + _cart + "\n" +
      "STORE=" + _store + "\n" +
      "STORAGEGROUP=" + _storageGroup + "\n" +
      "CREATOR=EuroStore-HSMV0.1\n";


    if (!_disk) {
      byte[] _info = _label.getBytes();
      
      System.arraycopy(_info, 0, dataBuffer, 0, _info.length);
      
      try {
	TapeOutputStream to = new TapeOutputStream(_deviceName);
	to.OpenReady(200);
	to.Display("LabelWR", _cart);
	to.Rewind();
	to.write(dataBuffer, 0, MAXSIZE);
	to.writeFilemark(1);
	_eorPos = to.GetPosition();
	to.close();
      } catch (DevIOException e) {
	esay(error = "Tape exception on Label-Create: " + e);
	pin(error);
	returnFinal(1, error);
      }
    } else {
      _eorPos = "0:0";
    }
    say(error = "Writing Label on " + _deviceName + " cartridge " +
	_cart + " done");
    pin(error);
    _activeRequest.setPosition("", _eorPos);
    returnFinal(0, "Done");
  }

  //  private void runRemoveMover(){
  //    File outputFile = null ;
  //try{
  //  outputFile = filepathFromRequest( _activeRequest ) ;
  //  if( ! outputFile.delete() )
  //throw new 
  //  Exception( "Couldn't delete "+_activeRequest.getBfid() ) ;
  //}catch( Exception pe ){
  //  String er = "PANIC : "+pe.getMessage() ;
  //  esay( er ) ;
  //  _activeRequest.setActionCommand( "Mover-OK" ) ;
  //  _activeRequest.setReturnValue( 44 , er ) ;
  //  sendBack() ;
  //  return ;
  //}
  //_activeRequest.setActionCommand( "Mover-OK" ) ;
  //_activeRequest.setReturnValue( 0 , "Done" ) ;
  //  
  //sendBack() ;
  //  
  //return ;
  //}


  // fill complete buffer with bytes from net
  private void fillNetBuffer(DataInputStream in, byte[] buf,
			     int from, int num) 
    throws IOException {
    int rb; 
    try {
      while(num > 0) {
	if ((rb = in.read(buf, from, num)) <= 0)
	  throw new IOException("EOF/EIO on data socket (" + rb + ")");
	from += rb;
	num -= rb;
      }
    } catch (IOException e) {
      throw(e);
    }
  }

  private int padding(int number, int bound) {
    return((number == 0) ? 0 : ((bound - 1) - (((number) - 1) % (bound))));
  }

  private void runPutMover() {
    Socket             moverSocket   = null ;
    DataInputStream    inputStream   = null ;
    //DataOutputStream   outputStream  = null ;
    DataOutputStream   controlStream = null ;
    TapeOutputStream   to = null;
    cpio _cpio = null;
    //File               dataFile      = null ;
    byte [] dataBuffer = new byte[MAXSIZE] ;
    byte [] trailer = new byte[MAXSIZE];
    String error = null ;
    File _f = null;
    BufferedOutputStream _fo = null;
    //int rc = 0 , r ;
    //
    // extract what we need for connecting the client
    //
    String host      = _fakeHost ==  null ? 
      _activeRequest.getHostName() :
      "localhost" ;
    int    port      = _activeRequest.getHostPort() ;
    _fileSize  = _activeRequest.getFileSize() ;
    String clientId  = _activeRequest.getClientReqId() ;
    pin(">>> PUT "+_activeRequest) ;
    String fileName = _activeRequest.getBfid();
    String eorPos = _activeRequest.getEorPosition();
    String _cart = _activeRequest.getCartridge();
    String filePos = null;  // location of new file to write
    long mediaBytes = 0;
    long _totalBytes = 0;


    _ioCounter = 0 ;
    try {   // here comes the BIG... try
    
      if ( _delay > 0 ) {
	pin( "Warning : delaying by "+_delay+" seconds" ) ;
	try { Thread.currentThread().sleep(_delay*1000) ;}
	catch (InterruptedException ie ) {}
	pin( "Woken up" ) ;
      }

      if (_disk) {
	_f = new File(_deviceName, fileName);
	if (_f.exists()) {
	  pin(error = "PUT: file " + fileName + "already exist");
	  throw new DevIOException(error);
	}
	_fo = new BufferedOutputStream(new FileOutputStream(_f));
      } else {
	to = new TapeOutputStream(_deviceName, this);
      }
      _cpio = new cpio(fileName, "PATHNAMES/PATHNAME." + fileName,
		       "FSINFO/PNFS_ID." + fileName, _fileSize, 0, 0);


      pin( "Trying to connect to "+host+":"+port+")" ) ;
      moverSocket  = new Socket( host , port ) ;
      pin( "Connected ... waiting for "+_fileSize+" bytes" ) ;
      inputStream   = new DataInputStream( moverSocket.getInputStream() ) ;
      controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
      controlStream.writeUTF( "Hello-EuroStore-Client " + clientId ) ;

      pin( "Starting data tranfer" ) ;

      if (!_disk) {
	to.OpenReady(100);  // open tape device - native code
	to.Display("Locate", _cart);
	to.PositionEOR(eorPos);
	filePos = to.GetPosition();
      } else {
	filePos = "1:0";
      }

      pin("positioned to " + filePos);

      if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	pin( error = "Simulated IOException" ) ;
	throw new IOException( error ) ;
      }

      long start = System.currentTimeMillis() ;

      int pos  = 0 ;
      if ((pos = _cpio.createHeader(dataBuffer)) < 0) {
	throw new DevIOException("cpio header create failed", 1);
      }

      _rest = _fileSize;
      int _toRead = MAXSIZE - pos;  /* for the initial block */
      int _toWrite = pos;

      if (!_disk) {
	to.Display("Write", _cart);
	
	// move data !
	while (_rest > 0) {
	  _toRead = (_toRead <= _rest) ? _toRead : (int) _rest;
	  fillNetBuffer(inputStream, dataBuffer, pos, _toRead);
	  _ioCounter++;
	  _toWrite += _toRead;
	  
	  if ((_rest -= _toRead) <= 0)  //  later buffer flush
	    break;
	  
	  if (_toWrite != MAXSIZE)
	    pin("_toWrite garbled " + _toWrite);
	  
	  to.write(dataBuffer, 0, _toWrite);
	  
	  _totalBytes += _toRead;
	  
	  _toRead = MAXSIZE;
	  _toWrite = pos = 0;
	}
      } else {  // to disk
	while(_rest > 0) {
	  _toRead = (_toRead <= _rest) ? _toRead : (int) _rest;
	  fillNetBuffer(inputStream, dataBuffer, pos, _toRead);
	  _ioCounter++;
	  _toWrite += _toRead;
	  
	  if ((_rest -= _toRead) <= 0)  //  later buffer flush
	    break;
	  
	  if (_toWrite != MAXSIZE)
	    pin("_toWrite garbled " + _toWrite);
	  
	  _fo.write(dataBuffer, 0, _toWrite);
	  
	  _totalBytes += _toRead;
	  
	  _toRead = MAXSIZE;
	  _toWrite = pos = 0;
	}
      }

      // create trailer cpio stuff
      if ((pos = _cpio.createTrailer(trailer)) < 0)
	throw new DevIOException("cpio trailer create failed", 1);

      // check if the trailer stuff fits into databuffer
      if ((_toWrite + pos) > MAXSIZE) {  // doesn't fit
	int left = MAXSIZE - _toWrite;
	System.arraycopy(trailer, 0, dataBuffer, _toWrite, left);
	if (_disk)
	  _fo.write(dataBuffer, 0, MAXSIZE);
	else
	  to.write(dataBuffer, 0, MAXSIZE);
        _totalBytes += MAXSIZE;
	_toWrite = pos - left;
	System.arraycopy(trailer, left, dataBuffer, 0, _toWrite);
			 
      } else {  // fit within dataBuffer
	System.arraycopy(trailer, 0, dataBuffer, _toWrite, pos);
	_toWrite += pos;
      }

      if (_toWrite > 0) { // and the last one :-)
	int _freeBytes = MAXSIZE - _toWrite;
	int _lastFlush = (MAXSIZE - _freeBytes) +
	  padding((MAXSIZE - _freeBytes), MINSIZE);
	// zero remaining stuff
//	for(int i=_toWrite; i<_lastFlush; i++) {
//	  dataBuffer[i] = 0;
//	}
	if (_disk)
	  _fo.write(dataBuffer, 0, _lastFlush);
	else
	  to.write(dataBuffer, 0, _lastFlush);
        _totalBytes += _lastFlush;
      }
      if (!_disk) {
	to.writeFilemark(1);
	eorPos = to.GetPosition();
	mediaBytes = to.bytesOnMedia();
      } else {
	eorPos = "1:0";
	mediaBytes = _totalBytes;
      }

      if (_disk)
	_fo.flush();

      pin("moved bytes: " + _fileSize + " on media: " + mediaBytes);

      if( _fileSize == 0 ){
	pin( "Zero byte file transfered" );
      }
      long   now  = System.currentTimeMillis() ;
      _rate = ( (double)_fileSize / (double)(now-start) )  ;
      pin( ""+_fileSize+" Bytes transferred with "
		   +_rate+" KBytes/sec" );

    } catch (DevIOException dioe) {
      if (_stopOnError) unregister();
      try { moverSocket.close() ; } catch (Exception eee) {}
      esay( error = "Exception for Tape access (" 
	    + _deviceName + ") " + dioe) ;
      dioe.printStackTrace();
      _activeRequest.setResidualBytes(_totalBytes);

      // check EOM condition
      if (dioe.getErrorCode() == 666) {
	returnFinal(999, "EOM detected: " + dioe);
      } else {
	returnFinal( 11 , error );
      }
      _lastException = dioe ;
      pin( "<<< "+error ) ;
      if (_disk)
	_f.delete();
      return;
    }
    catch (Exception e ) {
      if( _stopOnError )unregister() ;
      try { moverSocket.close() ; } catch(Exception eee){}
      esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
      e.printStackTrace();
      returnFinal( 11 , error ) ;
      _lastException = e ;
      pin( "<<< "+error ) ;
      if (_disk)
	_f.delete();
      return ;
    } finally {
      pin( "Closing tape output stream" );
      if (!_disk) {
	to.Display("Close", _cart);
	try{ to.close()  ; }catch(Exception eee){} 
      } else {  // disk access
	try { if (_fo != null) _fo.close(); } catch(IOException eee) {}
      }
    }


//     if( rc <= 0 ) {
//       if( _stopOnError )unregister() ;
//       esay( error = "PANIC : unexpected end of stream rc="+rc ) ;
//       pin( error ) ;
//       returnFinal( 12 , error ) ;
//       dataFile.delete() ;
//     }

    // sent ACK to client
    try {
      controlStream.writeUTF( "MACK "+clientId+" 0 "+_fileSize ) ;       
      pin( "Data Mover finished successfully" ) ;
      _activeRequest.setPosition(filePos, eorPos);
      _activeRequest.setRealBytes(mediaBytes);
      returnFinal( 0 , "Done" ) ;
    } catch(Exception eee) {
      _lastException = eee ;
      esay( error = "IOException in sending MACK : "+eee) ;
      pin( error ) ;
      returnFinal( 13 , error ) ;
      if (_disk)
	_f.delete();
    }
    
    pin( "Closing net input stream" ) ;
    try{ moverSocket.close() ; }catch(Exception eee){}
    pin( "<<< Done" ) ;
    return ;
    
  }

  private void runGetMover() {
    Socket             moverSocket   = null ;
    //DataInputStream    inputStream   = null;
    TapeInputStream ti = null;
    DataOutputStream   outputStream  = null ;
    DataOutputStream   controlStream = null ;
    File               dataFile      = null ;
    // NOTE: the blocksize should be stored in BB's db
    byte [] dataBuffer = new byte[MAXSIZE];
    String error = null ;
    int rc = 0 , r ;
    cpio _cpio = null;
    BufferedInputStream _fi = null;
    File _f = null;
    //
    // extract what we need for connecting the client
    //
    String host      = _fakeHost ==  null ? 
      _activeRequest.getHostName() :
      "localhost" ;
    int    port      = _activeRequest.getHostPort();
    String clientId  = _activeRequest.getClientReqId();    
    String fileName = _activeRequest.getBfid();
    String filePos = _activeRequest.getPosition();
    String _cart = _activeRequest.getCartridge();
    _fileSize  = _activeRequest.getFileSize();
    
    
    pin(">>> GET "+_activeRequest) ;

    try {
      if (_disk) {
	_f = new File(_deviceName, fileName);
	if (!_f.exists()) {
	  throw new DevIOException(
	      error = "Datafile " + fileName + " does not exist");
	}
	_fi = new BufferedInputStream(new FileInputStream(_f));
      } else {
	ti = new TapeInputStream(_deviceName, this);
      }
    
    
    //        try{
    //           dataFile = filepathFromRequest( _activeRequest ) ;
    //        }catch( Exception pe ){
    // 	  if( _stopOnError )unregister() ;
    //           _lastException = pe ;
    //           esay( error = "PANIC : "+pe.getMessage() ) ;
    //           returnFinal( 44 , error ) ;
    // 	  pin( "<<< "+error ) ;
    //           return ;
    //        }

      if ( _delay > 0 ) {
	pin( "Warning : delaying by "+_delay+" seconds" ) ;
	try{ Thread.currentThread().sleep(_delay*1000) ; }
	catch(InterruptedException ie ){}
	pin( "Woken up" ) ;
      }
      
      //          inputStream   = new DataInputStream(
      //                      new FileInputStream( dataFile ) 
      //                 ) ;
      
      
      pin( "Trying to connect to "+host+":"+port+")" ) ;
      moverSocket  = new Socket( host , port ) ;
      pin( "Connected ... will send "+_fileSize+" bytes" ) ;
      controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
      outputStream  = controlStream ;
      controlStream.writeUTF( "Hello-EuroStore-Client "+clientId ) ;

      pin("open tape and locate to '" + filePos + "'");

      if (!_disk) {
	ti.OpenReady(200);
	ti.Display("Locate", _cart);
	ti.Position(filePos);
      }
      
      _rest = _fileSize ;
      int pos  = 0 ;
      pin( "Starting data tranfer" ) ;
      
      
      if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	pin( error = "Simulated IOException" ) ;
	throw new IOException( error ) ;
      }
      
      long start = System.currentTimeMillis();
      
      _ioCounter = 0 ;
      boolean _headerDone = false;
      int _rDone;
      
      _cpio = new cpio(fileName, "PATHNAMES/PATHNAME." + fileName,
		       "FSINFO/PNFS_ID." + fileName, _fileSize, 0, 0); 

      if (!_disk) {
	ti.Display("Read", _cart);
	
	while(_rest > 0) {
	  _rDone = ti.read(dataBuffer, 0, MAXSIZE);
	  //pin("read " + _rDone + " bytes");
	  _ioCounter++;
	  if (_headerDone == false) { // process cpio header
	    if ((pos = _cpio.checkHeader(dataBuffer)) < 0 ||
          	pos > _rDone)
	      throw new DevIOException("invalid CPIO header found");
	    pin("cpio headersize: " + pos);
	    _headerDone = true;
	    _rDone -= pos;    // adjust user byte count
	  }
	  if (_rDone > _rest)
	    _rDone = (int) _rest;
	  
	  outputStream.write(dataBuffer, pos, _rDone);
	  //pin("write: pos " + pos + " _rDone " + _rDone);
	  pos = 0;
	  _rest -= _rDone;
	}
      } else { // disk access
	while(_rest > 0) {
	  _rDone = _fi.read(dataBuffer, 0, MAXSIZE);
	  //pin("read " + _rDone + " bytes");
	  _ioCounter++;
	  if (_headerDone == false) { // process cpio header
	    if ((pos = _cpio.checkHeader(dataBuffer)) < 0 ||
          	pos > _rDone)
	      throw new DevIOException("invalid CPIO header found");
	    pin("cpio headersize: " + pos);
	    _headerDone = true;
	    _rDone -= pos;    // adjust user byte count
	  }
	  if (_rDone > _rest)
	    _rDone = (int) _rest;
	  
	  outputStream.write(dataBuffer, pos, _rDone);
	  //pin("write: pos " + pos + " _rDone " + _rDone);
	  pos = 0;
	  _rest -= _rDone;
	}
      }
      
      if( _fileSize == 0 ){
	pin( "Zero byte file transfered" );
      }
      long   now  = System.currentTimeMillis() ;
      _rate = ( (double)_fileSize / (double)(now-start) )  ;
      pin( ""+_fileSize+" Bytes transferred with "
		   +_rate+" KBytes/sec" );

      //long mediaBytes = ti.bytesOnMedia();

      //pin("moved bytes: " + _fileSize + " on media: " + mediaBytes);

      
    } catch (DevIOException dioe) {
      if (_stopOnError) unregister() ;
      try { moverSocket.close(); } catch (Exception eee) {}
      esay( error = "Exception in tape handling " + dioe);
      returnFinal( 11 , error ) ;
      _lastException = dioe ;
      pin( "<<< "+error ) ;
      return;
    } catch (Exception e ) {
      if ( _stopOnError ) unregister() ;
      try { moverSocket.close() ; } catch (Exception eee) {}
      esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
      returnFinal( 11 , error ) ;
      _lastException = e ;
      pin( "<<< "+error ) ;
      return ;
    } finally {
      pin( "Closing file input streams" ) ;
      if (!_disk) {
	ti.Display("Close", _cart);
	try { if (ti != null) ti.close(); } catch (Exception eee) {} 
      } else {  // disk access 
	try { if (_fi != null) _fi.close(); } catch (IOException eee) {}
      }
    }
    
    //     if( rc <= 0 ){
    //       if( _stopOnError )unregister() ;
    //       esay( error = "PANIC : unexpected end of stream" ) ;
    //       pin( error ) ;
    //       returnFinal( 12 , error ) ;
    //     }
    
    try {
      controlStream.writeUTF(error = "MACK "+clientId+" 0 "+_fileSize ) ;
      pin("sent to client '" + error + "'");
      pin( "Data Mover finished successfully" ) ;
      returnFinal( 0 , "Done" ) ;
    } catch ( Exception eee ) {
      _lastException = eee ;
      esay( error= "IOException in sending MACK : "+eee ) ;
      pin( error ) ;
      returnFinal( 13 , error ) ;
    }

    pin( "Closing net input stream" ) ;
    try { moverSocket.close() ; } catch (Exception eee) {}
    pin( "<<< Done" ) ;
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
}
