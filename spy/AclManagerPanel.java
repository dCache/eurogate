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

public class      AclManagerPanel 
       extends    SimpleBorderPanel  {
       
     private Font   _headerFont = 
                           new Font( "SansSerif" , 0 , 18 ) ;
     private Font   _itemFont = 
                           new Font( "SansSerif" , 0 , 12 ) ;
                           
     private DomainConnection _connection = null ;
     private TextField    _aclText      = new TextField("") ;
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     private class CreateAclPanel 
             extends SimpleBorderPanel 
             implements ActionListener,
                        DomainConnectionListener  {
             
        private TextField _aclText  = new TextField("") ;
        private Label     _messages = new Label("") ;
        private Button    _createButton  = new Button( "Create" ) ;
        private Button    _discardButton = new Button( "Discard" ) ;
        private Button    _updateButton  = new Button( "Update" ) ;
        private CreateAclPanel( DomainConnection connection ){
           super( new GridLayout(0,1) , 15 , Color.red ) ;
           
           Panel top = new Panel( new RowColumnLayout(5,RowColumnLayout.LAST) ) ;
           
           _createButton.setEnabled(false) ;
           _discardButton.setEnabled(false) ;
           _updateButton.setEnabled(false) ;
           
           _createButton.addActionListener( this ) ;
           _discardButton.addActionListener( this ) ;
           _updateButton.addActionListener( this ) ;
           _aclText.addActionListener( this ) ;

           top.add( _createButton ) ;
           top.add( _discardButton ) ;
           top.add( _updateButton ) ;
           Label acl = new Label("Acl") ;
           acl.setFont( _headerFont ) ;
           top.add( acl ) ;
           top.add( _aclText ) ;
           add( top ) ;
           add( _messages ) ;
        }
        private void sendCommand( String command ){
          try{
             _connection.sendObject( command + " -cellPath=AclCell" ,
                                     this , 0 ) ;
          }catch(Exception ee ){
             setText( ee.toString() ) ;
          }
        }
        public void actionPerformed( ActionEvent event ) {
          Object source = event.getSource() ;
          setText("");
          if( source == _aclText ){
             String acl = _aclText.getText() ;
             if( acl.equals("") ){
                setText( "Which acl ??? " ) ;
                return ;
             }
             sendCommand( "show acl "+acl ) ;
          }
        }     
        public void domainAnswerArrived( Object obj , int id ){
           if( obj instanceof Throwable ){
               displayThrowable( (Throwable)obj ) ;
               _createButton.setEnabled(true) ;
               _discardButton.setEnabled(false) ;
               _updateButton.setEnabled(false) ;
               return ;
           }else if( obj instanceof Hashtable ){
               _createButton.setEnabled(false) ;
               _discardButton.setEnabled(true) ;
               _updateButton.setEnabled(true) ;
           }else{
              setText( obj.toString() ) ;
           }  
        }
        private void setText(String str ){ _messages.setText(str);}
     
     private void displayThrowable( Throwable obj ){
        if( obj instanceof CommandThrowableException ){
            CommandThrowableException cte =
            (CommandThrowableException)obj ;
            Throwable t = cte.getTargetException() ;
            if( t instanceof AclException ){
               setText( "No Permission : "+t.getMessage() ) ; 
            }else{
               setText( t.toString() ) ;
            }
        }else if( obj instanceof CommandException ){
            setText( ((Exception)obj).getMessage() ) ;
        }else{
            setText( obj.toString() ) ;
        }   
     }
     }
     private CreateAclPanel _createAclPanel = null ;
     public AclManagerPanel( DomainConnection connection ){
     
        super( new BorderLayout() , 15 , Color.white ) ;
                
        _connection = connection ;
                
        Label title = new Label( "Acl Manager" , Label.CENTER ) ;
        title.setFont( _headerFont ) ;
        
        add( title , "North" ) ;
         
        Panel masterPanel = 
           new SimpleBorderPanel( new BorderLayout() , 15 , Color.blue ) ;
           
        Panel aclPanel = new Panel( new BorderLayout() ) ;
        aclPanel.add( new Label( "Acl" ) , "West" ) ;
        aclPanel.add( _aclText , "Center" ) ;
//        aclPanel.add( _updateButton , "East" ) ;
        
        _createAclPanel = new CreateAclPanel( _connection ) ;
        
        masterPanel.add( _createAclPanel , "North" ) ;
        
        add( masterPanel , "Center" ) ;
        
        
     } 
}
