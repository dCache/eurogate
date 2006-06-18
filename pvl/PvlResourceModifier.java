package eurogate.pvl ;


public class PvlResourceModifier  implements java.io.Serializable {

   private String _cartridge = null ;
   private String _drive     = null ;
   private String _pvr       = null ;
   private String _errorMsg  = null ;
   private int    _errorCode = 0 ;
   private String _actionEvent = null ;
   
   public PvlResourceModifier(){}
   public PvlResourceModifier( String action ){
      _actionEvent = action ; 
   }
   public PvlResourceModifier( String actionEvent ,
                               String pvrName ,
                               String driveName ,
                               String cartridgeName ){
                               
      _actionEvent = actionEvent ; 
      _cartridge   = cartridgeName ;
      _drive       = driveName ;
      _pvr         = pvrName ;
   }
   public String getActionEvent(){ return _actionEvent ; }
   public void   setActionEvent( String actionEvent ){
      _actionEvent = actionEvent ;
   }
   public void setReturnValue( int code , String message ){
      _errorMsg  = message ;
      _errorCode = code ;
   }
   public String getErrorMessage(){ return _errorMsg ; }
   public int    getErrorCode(){ return _errorCode ; }
   public String getPvr(){ return _pvr ;}
   public String getDrive(){ return _drive ; }
   public String getCartridge(){ return _cartridge ; }
   
   public void setCartridge( String cartridge ){
      _cartridge = cartridge ; 
   }
   public void setDrive( String drive ){
      _drive = drive ;
   }
   public void setPvr( String pvr ){
      _pvr = pvr ;
   }
   public String toString(){
      return _actionEvent+"("+
             (getPvr()==null?"":getPvr())+","+
             (getDrive()==null?"":getDrive())+","+
             (getCartridge()==null?"":getCartridge())+")" ;
   }
}
