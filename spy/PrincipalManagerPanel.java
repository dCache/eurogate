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

public class      PrincipalManagerPanel 
       extends    SimpleBorderPanel  
       implements ActionListener    {


   private Font   _headerFont = 
                         new Font( "SansSerif" , 0 , 18 ) ;
   private Font   _itemFont = 
                         new Font( "SansSerif" , 0 , 12 ) ;

   private DomainConnection _connection      = null ;
   private Label            _messages        = new Label("") ;
   private Panel            _masterPanel     = null ;
   private CreateUserPanel  _createUserPanel = null ;
   private Panel            _currentPanel    = null ;
   private GroupPanel       _groupPanel      = new GroupPanel() ;
   private UserPanel        _userPanel       = new UserPanel() ;
   public PrincipalManagerPanel( DomainConnection connection ){

      super( new BorderLayout() , 15 , Color.white ) ;

      _connection = connection ;

      _createUserPanel = new CreateUserPanel() ;
      _createUserPanel.addActionListener(this) ;
      
      _groupPanel.addActionListener(this) ;
      
      
      Label title = new Label( "Principal Relation Manager" , Label.CENTER ) ;
      title.setFont( _headerFont ) ;

      add( title , "North" ) ;

      _masterPanel = 
         new SimpleBorderPanel( new BorderLayout() , 15 , Color.blue ) ;

      _masterPanel.add( _createUserPanel , "North" ) ;
//      masterPanel.add( _listPanel      , "Center" ) ;
//      masterPanel.add( _principalPanel , "South" ) ;

      add( _masterPanel , "Center" ) ;

      _messages.setForeground( Color.red ) ;
      add( _messages , "South" ) ;


   }
   //
   // our outfit
   // 
   public Dimension getPreferredSize(){ 
       Dimension ss = super.getMinimumSize() ;
       return  ss ; 
   }
   public Dimension getMaximumSize(){ 
       Dimension ss = super.getMinimumSize() ;
       return  ss ; 
   }
   //////////////////////////////////////////////////////////////////
   //
   //
   //              main switch board
   //
   private void showMaster( Panel panel ){
      if( panel == _currentPanel )return ;
      if( _currentPanel != null ){
         _masterPanel.remove( _currentPanel ) ;
         _currentPanel = null ;
      }
      if( panel == null )return ;
      _masterPanel.add( _currentPanel = panel , "Center" ) ;
   }
   public synchronized void  actionPerformed( ActionEvent e ){
      Object source = e.getSource() ;
      String action = e.getActionCommand() ;
      System.out.println( "Action ["+source+"] : "+e ) ;
      if( action.equals("groupOk") ){
         _groupPanel.setGroup( _createUserPanel.getPrincipal() ) ;
         _groupPanel.update() ;
         showMaster( _groupPanel ) ;
      }else if( action.equals("userOk" ) ){
         _userPanel.setUser( _createUserPanel.getPrincipal() ) ;
         _userPanel.update() ;
         showMaster( _userPanel ) ;
         
      }else{        
         showMaster(null);
      }
      validate() ;
//      if( source == _listPanel ){
//         if( action.equals("") ){
//         }
   } 
   /////////////////////////////////////////////////////////////
   //
   //  needful things
   //
   //  i) the eventmulticaster
   //
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
   //
   //  ii) the command sender
   //
   private void sendCommand( String command , DomainConnectionListener l ){
     try{
        String toBeSent = command + " -cellPath=AclCell" ;
        System.out.println( "sending : "+toBeSent ) ;
        _connection.sendObject(  toBeSent ,  l , 0 ) ;
     }catch(Exception ee ){
        setText( ee.toString() ) ;
     }
   }
   private void sendCommand( String command , 
                             DomainConnectionListener l ,
                             int userId  ){
     try{
        String toBeSent = command + " -cellPath=AclCell" ;
        System.out.println( "sending : "+toBeSent ) ;
        _connection.sendObject(  toBeSent ,  l , userId ) ;
     }catch(Exception ee ){
        setText( ee.toString() ) ;
     }
   }
   //
   // iii) error handling
   //
   private Object setText(Object obj ){
      if( obj instanceof Throwable ){
        return displayThrowable( (Throwable)obj ) ;
      }else{
         _messages.setText(obj.toString());
         return obj ;
      }
   }
   private Throwable displayThrowable( Throwable obj ){
       if( obj instanceof CommandThrowableException ){
           CommandThrowableException cte =
           (CommandThrowableException)obj ;
           Throwable t = cte.getTargetException() ;
           if( t instanceof AclException ){
              setText( "No Permission : "+t.getMessage() ) ; 
           }else if( t instanceof AclPermissionException ){
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
   /////////////////////////////////////////////////////////
   //
   // user admin panel
   //
   private class      PasswordPanel 
           extends    EventHelper 
           implements ActionListener, 
                      TextListener ,
                      DomainConnectionListener  {
        
        private Button _updateButton          = new Button( "Update" ) ;
        private TextField _oldPasswordText    = new TextField("");
        private TextField _newPasswordText    = new TextField("");
        private TextField _verifyPasswordText = new TextField("") ;
        private PasswordPanel(){
           super( new BorderLayout() , 15 , Color.red ) ;
           GridLayout gl = new GridLayout(0,3);
           gl.setHgap(15);
           gl.setVgap(15);
           setLayout(gl);
           add( _updateButton ) ;
           add( new Label( "Old Password" , Label.RIGHT ) ) ;
           add( _oldPasswordText ) ;
           add( new Label( "New Password" , Label.RIGHT ) ) ;
           add( _newPasswordText ) ;
           add( _verifyPasswordText ) ;
           
           _newPasswordText.addTextListener(this);
           _verifyPasswordText.addTextListener(this);
        }          
        public void textValueChanged(TextEvent e){  
           _updateButton.setEnabled(
                 ( ! _newPasswordText.getText().equals("") ) &&
                 (  _newPasswordText.getText().equals(
                     _verifyPasswordText.getText() ) ) ) ;      
        }
        public void actionPerformed( ActionEvent event ) {
        }
        public void domainAnswerArrived( Object obj , int id ){
           if( obj instanceof Throwable){
              setText(obj) ;
              return ;
           }
        }
   }
   /////////////////////////////////////////////////////////
   //
   // user admin panel
   //
   private class      UserPanel 
           extends    EventHelper 
           implements ActionListener, 
                      TextListener ,
                      DomainConnectionListener  {
                      
      private String _user = "" ;
      private List   _parentList = new List() ;
      private Label  _titleLabel = new Label( "User Manager",Label.CENTER) ;
      private TextField _emailText = new TextField("") ;
      private TextField _nameText  = new TextField("") ;
      private PasswordPanel _passwordPanel = new PasswordPanel() ;
      private UserPanel(){
        super( new BorderLayout() , 15 , Color.green ) ;
        
        BorderLayout bl = new BorderLayout() ;
        bl.setVgap(10) ;
        bl.setHgap(10) ;
        Panel parentPanel = new Panel( bl ) ;
        Label parentLabel = new Label( "Parents" , Label.CENTER ) ;
        parentLabel.setFont( _headerFont ) ;
        parentPanel.add( parentLabel , "North" ) ;
        parentPanel.add( _parentList , "Center" ) ;
        _parentList.addActionListener( this ) ;
        _titleLabel.setFont( _headerFont ) ;        
        add( _titleLabel , "North" ) ;
        add( parentPanel , "West" ) ;
         
        bl = new BorderLayout() ;
        bl.setVgap(10) ;
        bl.setHgap(10) ;
        Panel attributePanel = new SimpleBorderPanel( bl , 10 , Color.green ) ;
        attributePanel.add( new Label("User Attributes",Label.CENTER ),"North") ;

        _emailText.addActionListener( this ) ;
        _nameText.addActionListener( this ) ;
        RowColumnLayout rcl = new RowColumnLayout(2,RowColumnLayout.LAST) ;
        rcl.setVgap(10);
        rcl.setHgap(10);
        Panel rowPanel = new Panel( rcl ) ;
        rowPanel.add( new Label("Full Name") ) ;
        rowPanel.add( _nameText ) ;
        rowPanel.add( new Label("E-Mail") ) ;
        rowPanel.add( _emailText ) ;
        attributePanel.add( rowPanel , "Center" ) ;
        
        add( attributePanel , "Center" ) ;
        
        attributePanel.add( _passwordPanel , "South" ) ;
      }
      private void setUser(String user ){
         _user = user ; 
         _titleLabel.setText( "User >>"+_user+"<<") ;
         _titleLabel.invalidate() ;
      }
      private void update(){
         if( _parentList.getItemCount() > 0 )_parentList.removeAll() ;
         Enumeration e = _createUserPanel.getGroupParents() ;
         if( e != null )
            while( e.hasMoreElements() )
               _parentList.add(e.nextElement().toString());
         _emailText.setText("");
         _nameText.setText("") ;
         Hashtable attr = _createUserPanel.getPrincipalAttributes() ;
         
         String value = (String)attr.get("eMail") ;
         if( value != null )_emailText.setText(value) ;
         value = (String)attr.get("fullName") ;
         if( value != null )_nameText.setText(value) ;
      }
                      
      public void textValueChanged(TextEvent e){        
      }
      public void actionPerformed( ActionEvent event ) {
        Object source = event.getSource() ;
        String command = event.getActionCommand() ;
        setText("");
        if( source == _parentList ){
           _createUserPanel.activatePrincipal(command) ;
           return ;
        }else if( source == _emailText ){
           command = "set principal "+_user+
                            " \"eMail="+_emailText.getText()+"\"" ;
           setText( "Waiting for : "+command ) ;
           sendCommand( command , this ) ;
        }else if( source == _nameText ){
           command = "set principal "+_user+
                            " \"fullName="+_nameText.getText()+"\"" ;
           setText( "Waiting for : "+command ) ;
           sendCommand( command , this ) ;
        }
      }     
      public void domainAnswerArrived( Object obj , int id ){
         if( obj instanceof Throwable){
            setText(obj) ;
            update() ;
            return ;
         }
         _createUserPanel.activatePrincipal(_user) ;
      }
                      
   }
   /////////////////////////////////////////////////////////
   //
   // group admin panel
   //
   private class      GroupPanel 
           extends    EventHelper 
           implements ActionListener, 
                      TextListener ,
                      ItemListener ,
                      DomainConnectionListener  {
      
      private String _group      = "" ;
      private List   _memberList = new List() ;      
      private List   _parentList = new List() ;
      private Label  _titleLabel = new Label("",Label.CENTER) ; 
      private Button _removeButton = new Button( "Remove" ) ;
      private Button _addButton    = new Button( "Add" ) ;
      private TextField _memberText = new TextField( "" ) ;
      private Hashtable _memberHash = null ;
      private class ModifyMemberPanel extends SimpleBorderPanel {
         private ModifyMemberPanel(){
             super( new FlowLayout() , 15 , Color.blue ) ;
             RowColumnLayout rcl = new RowColumnLayout(1,0) ;
             rcl.setHgap(15) ;
             rcl.setVgap(15) ;
             setLayout( rcl ) ;
             
             Label addRemoveTitle = 
                  new Label( "Add / Remove Members" , Label.CENTER);
        
             add( addRemoveTitle ) ;
             
             add( _memberText ) ;
        
             GridLayout gl = new GridLayout(0,2) ;
             Panel buttonPanel = new Panel( gl ) ;
             buttonPanel.add( _addButton ) ;
             buttonPanel.add( _removeButton ) ;
        
             add( buttonPanel ) ;
                 
         }
      }
      private GroupPanel(){
        super( new BorderLayout() , 15 , Color.red ) ;
        
        _titleLabel.setFont( _headerFont ) ;
        add( _titleLabel , "North" ) ;
        
        Label membersTitle = new Label("Members" , Label.CENTER) ;
        Label parentsTitle = new Label("Parents" , Label.CENTER);        
        membersTitle.setFont( _headerFont ) ;
        parentsTitle.setFont( _headerFont ) ;
        
        BorderLayout bl = new BorderLayout() ;      
        Panel listPanel = new Panel( bl ) ;
        
        listPanel.add( parentsTitle , "North" ) ;
        listPanel.add( _parentList , "Center" ) ;
        
        add( listPanel , "West" ) ;
        
        bl = new BorderLayout(0,1) ;      
        listPanel = new Panel( bl ) ;
        
        listPanel.add( membersTitle , "North" ) ;
        listPanel.add( _memberList , "Center" ) ;
        
        add( listPanel , "East" ) ;
        
        _memberText.addTextListener( this ) ;
        _memberList.addItemListener( this ) ;
        _addButton.addActionListener( this ) ;
        _removeButton.addActionListener( this ) ;
        _memberList.addActionListener( this ) ;
        _parentList.addActionListener(this) ;
        add( new ModifyMemberPanel() , "Center" ) ;
      } 
      private void setGroup(String group ){
         _group = group ; 
         _titleLabel.setText( "Group >>"+_group+"<<") ;
         _titleLabel.invalidate() ;
      }
      private void update(){
         if( _memberList.getItemCount() > 0 )_memberList.removeAll() ;
         Enumeration e = _createUserPanel.getGroupMembers() ;
         _memberHash = new Hashtable() ;
         if( e != null )
            for( int i = 0 ;  e.hasMoreElements() ; i++ ){
               String member = e.nextElement().toString() ;
               _memberList.add( member );
               _memberHash.put( member , new Integer(i)  ) ;
            }
         if( _parentList.getItemCount() > 0 )_parentList.removeAll() ;
         e = _createUserPanel.getGroupParents() ;
         if( e != null )
            while( e.hasMoreElements() )
               _parentList.add(e.nextElement().toString());
         
         _memberText.setText("");
      }
      public void itemStateChanged( ItemEvent e ){
        int i = _memberList.getSelectedIndex() ;
        if( i < 0 )return ;
        String member = _memberList.getItem(i) ;
        _memberText.setText(member) ;
      }
      public void textValueChanged(TextEvent e){        
         Object source = e.getSource() ;
         if( source == _memberText ){
            int i = _memberList.getSelectedIndex() ;
            if( i > -1 )_memberList.deselect(i) ;
            _removeButton.setEnabled(false) ;
            _addButton.setEnabled(false);
            String member = _memberText.getText() ;
            if( member.equals("") )return ;
           
            Integer n = null ;
            if( ( n = (Integer)_memberHash.get( _memberText.getText() ) ) != null ){
               _removeButton.setEnabled(true) ;
               _memberList.select( n.intValue() ) ;
            }else{
               _addButton.setEnabled(true) ;
            }
         }
      }

      public void actionPerformed( ActionEvent event ) {
        Object source = event.getSource() ;
        String command = null ;
        if( source == _addButton ){
           command = "add "+_memberText.getText()+ " to "+_group ;
           setText( "Waiting for >>"+command+"<<" ) ;
           sendCommand( command, this , 1 ) ;
        }else if( source == _removeButton ){
           command = "remove "+_memberText.getText()+ " from "+_group ;
           setText( "Waiting for >>"+command+"<<" ) ;
           sendCommand( command, this , 1 ) ;
        }else if( ( source == _memberList ) ||
                  ( source == _parentList )   ){
           _createUserPanel.activatePrincipal( event.getActionCommand() ) ;  
        }
      }     
      public void domainAnswerArrived( Object obj , int id ){
         System.out.println( "Answer for groupPanel : "+obj.getClass()+";id="+id);
         if( obj instanceof Throwable ){
            
         }else if( id == 1 ){
             String command = "show group "+_group  ;
             setText( "Waiting for >>"+command+"<<" ) ;
             sendCommand( command, _createUserPanel , 0 ) ;
         }else if( id == 2 ){
             processActionEvent(
               new ActionEvent( this , 0 , "groupOk" ) 
             ) ;
         }
      }
   }
   /////////////////////////////////////////////////////////
   //
   // user group creation panel
   //
   private class      CreateUserPanel 
           extends    EventHelper 
           implements ActionListener, 
                      TextListener ,
                      DomainConnectionListener  {
      
      private Button    _createUserButton  = new Button("Create User") ;
      private Button    _createGroupButton = new Button("Create Group" ) ;
      private Button    _removeButton      = new Button("Destroy" ) ;
      private TextField _principalText     = new TextField("");   
      private Hashtable _userAttributes    = null ;
      private Vector    _groupMembers      = null ;
      private Vector    _groupParents      = null ;
      private CreateUserPanel( ){

         super( new GridLayout(0,1) , 15 , Color.red ) ;

         Panel top = new Panel( new RowColumnLayout(5,RowColumnLayout.LAST) ) ;

         setEnabled( false , false , false , true ) ;
         
         _createUserButton.addActionListener( this ) ;
         _createGroupButton.addActionListener( this ) ;
         _removeButton.addActionListener( this ) ;
         _principalText.addActionListener( this ) ;
         _principalText.addTextListener(this) ;

         top.add( _createUserButton ) ;
         top.add( _createGroupButton ) ;
         top.add( _removeButton ) ;
         Label principal = new Label("Principal") ;
         principal.setFont( _headerFont ) ;
         top.add( principal ) ;
         top.add( _principalText ) ;
         add( top ) ;

      }
      private void setEnabled( boolean cu , boolean cg , boolean rm ,
                               boolean tx ){
         _createUserButton.setEnabled( cu ) ;
         _createGroupButton.setEnabled( cg ) ;
         _removeButton.setEnabled( rm) ;
         _principalText.setEnabled( tx ) ;                        
      }
      private Enumeration getGroupMembers(){ 
         return _groupMembers == null ? null : _groupMembers.elements() ;
      }
      private Enumeration getGroupParents(){ 
         return _groupParents == null ? null : _groupParents.elements() ;
      }
      private Hashtable getPrincipalAttributes(){ 
         return _userAttributes ;
      }
      private void activatePrincipal( String principal ){
         setPrincipal( principal ) ;
         sendCommand( "show principal "+principal , this ) ;
      }
      private void setPrincipal( String str ){ _principalText.setText(str) ; }
      private String getPrincipal(){ return _principalText.getText() ; }
      public String toString(){ return "CreateUserPanel" ; }
      public void textValueChanged(TextEvent e){        
         setEnabled( false , false , false , true ) ;
      }
      public void actionPerformed( ActionEvent event ) {
        Object source = event.getSource() ;
        setText("");
        String principal = getPrincipal() ;
        _userAttributes = null ;
        _groupMembers   = null ;
        if( source == _principalText ){
           if( principal.equals("") ){
              setText( "Need a principal name ... " ) ;
              return ;
           }
           _userAttributes = null ;
           _groupMembers   = null ;
           setText( "Waiting for 'show principal "+principal ) ;
           sendCommand( "show principal "+principal , this ) ;
        }else if( source == _createUserButton ){
           setText( "Waiting for 'create user "+principal ) ;
           sendCommand( "create user "+principal , this ) ;
        }else if( source == _createGroupButton ){
           setText( "Waiting for 'create group "+principal ) ;
           sendCommand( "create group "+principal , this ) ;
        }else if( source == _removeButton ){
           setText( "Waiting for 'destroy principal "+principal ) ;
           sendCommand( "destroy principal "+principal , this , 100) ;
        }
      }     
      public void domainAnswerArrived( Object obj , int id ){
     
         String principal = getPrincipal() ;
         System.out.println( "createUserPanel : arrived : "+
                             obj.getClass()+"id="+id) ;
         if( obj instanceof Hashtable ){
            _userAttributes = (Hashtable)obj ;
            String type = (String) _userAttributes.get("type" ) ;
            if( type.equals("user") ){
               setText("Waiting for 'show parents'") ;
               sendCommand( "show parents "+principal , this ) ;
          
            }else if( type.equals("group") ){
               setText( "Waiting for 'show group "+principal ) ;
               sendCommand( "show group "+principal , this ) ;
            }else{
               setText( "Can't handle principal type : "+type ) ;
               processActionEvent(
                  new ActionEvent( this , 0 , "clear" ) 
               ) ;
            }
         }else if( obj instanceof Vector ){
            _groupParents = (Vector) obj ;
            _groupMembers = null ;
            setText("") ;
            setEnabled( false , false , true , true ) ;
            processActionEvent(
               new ActionEvent( this , 0 , "userOk" ) 
            ) ;
         }else if( ( obj instanceof Object [] ) &&
                   ( ((Object[])obj).length > 1 ) &&
                   ( ((Object[])obj)[0] instanceof Vector ) &&
                   ( ((Object[])obj)[1] instanceof Vector )    ){
            _groupParents = (Vector) (((Object[])obj)[0]);
            _groupMembers = (Vector) (((Object[])obj)[1]);
            setText("") ;
            setEnabled( false , false , true , true ) ;
            processActionEvent(
               new ActionEvent( this , 0 , "groupOk" ) 
            ) ;
         }else if( obj instanceof String){
            if( id == 100 ){
               setText( "User destroyed : "+principal ) ;
               _groupMembers   = null ;
               _userAttributes = null ;
               setPrincipal( "" ) ;
               setEnabled(false,false,false,true);
               processActionEvent(
                  new ActionEvent( this , 0 , "clear" ) 
               ) ;
            }else{
               setText( "Waiting for 'show principal "+principal ) ;
               _groupMembers   = null ;
               _userAttributes = null ;
               sendCommand( "show principal "+principal , this ) ;
            }
         }else{
            Object t = setText(obj) ;
            if( t instanceof NoSuchElementException ){
               setEnabled( true , true , false , true ) ;
            }else{
               setEnabled( false , false , false , true ) ;
            }
            processActionEvent(
               new ActionEvent( this , 0 , "clear" ) 
            ) ;
         }
      }

   }
}
