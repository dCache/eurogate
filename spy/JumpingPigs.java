package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;
public class      JumpingPigs
       extends    Canvas 
       implements ActionListener ,
                  MouseListener,
                  MouseMotionListener       {

    private static final int IDLE    = 0 ;
    private static final int INITIAL = 1 ;
    private static final int MOVING  = 2 ;
    private static final int FINAL   = 3 ;
    private int    _state   = 0 ;
    private int    _recPosX = 0 , _recPosY = 0 ;
    private int    _dX      = 0 , _dY      = 0 ;
    private Font      _memberFont = new Font( "SansSerif" , 0 , 12 ) ;
    private Hashtable _items      = new Hashtable() ;
    private Vector    _relations  = new Vector() ;
    private Image []  _images     = null ;
    private Element   _moving     = null ;
    private boolean   _needsRedraw = false ;
    private Color     _background = new Color(190,200,220) ;
    private static final int GROUP = 0 ;
    private static final int USER  = 1 ;
    private class Element {
       private Rectangle _rectangle = new Rectangle(0,0,-1,-1) ;
       private int       _imageType = -1 ;
       private String    _name      = "" ;
       private Element( String name , int imageType ){ 
          _name  = name ;
          _imageType = imageType ; 
       }
       private Rectangle getRectangle(){ 
          if( _rectangle.width == -1 ){
              _rectangle.width = _images[_imageType].getWidth(null);
              _rectangle.height = _images[_imageType].getHeight(null) ;
          }
          return _rectangle ; 
       }
       private String getName(){ return _name ; }
       private Image  getImage(){ return _images[_imageType] ; }
    }
    public JumpingPigs(){
       addMouseMotionListener(this);
       addMouseListener(this);
    }    
    public void addContainer( String name ){
       synchronized( _items ){
          if( _items.get( name ) != null )
            throw new
            IllegalArgumentException("Duplicate Entry") ;
          _items.put( name , new Element( name , GROUP ) ) ;          
       }
       refresh() ;
    }
    public void addTerminal( String name ){
       synchronized( _items ){
          if( _items.get( name ) != null )
            throw new
            IllegalArgumentException("Duplicate Entry") ;
          _items.put( name , new Element( name , USER ) ) ;
       }
       refresh() ;
    }
    public void removeItem( String name ){
       synchronized( _items ){
          _items.remove( name ) ;
          for( int i = 0 ; i < _relations.size() ; ){
             String [] pair = (String[])_relations.elementAt(i) ;
             if( pair[0].equals(name) || pair[1].equals(name) ){
                 _relations.removeElementAt(i) ;
             }else{
                 i++ ;
             }
          }
       }
       refresh() ;
    }
    public void removeAll(){
       synchronized( _items ){
          _items.clear() ;
          _relations.removeAllElements() ;
       }
       refresh() ;
    }
    public void showProgressBar( String title ){
    
    }
    public void addRelation( String left , String right ){
       synchronized( _items ){
          String [] pair = { left , right } ;
          _relations.addElement( pair ) ;
       }
       refresh() ;
    }
    public void removeRelation( String left , String right ){
       synchronized( _items ){
          for( int i = 0 ; i < _relations.size() ; ){
             String [] pair = (String[])_relations.elementAt(i) ;
             if( ( pair[0].equals(left) && pair[1].equals(right) ) ||
                 ( pair[1].equals(left) && pair[0].equals(right) )  ){
                 
                 _relations.removeElementAt(i) ;
                    
             }else{
                 i++ ;
             }
          }
       }
       refresh() ;
    }
    public void setProgressBar( int n ){
    
    }
    public void actionPerformed( ActionEvent e ){
        Object source = e.getSource() ;
    }
    private void refresh(){
      _needsRedraw = true ;
      repaint() ;
    }
    public void paint( Graphics g ){
       if( _images == null ){
           _images = new Image[2] ;
           _images[0] = makeGroupIcon() ;
           _images[1] = makeUserIcon() ;
       }
       refreshAll(g) ;
       update(g) ;
    }
    public void update( Graphics g ){
       if( _needsRedraw ){
           _needsRedraw = false ;
           refreshAll(g);
       }
       if( _moving != null  )drawMovingPig( g ) ;

    }
    private void drawMovingPig( Graphics g ){
       Rectangle p     = _moving.getRectangle() ;
       Image     image = _moving.getImage() ;

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
         _moving = null ;
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
    private void refreshAll( Graphics g ){
       drawFrame( g ) ;
       drawRelations( g ) ;
       drawPigs( g ) ;
    }
    private void drawRelations( Graphics g ){
       //
       //
       g.setColor( Color.white ) ;
       for( int i = 0 ; i < _relations.size() ; i++ ){
          String [] n = (String[])_relations.elementAt(i) ;
          Element element1 = (Element)_items.get(n[0]) ;
          Element element2 = (Element)_items.get(n[1]) ;
//          System.out.println(""+n[0]+"="+element1+";"+n[1]+"="+element2) ;
          if( ( element1 == null ) || ( element2 == null ) )continue ;
          Rectangle e1 = element1.getRectangle() ;
          Rectangle e2 = element2.getRectangle() ;
          int x1 = e1.x + e1.width/2 ;
          int y1 = e1.y + e1.height/2 ;
          int x2 = e2.x + e2.width/2 ;
          int y2 = e2.y + e2.height/2 ;
          g.drawLine( x1 , y1 , x2 , y2 ) ;
       }
    }
    private void drawPigs( Graphics g ){
       //
       // now the pics
       //
       Enumeration items = _items.elements();
       while( items.hasMoreElements() ){
          Element element = (Element)items.nextElement() ;
          //
          // we are not allowed to draw the moving pig in 
          // paint mode.
          //
          if( _moving == element )continue ;
          Image     image = element.getImage() ;
          Rectangle rec   = element.getRectangle() ;
          g.drawImage( image , rec.x ,rec.y , null ) ;
          g.setColor(Color.black) ;
          g.setFont( _memberFont ) ;
          g.drawString( element.getName() , 
                        rec.x + rec.width  + 4 ,
                        rec.y + rec.height - 4 ) ;
       }
    }
    private void drawFrame( Graphics g ){
        g.setColor( _background ) ;
        Dimension d  = getSize() ;
        g.fillRect(0,0,d.width-1,d.height-1);
        g.setColor(_background.brighter());
        g.drawLine(0,d.height-1,0,0);
        g.drawLine(0,0,d.width-1,0);
        g.setColor(_background.darker());
        g.drawLine(0,d.height-1,d.width-1,d.height-1);
        g.drawLine(d.width-1,d.height-1,d.width-1,0);

    }
    public void mouseDragged(MouseEvent e){
      if( _moving != null ){
        _recPosX =  _dX + e.getX() ;
        _recPosY =  _dY + e.getY() ;
        repaint() ;
      }

    }
    public void mousePressed(MouseEvent e){
      Point p = e.getPoint() ;
      Enumeration items = _items.elements() ;
      while( items.hasMoreElements() ){
         Element element = (Element)items.nextElement() ;
         if( element.getRectangle().contains(p) ){
             _moving  = element ;
             _state   = INITIAL ;
             _dX = element.getRectangle().x - e.getX() ;
             _dY = element.getRectangle().y - e.getY() ;
             _recPosX = _dX + e.getX() ;
             _recPosY = _dY + e.getY() ;
             repaint() ;
             return ;
         }
      }
    }
    public void mouseReleased(MouseEvent e){
      if( _moving != null ){
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
