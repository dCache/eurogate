package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      JumpingPigs
       extends    Canvas 
       implements ActionListener        {

    private Hashtable _items     = new Hashtable() ;
    private Vector    _relations = new Vector() ;
    
    public void addContainer( String name ){
       synchronized( _items ){
          if( _items.get( name ) != null )
            throws new
            IllegalArgumentException("Duplicated Entry") ;
            
          
       }
    
    }
    public void addTerminal( String name ){
       synchronized( _items ){
          if( _items.get( name ) != null )
            throws new
            IllegalArgumentException("Duplicated Entry") ;
       }
    }
    public void removeItem( String name ){
    
    }
    public void removeAll(){
    
    }
    public void showProgressBar( String title ){
    
    }
    public void addRelation( String left , String right ){
       synchronized( _items ){
          String [] pair = { left , rigth } ;
          _relations.addElement( pair ) ;
          
       }
    }
    public void removeRelation( String left , String right ){
       synchronized( _items ){
          for( int i = 0 ; i < _relations.size() ; ){
             String [] pair = (String[])_relations.elementAt(i) ;
             if( ( pair[0].equals(left) && pair[1].equals(right) ) ||
                 ( pair[1].equals(left) && pair[0].equals(right) )  ){
                 
                 _items.removeElementAt(i) ;
                    
             }else{
                 i++ ;
             }
          }
          
       }
    }
    public void setProgressBar( int n ){
    
    }
    public void actionPerformed( ActionEvent e ){
        Object source = e.getSource() ;
    }
    
    public void paint( Graphics g ){
       g.setColor( Color.red ) ;
       Dimension d = getSize() ;
       g.fillRect( 0 , 0, d.width - 1 , d.height - 1 ) ;
    }
    

}
