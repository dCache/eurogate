package eurogate.simulation.graphics.pvr ;

import java.lang.reflect.* ;
import java.applet.*;
import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import java.io.* ;
import java.net.* ;

import dmg.util.* ;
import dmg.cells.services.* ;
import dmg.cells.nucleus.* ;

public class      CPvrFrame 
       extends    Frame 
       implements WindowListener, 
                  ActionListener,
                  Runnable            {

   private Font   _bigFont = 
             new Font( "SansSerif" , Font.BOLD , 18 )  ; 
   private Font   _bigFont2 = 
             new Font( "SansSerif" , Font.BOLD , 24 )  ; 
   private Thread  _listenThread ;
   private DataInputStream _in ;
   private DataOutputStream _out ;
   private TextArea _display = new TextArea() ;
   private TextField _input = new TextField() ;
   private String    _host ;
   private int       _port ;
   private String    _pvr   = null ;
   private Button    _connectButton ;
   private Socket    _socket ;
  public CPvrFrame( String [] args  ) throws Exception {
      super( "CPvrFrame" ) ;
      setLayout( new BorderLayout() ) ;
      addWindowListener( this ) ;
      setLocation( 60 , 60) ;
      
      _host = args[0] ;
      _port = Integer.parseInt( args[1] ) ;
      
      if( args.length > 2 ) _pvr = args[2] ;
      
      add( _display , "Center" ) ;
      add( _input   , "South"  ) ;
      _input.addActionListener( this ) ;
      
      Panel topPanel = new Panel( new FlowLayout() ) ;
      
      Label top = new Label( "PvrFrame" , Label.CENTER ) ;
      top.setFont( _bigFont ) ;
      
      _connectButton = new Button( "   Connect    " ) ;
      _connectButton.setFont( _bigFont ) ;
      _connectButton.addActionListener( this ) ;
      
      topPanel.add( _connectButton ) ;
      topPanel.add( top ) ;
      add( topPanel , "North" );
      setSize( 750 , 500 ) ;
      pack() ;
      setSize( 750 , 500 ) ;
      setVisible( true ) ;
  
  }
  private synchronized boolean startConnection(){
     try{
        _socket = new Socket( _host , _port ) ;

        _in  = new DataInputStream( _socket.getInputStream() ) ;
        _out = new DataOutputStream( _socket.getOutputStream() ) ;
        
        if( _pvr != null )
           _out.writeUTF( "hello "+_pvr+ " "+_pvr+"-type" ) ;
      }catch(Exception e ){
        _display.append( "Connection failed : "+e+"\n" ) ;
        _out = null ;
        return false ;
      }  
     _listenThread = new Thread( this ) ;
     _listenThread.start() ;
     _connectButton.setLabel( "DisConnect" ) ;
     return true ;
  
  }
  private synchronized void stopConnection(){
     _connectButton.setLabel( "Connect" ) ;
     _display.append( "Stopping streams\n" ) ;
     try{ _out.close() ; }catch(Exception e){
        _display.append( "out : "+e+"\n" ) ;
     }
     try{ _in.close() ; }catch(Exception e){
        _display.append( "in : "+e+"\n" ) ;
     }
     try{ _socket.close() ; }catch(Exception e){
        _display.append( "socket : "+e+"\n" ) ;     
     }
     _display.append( "All streams closed\n" ) ;  
     _listenThread.interrupt() ;   
     _out = null ;
  }
  private synchronized boolean isConnected(){ 
      return _out != null ;
  } 
  public void run(){
     try{
        String str = null ;
        _display.append( "Connected\nListener started\n" ) ;
        while( ( str = _in.readUTF() ) != null ){
           
            _display.append( str +"\n" ) ;
            
        }
     
     }catch( Exception ee ){
        try{ stopConnection() ; }catch(Exception xx ){}
     }
     _display.append( "Listen Thread terminated\n" ) ;
     _connectButton.setLabel("Connect") ;
  
  }
  public void actionPerformed( ActionEvent event ){
    Object source = event.getSource() ;
    if( source == _input ){
        try{
          _out.writeUTF( _input.getText() ) ;
          _input.setText("");
        }catch(Exception e ){
          _display.append( "PROBLEM writing : "+e+"\n" ) ;
          stopConnection() ;
        }
     }else if( source == _connectButton ){
        if( isConnected() ){
            stopConnection() ;
        }else{
            startConnection() ;
        }
     
     }
  }
  //
  // window interface
  //
  public void windowOpened( WindowEvent event ){}
  public void windowClosed( WindowEvent event ){
      System.exit(0);
  }
  public void windowClosing( WindowEvent event ){
      System.exit(0);
  }
  public void windowActivated( WindowEvent event ){}
  public void windowDeactivated( WindowEvent event ){}
  public void windowIconified( WindowEvent event ){}
  public void windowDeiconified( WindowEvent event ){}
   public static void main( String [] args ){
      if( args.length < 2 ){
         System.out.println( "Usage : ... <host> <port> [<stk>}" ) ;
         System.exit(4) ;
      }
      try{
            
         new CPvrFrame( args ) ;
      
      }catch( Exception e ){
         e.printStackTrace() ;
         System.err.println( "Exception : "+e.toString() ) ;
         System.exit(4);
      }
      
   }

}
