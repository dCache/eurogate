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
       extends    Panel      
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
     private Label [][]_labels ;
     private boolean   _autoUpdate = false ;
     private Thread    _updateThread = null ;     
     private Font   _headerFont = 
                       new Font( "SansSerif" , 0 , 18 ) ;
 //                                Font.ITALIC|Font.BOLD , 14 )  ;
     private Font   _itemFont = 
                       new Font( "SansSerif" , 0 , 12 ) ;
 //                                Font.BOLD , 12 )  ;
                                 
     private String [] titles = 
             { "Drive" , "Mode" , "Cartridge" , "Owner" , "Action" } ;
             
     private int _b = 5 ;
     public Insets getInsets(){ return new Insets(_b , _b ,_b , _b) ; }
     public void paint( Graphics g ){

        Dimension   d    = getSize() ;
        Color base = getBackground() ;
        g.setColor( Color.white ) ;
        g.drawRect( _b/2 , _b/2 , d.width-_b , d.height-_b ) ;
     }
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         System.out.println( "getPreferredSize : "+ss ) ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         System.out.println( "getMaximumSize : "+ss ) ;
         return  ss ; 
     }
     private class DummyListener implements DomainConnectionListener {
         public void domainAnswerArrived( Object obj , int id ){}     
     }
     private DummyListener _dummyListener = new DummyListener() ;
     private class DriveLabel extends Label {
     
        private DriveLabel( String l ){ super(l) ; }
        private DriveLabel( String l , int p ){ super( l , p ) ; }
        public  void setText( String text ){
           super.setText( text ) ;
           invalidate() ;
        }
     }
     private class DriveContainerPanel extends Panel {
        private DriveContainerPanel( LayoutManager lo ){ super(lo) ; }
        private int _b = 5 ;
        public Insets getInsets(){ return new Insets(_b , _b ,_b , _b) ; }
        public void paint( Graphics g ){

           Dimension   d    = getSize() ;
           Color base = getBackground() ;
           g.setColor( Color.black ) ;
           g.drawRect( _b/2 , _b/2 , d.width-_b , d.height-_b ) ;
        }
     
     
     }
     public DriveDisplayPanel( DomainConnection connection ){
        _connection = connection ;
        setLayout( new BorderLayout() ) ;
        _buttons = new Panel( new FlowLayout() ) ;
        
        _update = new Button( "Update" ) ;
        _auto   = new Button( _startString ) ;
        
        _buttons.add( _update  ) ;
        _buttons.add( _auto    ) ;
        
        _update.addActionListener( this ) ;
        _auto.addActionListener( this ) ;
        
        add( _buttons  , "North"  ) ;
        
        _driveList = new DriveContainerPanel( new GridLayout( 0,6 ) ) ;
        _driveList.setBackground( new Color( 200 , 200 ,200 ) ) ;
        
        Label title = null ;
        Label sel = new Label( "S" ) ;
        sel.setFont( _headerFont ) ;
        _driveList.add( sel ) ;
        for( int i = 0 ; i < titles.length ;  i++ ){
           title = new Label( titles[i] , Label.CENTER) ;
           title.setFont( _headerFont ) ;
//           title.setForeground( Color.red ) ;
//           title.setBackground( Color.yellow ) ;
           _driveList.add( title ) ;
        }
        
        Panel center = new Panel( new CenterLayout() ) ;
        center.add( _driveList ) ;
        add( center , "Center" ) ;
//        add( _textArea , "Center" ) ;
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
    private void displayDrives( Object [] pvrSet ){
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
             _labels = new Label[driveCount][] ;
             m = 0 ;
//             Color lc = _driveList.getBackground().brighter()  ;
             Color lc = new Color( 230 , 230 , 230 ) ;
             for( int i = 0 ; i < pvrSet.length ; i += 2 ){
                String [] [] pvr = (String[][])pvrSet[i+1] ;
                for( int j = 0 ; j < pvr.length ; j++ ){
                   _driveList.add( new Checkbox( "" ) ) ;
                   String [] drive = (String [])pvr[j] ;
                   Label  [] lx = new Label[drive.length] ;
                   for( int l = 0 ; l < drive.length ; l ++ ){
                      
                      lx[l] = new DriveLabel( l == 0 ? 
                                         pvrSet[i].toString()+":"+drive[0] :
                                         drive[l] , Label.CENTER )  ;
                                         
//                      lx[l].setForeground( Color.blue ) ;
                      lx[l].setBackground( lc ) ;
                      lx[l].setFont( _itemFont ) ;

                      _driveList.add( lx[l] ) ;
                   }
                   _labels[m++] = lx ;
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
   public void connectionOpened( DomainConnection connection ){
      System.out.println("Connection established" ) ;
   }
   public void connectionClosed( DomainConnection connection ){
      System.out.println("Connection closed" ) ;
   }
   public void connectionOutOfBand( DomainConnection connection ,
                                    Object subject                ){
   }

}
 
