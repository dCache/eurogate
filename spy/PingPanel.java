package eurogate.spy ;


import java.applet.*;
import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import java.io.* ;
import java.net.* ;

import dmg.util.* ;
import dmg.cells.services.* ;
import dmg.cells.nucleus.* ;
import dmg.cells.applets.spy.* ;

public class      PingPanel 
       extends    Panel   {
       
     private DomainConnection _connection = null ;
     private Font   _headerFont = 
                       new Font( "SansSerif" , 
                                 Font.ITALIC , 24 )  ;
     private Toolkit _toolkit = Toolkit.getDefaultToolkit() ;
     private class      PingLabel 
             extends    Label 
             implements FrameArrivable , Runnable {
             
        private String _destination = null ;
        private String _command     = null ;
        private long   _last        = System.currentTimeMillis() ;
        private boolean _isOk       = true ;
        public Insets getInsets(){ return new Insets( 10 , 10 ,10 , 10 ) ; }
        private PingLabel( String label , String command ){
            super(label,Label.CENTER) ;
            setFont( _headerFont ) ;
            _destination = label ;
            _command     = command ;
            new Thread(this).start() ;
            System.out.println( "Ping label started : "+label ) ;
        }
        private void setStatus( boolean ok ){
           if( ok  && ! _isOk ){
              setBackground( Color.green ) ;
           }else if( ! ok ){
              if( _isOk )setBackground( Color.red ) ;
               _toolkit.beep() ;
           }
           _isOk = ok ;
           return ;
        }
        public void run(){
           while(true){
               _connection.send( _destination , _command , this ) ;
	       try{ 
	           Thread.currentThread().sleep(10000) ;
	       }catch(InterruptedException ie ){
	           break ;
	       }
               if( ( System.currentTimeMillis() - _last ) > 25000 ){
                  setStatus( false ) ;
               }else{
                  setStatus( true ) ;
               }
	   }      
        }
        public void frameArrived( MessageObjectFrame frame ){
            Object obj = frame.getObject() ;
//            System.out.println( "Frame arrived : "+obj ) ;
            if( obj instanceof Exception){
//               System.out.println( "Exception arrived : "+obj.toString() ) ;
                setStatus( false ) ;
               return ;
            }
            _last = System.currentTimeMillis() ;
        }
     }
     public PingPanel( DomainConnection connection ){
        _connection = connection ;
        GridLayout grid = new GridLayout(0,2) ;
        grid.setHgap(10) ;
        grid.setVgap(10) ;
        setLayout( grid  ) ;
        
        
        add( new PingLabel( "System" , "ps -f" ) ) ;
        add( new PingLabel( "pvl" , "help" ) ) ;
        add( new PingLabel( "MAIN-store" , "help" ) ) ;
        add( new PingLabel( "stk" , "help" ) ) ;
        add( new PingLabel( "drive0" , "help" ) ) ;
        add( new PingLabel( "drive1" , "help" ) ) ;
     }      

}
 
 
