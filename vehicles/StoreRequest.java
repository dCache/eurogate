package eurogate.vehicles ;

import  eurogate.misc.* ;

public class StoreRequest implements java.io.Serializable {
   private String _command   = null ;
   private String _id        = null ;
   private String _store     = null ;
   private String _bfidName  = null ;
   private BitfileId _bfid   = null ; 
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
}
