package eurogate.pvl ;

import dmg.cells.nucleus.* ;
import eurogate.vehicles.*; 

class PvlCompanion {
   private CellMessage _message = null , _nextMessage = null ;
   PvlCompanion( CellMessage msg ){ _message = msg ; }
   CellMessage getMessage(){ return _message ; }
   CellMessage getNextMessage(){ return _nextMessage ; }
   void setNextMessage( CellMessage msg ){ _nextMessage = msg ; }
   void setRequest( EurogateRequest request ){
          _message.setMessageObject( request ) ;
   }
   EurogateRequest getRequest(){ 
       return (EurogateRequest)_message.getMessageObject() ; 
   }

}
