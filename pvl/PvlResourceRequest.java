package eurogate.pvl ;

import  eurogate.vehicles.* ;

import  dmg.util.* ;
import  dmg.cells.nucleus.* ;

public class PvlResourceRequest extends PvlResourceModifier {
 
    private int        _priority  = 0 ;
    private long       _timestamp = System.currentTimeMillis() ;
    private int        _position  = 0 ;
    private PvlRequest _request   = null ;
    private CellMessage _message  = null ;
    
    PvlResourceRequest( PvlRequest request ){
       super( "newRequest" ) ;
       _request = request ;
    }
    PvlResourceRequest( CellMessage msg , PvlRequest request ){
       super( "newRequest" ) ;
       _request = request ;
       _message = msg ;
    }
    //
    // overwrite the cartridge , drive and pvr   SET/GET
    //
    public String getCartridge(){ return _request.getCartridge() ; }
    public String getDrive(){ return _request.getDrive() ; }
    public String getPvr(){ return _request.getPvr() ; }
    
    public void setCartridge( String cartridge ){ 
       _request.setCartridge( cartridge ) ;
    }
    public void setDrive( String drive ){ 
       _request.setDrive( drive ) ;
    }
    public void setPvr( String pvr ){ 
       _request.setPvr( pvr ) ;
    }
    
    //
    int  getPosition(){ return _position ; }
    void setPosition( int position ){ _position = position ; }

    public int    getPriority() { return _priority ; }
    public void   setPriority( int priority ){ _priority = priority ; }
    
    public String getDirection(){  return _request.getType()  ; }
    public String getVolume(){     return _request.getVolume() ; }
    public String getVolumeSet(){  return _request.getVolumeSet() ; }
    public long   getFileSize(){   return _request.getFileSize() ; }
    public int    getSerialId(){   return _request.getSerialId() ; }
    public void   setVolumeId( String volId ){
        _request.setVolumeId( volId ) ;
    }
    public void setVolume( String volume ){ 
        _request.setVolume(volume) ; 
    }
    public long   getArrivalTime(){ return _timestamp ; }
    public CellMessage getMessage(){ return _message ; }
    public PvlRequest  getRequest(){ return _request ; }
    public String toLine(){
      String pvr = getPvr() ;
      pvr = ( pvr == null ) || pvr.equals("") ? 
            "-" : pvr ;
      String volume = getVolume() ;
      volume = ( volume == null ) || volume.equals("") ? 
               "-" : volume ;
      String cartridge = getCartridge() ;
      cartridge = ( cartridge == null ) || cartridge.equals("") ? 
                  "-" : cartridge ;
      String volumeSet = getVolumeSet() ;
      volumeSet = ( volumeSet == null ) || volumeSet.equals("") ? 
                  "-" : volumeSet ;
      if( getDirection().equals("get") ){
         return 
         Formats.field( ""+getSerialId() , 7 , Formats.LEFT ) +
         Formats.field( "get"            , 4 , Formats.LEFT ) +
         Formats.field( pvr              , 6 , Formats.LEFT ) +
         Formats.field( volume           , 8 , Formats.LEFT ) +
         Formats.field( cartridge        , 8 , Formats.LEFT ) +
         Formats.field( volumeSet        ,10 , Formats.LEFT ) +
         Formats.field( ""+getFileSize() , 8 , Formats.LEFT ) ;
      }else if( getDirection().equals( "put" ) ){
         return 
         Formats.field( ""+getSerialId() , 7 , Formats.LEFT ) +
         Formats.field( "put"            , 4 , Formats.LEFT ) +
         Formats.field( pvr              , 6 , Formats.LEFT ) +
         Formats.field( volume           , 8 , Formats.LEFT ) +
         Formats.field( cartridge        , 8 , Formats.LEFT ) +
         Formats.field( volumeSet        ,10 , Formats.LEFT ) +
         Formats.field( ""+getFileSize() , 8 , Formats.LEFT ) ;
      }
      return "Invalid direction (PANIC)" ;
    }
    public String toString(){
      String r = super.toString() ;
      if( getDirection().equals("get") ){
         r += ""+getSerialId()+" get:"+getVolume()+";" ; 
      }else if( getDirection().equals( "put" ) ){
         r += ""+getSerialId()+" put:"+getVolumeSet()+";"+getFileSize()+";" ; 
      }else{
         r += "invalid;" ;
      }
      return r ;
    }
}
