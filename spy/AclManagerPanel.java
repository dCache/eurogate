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

import eurogate.misc.users.* ;

public class      AclManagerPanel 
       extends    MasterPanel  
       implements ActionListener    {
        
     private Font   _headerFont = 
                           new Font( "SansSerif" , 0 , 18 ) ;
     private Font   _itemFont = 
                           new Font( "SansSerif" , 0 , 12 ) ;
                           
     private DomainConnection _connection     = null ;
     private CreateAclPanel   _createAclPanel = null ;
     private ListPanel        _listPanel      = null ;
     private PrincipalPanel   _principalPanel = null ;
     private Hashtable        _aclHash        = null ;
     private String           _inherits       = null ;
     
     public Dimension getPreferredSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     public Dimension getMaximumSize(){ 
         Dimension ss = super.getMinimumSize() ;
         return  ss ; 
     }
     class EventHelper extends SimpleBorderPanel {
        private ActionListener _actionListener = null ;
        EventHelper( LayoutManager lm , int border , Color color ){
            super( lm , border , color ) ;
        }
        public void addActionListener( ActionListener actionListener ){
           _actionListener = AWTEventMulticaster.add( 
                                _actionListener, actionListener ) ;                                  
        }
        protected void processActionEvent( ActionEvent e ){
            if( _actionListener != null )
               _actionListener.actionPerformed(e) ;
        }
     }
     private class PrincipalEvent extends ActionEvent {
        private String _principal = "" ;
        private String _mode = "" ;
        private PrincipalEvent( String mode , String principal , Object obj ){
            super( obj , 0 ,  mode+":"+principal ) ;
            _mode = mode ;
            _principal = principal ;
            
        }
        private String getPrincipal(){ return _principal ; }
        private String getMode(){return _mode ; }
     }
     private void sendCommand( String command , DomainConnectionListener l ){
       try{
          String toBeSent = command + " -cellPath=AclCell" ;
          System.out.println( "sending : "+toBeSent ) ;
          _connection.sendObject(  toBeSent ,  l , 0 ) ;
       }catch(Exception ee ){
          setText( ee.toString() ) ;
       }
     }
     //////////////////////////////////////////////////////////////////////////
     //
     //   the acl create, discard part
     //
     private class      CreateAclPanel 
             extends    EventHelper 
             implements ActionListener, 
                        TextListener ,
                        DomainConnectionListener  {
            
        private TextField _aclText       = new TextField("") ;
        private Button    _createButton  = new Button( "Create" ) ;
        private Button    _discardButton = new Button( "Destroy" ) ;
        private Button    _updateButton  = new Button( "Resolve" ) ;
        private String    _mode          = "" ;
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
        public String getAclName(){ return _aclText.getText() ; }
        public void setEnabled( boolean en ){
            _createButton.setEnabled(en) ;
            _discardButton.setEnabled(en) ;
//            _updateButton.setEnabled(en) ;
        }
        private void setCreateEnabled( boolean en ){
            _createButton.setEnabled(en) ;
//            _updateButton.setEnabled(!en) ;
            _discardButton.setEnabled( 
                ( !en )  && 
                ( _aclHash != null ) && 
                ( _aclHash.size() == 0 )   );
        }
        public void textValueChanged(TextEvent e){        
           setEnabled(false) ;
        }
        public void actionPerformed( ActionEvent event ) {
          Object source = event.getSource() ;
          String acl = _aclText.getText() ;
          //
          // we want to stay disabled until the answer arrived.
          //
          setEnabled(false);
          if( source == _aclText ){
             if( acl.equals("") ){
                setText( "Which acl ??? " ) ;
                processActionEvent(new ActionEvent(this,0,"clear"));
                return ;
             }
             setText("Waiting for update");
             _mode = "update" ;
             sendCommand( "show acl "+acl ,this ) ;
          }if( source == _updateButton ){
             _mode = "update" ;
             setText("Waiting for update");
             sendCommand( "show acl -resolve "+acl ,this ) ;
          }if( source == _discardButton ){
             _mode = "discard" ;
             setText("Waiting for discard operation");
             sendCommand( "destroy acl "+acl ,this ) ;
          }if( source == _createButton ){
             _mode = "create" ;
             setText("Waiting for create operation");
             sendCommand( "create acl "+acl , this ) ;
          }
        }     
        public void domainAnswerArrived( Object obj , int id ){
           if( obj instanceof Hashtable ){
               _aclHash  = (Hashtable)obj ;
               _inherits = (String)_aclHash.get("<inheritsFrom>") ;
               _aclHash.remove("<inheritsFrom>") ;
               
               processActionEvent(new ActionEvent(this,0,"ok"));
               setCreateEnabled(false) ;
               setText("");
           }else if( obj instanceof String ){
               //
               // for now, we assume that is String means 'ok'.
               //
               setText("");
               if( _mode.equals("update" ) || _mode.equals("create" ) ){
                   sendCommand( "show acl "+_aclText.getText() , this ) ;
               }else{
                   setEnabled(false);
                   processActionEvent(new ActionEvent(this,0,"clear"));                 
               }
               //
               // this will result in an empty hashtable
               // but it's much simpler for our logic.
               //
           }else{
               Object result = setText(obj) ;
               if( result instanceof NoSuchElementException ){
                  setCreateEnabled(true);
               }else{
                  setEnabled(false);
               }
               processActionEvent(new ActionEvent(this,0,"clear"));
               return ;
           }
        }
     }
     ////////////////////////////////////////////////////////////////////
     //
     //       p r i n c i p a l    P a n a l
     //
     private class      PrincipalPanel 
             extends    EventHelper
             implements ActionListener,
                        DomainConnectionListener,
                        TextListener {
         
         private Button _allowedButton = new Button("Add Allowed") ;
         private Button _deniedButton  = new Button("Add Denied" ) ;
         private Button _removeButton  = new Button("Remove");     
         private TextField _principalText = new TextField("");
         
         private PrincipalPanel(){
            super( new RowColumnLayout(5,RowColumnLayout.LAST) , 15 , Color.red) ;
            add( _allowedButton ) ;
            add( _deniedButton ) ;
            add( _removeButton ) ;
            Label t = new Label( "Principal" ) ;
            t.setFont( _headerFont ) ;
            
            add( t ) ;
            
            _principalText.addTextListener(this);
            _allowedButton.addActionListener(this);
            _deniedButton.addActionListener(this);
            _removeButton.addActionListener(this);
            
            add( _principalText ) ;
         }  
         public void textValueChanged(TextEvent e){
            if( _aclHash == null ){
               setButtonEnabled(false) ;
               return ;
            } 
            String name = _principalText.getText() ;
            if( name.equals("") ){
               setButtonEnabled(false) ;
               return ;
            } 
            setAddEnabled(  
               _aclHash.get( _principalText.getText() )== null ) ;   
         }
         private void setPrincipal( String principal ){
            _principalText.setText( principal ) ;
         }
         public void setEnabled( boolean en ){
            _allowedButton.setEnabled(en) ;
            _deniedButton.setEnabled(en);
            _removeButton.setEnabled(en);
            _principalText.setEnabled(en) ;
            
         }
         public void setButtonEnabled( boolean en ){
            _allowedButton.setEnabled(en) ;
            _deniedButton.setEnabled(en);
            _removeButton.setEnabled(en);
            
         }
         public void setAddEnabled( boolean en ){
            _allowedButton.setEnabled(en) ;
            _deniedButton.setEnabled(en);
            _removeButton.setEnabled(!en);
            
         }
         public void domainAnswerArrived( Object obj , int id ){
            if( obj instanceof Throwable ){
               setText(obj) ;
               setEnabled(false) ;
               return ;
            }else if( obj instanceof Hashtable ){
               setText("");
               _aclHash = (Hashtable)obj ;
               processActionEvent(new ActionEvent(this,0,"ok"));
            }else{
               sendCommand( "show acl "+_createAclPanel.getAclName() ,
                            this ) ;
            }   
         }
         private String _mode = "" ;
         public void actionPerformed( ActionEvent event ) {
           Object source = event.getSource() ;
           setEnabled(false);
           if( source == _removeButton ){
              _mode = "remove" ;
              setText( "Waiting for remove" ) ;
              sendCommand( "remove access "+
                           _createAclPanel.getAclName()+" "+
                           _principalText.getText() ,
                           this   ) ;
                           
           }else if( source == _allowedButton ){
              _mode = "allowed" ;
              setText( "Waiting for allowed" ) ;
              sendCommand( "add access -allowed "+
                           _createAclPanel.getAclName()+" "+
                           _principalText.getText() ,
                           this   ) ;
           }else if( source == _deniedButton ){
              _mode = "denied" ;
              setText( "Waiting for denied" ) ;
              sendCommand( "add access -denied "+
                           _createAclPanel.getAclName()+" "+
                           _principalText.getText() ,
                           this   ) ;
           }
         }     
     }
     ////////////////////////////////////////////////////////////////////
     //
     //       L i s t   P a n a l
     //
     private class   ListPanel 
             extends EventHelper {
             
         private java.awt.List _allowedList = new java.awt.List() ;
         private java.awt.List _deniedList  = new java.awt.List() ;
         private Label         _inheritanceLabel = new Label("") ;
         private ListPanel(){
            super( new BorderLayout() , 20 , Color.blue ) ;
            
            BorderLayout gl = new BorderLayout() ;
            gl.setHgap(20) ;
            gl.setVgap(20);
            setLayout( gl ) ;
            
            BorderLayout leftLayout = new BorderLayout() ;
            leftLayout.setHgap(10) ;
            leftLayout.setVgap(10) ;
            BorderLayout rightLayout = new BorderLayout() ;
            rightLayout.setHgap(10) ;
            rightLayout.setVgap(10) ;
            Panel leftPanel = new Panel( leftLayout ) ;
            Panel rightPanel =  new Panel( rightLayout ) ;
            
            Label tmp = new Label( "Allowed" , Label.CENTER) ;
            tmp.addMouseListener( new ClearSelection() ) ;
            tmp.setFont( _headerFont ) ;
            leftPanel.add( tmp , "North" ) ;
            leftPanel.add( _allowedList , "Center" ) ;
            
            tmp = new Label( "Denied" , Label.CENTER ) ;
            tmp.setFont( _headerFont ) ;
            tmp.addMouseListener( new ClearSelection() ) ;
            rightPanel.add( tmp , "North" ) ;
            rightPanel.add( _deniedList , "Center" ) ;
            
            Panel centerPanel = new Panel( new GridLayout( 0 , 2 ) ) ;
            centerPanel.add( leftPanel ) ;
            centerPanel.add( rightPanel ) ;
            
            Panel bottomPanel = new Panel( new RowColumnLayout(2,1) ) ;
            Label inLabel =  new Label("Inherits") ;
            inLabel.setFont( _headerFont ) ;
            bottomPanel.add( inLabel ) ;
            bottomPanel.add( _inheritanceLabel) ;
            
            add( centerPanel , "Center" ) ;
            add( bottomPanel , "South" ) ;
            
             _allowedList.addItemListener(
                 new ItemListener(){
                    public void itemStateChanged( ItemEvent e ){
                      int i = _deniedList.getSelectedIndex() ;
                      if( i > -1 )_deniedList.deselect(i) ;
                      i = _allowedList.getSelectedIndex() ;
                      if( _inherits == null )return ;
                      if( i < 0 )sendAction("") ;
                      else sendAction( _allowedList.getItem(i) ) ;
                    }
                 }
             ) ;
             _deniedList.addItemListener(
                 new ItemListener(){
                    public void itemStateChanged( ItemEvent e ){
                      int i = _allowedList.getSelectedIndex() ;
                      if( i > -1 )_allowedList.deselect(i) ;
                      i = _deniedList.getSelectedIndex() ;
                      if( _inherits == null )return ;
                      if( i < 0 )sendAction("") ;
                      else sendAction( _deniedList.getItem(i) ) ;
                    }
                 }
             ) ;
         }  
         private void modifyList( String mode , String principal ){
            if( mode.equals("remove") ){
               try{_deniedList.remove(principal) ;}catch(Exception ee){}
               try{_allowedList.remove(principal);}catch(Exception ee){}
            }else if( mode.equals( "allowed" ) ){
               _allowedList.add( principal ) ;
            }else if( mode.equals( "denied" ) ){
               _deniedList.add( principal ) ;
            }
         
         }
         private class ClearSelection extends MouseAdapter {
             public void mouseClicked( MouseEvent e ){
                 int i = _deniedList.getSelectedIndex() ;
                 if( i > -1 )_deniedList.deselect(i) ;
                 i = _allowedList.getSelectedIndex() ;
                 if( i > -1 )_allowedList.deselect(i) ;
                 sendAction("") ;
             }   
         }
         private void sendAction( String name ){
          processActionEvent(
                  new ActionEvent( this , 0 , name ) 
               ) ;
          
         }
         private void updateList(){ 
            if( _aclHash == null )return ;
            Enumeration e = _aclHash.keys() ;
            clearList() ;
            for( ; e.hasMoreElements() ; ){
               String principal = (String)e.nextElement() ;
               boolean x = ((Boolean)_aclHash.get( principal )).booleanValue() ;
               if( x ){
                  _allowedList.add( principal ) ;
               }else{
                  _deniedList.add( principal ) ;
               }
            }
            if( _inherits == null )_inheritanceLabel.setText("Resolved");
            else _inheritanceLabel.setText(_inherits);
         } 
         private void clearList(){
            if( _allowedList.getItemCount() != 0 )
               _allowedList.removeAll() ;
            if( _deniedList.getItemCount() != 0 )
               _deniedList.removeAll() ;
         }    
     }
     //////////////////////////////////////////////////////////////////
     //
     //
     //              main switch bord
     //
     public void actionPerformed( ActionEvent e ){
        Object source = e.getSource() ;
        String action = e.getActionCommand() ;
        System.out.println( "Action : "+e ) ;
        if( source == _listPanel ){
           if( action.equals("") ){
               _principalPanel.setPrincipal("") ;
               _principalPanel.setEnabled( true ); 
               _principalPanel.setButtonEnabled( false ); 
           }else{
               _principalPanel.setPrincipal( action ) ;
           }
          
        }else if( source == _createAclPanel ){
           if( action.equals( "ok" ) ){
              _listPanel.updateList() ;
              _principalPanel.setEnabled(_inherits!=null);
              _principalPanel.setButtonEnabled(false) ;
              _principalPanel.setPrincipal("") ;
           }else if( action.equals( "clear" ) ){
              _listPanel.clearList() ;
              _principalPanel.setEnabled(false);
              _principalPanel.setPrincipal("") ;
           }
          
        }else if( source == _principalPanel ){
           if( action.equals( "ok" ) ){
              _createAclPanel.setCreateEnabled(false) ;
              _listPanel.updateList() ;
              _principalPanel.setEnabled(true);
              _principalPanel.setButtonEnabled(false) ;
              _principalPanel.setPrincipal("") ;
           }
        }
     } 
    //////////////////////////////////////////////////////////////////
    //
    //
    //              m a i n    r o u t i n e s
     
    private Object setText(Object obj ){
       if( obj instanceof Throwable ){
         return displayThrowable( (Throwable)obj ) ;
       }else{
          setMessage(obj.toString());
          return obj ;
       }
    }
    private Throwable displayThrowable( Throwable obj ){
        if( obj instanceof CommandThrowableException ){
            CommandThrowableException cte =
            (CommandThrowableException)obj ;
            Throwable t = cte.getTargetException() ;
            if( t instanceof AclException ){
               setMessage( "No Permission : "+t.getMessage() ) ; 
            }else if( t instanceof AclPermissionException ){
               setMessage( "No Permission : "+t.getMessage() ) ; 
            }else if( t instanceof NoSuchElementException ){
               setMessage( t.getMessage() ) ; 
            }else{
               setMessage( t.toString() ) ;
            }
            return t ;
        }else if( obj instanceof CommandException ){
            setMessage( ((Exception)obj).getMessage() ) ;
        }else{
            setMessage( obj.toString() ) ;
        }
        return obj ;   
     }
     public AclManagerPanel( DomainConnection connection ){
        super( "Acl Manager" ) ;
        setBorderColor( Color.white ) ;
        setBorderSize( 5 ) ;
                
        _connection = connection ;
                
         
        Panel masterPanel = 
           new SimpleBorderPanel( new BorderLayout() , 15 , Color.blue ) ;
                   
        _createAclPanel = new CreateAclPanel() ;
        _listPanel      = new ListPanel() ;
        _principalPanel = new PrincipalPanel() ;
        _principalPanel.setEnabled(false);
        
        _listPanel.addActionListener( this ) ;
        _principalPanel.addActionListener( this ) ;
        _createAclPanel.addActionListener( this ) ;
        
        masterPanel.add( _createAclPanel , "North" ) ;
        masterPanel.add( _listPanel      , "Center" ) ;
        masterPanel.add( _principalPanel , "South" ) ;
        
        add( masterPanel ) ;
        
        
     } 
     private String _helpText = 
       "    Help for the AclMangerPanel\n"+
       "    dideldum ... \n" +
       "" ;
     public String getHelpText(){
        return _helpText ;
     }
}
