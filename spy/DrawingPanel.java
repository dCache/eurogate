package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      DrawingPanel
       extends    MasterPanel       {
    private Image _icon =  null ;
    private int _iconPosX = 0 ;
    private int _iconPosY = 0 ;
    private boolean _moving = false ;
    private class CanvasPanel 
            extends Canvas 
            implements MouseListener , MouseMotionListener {
        private CanvasPanel(){
           addMouseListener(this);
           addMouseMotionListener(this) ;
        }
        private Image makeIcon(){
           try{
              Image im = createImage(40,40) ;
              if( im == null )System.out.println( "Image is null" ) ;
              Graphics g = im.getGraphics() ;
              for( int i = 0 ; i < 40 ; i++ ){
                 for( int j = 0 ; j < 40 ; j++ ){
                    g.setColor( new Color(100+(i-20)*(i-20)/10+(j-20)*(j-20)/10,180,180)) ;
                    g.drawLine(j,i,j+1,i+1) ;
                 }
              }
              return im;
           }catch(RuntimeException tt ){
              tt.printStackTrace() ;
              throw tt ;;
           }
        }
        private Random _r = new Random() ;
        public void paint( Graphics g ){ 
            //
            // create a tiny little icon ( huaaa )
            //
            if( _icon == null )_icon = makeIcon() ;
            refreshAll(g) ;
            update(g);
        }
        public void update( Graphics g ){
            
            if( _moving ){
               if( _counter == 0 ){
                 // initial drawing of rectangle
                 refreshAll(g) ;
                 _counter ++ ;
                 g.setXORMode( Color.white ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
               }else if( _counter == -1 ) {
                 _moving = false ;
                 g.setXORMode( Color.white ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
                 g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
               }else{
                 g.setXORMode( Color.white ) ;
                 g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
                 _iconPosX = _recPosX ;
                 _iconPosY = _recPosY ;
                 g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
                 g.setPaintMode() ;
               }
               
            }else{
               g.drawImage( _icon , _iconPosX , _iconPosY , null ) ;
            }
            
        }
        private void refreshAll( Graphics g ){
            g.setColor( new Color(180,180,180) ) ;
            Dimension d  = getSize() ;
            g.fillRect(0,0,d.width-1,d.height-1);
            g.drawImage( _icon , 50 , 50 , null ) ;
        }
        public void mouseDragged(MouseEvent e){
          if( _moving){
            _recPosX =  _dX + e.getX() ;
            _recPosY =  _dY + e.getY() ;
            repaint() ;
          }

        }
        public void mouseMoved(MouseEvent e){
        }
        public void mouseClicked(MouseEvent e){}
        private int _counter = 0 , _recPosX = 0 , _recPosY = 0 ;
        private int _dX =  0 , _dY = 0 ;
        public void mousePressed(MouseEvent e){
          if( ( e.getX() > _iconPosX ) && ( e.getX() < ( _iconPosX+40) ) &&
              ( e.getY() > _iconPosY ) && ( e.getY() < ( _iconPosY+40) )    ){
            _moving = true ;
            _counter = 0 ;
            _dX = _iconPosX - e.getX() ;
            _dY = _iconPosY - e.getY() ;
            _recPosX = _dX + e.getX() ;
            _recPosY = _dY + e.getY() ;
            repaint() ;
          }
        }
        public void mouseReleased(MouseEvent e){
          if( _moving){
            _recPosX = _dX + e.getX() ;
            _recPosY = _dY + e.getY() ;
            _counter = -1 ;
            repaint() ;
          }
        }
        public void mouseEntered(MouseEvent e){}
        public void mouseExited(MouseEvent e){}
    }
    public DrawingPanel( DomainConnection connection ){
        super( "Drawing Panel") ;
        add( new CanvasPanel() ) ;

    }
 
}
