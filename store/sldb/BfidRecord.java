package eurogate.store.sldb ;

import java.io.* ;
import java.util.Date ;

public class BfidRecord 
       implements java.io.Serializable,
                  eurogate.vehicles.BfRecordable {
                  
    private String _name ;
    private long   _size ;
    private Date   _created , _lastRead ;
    private String _mode = "t" ;
    private int    _readCounter = 0 ;
    private String _parameter   = "" ;
    private String _volume      = "" ;
    private String _position    = "" ;
    private String _group       = null ;
    
    public String toString(){
       return "(n="+_name+";s="+_size+
              ";c="+_created.getTime()+";r="+_lastRead.getTime()+
              ";m="+_mode+";c="+_readCounter+";v="+_volume+";p="+
              _position+";x="+_parameter+")" ;
    
    }
    public String toLongString(){
       StringBuffer sb = new StringBuffer() ;
       sb.append( "Name       = " ).append( _name ).append( "\n" ) ;
       sb.append( "Size       = " ).append( _size ).append( "\n" ) ;
       sb.append( "Created    = " ).append( _created.toString() ).append( "\n" ) ;
       sb.append( "LastUsed   = " ).append( _lastRead.toString() ).append( "\n" ) ;
       sb.append( "Mode       = " ).append( _mode ).append( "\n" ) ;
       sb.append( "Read Count = " ).append( _readCounter ).append( "\n" ) ;
       sb.append( "Volume     = " ).append( _volume ).append( "\n" ) ;
       sb.append( "Position   = " ).append( _position ).append( "\n" ) ;
       sb.append( "Params     = " ).append( _parameter ).append( "\n" ) ;
       return sb.toString() ;
    
    }
    public byte [] getByteArray( int maxSize ) throws IOException {
       ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
       DataOutputStream out = new DataOutputStream( baos ) ;
       out.writeUTF( _name ) ;
       out.writeLong( _size ) ;
       out.writeLong( _created.getTime() ) ;
       out.writeLong( _lastRead.getTime() ) ;
       out.writeUTF( _mode )  ;
       out.writeInt( _readCounter ) ;
       out.writeUTF( _volume ) ;
       out.writeUTF( _position ) ;
       int len = _name.length() + 8 + 8 + 8 + _mode.length() +
                 4 + _volume.length() + _position.length() ;
       int rest = maxSize - len ;
       if( rest < 64 )
          throw new
          IllegalArgumentException( "record to long" ) ;
       if( _parameter.length() > rest )
          _parameter = _parameter.substring(0,rest) ;
       out.writeUTF( _parameter ) ;
       out.flush() ;
       return  baos.toByteArray() ;
    }
    public BfidRecord( String group , byte [] dataIn ) throws IOException {
       ByteArrayInputStream bais = new ByteArrayInputStream( dataIn ) ;
       DataInputStream in = new DataInputStream( bais ) ;
       _group       = group ;
       _name        = in.readUTF() ;
       _size        = in.readLong() ;
       _created     = new Date( in.readLong() ) ;
       _lastRead    = new Date( in.readLong() ) ;
       _mode        = in.readUTF() ;
       _readCounter = in.readInt() ;
       _volume      = in.readUTF() ;
       _position    = in.readUTF() ;
       _parameter   = in.readUTF() ;
    }
    public BfidRecord( String group , String bfid , long size ){
       _group   = group ;
       _name    = bfid ;
       _size    = size ;
       _created = _lastRead = new Date() ;
    }
    public String getBfid(){ return _name ; }
    public long   getFileSize(){ return _size ; }
    public String getStatus(){ return _mode ; }
    public String getVolume(){ return _volume ; }
    public String getFilePosition(){ return _position ; }
    public void setVolume( String volume ){ _volume = volume ; }
    public void setPosition( String position ){ _position = position ; }
    public void setMode( String mode ){
       _mode=mode ;
    }
    public Date getCreationDate(){ return _created ; }
    public Date getLastAccessDate(){ return _lastRead ; }
    public int  getAccessCounter(){ return _readCounter ; }
    public void touch(){ 
       _lastRead = new Date() ;
       _readCounter ++ ;
    }
    public String getStorageGroup(){ return _group ; }
    public void setParameter( String parameter ){
       _parameter = parameter ;
    }
    
    public String getParameter(){ return _parameter ; }
    

}
