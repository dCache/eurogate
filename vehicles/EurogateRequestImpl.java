package eurogate.vehicles ;

public class EurogateRequestImpl implements EurogateRequest {

    private static int    __serialId  = 99999 ;
    
    private String _type       = "" ;
    private String _command    = "" ;
    private String _retMessage = "" ;
    private int    _retCode    = 0 ;
    private int    _serialId   = 0 ;
    
    public EurogateRequestImpl(){
       synchronized( this.getClass() ){
          if( (++__serialId) > 999999 )__serialId = 100000 ; 
          _serialId = __serialId ;          
       }
    }
    public EurogateRequestImpl( String type , String command ){
       this() ;
       _type    = type ;
       _command = command ;
    }
    public EurogateRequestImpl( String command ){
       this() ;
       _command = command ;
    }
    public void   setType( String requestType ){
       _type = requestType ;
    }
    public String getType(){ return _type ; }
    public void   setActionCommand( String command ){
       _command = command ;
    }
    public String getActionCommand(){ return _command ; }
    public void   setReturnValue( int retCode , String retMessage ){
       _retCode     = retCode ;
       _retMessage  = retMessage ;
    }
    public int    getReturnCode(){ return _retCode ; }
    public String getReturnMessage(){ return _retMessage ; }
    public int    getSerialId(){ return _serialId ; } 
    public String toString(){
       return "t="+_type+";a="+_command+";e=("+_retCode+")"+_retMessage ;
    }                                          
}
