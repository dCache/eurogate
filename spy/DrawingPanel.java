package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      DrawingPanel
       extends    MasterPanel       {
   
    private static final int IDLE    = 0 ;
    private static final int INITIAL = 1 ;
    private static final int MOVING  = 2 ;
    private static final int FINAL   = 3 ;
    private Font   _memberFont =  new Font( "SansSerif" , 0 , 12 ) ;
    private class CanvasPanel 
            extends Canvas 
            implements MouseListener , MouseMotionListener {
        private Image  _groupIcon =  null ;
        private Image  _userIcon =  null ;
        private int    _moving   = -1 ;
        private int    _state = 0 ;
        private int    _recPosX = 0 , _recPosY = 0 ;
        private int    _dX =  0 , _dY = 0 ;
        private Color  _background = new Color(140,140,200) ;
        private Vector _icon = new Vector() ;
        
        
        private class Element {
           private Rectangle _rectangle = new Rectangle(0,0,40,40) ;
           private Image     _image     = null ;
           private String    _name      = "" ;
           private Element( String name , Image image ){ 
              _name  = name ;
              _image = image ; 
           }
           private Rectangle getRectangle(){ return _rectangle ; }
           private String getName(){ return _name ; }
           private Image getImage(){ return _image ; }
        }
        private CanvasPanel(){
           addMouseListener(this);
           addMouseMotionListener(this) ;
        }
        private Image makeGroupIcon(){
           Image im = createImage(40,40) ;
           if( im == null )System.out.println( "Image is null " ) ;
           Graphics g = im.getGraphics() ;
           Polygon p = new Polygon() ;
           p.addPoint( 5 , 35 ) ;
           p.addPoint( 30 , 35 ) ;
           p.addPoint( 30 , 20 ) ;
           p.addPoint( 25 , 20 ) ;
           p.addPoint( 20 , 15 ) ;
           p.addPoint( 10 , 15 ) ;
           p.addPoint( 5  , 20 ) ;
           for( int i = 0 ; i < 40 ; i++ ){
              int red   = _background.getRed() ;
              int green = _background.getGreen() ;
              int blue  = _background.getBlue() ;
              g.setColor( new Color( red , green - 3 * i , blue ) ) ;
              g.drawLine( 0 , i , 40-1 , i ) ;
              g.setColor( Color.black ) ;
           }
           g.drawPolygon( p ) ;
           g.drawLine( 5 , 20 , 25 , 20 ) ;
           return im;
        }
        private Image makeUserIcon(){
           Image im = createImage(40,40) ;
           if( im == null )System.out.println( "Image is null " ) ;
           Graphics g = im.getGraphics() ;
           for( int i = 0 ; i < 40 ; i++ ){
              int red   = _background.getRed() ;
              int green = _background.getGreen() ;
              int blue  = _background.getBlue() ;
              g.setColor( new Color( red - 3 * i , green , blue ) ) ;
              g.drawLine( 0 , i , 40-1 , i ) ;
              g.setColor( Color.black ) ;
           }
           g.drawOval( 5 ,15 , 20 ,20  ) ;
           g.drawLine( 10 , 25 , 11 , 25  ) ;
           g.drawLine( 20 ,25 , 21 , 25   ) ;
           g.drawLine( 10 , 30  , 20 , 30   ) ;
           return im;
        }
        public void paint( Graphics g ){ 
            //
            // create a tiny little icon ( huaaa )
            //
            if( _groupIcon == null ){
               _groupIcon = makeGroupIcon() ;
               _userIcon  = makeUserIcon() ;
               System.out.println( "setting icons "+_groupIcon+":"+_userIcon ) ;
               _icon.addElement( new Element( "DESY" ,  _groupIcon ) ) ;
               _icon.addElement( new Element( "Zeuthen" , _groupIcon ) ) ;
               _icon.addElement( new Element( "Otto" , _userIcon ) ) ;
               _icon.addElement( new Element( "Karl" , _userIcon ) ) ;
               
            }
            refreshAll(g) ;
            update(g);
        }
        public void update( Graphics g ){
            
           if( _moving > -1  ){
            
               Rectangle p     = ((Element)_icon.elementAt(_moving)).getRectangle() ;
               Image     image = ((Element)_icon.elementAt(_moving)).getImage() ;
               
               if( _state == INITIAL ){
                 //
                 // initial drawing of rectangle
                 //
                 refreshAll(g) ;
                 g.setXORMode( Color.white ) ;
                 p.x = _recPosX ;
                 p.y = _recPosY ;
                 g.drawImage( image , p.x , p.y , null ) ;
                 g.setPaintMode() ;
                 _state = MOVING ;
               }else if( _state == FINAL ) {
                 p.x = _recPosX ;
                 p.y = _recPosY ;
                 _state = IDLE ;
                 _moving = -1 ;
                 refreshAll(g) ;
               }else if( _state == MOVING ){
                 g.setXORMode( Color.white ) ;
                 g.drawImage( image , p.x , p.y , null ) ;
                 p.x = _recPosX ;
                 p.y = _recPosY ;
                 g.drawImage( image , p.x , p.y , null ) ;
                 g.setPaintMode() ;
               }
               
            }
            
        }
        private void refreshAll( Graphics g ){
            g.setColor( _background ) ;
            Dimension d  = getSize() ;
            g.fillRect(0,0,d.width-1,d.height-1);
            g.setColor(_background.brighter());
            g.drawLine(0,d.height-1,0,0);
            g.drawLine(0,0,d.width-1,0);
            g.setColor(_background.darker());
            g.drawLine(0,d.height-1,d.width-1,d.height-1);
            g.drawLine(d.width-1,d.height-1,d.width-1,0);
            //
            // now the pics
            //
            for( int i = 0 ; i < _icon.size() ; i++ ){
              //
              // we are not allowed to draw the moving pig in 
              // paint mode.
              //
              if( i == _moving )continue ;
              Element   e     = ((Element)_icon.elementAt(i)) ;
              Image     image = e.getImage() ;
              Rectangle rec   = e.getRectangle() ;
              g.drawImage( image , rec.x ,rec.y , null ) ;
              g.setColor(Color.black) ;
              g.setFont( _memberFont ) ;
              g.drawString( e.getName() , 
                            rec.x + rec.width  + 4 ,
                            rec.y + rec.height - 4 ) ;
            }
        }
        public void mouseDragged(MouseEvent e){
          if( _moving > -1 ){
            _recPosX =  _dX + e.getX() ;
            _recPosY =  _dY + e.getY() ;
            repaint() ;
          }

        }
        public void mousePressed(MouseEvent e){
          Point p = e.getPoint() ;
          for( int i = 0 ; i < _icon.size() ; i++ ){
             if( ((Element)_icon.elementAt(i)).getRectangle().contains(p) ){
                 _moving  = i ;
                 _state   = INITIAL ;
                 _dX = ((Element)_icon.elementAt(i)).getRectangle().x - e.getX() ;
                 _dY = ((Element)_icon.elementAt(i)).getRectangle().y - e.getY() ;
                 _recPosX = _dX + e.getX() ;
                 _recPosY = _dY + e.getY() ;
                 repaint() ;
                 return ;
             }
          }
        }
        public void mouseReleased(MouseEvent e){
          if( _moving > -1 ){
            _recPosX = _dX + e.getX() ;
            _recPosY = _dY + e.getY() ;
            _state = FINAL ;
            repaint() ;
          }
        }
        public void mouseEntered(MouseEvent e){}
        public void mouseExited(MouseEvent e){}
        public void mouseMoved(MouseEvent e){}
        public void mouseClicked(MouseEvent e){}
    }
    public DrawingPanel( DomainConnection connection ){
        super( "Drawing Panel") ;
        add( new CanvasPanel() ) ;

    }
 
}
