package  eurogate.vehicles ;



public interface PvrRequest extends EurogateRequest {

    public String getCartridge() ;
    public String getGenericDrive() ;
    public String getSpecificDrive() ;
    public void   setDrive( String generic , String specific ) ;
    public void   setCartridge( String cartridgeName ) ;
    public void   setPvr( String pvr ) ;
    public String getPvr() ;
}
