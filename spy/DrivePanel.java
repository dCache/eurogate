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

public class      DrivePanel 
       extends    Panel      
       implements FrameArrivable , ActionListener, Runnable   {
       
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
                       new Font( "SansSerif" , 
                                 Font.ITALIC|Font.BOLD , 14 )  ;
     private Font   _itemFont = 
                       new Font( "SansSerif" , 
                                 Font.BOLD , 12 )  ;
                                 
     private String [] titles = 
             { "Drive" , "Cartridge" , "Action" , "Mode" , "Owner" } ;
             
     public DrivePanel( DomainConnection connection ){
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
        
        _driveList = new Panel( new GridLayout( 0,5 ) ) ;
        
        Label title = null ;
        for( int i = 0 ; i < titles.length ;  i++ ){
           title = new Label( titles[i] ) ;
           title.setFont( _headerFont ) ;
           title.setForeground( Color.red ) ;
           title.setBackground( Color.yellow ) ;
           _driveList.add( title ) ;
        }
        
        
        add( _driveList , "Center" ) ;
//        add( _textArea , "Center" ) ;
     }      
     private Color [] _colorSet = { Color.green , Color.yellow , Color.red } ;
     public void run(){
        for( int i = 0 ; ; i++ ){
            _connection.send( "pvl" , "x lsdrive" , this ) ;
	    try{ 
	      
	        Thread.currentThread().sleep(5000) ;
	    }catch(InterruptedException ie ){
	        break ;
	    }
            _auto.setBackground( _colorSet[i %_colorSet.length] ) ;
	}      
        _auto.setLabel( _startString ) ;
        _update.setEnabled(true) ;
        _autoUpdate = false ;
	_auto.setBackground( Color.green ) ;

     }
     public void frameArrived( MessageObjectFrame frame ){
         Object obj = frame.getObject() ;
         if( obj instanceof Exception){
            System.out.println( "Exception arrived : "+obj.toString() ) ;
            return ;
         }
         Object [] pvrSet = (Object []) obj ;
         int m = 0 ;
         if( _initiated ){
             for( int i = 0 ; i < pvrSet.length ; i++ ){
                Object [] pvr = (Object[])pvrSet[i] ;
                for( int j = 1 ; j < pvr.length ; j++ ){
                   String [] drive = (String [])pvr[j] ;
                   Label  [] lx    = _labels[m++];
                   for( int l = 0 ; l < drive.length ; l ++ ){
                      lx[l].setText(drive[l]) ;
                   }
                }

             }
         
         }else{
             int driveCount = 0 ;
             for( int i = 0 ; i < pvrSet.length ; i++ ){
                Object [] pvr = (Object[])pvrSet[i] ;
                for( int j = 1 ; j < pvr.length ; j++ ){
                   driveCount++ ;
                }

             }
             _labels = new Label[driveCount][] ;
             m = 0 ;
             for( int i = 0 ; i < pvrSet.length ; i++ ){
                Object [] pvr = (Object[])pvrSet[i] ;
                for( int j = 1 ; j < pvr.length ; j++ ){
                   String [] drive = (String [])pvr[j] ;
                   Label  [] lx = new Label[drive.length] ;
                   for( int l = 0 ; l < drive.length ; l ++ ){
                      lx[l] = new Label(drive[l])  ;
                      lx[l].setForeground( Color.blue ) ;
                      lx[l].setBackground( Color.orange ) ;
                      lx[l].setFont( _itemFont ) ;

                      _driveList.add( lx[l] ) ;
                   }
                   _labels[m++] = lx ;
                }

             }
             validateTree() ;
             _initiated = true ;
         }   
         
     }
     public void frameArrivedx( MessageObjectFrame frame ){
         Object obj = frame.getObject() ;
        _textArea.setText( "" ) ;
         Object [] pvrSet = (Object []) obj ;
         for( int i = 0 ; i < pvrSet.length ; i++ ){
            Object [] pvr = (Object[])pvrSet[i] ;
            _textArea.append( (String)pvr[0] + "\n" ) ;
            for( int j = 1 ; j < pvr.length ; j++ ){
               String [] drive = (String [])pvr[j] ;
               _textArea.append( "   " ) ;
               for( int l = 0 ; l < drive.length ; l ++ ){
                  _textArea.append( drive[l]+"  " ) ;
               }
               _textArea.append( "\n" ) ;
            
            }
         
         }
     }

  //
  // action interface
  //
  public void actionPerformed( ActionEvent event ) {
    Object source = event.getSource() ;
    if( source == _update ){
       _connection.send( "pvl" , "x lsdrive" , this ) ;
    }else if( source == _auto ){
       if( _autoUpdate ){
           _updateThread.interrupt() ;
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
 
