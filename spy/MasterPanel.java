package eurogate.spy ;

import java.awt.* ;
import java.awt.event.* ;
import dmg.cells.applets.login.* ;
public class MasterPanel extends Panel  {
   private int   _border = 15 ;
   private Color _color = Color.black ;
   private BorderLayout _layout     = new BorderLayout() ;
   private Label        _titleLabel = new Label("",Label.CENTER) ; 
   private Label        _messages   = new Label("") ;
   private TextArea     _helpText   = new TextArea() ;
   private Button       _helpButton = new Button("Help") ;
   private Component    _userComponent = null ;
   private Component    _current       = null ;
   private Font   _headerFont = 
                         new Font( "SansSerif" , 0 , 18 ) ;
   private Font   _textFont = new Font( "Courier" , 0 , 12 ) ;
   public MasterPanel( String title ){
      setLayout(_layout) ;
      _titleLabel.setFont(_headerFont) ;
      _titleLabel.setText(title) ;
      
      _helpText.setFont( _textFont ) ;
      _messages.setForeground(Color.red);
      
      RowColumnLayout rcl = new RowColumnLayout(2,0) ;
      rcl.setHgap(10) ;
      rcl.setVgap(10) ;
      Panel bottomPanel = new Panel( rcl ) ;
      bottomPanel.add( _messages ) ;
      bottomPanel.add( _helpButton ) ;
      
      super.add( _titleLabel , "North" ) ;
      super.add( bottomPanel , "South" ) ;
      
      _helpButton.addActionListener(
         new ActionListener(){
             public void actionPerformed( ActionEvent event ){
                if( event.getSource() == _helpButton ){
                   if( _helpButton.getLabel().equals("Help") ){
                      _helpButton.setLabel("Back") ;
                      showHelp() ;
                   }else{
                      _helpButton.setLabel("Help") ;
                      showUser() ;
                   }
                }
             }
         }
      
      ) ;
   }
   public void setBorderSize( int borderSize ){ 
      _border = borderSize ; 
      _layout.setHgap(_border) ;
      _layout.setVgap(_border);
   }
   public void setBorderColor( Color color ){ 
      _color = color ; 
   }
   public synchronized void remove( Component comp ){
      if( _userComponent == null )return ;
      if( _current == _userComponent ){
         //
         // we are active
         //
         super.remove( _userComponent ) ;
         _userComponent = _current = null ;
         validate() ;
      }else{
         _userComponent = null ;
      }
   }
   public synchronized Component add( Component comp ){
      if( _userComponent != null )
         throw new
         IllegalArgumentException( "Component already defined" ) ;
      
      _userComponent = comp ;
      if( _current == null ){  // we are not in help
         super.add( _current = _userComponent , "Center" ) ;
      }
      validate() ;
      return comp;
   }
   private synchronized void showHelp(){
      if( _current == _helpText )return ;
      if( _current != null )super.remove( _current ) ;
      _helpText.setText( getHelpText() ) ;
      super.add( _current = _helpText , "Center" ) ;
      validate() ;
   }
   private synchronized void showUser(){
      if( _current == _userComponent )return ;
      if( _current != null )super.remove( _current ) ;
      if( ( _current = _userComponent ) != null )
         super.add( _current = _userComponent , "Center" ) ;
      validate() ;
   }
   public Insets getInsets(){ 
       return new Insets(_border , _border ,_border , _border) ; 
   }
   public void paint( Graphics g ){

      Dimension   d    = getSize() ;
      g.setColor( _color ) ;
      g.drawRect( _border/2 , _border/2 , d.width-_border , d.height-_border ) ;
   }
   public String getHelpText(){ return "" ; }
   public void setMessage( String message ){
      _messages.setText(message);
      return  ;
   }


}
