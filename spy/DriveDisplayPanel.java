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
import dmg.cells.applets.login.* ;

public class      DriveDisplayPanel 
       extends    SimpleBorderPanel  {
       
     private Font   _headerFont = 
                           new Font( "SansSerif" , 0 , 18 ) ;
     private Font   _itemFont = 
                           new Font( "SansSerif" , 0 , 12 ) ;
     private Panel            _driveListPanel   = null ;
     private SingleDrivePanel _singleDrivePanel = null ;
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public DriveDisplayPanel( DomainConnection connection ){
     
        super( new BorderLayout() , 15 , Color.white ) ;
                
        _driveListPanel = new DriveListPanel( connection ) ;
         
        _singleDrivePanel = new SingleDrivePanel( connection ) ;
        
        add( _driveListPanel , "Center" ) ;
        
        Label title = new Label( "Drive Manager" , Label.CENTER ) ;
        title.setFont( _headerFont ) ;
        
        add( title , "North" ) ;
         
     } 
     private class DriveClick extends MouseAdapter {

         public void mouseClicked(MouseEvent e){
            Component comp = e.getComponent() ;
            if( comp instanceof Label ){
               String text = ((Label)comp).getText() ;
               int p ;
               if( ( p = text.indexOf(":") ) < 0 )return ;
               String pvr = text.substring(0,p) ;
               String drive = text.substring(p+1) ;
               remove( _driveListPanel  ) ;
               _singleDrivePanel.setDrive( pvr , drive ) ;
               add( _singleDrivePanel , "Center" ) ;
               validate() ;
            }

         }
     }
     private void swapBack(){
        remove( _singleDrivePanel  ) ;
        add( _driveListPanel , "Center" ) ;
        validate() ;
     }
     private DriveClick _driveClick = new DriveClick() ;
     
     //////////////////////////////////////////////////////////////////////////
     //
     //   T h e  S i n g l e  D r i v e 
     //  ----------------------------------
     //
     private class      SingleDrivePanel 
             extends    SimpleBorderPanel 
             implements DomainConnectionListener,
                        ActionListener   {

        private Label _driveLabel = new Label("",Label.CENTER) ;
        private Label _pvrLabel   = new Label("",Label.CENTER)  ;
        private Label _modeLabel  = new Label("")  ;
        private Label _cartridgeLabel = new Label("")  ;
        private Label _actionLabel    = new Label("")  ;
        private Label _ownerLabel     = new Label("")  ;
        private TextField _selectionText = new TextField() ;
        private Button _dismountButton = new Button("Dismount") ;
        private Button _enableButton   = new Button("Enable Drive") ;
        private Button _updateButton   = new Button("Update") ;
        private Button _backButton     = new Button("Back") ;
        private Label  _messages       = new Label("")  ;
        
        private DomainConnection _connection = null ;
        
        private SingleDrivePanel( DomainConnection connection ){
            super( new BorderLayout() , 15 , Color.red ) ;
            
            _connection = connection ;
            
            _dismountButton.addActionListener(this) ;
            _enableButton.addActionListener(this) ;
            _updateButton.addActionListener(this) ;
            _backButton.addActionListener(this) ;
            
            _driveLabel.setFont( _headerFont ) ;
            _pvrLabel.setFont( _headerFont ) ;
            
            _cartridgeLabel.setBackground(Color.white);
            _modeLabel.setBackground(Color.white);
            _actionLabel.setBackground(Color.white);
            _ownerLabel.setBackground(Color.white);
            
            Panel driveParas = new Panel( new RowColumnLayout(2) ) ;
            driveParas.add( _pvrLabel ) ;
            driveParas.add( _driveLabel ) ;
            driveParas.add( new Label( "Mode" , Label.CENTER ) ) ;
            driveParas.add( _modeLabel ) ;
            driveParas.add( new Label( "Cartridge" , Label.CENTER ) ) ;
            driveParas.add( _cartridgeLabel ) ;
            driveParas.add( new Label( "Action" , Label.CENTER ) ) ;
            driveParas.add( _actionLabel ) ;
            driveParas.add( new Label( "Owner" , Label.CENTER ) ) ;
            driveParas.add( _ownerLabel ) ;
            
            
            Panel centeredDrivePanel = new Panel( new CenterLayout() ) ;
            centeredDrivePanel.add( driveParas ) ;
            
            
            Panel selectionPanel = new Panel( new BorderLayout() ) ;
            selectionPanel.add( new Label( "Selection" ) , "West" ) ;
            selectionPanel.add( _selectionText , "Center" ) ;
            
            Panel buttonPanel = new Panel( new GridLayout( 0 , 4 ) ) ;
            buttonPanel.add( _backButton ) ;
            buttonPanel.add( _updateButton ) ;
            _enableButton.setForeground( Color.red ) ;
            _dismountButton.setForeground( Color.red ) ;
            buttonPanel.add( _enableButton ) ;
            buttonPanel.add( _dismountButton ) ;
            
            RowColumnLayout rcl2 = new RowColumnLayout(1) ;
            rcl2.setFitsAllSizes(0);
            Panel top = new Panel( rcl2 ) ;
            top.add( centeredDrivePanel  ) ;
            top.add( selectionPanel ) ;
            top.add( buttonPanel ) ;
            top.add( _messages ) ;
            
            add( top ) ;
            doLayout() ;
            validate() ;
        }
        private void setDrive( String pvr , String drive ){
            _driveLabel.setText( drive ) ;
            _pvrLabel.setText( pvr ) ;
            _modeLabel.setText("") ;
            _cartridgeLabel.setText("") ;
            _actionLabel.setText("") ;
            _ownerLabel.setText("") ;
            _selectionText.setText("") ;
            try{
               _connection.sendObject( 
                   "ls drive -pvr="+pvr+" "+drive+" -cellPath=pvl" , this , 0 ) ;
            }catch(Exception ee ){
               setText( "Send failed : "+ee ) ;
            }
        }
        private void displayDrive( Hashtable list ){
           String res = null ;
           if( ( res = (String)list.get("status") ) != null )
               _modeLabel.setText(res) ;
           if( ( res = (String)list.get("cartridge") ) != null )
               _cartridgeLabel.setText(res) ;
           if( ( res = (String)list.get("owner") ) != null )
               _ownerLabel.setText(res) ;
           if( ( res = (String)list.get("action") ) != null )
               _actionLabel.setText(res) ;
           if( ( res = (String)list.get("selection") ) != null )
               _selectionText.setText(res) ;
        }
        public void domainAnswerArrived( Object obj , int id ){

           try{ 
              if( obj instanceof Hashtable ){
                  displayDrive( (Hashtable) obj ) ;
                  setText("") ;
                  return ;
              }else{
                  setText( obj.toString() ) ;
              }   
           }catch( Exception ee ){
              setText( ee.toString() ) ;
           }
        }
        private void setText( String text ){
           _messages.setText( text ) ;
        }
        public void actionPerformed( ActionEvent event ) {
          Object source = event.getSource() ;
          if( source == _updateButton ){
              try{
                    _connection.sendObject( 
                     "ls drive -pvr="+_pvrLabel.getText()+
                     " "+_driveLabel.getText()+" -cellPath=pvl" , this , 0 ) ;
             }catch( Exception eee ){
                 setText( "Send failed : "+eee ) ;
             }
          }else if( source == _backButton){
             swapBack() ;
          }

        }
     }
     //////////////////////////////////////////////////////////////////////////
     //
     //   T h e   D r i v e   L i s t
     //  ----------------------------------
     //
     private class      DriveListPanel 
             extends    SimpleBorderPanel 
             implements DomainConnectionListener , 
                        ActionListener, 
                        Runnable   {
         
         private DomainConnection _connection = null ;
         private Panel     _buttons  ;
         private Button    _update  , _auto ;
         private Panel     _driveList ;
         private boolean   _initiated    = false ;
         private String    _startString  = "Start Automatic Update" ;
         private String    _stopString   = "Stop Automatic Update" ;
         private Label [][]_labels       = null ;
         private boolean   _autoUpdate   = false ;
         private Thread    _updateThread = null ;     
         private Label     _messages     = null ;
         private Object    _threadLock   = new Object() ;
         private String [] titles = 
                 { "Drive" , "Mode" , "Cartridge" , "Owner" , "Action" } ;
                 
         private class DriveLabel extends Label {

            private DriveLabel( String l ){ super(l) ; }
            private DriveLabel( String l , int p ){ super( l , p ) ; }
            public  void setText( String text ){
               super.setText( text ) ;
               invalidate() ;
            }
         }
         
         private DriveListPanel( DomainConnection connection ){
            super( new BorderLayout() , 30 ) ;
            
            _connection = connection ;
            //
            //   the buttons 
            //
            _buttons = new Panel( new GridLayout(0,2) ) ;

            _update = new Button( "Update" ) ;
            _auto   = new Button( _startString ) ;

            _buttons.add( _update  ) ;
            _buttons.add( _auto    ) ;

            _update.addActionListener( this ) ;
            _auto.addActionListener( this ) ;

            add( _buttons  , "North"  ) ;
            //
            //   the drive list
            //
            RowColumnLayout rcl = new RowColumnLayout( 5 ) ;
            rcl.setFitsAllSizes(4);
            _driveList = new SimpleBorderPanel( rcl , 30  ) ;
            _driveList.setBackground( new Color( 200 , 200 ,200 ) ) ;

            for( int i = 0 ; i < titles.length ;  i++ ){
               Label title = new Label( titles[i] , Label.CENTER) ;
               title.setFont( _headerFont ) ;
               _driveList.add( title ) ;
            }

            add( _driveList , "Center" ) ;
            
            _messages = new Label("") ;
            add( _messages  , "South" ) ;
            
         }
         private void setText( String text ){ _messages.setText( text ) ; }
         
         private void initDrives( Object [] pvrSet ){
            int driveCount = 0 ;
            for( int i = 0 ; i < pvrSet.length ; i += 2 ){
               String [] [] pvr = (String[][])pvrSet[i+1] ;
               driveCount += pvr.length ;
            }
            _labels = new Label[driveCount][] ;
            Color lc = new Color( 230 , 230 , 230 ) ;
            for( int i = 0 , m = 0 ; i < pvrSet.length ; i += 2 ){
               String [] [] pvr = (String[][])pvrSet[i+1] ;
               for( int j = 0 ; j < pvr.length ; j++ ){
                  String [] drive = (String [])pvr[j] ;
                  Label  [] lx = new Label[drive.length] ;
                  for( int l = 0 ; l < drive.length ; l ++ ){
                     lx[l] = new DriveLabel( "" , Label.CENTER )  ;
                     if( l == 0 )
                        lx[l].addMouseListener( _driveClick ) ;
                                   
                     lx[l].setBackground( lc ) ;
                     lx[l].setFont( _itemFont ) ;

                     _driveList.add( lx[l] ) ;
                  }
                  _labels[m++] = lx ;
               }

            }
            _initiated = true ;
         }
         private void displayDrives( Object [] pvrSet ){
            if( ! _initiated )initDrives( pvrSet ) ;
            for( int i = 0 , m = 0 ; i < pvrSet.length ; i += 2 ){
               String [][] pvr = (String[][])pvrSet[i+1] ;
               for( int j = 0 ; j < pvr.length ; j++ ){
                  String [] drive = (String [])pvr[j] ;
                  Label  [] lx    = _labels[m++];
                  for( int l = 0 ; l < drive.length ; l ++ ){
                     lx[l].setText(l == 0 ? 
                                   pvrSet[i].toString()+":"+drive[0] :
                                   drive[l])  ;

                  }
                  lx[0].setForeground(drive[1].equals("disabled" )?Color.red:Color.black) ;
               }

            }
            _driveList.invalidate() ; 
            validateTree() ;
//            repaint() ; 
//            doLayout() ;

         }
         public void domainAnswerArrived( Object obj , int id ){

            try{ 
               if( obj instanceof Object [] ){
                   displayDrives( (Object[]) obj ) ;
                   setText("") ;
                   return ;
               }else{
                   setText( obj.toString() ) ;
               }   
            }catch( Exception ee ){
               setText( ee.toString() ) ;
            }
         }
         //
         // action interface
         //
         public void actionPerformed( ActionEvent event ) {
           Object source = event.getSource() ;
           if( source == _update ){
               try{
                  synchronized( _threadLock ){
                     _connection.sendObject( "ls drive -cellPath=pvl" , this , 0 ) ;
                  }
               }catch( Exception eee ){
                  System.err.println( "Send failed : "+eee ) ;
                  eee.printStackTrace() ;
               }
           }else if( source == _auto ){
              synchronized( _threadLock ){
                 if( _autoUpdate ){
                    _autoUpdate = false ;
                    _threadLock.notifyAll() ;
                 }else{
                    _updateThread = new Thread( this ) ;
	            _updateThread.start() ;
                    _auto.setLabel( _stopString ) ;
                    _update.setEnabled(false) ;
                    _autoUpdate = true ;
                 }
              }
           }

         }
         public void run(){
           synchronized( _threadLock ){
              for( int i = 0 ; ; i++ ){
                 try{
                    _connection.sendObject( "ls drive -cellPath=pvl" , this , 0 ) ;
                 }catch( Exception eee ){
                    setText( "Send failed : "+eee ) ;
                    break ;
                 }
	         try{ 
                    _threadLock.wait(5000) ;
                    if( ! _autoUpdate )break ;
	         }catch(InterruptedException ie ){
	            setText( "Interrupted" ) ;
                    break ;
	         }
              }      
              _auto.setLabel( _startString ) ;
              _update.setEnabled(true) ;
              _autoUpdate = false ;
           }
        }
     } 
     //
     //
     //
     ////////////////////////////////////////////////////////////////////////////
     //    
   /*
   public void connectionOpened( DomainConnection connection ){
      System.out.println("Connection established" ) ;
      try{
         _connection.sendObject( "ls drive -cellPath=pvl" , this , 0 ) ;
      }catch(Exception ee ){
      
      }
   }
   public void connectionClosed( DomainConnection connection ){
      System.out.println("Connection closed" ) ;
   }
   public void connectionOutOfBand( DomainConnection connection ,
                                    Object subject                ){
   }
   */
}
 
