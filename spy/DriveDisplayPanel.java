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
       extends    SimpleBorderPanel      
       implements DomainConnectionListener , 
                  DomainEventListener ,
                  ActionListener, 
                  Runnable   {
       
     private DomainConnection _connection = null ;
     private Panel     _buttons  ;
     private TextArea  _textArea = new TextArea() ;
     private Button    _update  , _auto ;
     private Panel     _driveList ;
     private boolean   _initiated = false ;
     private String    _startString = "Start Automatic Update" ;
     private String    _stopString  = "Stop Automatic Update" ;
     private Label [][]_labels = null ;
     private boolean   _autoUpdate = false ;
     private Thread    _updateThread = null ;     
     private Font   _headerFont = 
                       new Font( "SansSerif" , 0 , 18 ) ;
     private Font   _itemFont = 
                       new Font( "SansSerif" , 0 , 12 ) ;
                                 
     private String [] titles = 
             { "Drive" , "Mode" , "Cartridge" , "Owner" , "Action" } ;
             
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     private class DriveLabel extends Label {
     
        private DriveLabel( String l ){ super(l) ; }
        private DriveLabel( String l , int p ){ super( l , p ) ; }
        public  void setText( String text ){
           super.setText( text ) ;
           invalidate() ;
        }
     }
     private Panel _driveListPanel = null ;
     public DriveDisplayPanel( DomainConnection connection ){
        super( new BorderLayout() , 15 , Color.white ) ;
        
        _connection = connection ;
        
        _driveListPanel = new SimpleBorderPanel( new BorderLayout() , 30 ) ;
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
        
        _driveListPanel.add( _buttons  , "North"  ) ;
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
        
        _driveListPanel.add( _driveList , "Center" ) ;
         
        add( _driveListPanel , "Center" ) ;
        
        Label title = new Label( "Drive Manager" , Label.CENTER ) ;
        title.setFont( _headerFont ) ;
        
        add( title , "North" ) ;
         
        _connection.addDomainEventListener( this ) ;       
     }      
    public void domainAnswerArrived( Object obj , int id ){
    
       try{ _domainAnswerArrived( obj , id ) ; 
       }catch( Exception ee ){
          ee.printStackTrace() ;
       }
    }
    private void _domainAnswerArrived( Object obj , int id ) throws Exception {
        if( obj instanceof Exception){
           System.out.println( "Exception arrived : "+obj.toString() ) ;
           return ;
        }else if( obj instanceof String ){
           System.out.println( "String arrived : "+obj.toString() ) ;
           return ;
        }else if( obj instanceof Object [] ){
           displayDrives( (Object[]) obj ) ;
           return ;
        }    
    }
    private class DriveClick extends MouseAdapter {
    
        public void mouseClicked(MouseEvent e){
        
           remove( _driveListPanel  ) ;
        
        }
    }
    private void displayDrives( Object [] pvrSet ){
         System.out.println( "pvrSet : "+pvrSet ) ;
         int m = 0 ;
         if( _initiated ){
             for( int i = 0 ; i < pvrSet.length ; i += 2 ){
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
             validateTree() ;
         }else{
             int driveCount = 0 ;
             for( int i = 0 ; i < pvrSet.length ; i += 2 ){
                String [] [] pvr = (String[][])pvrSet[i+1] ;
                for( int j = 0 ; j < pvr.length ; j++ ){
                   driveCount++ ;
                }

             }
             System.out.println( "Drive count : "+driveCount ) ;
             try{
                _labels = new Label[driveCount][] ;
             }catch( NullPointerException ee ){
                System.out.println( "problem in allocating labels : "+ee ) ;
                _labels = new Label[driveCount][] ;
             }
             m = 0 ;
//             Color lc = _driveList.getBackground().brighter()  ;
             Color lc = new Color( 230 , 230 , 230 ) ;
             for( int i = 0 ; i < pvrSet.length ; i += 2 ){
                String [] [] pvr = (String[][])pvrSet[i+1] ;
                for( int j = 0 ; j < pvr.length ; j++ ){
                   String [] drive = (String [])pvr[j] ;
                   Label  [] lx = new Label[drive.length] ;
                   for( int l = 0 ; l < drive.length ; l ++ ){
                      lx[l] = new DriveLabel( "" , Label.CENTER )  ;
                      if( l == 0 ){
                         lx[l].setText( pvrSet[i].toString()+":"+drive[0])  ;
                         lx[l].addMouseListener( new DriveClick() ) ;
                      }else{
                         lx[l].setText( drive[l])  ;
                      }              
                      lx[l].setBackground( lc ) ;
                      lx[l].setFont( _itemFont ) ;

                      _driveList.add( lx[l] ) ;
                   }
                   _labels[m++] = lx ;
                   lx[0].setForeground(drive[1].equals("disabled" )?Color.red:Color.black) ;
                }

             }
             validateTree() ;
             _initiated = true ;
         } 
         _driveList.invalidate() ; 
         repaint() ; 
         doLayout() ;
         
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
       if( _autoUpdate ){
//           _updateThread.interrupt() ;
          synchronized( _threadLock ){
             _autoUpdate = false ;
             _threadLock.notifyAll() ;
          }
       }else{
          synchronized( _threadLock ){
            _updateThread = new Thread( this ) ;
	    _updateThread.start() ;
            _auto.setLabel( _stopString ) ;
            _update.setEnabled(false) ;
            _autoUpdate = true ;
          }
       }
    }
  
  }
  private Color [] _colorSet = { Color.green , Color.yellow , Color.red } ;
  private Object _threadLock = new Object() ;
  public void run(){
    synchronized( _threadLock ){
       for( int i = 0 ; ; i++ ){
          try{
             _connection.sendObject( "ls drive -cellPath=pvl" , this , 0 ) ;
          }catch( Exception eee ){
             System.err.println( "Send failed : "+eee ) ;
             eee.printStackTrace() ;
             break ;
          }
	  try{ 

//	      Thread.currentThread().sleep(5000) ;
              _threadLock.wait(5000) ;
              if( ! _autoUpdate )break ;
	  }catch(InterruptedException ie ){
	      System.out.println( "Interrupted" ) ;
              break ;
	  }
//            _auto.setBackground( _colorSet[i %_colorSet.length] ) ;
       }      
       _auto.setLabel( _startString ) ;
       _update.setEnabled(true) ;
       _autoUpdate = false ;
//	_auto.setBackground( Color.green ) ;
    }

  }
  private SingleDrivePanel extends SimpleBorderPanel {
  
     private Label _driveText = null ;
     private Label _pvrTest   = null ;
  
     private SingleDrivePanel(){
         super( new BorderLayout() , 15 , Color.red ) ;
     }
  }
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

}
 
