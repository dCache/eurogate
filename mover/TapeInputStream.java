
package eurogate.mover;


import java.net.*;
import java.io.*;
import dmg.util.*;
import eurogate.vehicles.*;


public class TapeInputStream extends InputStream{

  private int fileDescriptor = -1;
  private String locationString = null;
  private int locationStringLength = 40;
  private String printablePosition = null;
  private int printablePositionLength = 80;
  private String initString = null;
  private int initStringLength = 80;
  private long bytesOnMedia = 0;
  private long remainingCapacity = 0;

  private final int RDONLY = 0;

  //  private Error _e = null;
  
  private native int _init( String devName );
  private native int _open( String devName, int mode );  
  private native int _openReady( String devName, int mode, int timeOut );  
  private native int _close(int fileDescr );  
  private native int _position(int fileDescr, String locString );
  private native int _positionEOR(int fileDescr, String locString );
  private native int _display(int fileDescr, String m1, String m2);
  private native int _getPosition(int fileDescr, int locStringLength );
  private native int _rewind(int fileDescr);
  private native int _unload(int fileDescr); 
  private native int _load(int fileDescr);
  private native int _ready(int fileDescr); 
  private native int _printablePosition( String locString, int printablePosLength);
  private native int _read(int fileDescr, byte[] byteArray, int start, int cnt );  
  private native int _bytesOnMedia(int fileDescr );
  private native int _remainingCapacity(int fileDescr);

    
  static { System.loadLibrary("InStreamHandler"); }

  private String _deviceName = null ;
  private int    _fileDescrip = 0 ;
  private Logable _log = null;

 
  /**
   * public TapeInputStream (String devName) <BR>
   *
   *  Constructor of the Stream <BR> <BR>
   *
   *  devName: system path and name of the device.  <BR>
   */
  public TapeInputStream (String devName) {
    _deviceName = devName;
  }

  public TapeInputStream (String devName, Logable log) {
    this(devName);
    _log = log;
  }
  


