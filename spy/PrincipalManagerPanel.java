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
   public synchronized void  actionPerformed( ActionEvent e ){
      Object source = e.getSource() ;
      String action = e.getActionCommand() ;
      System.out.println( "Action ["+source+"] : "+e ) ;
      if( action.equals("groupOk") ){
         if( _currentPanel != null ){
            _masterPanel.remove( _currentPanel ) ;
            _currentPanel = null ;
         }
         _groupPanel.update() ;
         _groupPanel.setTitle( "Group "+_createUserPanel.getPrincipal() ) ;
         _masterPanel.add( _currentPanel = _groupPanel , "Center" ) ;
      }else if( action.equals("userOk" ) ){
         if( _currentPanel != null ){
            _masterPanel.remove( _currentPanel ) ;
            _currentPanel = null ;
         }
         _masterPanel.add( _currentPanel = _userPanel , "Center" ) ;
      }else{        
         if( _currentPanel != null ){
            _masterPanel.remove( _currentPanel ) ;
            _currentPanel = null ;
         }
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
   private class      UserPanel 
           extends    EventHelper 
           implements ActionListener, 
                      TextListener ,
                      DomainConnectionListener  {
                      
      private UserPanel(){
        super( new BorderLayout() , 15 , Color.green ) ;
      }                
      public void textValueChanged(TextEvent e){        
      }
      public void actionPerformed( ActionEvent event ) {
        Object source = event.getSource() ;
        setText("");
      }     
      public void domainAnswerArrived( Object obj , int id ){
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
                      DomainConnectionListener  {
      
      private List   _memberList = new  List() ;      
      private Label  _titleLabel = new Label("",Label.CENTER) ;    
      private GroupPanel(){
        super( new BorderLayout() , 15 , Color.red ) ;
        
        _titleLabel.setFont( _headerFont ) ;
        add( _titleLabel , "North" ) ;
        add( _memberList , "West" ) ;
      } 
      private void setTitle(String title ){
         _titleLabel.setText(title) ;
         _titleLabel.invalidate() ;
      }
      private void update(){
         if( _memberList.getItemCount() > 0 )_memberList.removeAll() ;
         Enumeration e = _createUserPanel.getGroupMembers() ;
         if( e != null )
            while( e.hasMoreElements() )
               _memberList.add(e.nextElement().toString());
         
      }
      public void textValueChanged(TextEvent e){        
      }
      public void actionPerformed( ActionEvent event ) {
        Object source = event.getSource() ;
        setText("");
      }     
      public void domainAnswerArrived( Object obj , int id ){
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
         if( obj instanceof Hashtable ){
            _userAttributes = (Hashtable)obj ;
            String type = (String) _userAttributes.get("type" ) ;
            if( type.equals("user") ){
               setText("") ;
               setEnabled( false , false , true , true ) ;
               processActionEvent(
                  new ActionEvent( this , 0 , "userOk" ) 
               ) ;
          
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
            _groupMembers = (Vector)obj ;
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
