package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      DrawingPanel
       extends    MasterPanel       {
   
    private class CanvasPanel 
            extends Canvas 
            implements MouseListener , MouseMotionListener {
        private Image  _groupIcon =  null ;
        private Image  _userIcon =  null ;
        private int    _moving   = -1 ;
        private int    _counter = 0 , _recPosX = 0 , _recPosY = 0 ;
        private int    _dX =  0 , _dY = 0 ;
        private Color  _background = new Color(200,200,200) ;
        private Vector _icon = new Vector() ;
        
        private class Element {
           private Point getPosition(){ return null ; }
           private Rectangle getRectangle(){ return null ; }
        }
        private CanvasPanel(){
           addMouseListener(this);
           addMouseMotionListener(this) ;
        }
        private Image makeGroupIcon(){
           Image im = createImage(40,40) ;
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
            }
            refreshAll(g) ;
            update(g);
        }
        public void update( Graphics g ){
            
            if( _moving > -1  ){
               if( _counter == 0 ){
                 // initial drawing of rectangle
                 refreshAll(g) ;
                 _counter ++ ;
                 g.setXORMode( Color.white ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
               }else if( _counter == -1 ) {
                 _moving = -1 ;
                 g.setXORMode( Color.white ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
                 g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
               }else{
                 g.setXORMode( Color.white ) ;
                 g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
               }
               
            }else{
               g.drawImage( _groupIcon , _iconPosX , _iconPosY , null ) ;
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
            g.drawImage( _userIcon , 50 , 50 , null ) ;
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
          for( int i = 0 ; i < _iconPos.size() ; i++ ){
             if( ((Element)_icon.elementAt(i)).getRectangle().contains(p) ){
                 _moving = i ;
                 _counter = 0 ;
                 _dX = _icon.getPosition().x - e.getX() ;
                 _dY = _icon.getPosition().y - e.getY() ;
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
            _counter = -1 ;
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
