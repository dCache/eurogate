package eurogate.spy ;
   

import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;
import dmg.util.* ;
import dmg.cells.services.* ;
import dmg.cells.nucleus.* ;
import eurogate.misc.users.* ;

public class      RelationExplorer
       extends    MasterPanel 
       implements ActionListener  {
 
    private JumpingPigs _jumpingPigs  = null ;
    private Button      _reloadButton = null ;
    private Button      _storeButton  = null ;
    private DomainConnection _connection     = null ;
    private Hashtable _relationHash = null ;
    private Hashtable _userHash     = null ;
    private Companion _companion    = null ;
    private String    _setup        = null ;
    public RelationExplorer( DomainConnection connection ){
        super( "Group Relations") ;
        
        _connection = connection ;
        
        setBorderSize( 20 ) ;
        setBorderColor( Color.green ) ;
    
        BorderLayout bl = new BorderLayout() ;
        bl.setHgap(10) ;
        bl.setVgap(10);
        
        Panel centerPanel = new Panel( bl ) ;
            
        Panel buttonPanel = new Panel( new FlowLayout() ) ;
        _reloadButton = new Button("Reload") ;
        _reloadButton.addActionListener(this);
        _storeButton  = new Button("Store Setup") ;
        _storeButton.addActionListener(this);

        buttonPanel.add( _reloadButton ) ;
        buttonPanel.add( _storeButton ) ;
        _jumpingPigs = new JumpingPigs() ;
        
        centerPanel.add( buttonPanel  , "North" ) ;
        centerPanel.add( _jumpingPigs , "Center" ) ;
                
        add( centerPanel ) ;
    }
    public synchronized void actionPerformed( ActionEvent e ){
        setMessage( "" ) ;
        Object source = e.getSource() ;
        String command = e.getActionCommand() ;
        if( source == _reloadButton ){
           if( _companion != null ){
              setMessage( "Still active") ;
              return ;
           }
           _reloadButton.setEnabled(false) ;
           _storeButton.setEnabled(false);
           
           _companion = new ReloadCompanion() ;
           _companion.addActionListener(this) ;
           _companion.go() ;
           
        }else if( source == _storeButton ){
           _reloadButton.setEnabled(false) ;
           _storeButton.setEnabled(false);
           storeSetup() ;
        }else if( source instanceof LoadElementsCompanion ){
           _reloadButton.setEnabled(true) ;
           _storeButton.setEnabled(true);
           if( command == null ){
              setMessage( "Elements loaded" ) ;
              _jumpingPigs.setProgressTitle("Drawing relations ... ") ;
              drawRelations() ;
              _jumpingPigs.switchPigs() ;
           }else{
              setMessage( e.getActionCommand() ) ;
              _reloadButton.setEnabled(true) ;
              _storeButton.setEnabled(true);
           }
           _companion = null ;
        }else if( source instanceof ReloadCompanion ){
           if( command == null ){
              setMessage( "Groups loaded" ) ;
              _companion = new LoadElementsCompanion() ;
              _companion.addActionListener(this) ;
              _companion.go() ;
           }else{
              setMessage( e.getActionCommand() ) ;
              _reloadButton.setEnabled(true) ;
              _storeButton.setEnabled(true);
              _companion = null ;
           }

        }
    }
    private void drawRelations(){
        _jumpingPigs.removeAll() ;
        
        Enumeration e = _userHash.keys() ;
        while( e.hasMoreElements() )
           _jumpingPigs.addTerminal(e.nextElement().toString()) ;
        e = _relationHash.keys() ;
        while( e.hasMoreElements() ){
           String group = (String)e.nextElement() ;
           _jumpingPigs.addContainer(group) ;
           Vector relatives = (Vector)_relationHash.get(group) ;
           for( int i = 0 ; i < relatives.size() ; i++ )
              _jumpingPigs.addRelation( group , (String)relatives.elementAt(i) ) ;
           
        }
        if( _setup != null )_jumpingPigs.setSetup(_setup);
    }
    private void storeSetup(){
       String setup = _jumpingPigs.getSetup() ;
       try{
          _connection.sendObject( 
                "set principal -cellPath=AclCell "+_connection.getAuthenticatedUser()+
                " RE-setup="+setup , 
                new DomainConnectionListener(){
                    public void domainAnswerArrived( Object obj , int id ){
                       if( obj instanceof Throwable ){
                          displayThrowable( (Throwable)obj ) ;
                       }
                       _reloadButton.setEnabled(true) ;
                       _storeButton.setEnabled(true);
                    }
                }, 
                0 ) ;
       }catch(Exception e){
          setMessage( "Setup failed : "+e ) ;
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
    ///////////////////////////////////////////////////////////////////////
    //
    //     laod the groups
    //
    private class      LoadElementsCompanion 
            extends    Companion 
            implements DomainConnectionListener {
       private int    _state = -1 ;
       private Hashtable   _elements    = null ;
       private Enumeration _nextElement = null ;
       private String      _currentKey  = null ;
       private LoadElementsCompanion(){
          //
          // find all elements which are not in the
          // groupHash. This can be users and empty groups.
          //
          _jumpingPigs.setProgressTitle("Evaluating Elements ... ") ;
          _jumpingPigs.setProgressBar(0.0);
          _jumpingPigs.switchProgressBar();
          
          _userHash = new Hashtable() ;
         
       }   
       public void go(){   
          Enumeration keys = _relationHash.keys() ;
          _elements = new Hashtable() ;
          while( keys.hasMoreElements() ){
             Vector v = (Vector)_relationHash.get( keys.nextElement() ) ;
             for( int i = 0 ; i < v.size() ; i++ ){
                String elementName = v.elementAt(i).toString() ;
                if( _relationHash.get(elementName) == null )
                   _elements.put(elementName,"") ;                  
             }
          }
          if( _elements.size() == 0 ){
             requestDone() ;
             return ; 
          }
          _jumpingPigs.setProgressTitle("Loading "+_elements.size()+" Elements ... ") ;
          _state = 0 ;
          _nextElement = _elements.keys() ;
          _currentKey  = _nextElement.nextElement().toString()  ;
          sendCommand( "show principal "+ _currentKey ) ;
       }
       public void domainAnswerArrived( Object obj , int id ){
          if( obj instanceof Hashtable ){
             Hashtable attr = (Hashtable)obj  ;
             String    type = (String)attr.get("type") ;
             if( type.equals( "user" ) ){
                _userHash.put( _currentKey , attr ) ;
             }else if( type.equals("group") ){
                _relationHash.put( _currentKey , new Vector() ) ;
             }
             _state = id + 1 ;
             _jumpingPigs.setProgressBar((double)_state /(double)_elements.size() ) ;
             if( ! _nextElement.hasMoreElements() ){
                findSetup() ;
                requestDone() ;
                return ;
             }
             _currentKey  = _nextElement.nextElement().toString()  ;
             sendCommand( "show principal "+_currentKey ) ;
          }else if( obj instanceof Exception ){
             requestFailed( "Received Exception : "+obj.toString() ) ;
          }else{
             requestFailed( "Received non Vector : "+obj.getClass() ) ;
             return ;
          }
       } 
       private void findSetup(){
          String weAre   = _connection.getAuthenticatedUser() ;
          Hashtable x = (Hashtable)_userHash.get(weAre) ;
          if( x != null ){
             _setup = (String)x.get("RE-setup") ;
             if( _setup != null )return ;
          }
          x = (Hashtable)_userHash.get("admin") ;
          if( x == null )return ;
          
          _setup = (String)x.get("RE-setup") ;
       }
       private void sendCommand( String command ){
          sendCommand( command , _state ) ;
       }
    }
    ///////////////////////////////////////////////////////////////////////
    //
    //     laod the groups
    //
    private class      ReloadCompanion 
            extends    Companion 
            implements DomainConnectionListener {
       private int    _state = -1 ;
       private Vector _groups = null ;
       private ReloadCompanion(){
          _relationHash = new Hashtable() ;
          _jumpingPigs.setProgressTitle("Loading Groups ... ") ;
          _jumpingPigs.setProgressBar(0.0);
          _jumpingPigs.switchProgressBar();
         
       }   
       public void go(){   sendCommand( "show groups" ) ; }
       public void domainAnswerArrived( Object obj , int id ){
          if( obj instanceof Vector ){
             if( id == -1 ){ // initial request (show groups)
                _state  = id + 1 ;
                _groups =(Vector)obj ;
                if( _groups.size() == 0 ){
                   requestDone() ;
                   return ;
                }
                sendCommand( "show group "+_groups.elementAt(_state).toString() ) ; ;
             }else{
                requestFailed( "Internal error 1" ) ;
                return ;
             }                       
          }else if( obj instanceof Vector [] ){
             _relationHash.put( _groups.elementAt(id) , ((Vector [])obj)[1] ) ;
             _state = id + 1 ;
             _jumpingPigs.setProgressBar( (double)_state / (double)_groups.size() ) ;
             if( _state >= _groups.size() ){
                requestDone() ;
                return ;
             }
             sendCommand( "show group "+_groups.elementAt(_state).toString() ) ; ;
          }else if( obj instanceof Exception ){
             requestFailed( "Received Exception : "+obj.toString() ) ;
          }else{
             requestFailed( "Received non Vector : "+obj.getClass() ) ;
             return ;
          }
       } 
       private void sendCommand( String command ){
          sendCommand( command , _state ) ;
       }
    }
    ///////////////////////////////////////////////////////////////////////
    //
    //     the abstract companion
    //
    private abstract class Companion implements DomainConnectionListener { 
       private ActionListener _actionListener = null ;
       protected void go(){ System.out.println(" !!!! go called"); }
       private void addActionListener( ActionListener actionListener ){
          _actionListener = actionListener ; 
       }
       private void requestFailed( String errorMessage ){
          if( _actionListener != null )
             _actionListener.actionPerformed( new ActionEvent( this , 0 , errorMessage ) ) ;
       }   
       private void requestDone(){
//          _jumpingPigs.setProgressBar( 0.99 ) ;
          if( _actionListener != null )
             _actionListener.actionPerformed( 
                new ActionEvent( this , 0 , null ) ) ;
       }   
       private void sendCommand( String command , int state ){
          System.out.println("Sending : "+command);
          try{ Thread.currentThread().sleep(500) ; }catch(Exception ee){}
          try{
             _connection.sendObject( command+" -cellPath=AclCell" , this , state ) ;
          }catch( Exception ee ){
             requestFailed( ee.toString() ) ;
          }
       }       
    }
        
}
