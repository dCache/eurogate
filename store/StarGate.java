package eurogate.store ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.util.* ;
import java.text.* ;

public class StarGate extends CellAdapter implements Runnable {

   private CellNucleus _nucleus ;
   private Args        _args ;
   private Hashtable   _moverHash       = new Hashtable() ;
   private FifoX       _requestQueue    = new FifoX( 32 ) ;
   private Hashtable   _pendingHash     = new Hashtable() ;
   private Object      _selectionLock   = new Object() ;
   private int         _idleMoverCount  = 0 ;
   private Thread      _selectionThread = null ;
   private Thread      _watchdogThread  = null ;
   private long        _nextServerReqId = 100 ;
   private BitfileDatabase _dataBase    = null ;
   
   private class MoverProxy {
       private StateInfo    _info = null ;
       private CellPath     _path = null ;
       private boolean      _busy = false ;
       private Date         _modified = new Date() ;
       private RequestProxy _request = null ;
       public MoverProxy( StateInfo info , CellPath path ){
          _info = info ;
          _path = path ;
       }
       public void setInfo( StateInfo info  ){
          _info     = info ;
	  _modified = new Date() ;
       }
       public Date getLastModified(){ return _modified ; }
       public String getName(){ return _info.getName() ; }
       public StateInfo getInfo(){ return _info ; }
       public CellPath  getPath(){ return _path ; }
       public void setBusy( boolean busy ){ 
          _busy    = busy ; 
          _request = null ;
       }
       public void setRequest( RequestProxy proxy ){
          _request = proxy ;
       }
       public RequestProxy getRequest(){ return _request ; }
       public boolean isBusy(){ return _busy ; }
       public String  toString(){
          return _info.toString()+ " <"+(_busy?"Busy":"Idle")+">" ;
       }
   
   }
   private class RequestProxy {
       private RequestImpl _shuttle ; 
       private CellMessage    _message ;
       public RequestProxy( CellMessage msg , RequestImpl shuttle ){
          _shuttle = shuttle ;
          _message = msg ;
       }
       public RequestImpl getRequest(){ return _shuttle ; }
       public CellMessage    getMessage(){ return _message ; }
       public String toString(){ return _shuttle.toString() ; }
   }
   private class PendingEntry {
      private MoverProxy   _mover ;
      private RequestProxy _request ;
      private Date         _created = new Date() ;
      public PendingEntry( RequestProxy req , MoverProxy mover ){
          _request = req ; _mover = mover ;
      }
      public MoverProxy   getMover(){ return _mover ; }
      public RequestProxy getRequest(){ return _request ; }
      public Date         getCreationTime(){ return _created ; }
      public String toString(){
         return _request.toString()+" at "+_created ;
      }
   
   }
   public StarGate( String name , String args ){
   
       super( name , args , false ) ;
       _args = getArgs() ;
       try{
          if( _args.argc() < 1 ){
             start() ;
             kill() ;
             throw new 
             IllegalArgumentException( "Usage : ... <dbPath>" ) ;
          }

          _dataBase = new BitfileDatabase( _args.argv(0) ) ;
          
       }catch( DatabaseException de ){
          start() ;
          kill() ;
          throw new 
          IllegalArgumentException( "Database : "+de.getMessage()+
                                    " ("+de.getCode()+")" ) ;
       }catch( IllegalArgumentException e ){
          start() ;
          kill() ;
          throw e ;
       }

       _selectionThread = new Thread( this ) ;
       _watchdogThread  = new Thread( this ) ;
       _selectionThread.start() ;
       _watchdogThread.start() ;
       
       start() ;
   
   }
   public void run(){
      if( Thread.currentThread() == _selectionThread ){
         say( "Entering critical selection path" ) ;
         synchronized( _selectionLock ){  
            runSelection() ;  
         }    
      }else if( Thread.currentThread() == _watchdogThread ){
         runWatchdog() ;  
      }
   }
   private void runWatchdog(){
      MoverProxy mover = null ;
      StateInfo  info  = null ;
      int        period = 0 ;
      int        delay  = 0 ;
      long       now    = 0 ;
      while( true ){
        
          Enumeration e = _moverHash.elements() ;
	  now = new Date().getTime() ;
	  while( e.hasMoreElements() ){
	     if( ( mover = (MoverProxy)e.nextElement() )  == null )continue ;
	     if( ( info = mover.getInfo() ) == null )continue ;
	     period = info.getPeriod() ; //seconds
	     if( period <= 0 )return ;
	     delay = (int)( now - mover.getLastModified().getTime() )/1000 ;
	     if( delay > 2 * period ){
	        removeMover( mover.getName() ) ;
	     }
	      
	  }
	  try{
	     Thread.currentThread().sleep( 10 * 1000 ) ;
	  }catch( InterruptedException ie ){
	     break ;
          }
      }
   }
   private void runSelection(){
      RequestProxy proxy = null ;
      MoverProxy   mover = null ;
      say( "Entering selection Loop" ) ;
      while( true ){

          if( ( _idleMoverCount      > 0 ) &&
              ( _requestQueue.size() > 0 )     ){

              //
              say( "Mover and request found" ) ;
              //
              proxy = (RequestProxy)_requestQueue.get() ;
              mover = getIdleMover() ;
              PendingEntry entry = new PendingEntry( proxy , mover ) ;
              CellMessage  msg   = proxy.getMessage() ;
              _pendingHash.put( msg.getUOID() , entry ) ;
              try{
                 msg.getDestinationPath().add( mover.getPath() ) ;
                 msg.nextDestination() ;
                 sendMessage( msg ) ;
              }catch( Exception ee ){
	         //
		 // modified : bristol 
		 //
		   // ooops, mover gone ? which one ?
		   //
		   esay( "Selected mover couldn't be reached") ;
		   synchronized( _selectionLock ){
		      //
		      // make the current request disappear
		      //
		      releaseIdleMover( mover ) ;
                      _pendingHash.remove( msg.getUOID() ) ;
		      //
		      // remove the 'gone mover' from the list
		      // 
		      removeMover( mover.getName() ) ;
		   }
		   // 
		   // here we could requeue the request  , but
		   // unfortunately our message path is messed
		   // up somehow. ( could use mark, restore to 
		   // solve that )
		   
		 //
                 replyProblem( proxy , 34 , ee.toString() ) ;
              }
          }else{
              say( "Going to wait ... ") ;
              try{ _selectionLock.wait() ;}catch(InterruptedException ie){}         
              say( "Waking up  (idle="+_idleMoverCount+";req="+_requestQueue.size()+")") ;
          }

      }
   
   }
   public void getInfo( PrintWriter pw ){
      super.getInfo( pw ) ;
      pw.println( " Available Movers (idle="+_idleMoverCount+")" ) ;
      Enumeration e = _moverHash.elements() ;
      for( ; e.hasMoreElements() ; ){
         pw.println( "     "+e.nextElement().toString() ) ;
      }
      pw.println( " Waiting for mover" ) ;
      e = _requestQueue.elements() ;
      for( ; e.hasMoreElements() ; ){
         pw.println( "     "+e.nextElement().toString() ) ;
      }
      pw.println( " Running in mover" ) ;
      e = _pendingHash.elements() ;
      for( ; e.hasMoreElements() ; ){
         pw.println( "     "+e.nextElement().toString() ) ;
      }
   
   }
   public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       if( obj instanceof RequestImpl ){
           say( "Message arrived : "+obj ) ;
           RequestImpl req = (RequestImpl) obj ;
           addRequest( msg , req ) ;
       }else if( obj instanceof StateInfo ){
           CellPath path = msg.getSourcePath() ;
           path.revert() ;
           addStateInfo( (StateInfo)obj , path  ) ;       
       }
   }
   private void addRequest( CellMessage msg , RequestImpl req ){
       boolean ok = false ;
       //
       // create our internal representation of a request
       // and set the request id and the BB-OK.
       //
       RequestProxy proxy = new RequestProxy( msg , req ) ;       
       req.setServerReqId( _nextServerReqId++ ) ;
       req.setActionCommand( "BB-OK" ) ;  
                               
       if( req.getType().equals("put") )
           ok = addPutRequest( msg , req ) ;
       else if( req.getType().equals("get") )
           ok = addGetRequest( msg , req ) ;
       else if( req.getType().equals("remove") )
           ok = addRemoveRequest( msg , req ) ;
       else return ;
       //
       // add the request to the mover queue
       //
       if( ok ){
          synchronized( _selectionLock ){
             _requestQueue.put( proxy ) ;
             _selectionLock.notifyAll() ;
          }
       }
   }
   public void messageToForward( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       say( "Message to forward : "+obj ) ;
       if( obj instanceof RequestImpl ){
           returnedRequest( msg , (RequestImpl) obj ) ;  
       }
   }
   private void returnedRequest( CellMessage msg , RequestImpl req ){
      //
      // check if we are aware of this request
      //
      PendingEntry entry = (PendingEntry)_pendingHash.remove( msg.getLastUOID() ) ;
      if( entry == null ){
         esay( "UOID : "+msg.getLastUOID()+" not found in pendingList" ) ;
         return ;
      }
      //
      //  release the mover
      //
      say( "Releasing Mover : "+entry.getMover() ) ;
      releaseIdleMover( entry.getMover() ) ;
      
      if( req.getType().equals("put") )
      
         returnedPutRequest(  msg , req ) ;
         
      else if( req.getType().equals("get") )
      
         returnedGetRequest(  msg , req ) ;
         
      else if( req.getType().equals("remove") )
      
         returnedRemoveRequest( msg , req ) ;
         

      super.messageToForward( msg ) ;
   
   }
   private void returnedRemoveRequest( CellMessage msg , RequestImpl req ){
      
      if( req.getReturnCode() == 0 ){
         try{
            _dataBase.removeBitfileId( req.getStorageGroup() ,
                                       req.getBfid()  ) ;
         }catch( DatabaseException dbe ){
            req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
         }
      }
      
   }
   public void returnedPutRequest( CellMessage msg , RequestImpl req ){
      //
      // if the request was not successful we have to 
      // remove the bfid entry from the database 
      // and return the request to  EuroGate
      //
      String group = req.getStorageGroup() ;
      String bfid  = req.getBfid() ;
      
      if( req.getReturnCode() != 0 ){
         try{
            _dataBase.removeBitfileId( group , bfid )  ;
         }catch( DatabaseException dbe ){
            esay( "Couldn't remove bfid from database : "+bfid ) ;
         }            
         return ;
      }
      //
      // update the database entry
      //
      try{
         BitfileId bitfileid = _dataBase.getBitfileId( group , bfid ) ;
         bitfileid.setMode("persistent") ;
         _dataBase.storeBitfileId( group , bitfileid ) ;
      }catch( DatabaseException dbe ){
         esay( "storeBitfileId : "+dbe ) ;
         req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
      }
      
   }
   public void returnedGetRequest( CellMessage msg , RequestImpl req ){
      //
      // update the bf-counters
      //
      try{
          BitfileId bitfile = _dataBase.getBitfileId( 
                                    req.getStorageGroup() ,
                                    req.getBfid()   ) ;
          bitfile.touch() ;
          _dataBase.storeBitfileId( req.getStorageGroup() , bitfile ) ;
      }catch( DatabaseException dbe ){
          esay( "PANIC : could't update bitfile : "+req.getBfid() ) ;
      }

   }
   public boolean addRemoveRequest( CellMessage msg , RequestImpl req ){

       String group = null  ;
       String bfid  = req.getBfid() ;
       try{
           group = _dataBase.getGroupByBitfileId( bfid ) ;
           req.setStorageGroup( group ) ;
       }catch( DatabaseException dbe ){
           String error = "bfid not found : "+bfid ;
           req.setReturnValue( 11 , error ) ;
           msg.revertDirection() ;
           esay( "Sending problem : "+error  ) ;
           try{
              sendMessage( msg ) ;
           }catch(Exception ee ){
              esay( "PANIC : can't return error msg to "+msg.getDestinationPath() ) ;
           }
           return false ;
       }
       
       return true ;
       
   }
   public boolean addGetRequest( CellMessage msg , RequestImpl req ){

       String bfid = req.getBfid() ;
       say( "Searching bfid : "+bfid ) ;
       BitfileId bitfile = null  ;
       String    group   = null ;
       try{
           group   = _dataBase.getGroupByBitfileId( bfid ) ;
           bitfile = _dataBase.getBitfileId( group , bfid ) ;
       }catch( DatabaseException dbe ){
           String error = "bfid not found : "+bfid ;
           req.setReturnValue( 11 , error ) ;
           msg.revertDirection() ;
           esay( "Sending problem : "+error  ) ;
           try{
              sendMessage( msg ) ;
           }catch(Exception ee ){
              esay( "PANIC : can't return error msg to "+msg.getDestinationPath() ) ;
           }
           return false ;
       }
       say( "Found bfid : "+bfid + " in group : "+group ) ;
       req.setStorageGroup( group ) ;
       req.setFileSize( bitfile.getSize() ) ;
       //
       //  send the first BB-OK
       //
       CellPath bbReplyPath = (CellPath)msg.getSourceAddress().clone() ;
       bbReplyPath.revert() ;
       try{
          sendMessage( new CellMessage( bbReplyPath , req ) ) ;
       }catch(Exception nrtc ){
          esay( "Door cell seems to have disappeared" ) ;
          return false ;
       }
       return true ;       
   }
   public boolean addPutRequest( CellMessage msg , RequestImpl req ){
       //
       // assign a new bitfile to this request
       //
       String group = req.getStorageGroup() ;
       String bfid  = new Bfid().toString() ;
       req.setBfid( bfid ) ;
       say( "Assigning bfid : "+bfid ) ;
       BitfileId bitfile = new BitfileId( bfid , req.getFileSize() )  ;
       bitfile.setParameter( req.getParameter() ) ;
       //
       // store the bitfile into the database
       try{
          _dataBase.storeBitfileId( group , bitfile ) ;
       }catch( DatabaseException dbe ){
           String error = "StorageGroup not found : "+group ;
           req.setReturnValue( 11 , error ) ;
           msg.revertDirection() ;
           esay( "Sending problem : "+error  ) ;
           try{
              sendMessage( msg ) ;
           }catch(Exception ee ){
              esay( "PANIC : can't return error msg to "+msg.getDestinationPath() ) ;
           }
           return false ;
       }
       //
       //  send the first BB-OK
       //
       CellPath bbReplyPath = (CellPath)msg.getSourceAddress().clone() ;
       bbReplyPath.revert() ;
       try{
          sendMessage( new CellMessage( bbReplyPath , req ) ) ;
       }catch(Exception nrtc ){
          esay( "Door cell seems to have disappeared" ) ;
          return false ;
       }
       
       return true ;
                           
   }
   public void replyProblem( RequestProxy proxy , int rc , String rmsg ){
       CellMessage    msg = proxy.getMessage() ;
       RequestImpl req = proxy.getRequest() ;
       req.setReturnValue( rc , rmsg ) ;
       msg.revertDirection() ;
       esay( "Sending problem : "+rmsg  ) ;
       try{
          sendMessage( msg ) ;
       }catch(Exception ee ){
          esay( "PANIC : can't return error msg to "+msg.getDestinationPath() ) ;
       }
   }
   public void addStateInfo( StateInfo info , CellPath path ){

       synchronized( _selectionLock ){
          if( info.isUp() ){
             MoverProxy proxy = (MoverProxy)_moverHash.get( info.getName() ) ;
             if( proxy == null ){
                 _moverHash.put( info.getName(), 
                                 new MoverProxy( info , path ) ) ;
                 _idleMoverCount++ ;
                 _selectionLock.notifyAll() ;
             }else{
                 proxy.setInfo( info ) ;
             }
          }else{
             _moverHash.remove( info.getName() ) ;
             _idleMoverCount-- ;
          }
       
       }
   
   }
   public void removeMover( String moverName ){

       synchronized( _selectionLock ){
          MoverProxy proxy = (MoverProxy)_moverHash.get( moverName ) ;
          if( proxy != null ){
              _moverHash.remove( moverName ) ;
              _idleMoverCount-- ;
              _selectionLock.notifyAll() ;
          }
       }
   
   }
   private MoverProxy getIdleMover(){
      synchronized( _selectionLock ){
          Enumeration e = _moverHash.elements() ;
          MoverProxy proxy = null ;
          while( e.hasMoreElements() ){
              MoverProxy p = (MoverProxy)e.nextElement() ;
              if( ! p.isBusy() ){
                  proxy = p ;
                  break ;
              }
          }
          proxy.setBusy(true);
          _idleMoverCount -- ;
          return proxy ;
      }
   }
   private void releaseIdleMover( MoverProxy mover ){
      synchronized( _selectionLock ){
          mover.setBusy( false ) ;
          _idleMoverCount++ ;
          _selectionLock.notifyAll() ;
      }
   }
   //
   //
   // command interface
   //
   public String hh_create_group = "<groupName>" ;
   public String ac_create_group_$_1( Args args ) throws CommandException {
       String group = args.argv(0) ;
       try{
          _dataBase.createStorageGroup( group ) ;
       }catch( DatabaseException dbe ){
          throw new 
          CommandException( dbe.getCode() , dbe.getMessage() ) ;
       }
       return "Storage Group created : "+group ;
   }
   public String hh_remove_group = "<groupName>" ;
   public String ac_remove_group_$_1( Args args ) throws CommandException {
       String group = args.argv(0) ;
       try{
          _dataBase.removeStorageGroup( group ) ;
       }catch( DatabaseException dbe ){
          throw new 
          CommandException( dbe.getCode() , dbe.getMessage() ) ;
       }
       return "Storage Group removed : "+group ;
   }
   public String hh_ls_groups = "" ;
   public String ac_ls_groups( Args args ) throws CommandException {
      StringBuffer sb = new StringBuffer();
      sb.append( "  Group List\n" ) ;
      String [] list = null ;
      try{
         list = _dataBase.getGroups() ;
      }catch( DatabaseException dbe ){
          throw new 
          CommandException( dbe.getCode() , dbe.getMessage() ) ;
      }
      for( int i = 0 ; i < list.length ; i++ )
         sb.append( "     "+list[i]+"\n" ) ;
      return sb.toString();
   }
   public String hh_ls_group = "[-l] <storageGroup>" ;
   public String ac_ls_group_$_1( Args args ) throws CommandException {
      StringBuffer sb    = new StringBuffer();
      String       group = args.argv(0) ;
      DateFormat   df    = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      
      boolean longList = args.optc() > 0 ;
      sb.append( "  Bitfile List\n" ) ;
      String [] list = null ;
      try{
         list = _dataBase.getBitfileIds( args.argv(0) ) ;
      }catch( DatabaseException dbe ){
         throw new 
         CommandException( dbe.getCode() , dbe.getMessage() ) ;
      }
      BitfileId bitfileid = null ;
      for( int i = 0 ; i < list.length ; i++ ){
         if( longList ){
            try{
               bitfileid = _dataBase.getBitfileId(group,list[i]) ;
               sb.append( "     ").append(bitfileid.getName()).append("  ") ;
               sb.append( bitfileid.getSize() ).append( " mode= " ) ; 
               sb.append( bitfileid.getMode() ).append( "\n           " ) ; 
               sb.append( bitfileid.getAccessCount() ).append( "   " ) ; 
               sb.append( df.format(bitfileid.getCreationDate()) ).append( "   " ) ;
               sb.append( df.format( bitfileid.getLastAccessDate())) ;
               sb.append( "\n" ) ;
            }catch( DatabaseException dbe ){
               sb.append( "     "+list[i]+" !!! "+dbe ) ;
            }
         }else{
             sb.append( "     "+list[i]+"\n" ) ;
         }
      }
      return sb.toString();
   }
   

}
