package eurogate.mover;

import java.io.*;

// class interfacing to C based code creating and checking CPIO archives

public class cpio {

  private String _fileName1, _fileName2, _fileName3;
  private int _uid, _gid;
  private long _fileSize;
  private static final long maxFileSize = 8589934591L;

  // filesize has cpio limits - max is 8GB !!!!

  cpio(String filename1, String filename2, String filename3, long filesize,
       int uid, int gid) throws DevIOException {
    _uid = uid;
    _gid = gid;
    _fileSize = filesize;
    _fileName1 = filename1;
    _fileName2 = filename2;
    _fileName3 = filename3;
    if (_fileSize > maxFileSize)
      throw new DevIOException("filesize too large (max. 8GB)", 22);
  }

  static { System.loadLibrary("eurogate_mover_cpio"); }

  public native int createHeader(byte[] buffer);
  
  public native int createTrailer(byte[] buffer);

  public native int checkHeader(byte[] buffer);

}
