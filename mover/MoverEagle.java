package eurogate.mover ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

public class MoverEagle 
       extends CellAdapter
       implements Runnable, 
                  Logable {
  
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
  private boolean          _disk           = false; // root dir for disk mover
  private final String     __version       = "V0.1";
  
  // EAGLE specific stuff
  private final int MINSIZE = (2 * 1024);
  private final int MAXSIZE = (64 * 1024);

  // Logable interface implementations
  public void log(String s) {
    pin(s);
    super.say(s);
  }
  public void elog(String s) { log(s); }
  public void plog(String s) { log(s); }
 
  public void say( String s ){ pin(s) ; super.say(s) ; }
  public void esay( String s ){ pin(s) ; super.esay(s) ; }
   
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
      _pvlPath    = _args.argv(0);
      _deviceName = _args.argv(1);
      if( _args.argc() > 2 ){
	_disk = _args.argv(2).compareTo("disk") == 0;
	if( _disk ){
	  File f = new File(_deviceName);
	  if( ! f.exists()      ||
	      ! f.isDirectory() ||
	      ! f.canWrite()    ||
	      ! f.canRead()        ) {
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
      _cartLoaded = "UnChecked: <" + cart + ">";
    returnFinal(0, "Done");
  }

  private void runUnloadMover() {
    if( _disk ){
      _cartLoaded = null;
      returnFinal(0, "Done");
      return;
    }
    String          error = null;
    TapeInputStream ti    = new TapeInputStream(_deviceName);
    String          cart  = _activeRequest.getCartridge();

    try {
      ti.OpenReady(10);
      ti.Display("Unload", cart);
      ti.Unload();
    } catch(DevIOException e) {
      esay(error = "unload failed " + e);
      returnFinal(1, error);
      return;
    }finally{
       try{ ti.close() ; }catch(Exception e){}
    }
    _cartLoaded = null;
    returnFinal(0, "Done");
  }
  private Hashtable readLabel() throws DevIOException {
     byte[] dataBuffer  = new byte[MAXSIZE];
     String error       = null;
     TapeInputStream ti = null;
     int bRead ;

     ti = new TapeInputStream(_deviceName);

     try {
        ti.OpenReady(200);
        ti.Display("LabelCHK", "???");
        ti.Rewind();
        bRead = ti.read(dataBuffer, 0, MAXSIZE);
     }catch( DevIOException e ){
        esay(error = "Tape error while reading label " + e);
        throw e ;
     }finally{
        //
        // try to close it
        //
        try{ ti.close() ; }catch( Exception e ){}
     }

     if( bRead <= 0 ){
       esay( error = "emty label" ) ;
       throw new IllegalArgumentException( error ) ;
     }

     String cont = new String(dataBuffer);

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
    
    if (_disk) {
      _cartLoaded = cart ;
      returnFinal(0, "Done");
      return;
    }
    
    try{
    
       keyHash = readLabel() ;
       
    }catch( Exception e ){
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
    byte[] dataBuffer   = new byte[MAXSIZE];
    String store        = _activeRequest.getStore();
    String cart         = _activeRequest.getCartridge();
    String storageGroup = _activeRequest.getStorageGroup();
    String error        = null;
    String eorPos       = null;


    String label = "CARTNAME=" + cart + "\n" +
                   "STORE=" + store + "\n" +
                   "STORAGEGROUP=" + storageGroup + "\n" +
                   "CREATOR=EuroStore-HSMV0.1\n";


    if( ! _disk ){
       byte[] info = label.getBytes();

       System.arraycopy( info, 0, dataBuffer, 0, info.length);
       TapeOutputStream to = null ;
       try{

	 to = new TapeOutputStream(_deviceName);
	 to.OpenReady(200);
	 to.Display("LabelWR", cart);
	 to.Rewind();
	 to.write(dataBuffer, 0, MAXSIZE);
	 to.writeFilemark(1);
	 eorPos = to.GetPosition();

       }catch (DevIOException e) {
	 esay(error = "Tape exception on Label-Create: " + e);
	 returnFinal(1, error);
       }finally{
          try{ to.close() ; }catch(Exception ee ){}
       }
    }else{
       eorPos = "0:0";
    }
    say(error = "Writing Label on " + _deviceName + 
                " cartridge " + cart + " done");
    _activeRequest.setPosition("", eorPos);
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
    TapeOutputStream     to   = null;
    cpio                 cpio = null;
    BufferedOutputStream fo   = null;
    byte [] dataBuffer = new byte[MAXSIZE] ;
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

      if(_disk){
      
	f = new File(_deviceName, fileName);
	if ( f.exists()) {
	  say(error = "PUT: file " + fileName + "already exist");
	  throw new DevIOException(error);
	}
	fo = new BufferedOutputStream(new FileOutputStream(f));
        
      }else{
	to = new TapeOutputStream(_deviceName, this);
      }
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

      if (!_disk) {
	to.OpenReady(100);  // open tape device - native code
	to.Display("Locate", cart);
	
        try{
	    to.PositionEOR(eorPos);
         }catch( DevIOException devIoE ){
            say( "PositioningEOR : ("+devIoE.getMessage()+") rewind/retry" ) ;
            to.Rewind() ;
            to.PositionEOR(eorPos) ;
         }

	filePos = to.GetPosition();
      } else {
	filePos = "1:0";
      }

      say("positioned to " + filePos);

      if( _havingProblems && ( ( _problemCounter++ % 4 ) == 0 ) ){
	say( error = "Simulated IOException" ) ;
	throw new IOException( error ) ;
      }

      long start = System.currentTimeMillis() ;

      int pos  = 0 ;
      if( (pos = cpio.createHeader(dataBuffer) ) < 0 ){ 
	throw new DevIOException("cpio header create failed", 1);
      }

      _rest = _fileSize;
      int toRead  = MAXSIZE - pos;  /* for the initial block */
      int toWrite = pos;

      if (!_disk) {
	to.Display("Write", cart);
	
	// move data !
	while (_rest > 0) {
	  toRead = ( toRead <= _rest ) ? toRead : (int) _rest;
	  fillNetBuffer(inputStream, dataBuffer, pos, toRead);
	  _ioCounter++;
	  toWrite += toRead;
	  
	  if ((_rest -= toRead) <= 0)  //  later buffer flush
	    break;
	  
	  if (toWrite != MAXSIZE)
	    say("_toWrite garbled " + toWrite);
	  
	  to.write(dataBuffer, 0, toWrite);
	  
	  totalBytes += toRead;
	  
	  toRead = MAXSIZE;
	  toWrite = pos = 0;
	}
        
      }else{  // to disk
      
	while(_rest > 0) {
	  toRead = (toRead <= _rest) ? toRead : (int) _rest;
	  fillNetBuffer(inputStream, dataBuffer, pos, toRead);
	  _ioCounter++;
	  toWrite += toRead;
	  
	  if ((_rest -= toRead) <= 0)  //  later buffer flush
	    break;
	  
	  if (toWrite != MAXSIZE)
	    say("toWrite garbled " + toWrite);
	  
	  fo.write(dataBuffer, 0, toWrite);
	  
	  totalBytes += toRead;
	  
	  toRead  = MAXSIZE;
	  toWrite = pos = 0;
	}
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
	System.arraycopy(trailer, 0, dataBuffer, toWrite, left);
	if (_disk){
        
	   fo.write(dataBuffer, 0, MAXSIZE);
	
        }else{
        
	   to.write(dataBuffer, 0, MAXSIZE);
          
        }
        totalBytes += MAXSIZE;
	toWrite     = pos - left;
        
	System.arraycopy(trailer, left, dataBuffer, 0, toWrite);
			 
      }else{
        // 
        // fit within dataBuffer
        //
	System.arraycopy(trailer, 0, dataBuffer, toWrite, pos);
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
//	for(int i=_toWrite; i<_lastFlush; i++) {
//	  dataBuffer[i] = 0;
//	}
	if (_disk){
	   fo.write(dataBuffer, 0, lastFlush);
	}else{
	   to.write(dataBuffer, 0, lastFlush);
        }
        totalBytes += lastFlush;
      }
      if (!_disk) {
	 to.writeFilemark(1);
	 eorPos = to.GetPosition();
	 mediaBytes = to.bytesOnMedia();
      }else{
	 eorPos = "1:0";
	 mediaBytes = totalBytes;
      }

      if (_disk)fo.flush();

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
       if (_disk)f.delete();
       return;

    }catch (Exception e ) {
    
       if( _stopOnError )unregister() ;
       try { moverSocket.close() ; } catch(Exception eee){}
       esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
       e.printStackTrace();
       returnFinal( 11 , error ) ;
       _lastException = e ;
       say( "<<< "+error ) ;
       if (_disk)f.delete();
       return ;
       
    } finally {
    
       say( "Closing tape output stream" );
       if (!_disk) {
	  to.Display("Close", cart);
	  try{ to.close()  ; }catch(Exception eee){} 
       } else {  // disk access
	 try { 
            if (fo != null) fo.close(); 
         }catch(IOException eee) {}
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
       say( "Data Mover finished successfully" ) ;
       _activeRequest.setPosition(filePos, eorPos);
       _activeRequest.setRealBytes(mediaBytes);
       returnFinal( 0 , "Done" ) ;
    } catch(Exception eee) {
       _lastException = eee ;
       esay( error = "IOException in sending MACK : "+eee) ;
       returnFinal( 13 , error ) ;
       if (_disk)f.delete();
    }
    
    say( "Closing net input stream" ) ;
    try{ moverSocket.close() ; }catch(Exception eee){}
    say( "<<< Done" ) ;
    return ;
    
  }

  private void runGetMover() {
    Socket             moverSocket   = null ;
    TapeInputStream    ti            = null;
    DataOutputStream   outputStream  = null ;
    DataOutputStream   controlStream = null ;
    File               dataFile      = null ;
    //
    // NOTE: the blocksize should be stored in BB's db
    //
    byte [] dataBuffer = new byte[MAXSIZE];
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
      if (_disk) {
	 f = new File(_deviceName, fileName);
	 if (!f.exists()) {
           say( error = "Datafile " + fileName + " does not exist" ) ;
	   throw new DevIOException(error);
	 }
	 fi = new BufferedInputStream(new FileInputStream(f));
      }else{
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
	say( "Warning : delaying by "+_delay+" seconds" ) ;
	try{ Thread.currentThread().sleep(_delay*1000) ; }
	catch(InterruptedException ie ){}
	say( "Woken up" ) ;
      }
      
      //          inputStream   = new DataInputStream(
      //                      new FileInputStream( dataFile ) 
      //                 ) ;
      
      
      say( "Trying to connect to "+host+":"+port+")" ) ;
      moverSocket  = new Socket( host , port ) ;
      say( "Connected ... will send "+_fileSize+" bytes" ) ;
      controlStream = new DataOutputStream( moverSocket.getOutputStream() ) ;
      outputStream  = controlStream ;
      controlStream.writeUTF( "Hello-EuroStore-Client "+clientId ) ;

      say("open tape and locate to '" + filePos + "'");

      if (!_disk) {
	 ti.OpenReady(200);
	 ti.Display("Locate", cart);
         try{
	    ti.Position(filePos);
         }catch( DevIOException devIoE ){
            say( "Positioning : ("+devIoE.getMessage()+") rewind/retry" ) ;
            ti.Rewind() ;
            ti.Position(filePos) ;
         }
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

      if (!_disk) {
	ti.Display("Read", cart);
	
	while(_rest > 0) {
	  rDone = ti.read(dataBuffer, 0, MAXSIZE);
	  _ioCounter++;
	  if( ! headerDone  ) { 
            //
            // process cpio header
            //
	    if( (pos = cpio.checkHeader(dataBuffer) ) < 0 ||
          	pos > rDone  )
	      throw new DevIOException("invalid CPIO header found");
              
	    say("cpio headersize: " + pos);
	    headerDone = true;
	    rDone     -= pos;    // adjust user byte count
	  }
	  if( rDone > _rest )rDone = (int) _rest;
	  
	  outputStream.write(dataBuffer, pos, rDone);
          
	  pos    = 0;
	  _rest -= rDone;
	}
      }else{ // disk access
	while(_rest > 0) {
	  rDone = fi.read(dataBuffer, 0, MAXSIZE);
	  _ioCounter++;
	  if( ! headerDone ){
            ///
            // process cpio header
            //
	    if( (pos = cpio.checkHeader(dataBuffer) ) < 0 ||
          	pos > rDone)
	      throw new DevIOException("invalid CPIO header found");
	    say("cpio headersize: " + pos);
	    headerDone = true;
	    rDone     -= pos;    // adjust user byte count
	  }
	  if( rDone > _rest)rDone = (int) _rest;
	  
	  outputStream.write(dataBuffer, pos, rDone);
	  pos    = 0;
	  _rest -= rDone;
	}
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
       if ( _stopOnError ) unregister() ;
       try { moverSocket.close() ; } catch (Exception eee) {}
       esay( error = "Exception in connection to "+host+":"+port+" : "+e ) ;
       returnFinal( 11 , error ) ;
       _lastException = e ;
       say( "<<< "+error ) ;
       return ;
    } finally {
       say( "Closing file input streams" ) ;
       if (!_disk){
	  ti.Display("Close", cart);
	  try { ti.close(); } catch (Exception eee) {} 
       }else{  // disk access 
	  try { if( fi != null) fi.close(); } catch (IOException eee) {}
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
