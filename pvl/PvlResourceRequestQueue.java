package eurogate.pvl ;

import java.util.* ;

public class PvlResourceRequestQueue {

    private Vector _vector             = new Vector() ;
    private int    _newRequestPosition = -1 ;
    private int    _newRequestCount    = 0 ;
    
    synchronized void addRequest( PvlResourceRequest request ){
       _vector.addElement( request ) ;
       if( _newRequestCount == 0 ){
         _newRequestPosition = _vector.size() - 1 ;
       }
       _newRequestCount ++ ;
       request.setPosition(-1) ;
    }
    synchronized void resetNewRequests(){ 
       _newRequestCount = 0 ;
       _newRequestPosition = -1 ;
    }
    synchronized PvlResourceRequest removeRequestAt( int position ){
        PvlResourceRequest req = 
            (PvlResourceRequest)_vector.elementAt( position ) ;
        _vector.removeElementAt( position ) ;
        return req ;
    }
    public int getRequestCount(){ return _vector.size() ; }
    
    public PvlResourceRequest getRequestAt( int position ){
        PvlResourceRequest req = 
            (PvlResourceRequest)_vector.elementAt( position ) ;
        req.setPosition( position ) ;
        return req ;
    } 
    public int getFirstNewRequestPosition(){ return _newRequestPosition ; }
    public int getNewRequestCount(){ return _newRequestCount ; }

    public void moveToTop( int position ){
        PvlResourceRequest req = 
            (PvlResourceRequest)_vector.elementAt( position ) ;
        _vector.removeElementAt( position ) ;
        _vector.insertElementAt( req , 0) ;    
    }
    public void swap( int pos1 , int pos2 ){
        Object req1 = _vector.elementAt( pos1 ) ;
        Object req2 = _vector.elementAt( pos2 ) ;
        _vector.setElementAt( req1 , pos2 ) ;
        _vector.setElementAt( req2 , pos1 ) ;

    }
    public PvlResourceRequest [] getRequests(){
        PvlResourceRequest [] reqs = 
           new PvlResourceRequest[_vector.size()] ;
        _vector.copyInto( reqs ) ;
        return reqs ;
    }

}
