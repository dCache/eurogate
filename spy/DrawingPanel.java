package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      DrawingPanel
       extends    MasterPanel       {

    private class CanvasPanel extends Canvas {
        private Random _r = new Random() ;
        public void paint( Graphics g ){
            Dimension d = getSize() ;
            int centerX = d.width / 2 ;
            int centerY = d.height / 2 ;
            int [] x = new int[3] ;
            for( int i = centerX - 40 ; i < centerX + 40 ; i++ ){
            
                for( int j = centerY - 40 ; j < centerY + 40 ; j++ ){
//                   byte [] b = new byte[3] ;
//                   _r.nextBytes(b) ;
//                   for( int l =  0 ; l < 3 ; l++ )
//                      x[l] =  b[l] < 0 ? b[l]+255 : b[l] ;
//                   System.out.println( ""+x[0]+" "+x[1]+" "+x[2] ) ;
                   x[0] = i ; x[1] = j ; x[2] = i + j ;
                   g.setColor( new Color( x[0]%256 , x[1]%256 , x[2]%256 ) ) ;
                   g.drawLine( i , j , i+1 , j+1 ) ;
                }
            
            }
            
        }
    }
    public DrawingPanel( DomainConnection connection ){
        super( "Drawing Panel") ;
        add( new CanvasPanel() ) ;

    }
}
