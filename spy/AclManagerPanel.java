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
       extends    SimpleBorderPanel  
       implements DomainConnectionListener  {
        
     private Font   _headerFont = 
                           new Font( "SansSerif" , 0 , 18 ) ;
     private Font   _itemFont = 
                           new Font( "SansSerif" , 0 , 12 ) ;
                           
     private DomainConnection _connection     = null ;
     private TextField        _aclText        = new TextField("") ;
     private Label            _messages       = new Label("") ;
     private CreateAclPanel   _createAclPanel = null ;
     private Hashtable        _aclHash        = null ;
     
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     //////////////////////////////////////////////////////////////////////////
     //
     //   the acl create, discard part
     //
     private class CreateAclPanel 
             extends SimpleBorderPanel 
             implements ActionListener, 
                        TextListener   {
            
        private TextField _aclText  = new TextField("") ;
        private Button    _createButton  = new Button( "Create" ) ;
        private Button    _discardButton = new Button( "Discard" ) ;
        private Button    _updateButton  = new Button( "Update" ) ;
        private CreateAclPanel( ){
           super( new GridLayout(0,1) , 15 , Color.red ) ;
           
           Panel top = new Panel( new RowColumnLayout(5,RowColumnLayout.LAST) ) ;
           
           setEnabled(false) ;
           
           _createButton.addActionListener( this ) ;
           _discardButton.addActionListener( this ) ;
           _updateButton.addActionListener( this ) ;
           _aclText.addActionListener( this ) ;
           _aclText.addTextListener(this) ;

           top.add( _createButton ) ;
           top.add( _discardButton ) ;
           top.add( _updateButton ) ;
           Label acl = new Label("Acl") ;
           acl.setFont( _headerFont ) ;
           top.add( acl ) ;
           top.add( _aclText ) ;
           add( top ) ;
        }
        public void setEnabled( boolean en ){
            _createButton.setEnabled(en) ;
            _discardButton.setEnabled(en) ;
            _updateButton.setEnabled(en) ;
        }
        private void setCreateEnabled( boolean en ){
            _createButton.setEnabled(en) ;
            _discardButton.setEnabled(!en) ;
            _updateButton.setEnabled(!en) ;
        }
        public void textValueChanged(TextEvent e){        
           setEnabled(false) ;
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
     }
     private PrincipalPanel _principalPanel = null ;
     private class PrincipalPanel 
             extends SimpleBorderPanel {
         
         private Button _allowedButton = new Button("Add Allowed") ;
         private Button _deniedButton  = new Button("Add Denied" ) ;
         private Button _removeButton  = new Button("Remove");     
         private TextField _principalText = new TextField("");
         
         private PrincipalPanel(){
            super( new RowColumnLayout(5,RowColumnLayout.LAST) , 15 ) ;
            add( _allowedButton ) ;
            add( _deniedButton ) ;
            add( _removeButton ) ;
            Label t = new Label( "Principal" ) ;
            t.setFont( _headerFont ) ;
            
            add( t ) ;
            
            add( _principalText ) ;
         }  
     }
     private ListPanel _listPanel = null ;
     private class ListPanel 
             extends SimpleBorderPanel {
             
         private List _allowedList = new List() ;
         private List _deniedList  = new List() ;
         private ListPanel(){
            super( new GridLayout( 0 , 2 ) , 20 , Color.blue ) ;
            BorderLayout leftLayout = new BorderLayout() ;
            leftLayout.setHgap(10) ;
            leftLayout.setVgap(10) ;
            BorderLayout rightLayout = new BorderLayout() ;
            rightLayout.setHgap(10) ;
            rightLayout.setVgap(10) ;
            Panel leftPanel = new Panel( leftLayout ) ;
            Panel rightPanel =  new Panel( rightLayout ) ;
            
            Label tmp = new Label( "Allowed" , Label.CENTER) ;
            tmp.setFont( _headerFont ) ;
            leftPanel.add( tmp , "North" ) ;
            leftPanel.add( _allowedList , "Center" ) ;
            
            tmp = new Label( "Denied" , Label.CENTER ) ;
            tmp.setFont( _headerFont ) ;
            rightPanel.add( tmp , "North" ) ;
            rightPanel.add( _deniedList , "Center" ) ;
            
            add( leftPanel ) ;
            add( rightPanel ) ;
            
         
         }  
         private void updateList(){ 
            if( _aclHash == null )return ;
            Enumeration e = _aclHash.keys() ;
            _allowedList.removeAll() ;
            _deniedList.removeAll() ;
            for( ; e.hasMoreElements() ; ){
               String principal = (String)e.nextElement() ;
               boolean x = ((Boolean)_aclHash.get( principal )).booleanValue() ;
               if( x ){
                  _allowedList.add( principal ) ;
               }else{
                  _deniedList.add( principal ) ;
               }
            }
         }     
     }
     private void setText(String str ){ _messages.setText(str);}
     public void domainAnswerArrived( Object obj , int id ){
        if( obj instanceof Throwable ){
           Throwable t = displayThrowable( (Throwable)obj ) ;
           if( t instanceof NoSuchElementException ){
              _createAclPanel.setCreateEnabled( true ) ;
           }else{
              _createAclPanel.setEnabled( false ) ;
           }
            
           return ;
        }else if( obj instanceof Hashtable ){
           _aclHash = (Hashtable)obj ;
           _createAclPanel.setCreateEnabled(false);
           if( _aclHash.size() > 0 ){
              _createAclPanel._discardButton.setEnabled(false);
              _listPanel.updateList() ;
           }
        }else{
           setText( obj.toString() ) ;
        }  
     }
     
     private Throwable displayThrowable( Throwable obj ){
        if( obj instanceof CommandThrowableException ){
            CommandThrowableException cte =
            (CommandThrowableException)obj ;
            Throwable t = cte.getTargetException() ;
            if( t instanceof AclException ){
               setText( "No Permission : "+t.getMessage() ) ; 
            }else if( t instanceof NoSuchElementException ){
               setText( t.getMessage() ) ; 
            }else{
               setText( t.toString() ) ;
            }
            return t ;
        }else if( obj instanceof CommandException ){
            setText( ((Exception)obj).getMessage() ) ;
        }else{
            setText( obj.toString() ) ;
        }
        return obj ;   
     }
     private void sendCommand( String command ){
       try{
          _connection.sendObject( command + " -cellPath=AclCell" ,
                                  this , 0 ) ;
       }catch(Exception ee ){
          setText( ee.toString() ) ;
       }
     }
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
        
        _createAclPanel = new CreateAclPanel() ;
        _listPanel      = new ListPanel() ;
        _principalPanel = new PrincipalPanel() ;
        
        masterPanel.add( _createAclPanel , "North" ) ;
        masterPanel.add( _listPanel      , "Center" ) ;
        masterPanel.add( _principalPanel , "South" ) ;
        add( masterPanel , "Center" ) ;
        
        add( _messages , "South" ) ;
        
        
     } 
}
