package eurogate.misc ;

import java.io.Serializable ;
import java.util.Date ;

public class BitfileId implements java.io.Serializable {
    private String _name ;
    private long   _size ;
    private Date   _created , _lastRead ;
    private String _mode = "transient" ;
    private int    _readCounter = 0 ;
    private String _parameter   = "" ;
    private String _volume      = "" ;
    private String _position    = "" ;
    
    public BitfileId( String name , long size ){
       _name = name ;
       _size = size ;
       _created = _lastRead = new Date() ;
    }
    public String getName(){ return _name ; }
    public long   getSize(){ return _size ; }
    public String getMode(){ return _mode ; }
    public String getVolume(){ return _volume ; }
    public String getPosition(){ return _position ; }
    public void setVolume( String volume ){ _volume = volume ; }
    public void setPosition( String position ){ _position = position ; }
    public String toString(){ 
       return _name + 
              "   (mode="+_mode+",size="+_size+")" ;
    }
    public void setMode( String mode ){
       _mode=mode ;
    }
    public Date getCreationDate(){ return _created ; }
    public Date getLastAccessDate(){ return _lastRead ; }
    public int  getAccessCount(){ return _readCounter ; }
    public void touch(){ 
       _lastRead = new Date() ;
       _readCounter ++ ;
    }
    public void setParameter( String parameter ){
       _parameter = parameter ;
    }
    public String getParameter(){ return _parameter ; }
    

}
