package  eurogate.mover ;

import java.util.*;

import dmg.cells.nucleus.*; 
import dmg.util.*;

import eurogate.vehicles.* ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  * 
 */
public class      DummyMover 
       extends    CellAdapter {
 
 
  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private Dictionary   _context ;
  private Args         _args ;
  /**
  */
  public DummyMover( String name , String args  ) throws Exception {
       super( name , args , false ) ;
       
       _nucleus       = getNucleus() ;
       _cellName      = name ;
       _context       = getDomainContext() ;
       _args          = getArgs() ;
       
       try{
       
       }catch( Exception e ){
          //
          // something went wrong, so we start, kill and exit
          //
          start() ;
          kill() ;
             
          throw e ;
       }
       _nucleus.setPrintoutLevel( 0xf ) ;
       
       start() ;
  }
  public void messageArrived( CellMessage msg ){
     Object req = msg.getMessageObject() ;
     if( req instanceof MoverRequest ){
        try{
            processRequest( (MoverRequest) req ) ;
            msg.revertDirection() ;
            sendMessage( msg ) ;
        }catch( Exception e ){
            esay( "can't revert message : "+msg ) ;
        }
     }else{
        esay( "Unidentified message to forward : "+req.getClass().getName() ) ;
        Class [] ii = req.getClass().getInterfaces() ;
        for( int i = 0 ; i < ii.length ; i++ )
           esay( " interface : "+ii[i].getName() ) ;
        return ;
     }
  }
  private void processRequest( MoverRequest request ){
  
      request.setReturnValue( 0 , "Done" ) ;
  }
 
 
 
}
