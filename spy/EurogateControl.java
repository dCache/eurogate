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

public class      EurogateControl 
       extends    Frame 
       implements WindowListener, 
                  ActionListener ,
                  DomainConnectionListener {

   private DomainConnection _connection = null ;
   private Button _connectButton    = null ;
   private Button _closeButton      = null ;
   private Label  _messageText      = null ;
   private Panel  _drivePanel       = null ;
   
   private class BorderPanel extends Panel {
      public BorderPanel( Component c ){
         super( new BorderLayout() ) ;
         add( c , "Center" ) ;
      }
     
      public Insets getInsets(){ return new Insets( 10 , 10 ,10 , 10 ) ; }
   
   }
   
  public EurogateControl( String host , int port  ){
      super( "EurogateControl" ) ;
      
      _connection = new DomainConnection( host , port ) ;
      
      
      addWindowListener( this ) ;
      _connection.addConnectionListener( this ) ;
      _drivePanel    = new PingPanel( _connection ) ;
      
      Panel masterPanel = new Panel( new BorderLayout() ) ;
      Panel buttonPanel = new Panel( new FlowLayout( FlowLayout.CENTER ) ) ;
      Panel actionPanel = new BorderPanel( _drivePanel ) ;
      actionPanel.setBackground( Color.blue ) ;
      
      _connectButton = new Button( "Connect" ) ;
      _closeButton   = new Button( "Close Connection" ) ;
      _messageText   = new Label("Not Connected") ;
      
      _connectButton.addActionListener( this ) ;
      _closeButton.addActionListener( this ) ;
      
      buttonPanel.add( _connectButton ) ;
      buttonPanel.add( _closeButton ) ;
      
      masterPanel.add( buttonPanel , "North" ) ;
      masterPanel.add( actionPanel , "Center" ) ;
      masterPanel.add( _messageText , "South" ) ;
      
      add( masterPanel ) ;
      
      connectionActive( false ) ;
      setLocation( 100  , 100 ) ;
      setSize( 600 , 300 ) ;
      pack() ;
      setSize( 600 , 300 ) ;
      setVisible( true ) ;
  
  }
  //
  // action interface
  //
  public void actionPerformed( ActionEvent event ) {
    Object source = event.getSource() ;
    if( source == _connectButton ){
       try{
          _connection.connect() ;
       }catch( Exception e ){
          _messageText.setText( "Not Connected : "+e.getMessage() ) ;
          return ;
       }
    
    }else if( source == _closeButton ){
       _connection.close() ;
    }
  
  }
  //
  // domain connection interfase
  //
  public void connectionActivated( DomainConnectionEvent event ){
      connectionActive( true ) ;
      _messageText.setText( "Connected" ) ;
  }
  public void connectionDeactivated( DomainConnectionEvent event ){
      System.out.println( "Listener : "+event.getMessage()) ;
      connectionActive( false ) ;
      _messageText.setText( "Connection Closed : "+event.getMessage() ) ;
  }
  public void connectionFailed( DomainConnectionEvent event ){
      _messageText.setText( "Connection Failed : "+event.getMessage() ) ;
      connectionActive( false ) ;
  }
  private void connectionActive( boolean enabled ){
      _connectButton.setEnabled( ! enabled ) ;
      _closeButton.setEnabled( enabled ) ;
      _messageText.setBackground( enabled ? Color.green : Color.red ) ;
//      _domainListPanel.setEnabled(enabled);
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
         System.err.println( "Usage : ... <hostName> <spyListenPort>" ) ;
         System.exit(4) ;
      }
      if( args.length > 2 ){
         Properties props = System.getProperties() ;
         props.put( "bw" , args[2] ) ;     
      }
      int port = Integer.parseInt( args[1] ) ;
      try{
            
         new EurogateControl( args[0] , port ) ;
      
      }catch( Exception e ){
         e.printStackTrace() ;
         System.err.println( "Connection failed : "+e.toString() ) ;
         System.exit(4);
      }
      
   }

}
