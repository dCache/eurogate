package eurogate.spy ;

import dmg.cells.applets.login.* ;
import dmg.cells.applets.login.CenterLayout ;
import java.lang.reflect.* ;
import java.applet.*;
import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;


public class      EGTapePanel 
       extends    Panel 
       implements ActionListener,DomainConnectionListener   {

   private Font   _bigFont = 
             new Font( "SansSerif" , Font.BOLD , 18 )  ; 
   private Label  _errorLabel = new Label() ;
   private DomainConnection _domain ;
   public EGTapePanel( DomainConnection dc ){
       _domain = dc ;
       BorderLayout bl = new BorderLayout() ;
       bl.setHgap(5) ;
       bl.setVgap(5) ;
       setLayout( bl ) ;
       
       Panel dp = new DrivePanel() ;
       Panel vp = new VolumePanel() ;
       
       _volumeName.addActionListener(this);
       _volumeStatus.addActionListener(this);
       _pvrName.addActionListener(this);
       _driveName.addActionListener(this);
       _driveStatus.addActionListener(this);
       
       Label topLabel = new Label("Resource Manager",Label.CENTER ) ;
       topLabel.setFont( _bigFont ) ;
       
       add( topLabel , "North" ) ;
       
       Panel l1Panel = new Panel(new BorderLayout()) ;
       add( l1Panel , "Center" ) ;
       add( _errorLabel , "South" ) ;
       _errorLabel.setBackground( Color.yellow ) ;
       
       l1Panel.add( dp , "North" ) ;
       
       Panel l2Panel = new Panel(new BorderLayout()) ;
       l1Panel.add( l2Panel , "South" ) ;
       
       l2Panel.add( vp , "North" ) ;
              
       
       
   }

   private class VolumePanel extends Panel {
      private int _b = 5 ;
      public void paint( Graphics g ){
         Dimension   d    = getSize() ;
         g.setColor( Color.white ) ;
         g.drawRect( _b/2 , _b/2 , d.width-_b , d.height-_b ) ;
      }
      public Insets getInsets(){ return new Insets(_b,_b,_b,_b) ; }
      public VolumePanel(){
          BorderLayout bl = new BorderLayout() ;
          bl.setHgap(5) ;
          bl.setVgap(5) ;
          setLayout( bl ) ;
          GridLayout gl = new GridLayout(0,2) ;
          gl.setHgap(5) ;
          gl.setVgap(5) ;
          Label top = new Label( "Volume Manager" , Label.CENTER ) ;
          Panel bottom = new Panel( gl ) ;
          bottom.add( new Label( "Volume Name" ) ) ;
          bottom.add( _volumeName  ) ;
          bottom.add( new Label( "Volume Status" ) ) ;
          bottom.add( _volumeStatus  ) ; 
          add( top , "North" ) ;
          add( bottom , "South" ) ;
      }
   }   
   private class DrivePanel extends Panel {
      private int _b = 5 ;
      public void paint( Graphics g ){
         Dimension   d    = getSize() ;
         g.setColor( Color.white ) ;
         g.drawRect( _b/2 , _b/2 , d.width-_b , d.height-_b ) ;
      }
      public Insets getInsets(){ return new Insets(_b,_b,_b,_b) ; }
      public DrivePanel(){
          BorderLayout bl = new BorderLayout() ;
          bl.setHgap(5) ;
          bl.setVgap(5) ;
          setLayout( bl ) ;
          GridLayout gl = new GridLayout(0,2) ;
          gl.setHgap(5) ;
          gl.setVgap(5) ;
          Label top = new Label( "Drive Manager" , Label.CENTER ) ;
          Panel bottom = new Panel( gl ) ;
          bottom.add( new Label( "Pvr Name" ) ) ;
          bottom.add( _pvrName  ) ;
          bottom.add( new Label( "Drive Name" ) ) ;
          bottom.add( _driveName  ) ;
          bottom.add( new Label( "Drive Status" ) ) ;
          bottom.add( _driveStatus  ) ; 
          add( top , "North" ) ;
          add( bottom , "South" ) ;
      }
   }   
     private TextField _volumeName   = new TextField() ;
     private TextField _volumeStatus = new TextField() ;
     private TextField _pvrName      = new TextField("stk") ;
     private TextField _driveName    = new TextField() ;
     private TextField _driveStatus  = new TextField() ;
     public void actionPerformed( ActionEvent event ){
        Object source = event.getSource() ;
        if( source == _volumeName ){
           String volume = _volumeName.getText() ;
           if( volume.length() == 0 )return ;
           Object [] r = new Object[5] ;
           r[0] = "pvl-request" ;
           r[1] = "*" ;
           r[2] = "get-volume-status" ;
           r[3] = "*" ;
           r[4] = volume ;
           try{
             _domain.sendObject( r , this , 0 ) ;
             _errorLabel.setText("Sending ...") ;
           }catch(Exception e){
             _errorLabel.setText( "E="+e.getMessage() ) ;
           }
        }else if( source == _volumeStatus ){
           Object [] r = new Object[6] ;
           String volume = _volumeName.getText() ;
           if( volume.length() == 0 )return ;
           String volumeStat = _volumeStatus.getText() ;
           if( volumeStat.length() == 0 )return ;
           r[0] = "pvl-request" ;
           r[1] = "*" ;
           r[2] = "set-volume-status" ;
           r[3] = "*" ;
           r[4] = volume ;
           r[5] = volumeStat ;
           try{
             _domain.sendObject( r , this , 0 ) ;
             _errorLabel.setText("Sending ...") ;
           }catch(Exception e){
             _errorLabel.setText( "E="+e.getMessage() ) ;
           }
        }else if( source == _driveStatus ){
           String pvrName = _pvrName.getText() ;
           if( pvrName.length() == 0 )return ;
           String driveName = _driveName.getText() ;
           if( driveName.length() == 0 )return ;
           String driveStatus = _driveStatus.getText() ;
           if( driveStatus.length() == 0 )return ;
           Object [] r = new Object[6] ;
           r[0] = "pvl-request" ;
           r[1] = "*" ;
           r[2] = "set-drive-status" ;
           r[3] = pvrName ;
           r[4] = driveName ;
           r[5] = driveStatus ;
           try{
             _domain.sendObject( r , this , 0 ) ;
             _errorLabel.setText("Sending ...") ;
           }catch(Exception e){
             _errorLabel.setText( "E="+e.getMessage() ) ;
           }
        }else if( source == _driveName ){
           String pvrName = _pvrName.getText() ;
           if( pvrName.length() == 0 )return ;
           String driveName = _driveName.getText() ;
           if( driveName.length() == 0 )return ;
           Object [] r = new Object[5] ;
           r[0] = "pvl-request" ;
           r[1] = "*" ;
           r[2] = "get-drive-status" ;
           r[3] = pvrName ;
           r[4] = driveName ;
           try{
             _domain.sendObject( r , this , 0 ) ;
             _errorLabel.setText("Sending ...") ;
           }catch(Exception e){
             _errorLabel.setText( "E="+e.getMessage() ) ;
           }
           
        }
     }
    public void domainAnswerArrived( Object obj , int id ){
       System.out.println( "Object arrived : "+obj ) ;
       if( obj instanceof Object [] ){
          _errorLabel.setText("O.K") ;
          Object [] a = (Object[]) obj ;
          String command = a[2].toString() ;
          if( command.equals("get-volume-status") ||
              command.equals("set-volume-status")    ){
             _volumeName.setText( a[4].toString() ) ;
             _volumeStatus.setText( a[5].toString() ) ;
          }else if( command.equals("set-drive-status") ||
                    command.equals("get-drive-status")    ){
             _pvrName.setText( a[3].toString()) ;
             _driveName.setText( a[4].toString()) ;
             _driveStatus.setText( a[5].toString()) ;
          }else{
             for( int i = 0 ; i < a.length ; i++ ){
                System.out.println( "a["+i+"]="+a[i].toString() ) ;
             }
          }
       }else if( obj instanceof Exception ){
          _errorLabel.setText( ((Exception)obj).getMessage() ) ;
       }else{
          _errorLabel.setText( obj.toString() ) ;
       }
    }

   


}