  /**
   * public String initDrive ( String _deviceName )  <BR>
   *
   *  initialize drive, must be called after instantiating the stream <BR>
   *  returns an initializing string such as "STK-9840" for the eagle drive. <BR> <BR>
   *
   *  devName: system path and name of the device. 
   */
  public  String initDrive(/* String _deviceName*/ ) throws DevIOException {
    char[] alloChar = new char[initStringLength];
    initString = new String(alloChar);
    int returnCode = _init( _deviceName );
    //System.out.println("initString in TapeStreamHandler: " + initString);
    if( returnCode != 0 ) {
       throw new DevIOException("initdrive failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    return(initString);
  } // end function initDrive



  /**
   * public void open ( String _deviceName, int wrNotRd ) throws DevIOException  <BR>
   *
   *  Open device. Returns and sets the file descriptor or throws an exception. <BR> <BR>
   *
   *  read : wrNotRd = 0 (O_RDONLY) <BR>
   *  write: wrNotRd = 1 (O_WRONLY) <BR>
   */
  public void Open(/*String _deviceName, int wrNotRd*/) throws DevIOException {
    int returnCode = _open( _deviceName, RDONLY) ;
    if (returnCode >= 0) _fileDescrip = returnCode;
    if( returnCode < 0 ) {
       returnCode = - returnCode;
       throw new DevIOException("open device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    }
    //return(returnCode);
  } // end function open



  /**
   * public void openReady (String _deviceName, long wrNotRd, int timeOut) throws DevIOException <BR>
   *
   *  Try to open device within given time. Returns and sets the file descriptor   <BR> <BR>
   *  or throws an exception.
   *
   *  read : wrNotRd = 0 <BR>
   *  write: wrNotRd = 1 <BR>
   *  timeout: in seconds <BR>
   */
  public void OpenReady(/*String _deviceName, int wrNotRd,*/ int timeOut)
    throws DevIOException {
    int returnCode = _openReady( _deviceName, RDONLY, timeOut) ;
    if (returnCode >= 0) _fileDescrip = returnCode;
    if( returnCode < 0 ) {
       returnCode = - returnCode;
       throw new DevIOException("open device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    }
    //return;
  } // end function open




  /**
   * public void close() throws DevIOException   <BR>
   *  Closes the Stream. <BR>
   */

  public  void close() throws DevIOException {
    Close();
  }

//  public  void Close() throws DevIOException {
//    int returnCode = _close( _fileDescrip ) ;
//    if( returnCode != 0 ) {
//       throw new DevIOException("close device failed");
//       // _e.get("20" + Integer.toString(returnCode)),returnCode);
//    } 
//    //return returnCode;
//  } // end function open

  public  void Close() {
    if (_fileDescrip < 0)
      return;
    int returnCode = _close( _fileDescrip ) ;
//    if( returnCode != 0 ) {
//       throw new DevIOException("close device failed");
//       // _e.get("20" + Integer.toString(returnCode)),returnCode);
//    } 
    //return returnCode;
  } // end function open



  /**
   * public int rewind() throws DevIOException  <BR>
   *
   *  Rewinds the Tape. Returns 0 or an error number. <BR>
   */
  public void Rewind() throws DevIOException {
    int returnCode = _rewind( _fileDescrip ) ;
    if( returnCode != 0 ) {
       throw new DevIOException("rewind device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
  } // end function rewind



  /**
   * public int unload() throws DevIOException  <BR>
   *
   *  Unloads the Tape. Returns 0 or an error number. <BR>
   */
  public void Unload() throws DevIOException {
    int returnCode = _unload( _fileDescrip ) ;
    if( returnCode != 0 ) {
       throw new DevIOException("unload device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
  } // end function unload



  /**
   * public int load() throws DevIOException  <BR>
   *
   *  Loads the Tape.  <BR> <BR>
   *  Returns 0 or an error number. <BR>
   */
  public void Load() throws DevIOException {
    int returnCode = _load( _fileDescrip ) ;
    if( returnCode != 0 ) {
       throw new DevIOException("load device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    //return(returnCode);
  } // end function load



  /**
   * public int ready() throws DevIOException  <BR>
   *
   *  Reports ready state for a new operation.  <BR> <BR>
   *
   *  Return value: ask Martin <BR>
   */
  public void Ready() throws DevIOException {
    int returnCode = _ready( _fileDescrip ) ;
    if( returnCode != 0 ) {
       throw new DevIOException("device not ready");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    //return(returnCode);
  } // end function ready



  /**
   * public String printablePos ( String _locString ) throws DevIOException  <BR>
   *
   *  get printable tape position  <BR>  <BR>function returnes a
   *
   *  locationString: a drive dependent String, for which this   <BR>
   *                  function returnes a readable String.  <BR>
   */
  public String PrintablePos( String _locString ) throws DevIOException {
    char[] alloChar = new char[printablePositionLength];
    printablePosition = new String(alloChar);
    int returnCode = _printablePosition(  _locString, printablePositionLength);
    //System.out.println("Position in TapeStreamHandler: " + printablePosition);
    if( returnCode != 0 ) {
       throw new DevIOException("printable position failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    return(printablePosition);
  } // end function printablePosition




  /**
   * public String getPosition () throws DevIOException  <BR>
   *
   *  Get machine readable tape position as a string, returned by this function.  <BR>
   */
  public  String GetPosition() throws DevIOException {
    char[] alloChar = new char[locationStringLength];
    locationString = new String(alloChar);
    int returnCode = _getPosition( _fileDescrip, locationStringLength );
    //System.out.println("Position in TapeStreamHandler: " + locationString);
    if( returnCode != 0 ) {
       throw new DevIOException("getposition device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    return(locationString);
  } // end function getPosition



  /**
   * public int position ( String _locString ) throws DevIOException  <BR>
   *
   *  locString: a machine readable string to set tape position. <BR> <BR>  
   *
   *  Returns 0 or an error number. <BR>
   */
  public void Position( String _locString ) throws DevIOException {
    int returnCode = _position( _fileDescrip, _locString );
    if( returnCode != 0 ) {
       throw new DevIOException("position device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    //return(returnCode);
  } // end function position



  /**
   * public int positionEOR ( String _locString ) throws DevIOException  <BR>
   *
   *  set tape position (end of record ? ask Martin.) <BR> <BR>
   *
   *  locString: a machine readable string to set tape position.  <BR>
   *
   *  Returns 0 or an error number. <BR>
   */
  public void PositionEOR( String _locString ) throws DevIOException {
    int returnCode = _positionEOR( _fileDescrip, _locString );
    if( returnCode != 0 ) {
       throw new DevIOException("positionEOR device failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    //return(returnCode);
  } // end function positionEOR


  public void Display(String m1, String m2) {
    int ret = _display(_fileDescrip, m1, m2);
    //    if (ret != 0) {
    //      throw new DevIOException("display failed");
    //    }
  }


  /**
   * public int read () throws DevIOException  <BR>
   *  
   *  Reads a Bytes and returns it as an integer in the range of 0 to 255. <BR> 
   *  Reads the next byte of data from this input stream.  <BR>
   *  The value byte is returned as an int in the range 0 to 255.  <BR>
   *  If no byte is available because the end of the stream has been reached, <BR>
   *  the value -1 is returned.  <BR> <BR>
   *  This method blocks until input data is available, the end of the stream is  <BR>  
   *  detected, or an DevIOException is thrown. <BR>
   */
  public int read () throws DevIOException { 
    byte[] byteArray = new byte[1];
    //byteArray[0] = (byte)i;
    int returnCode = _read( _fileDescrip, byteArray, 0, 1 );        
      
    if( returnCode < 1 ) {
       returnCode = - returnCode;
       throw new DevIOException("readbyte failed");
       // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    return((int)byteArray[0]);
  } // end function read

   


  /**
   * public int read ( byte[] byteArray, int start, int cnt, ) throws DevIOException  <BR>
   *  
   *  Read cnt Bytes to array byteArray at position start.  <BR> <BR>
   *
   *  Returns 0 or an error number. <BR>
   */
  public int read ( byte[] byteArray, 
                       int start, int cnt ) throws DevIOException { 
    int returnCode = _read( _fileDescrip, byteArray, start, cnt );  
      
    if( returnCode < 0 ) {
      int rcode = -returnCode;
      throw new DevIOException("read failed");
      // _e.get("20" + Integer.toString(rCode)),rCode);
    } 
    return(returnCode);
  } // end function read

   


  /**
   * public long bytesOnMedia () throws DevIOException  <BR>
   *
   *  Returns number of bytes on media.  <BR>
   */
  public long BytesOnMedia() throws DevIOException {
    int returnCode = _bytesOnMedia( _fileDescrip );
    System.out.println("_bytesOnMedia: " + bytesOnMedia);
    if( returnCode != 0 ) {
      throw new DevIOException("bytesonmedia device failed");
      // _e.get("20" + Integer.toString(returnCode)),returnCode);
    } 
    return(bytesOnMedia);
  } // end function bytesOnMedia




 /**
   * public void logReport ( int severityLevel, String errorString ) throws DevIOException <BR>
   *
   *  A java function called from the c-driver to protocol the drive response in the log file. <BR> 
   */
  public void logReport( int severityLevel, String errorString ) {
    if (_log == null) {
      System.err.println("LOG(" + severityLevel + "): " + errorString);
    } else {
      _log.log("LOG(" + severityLevel + "): " + errorString);
    }
  } // end function logReport  
}

