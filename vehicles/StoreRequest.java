package eurogate.vehicles ;

import  eurogate.misc.* ;

public class StoreRequest implements java.io.Serializable {
   private String _command   = null ;
   private String _id        = null ;
   private String _store     = null ;
   private String _bfidName  = null ;
   private BitfileId _bfid   = null ; 
   private String    _retMsg = "" ;
   private int    _retCode   = 0 ;
   private String _host      = null ;
   private int    _port      = 0 ;
   public StoreRequest( String command ,
                        String id ,
                        String store ,
                        String bfidName  ){
                        
       _command  = command ;
       _id       = id ;
       _store    = store ;
       _bfidName = bfidName ;                    
   }
   public BitfileId getBitfileId(){ return _bfid ; }
   public void      setBitfileId( BitfileId bfid ){ _bfid = bfid ; }
   public String getStore(){ return _store ; }
   public String getId(){ return _id ; }
   public String getCommand(){ return _command ; }
   public String getBfid(){ return _bfidName ; }
   public String getVolume(){ return _bfidName ; }
   public String getHost(){ return _host ; }
   public int    getPort(){ return _port ; }
   public void   setHost( String host , int port ){
      _host = host ;
      _port = port ;
   }
   public String getReturnMessage(){ return _retMsg ; }
   public int    getReturnCode(){ return _retCode ; }
   public void   setReturnValue( int retCode , String retMsg ){
       _retCode = retCode ;
       _retMsg  = retMsg ;
   } 
}
