package eurogate.store ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.net.* ;
import java.util.* ;
import java.text.* ;
import java.lang.reflect.* ;

public class StarGateVX extends CellAdapter implements Logable  {

   private CellNucleus     _nucleus ;
   private Args            _args ;
   private long            _nextServerReqId = 100 ;
   private CellPath        _pvlPath     = null ;
   private String          _euroClass   = null ;
   private EuroStoreable   _eurostore   = null ;
   private SessionHandler  _sessionHandler = null ;
   public StarGateVX( String name , String args ) throws Exception {
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          if( _args.argc() < 2 ){
             throw new 
             IllegalArgumentException( 
             "Usage : ... <EuroStorableClass> <pvlPath> [EuroStorable specific infos]" ) ;
          }

          _euroClass  = _args.argv(0);
          _pvlPath    = new CellPath( _args.argv(1) ) ;
          _args.shift() ;
          _args.shift() ;
          
          loadStoreClass() ;
          
          _sessionHandler = new SessionHandler( 
                                 _eurostore.getStorageSessionable() ) ;
       }catch( IllegalArgumentException e ){
          start() ;
          kill() ;
          throw e ;
       }catch( Exception ee ){
          start() ;
          kill() ;
          throw ee ;
       }catch( Throwable de ){
          start() ;
          kill() ;
          throw new 
          IllegalArgumentException( de.toString() ) ;
       }

       start() ;
   
   }
   public void cleanUp(){
      if( _eurostore != null ){
         say( "Closing database" ) ;
         try{
            _eurostore.close() ;
         }catch(Exception ee ){
            esay( "Problem closing database : "+ee ) ;
            esay(ee) ;
         } 
      }else{
         esay( "Closing database (why is the database == null ? )" ) ;
      }
        
   }
   private class Session {
       private int               _position = -1 ;
       private StorageSessionable _sessionable = null ;
       private Session( int position , StorageSessionable ss ){
           _position    = position ;
           _sessionable = ss ;
           System.err.println("SESSION : creating : "+position ) ;
       }
       private StorageSessionable get(){ return _sessionable ; }
       private int getPosition(){ return _position ; }
   }
   private class SessionHandler {
       private Session []_sessions = null ;
       private Session []_stack    = null ;
       private int _stackPosition  = 0 ;
       private Object    _lock     = new Object() ;
       
//       private GateKeeper _gate = new GateKeeper() ;

       private SessionHandler( StorageSessionable [] sessions ){
           if( ( sessions == null ) ||
               ( sessions.length == 0 ) ){
              _sessions = null ;
              return ;   
           }
           _sessions = new Session[sessions.length] ;
           _stack    = new Session[sessions.length] ;
           for( int i = 0 ; i < _sessions.length ; i++ )
              _stack[i] = _sessions[i] = new Session( i, sessions[i] ) ;
           _stackPosition = _sessions.length - 1 ;
       }
       private Session getSession(int priority)throws InterruptedException {
          if( _sessions == null )return new Session(-1,null) ;
//          _gate.open(priority);
          try{
             synchronized(_lock){
                while(_stackPosition<0)_lock.wait() ;
                System.err.println("SESSION : getSession : "+_stackPosition+
                                   " "+ _sessions[_stackPosition].getPosition()) ; 
                return _stack[_stackPosition--] ;
             }
          }finally{
//             _gate.close() ;
          }
       }
       private void releaseSession( Session session ){
           if( session.getPosition() < 0 )return ;
           synchronized( _lock ){
              _stack[++_stackPosition] = session ;
                System.err.println("SESSION : releaseSession : "+_stackPosition+
                                   " "+ _sessions[_stackPosition].getPosition()) ; 
              _lock.notifyAll() ;
           }
       }
   }
   private static final Class [] __constArgs = {
          dmg.util.Args.class ,
          java.util.Dictionary.class ,
          dmg.util.Logable.class 
   } ;
   private void loadStoreClass() throws Throwable {
   
       try{
           Class       storeClass = Class.forName( _euroClass ) ;
           Constructor cons       = storeClass.getConstructor( __constArgs ) ;
           Object []   args       = new Object[3] ;
           
           args[0] = _args.clone() ;
           args[1] = _nucleus.getDomainContext() ;
           args[2] = this ;
           
           Object obj = cons.newInstance( args ) ;
           if( ! ( obj instanceof EuroStoreable ) )
              throw new 
              IllegalArgumentException(
              "<EuroStorableClass> must implement eurogate.store.EuroStorable" );
           
           _eurostore = (EuroStoreable)obj ;
       }catch( InvocationTargetException ite ){
           throw ite.getTargetException() ;
       }
       say( "EuroStorable created : "+_euroClass ) ;
   }
   public void log( String msg ){ say( msg ) ; }
   public void elog( String msg ){ esay( msg ) ; }
   public void plog( String msg ){ esay( msg ) ; }
   public void say( String msg ){
       pin( msg ) ;
       super.say( msg ) ;
   }
   public void esay( String msg ){
       pin( "ERROR : "+msg ) ;
       super.say( msg ) ;
   }
   
   public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       if( obj instanceof BitfileRequest ){
           say( "BitfileRequest arrived : "+obj ) ;
           addRequest( msg , (BitfileRequest) obj ) ;
       }else if( obj instanceof StoreRequest ){
           StoreRequest sr = (StoreRequest)obj ;
           say( "StoreRequest arrived : "+obj ) ;
           if( sr.getCommand().equals( "get-bfid" ) ){
              try{
                 sr.setBitfileId( 
                       _eurostore.getBitfileRecord(null,sr.getBfid()) ) ;
              }catch( Exception dbe ){
                 sr.setBitfileId(null);
              }
              msg.revertDirection() ;
              try{
                 sendMessage(msg);
              }catch(Exception ee){
                 esay( "Problem with answer : "+ee ) ;
                 ee.printStackTrace() ;
              }
           }else if( sr.getCommand().equals( "list-volume" ) ){
              new ListVolumeController( msg , sr ) ;
           }
       }else{
           say( "Unknown object arrived : "+obj.getClass() ) ;
       }
   }
   private void addRequest( CellMessage msg , BitfileRequest req ) {

       req.setServerReqId( _nextServerReqId++ ) ;
        
       //
       //  within initial request we have to wait for
       //  a session. This wait may be interrupted be
       //  a 'kill cell'.
       //
       try{
          initialRequest( req ) ;
       }catch(InterruptedException ie ){
          esay( "Wait for database session was interrupted");
          try{
             req.setActionCommand( "BB-OK" ) ;  
             msg.revertDirection() ;
             sendMessage( msg ) ;
          }catch( Exception e ){
             esay( "PANIC : problem sending error report to door "+e ) ;
             esay(e);
          }
          return ;       
       }
       String ioType = req.getType() ;
       if( req.getReturnCode() != 0 ){
          //
          // something went wrong. So we need to inform the
          // Door. no further actions are necessary.
          //
          try{
             req.setActionCommand( "BB-OK" ) ;  
             msg.revertDirection() ;
             sendMessage( msg ) ;
          }catch( Exception e ){
             esay( "PANIC : problem sending error report to door "+e ) ;
          }
          return ;
       }else if( ioType.equals("put") || ioType.equals( "get" ) ){
          //
          // if everthing seems to be fine, put,get sends a reply to
          // the door.
          //
          req.setActionCommand( "BB-OK" ) ;  
          CellPath bbReplyPath = (CellPath)msg.getSourceAddress().clone() ;
          bbReplyPath.revert() ;
          try{
             sendMessage( new CellMessage( bbReplyPath , req ) ) ;
          }catch(Exception nrtc ){
             //
             // at this point we have to redo the store-operation.
             // and then send the error report back to the door.
             //
             esay( "Door cell seems to have disappeared "+nrtc ) ;
             req.setReturnValue( 55 , nrtc.toString() ) ;
             try{          
                 finalRequest( req ) ;
             }catch(InterruptedException iea){
                 //
                 // this time there is nothing we can do/
                 //
                 esay( "Wait for database session was interrupted" ) ;
             }
             return ;
          }
       
       }
       //
       // if ok, send the request to our pvl.
       //
       say( "Forwarding request to pvl : "+_pvlPath ) ;
       try{
          req.setActionCommand( "i/o" ) ;  
          msg.getDestinationPath().add( _pvlPath ) ;
          msg.nextDestination() ;
          sendMessage( msg ) ;
       }catch( Exception ee ){
          String problem = "Can't reach pvl : "+ee.getMessage() ;
          esay( problem ) ;
          req.setReturnValue( 33 , problem ) ;
          try{
             finalRequest( req ) ;
          }catch(InterruptedException e ){
             esay( "Wait for session interrpupted"+
                   " (Couldn't remove the failed request)" ) ;
          }
          msg.revertDirection();
          req.setActionCommand( "Mover-OK" ) ;  
          try{
             sendMessage( msg ) ;                 
          }catch(Exception nrtc ){
             esay( "Door cell seems to have disappeared" ) ;
          }

       }
          
       
   }
   private void finalRequest( BitfileRequest req )
           throws InterruptedException {
       String ioType = req.getType() ; 
       Session s = _sessionHandler.getSession(0) ;
       try{    
          if( ioType.equals("put")  ){
              _eurostore.finalPutRequest(s.get(),req ) ;
          }else if( ioType.equals("get")  ){
              _eurostore.finalGetRequest(s.get(),req ) ;
          }else if( ioType.equals("remove") ){
              _eurostore.finalRemoveRequest(s.get(),req ) ;
          }else{
              req.setReturnValue( 11 , "Operation not found : "+ioType ) ;
          } 
              
       }catch( Throwable t ){
          req.setReturnValue( 666 , t.toString() ) ;
       }finally{
           _sessionHandler.releaseSession(s) ;
       }
   }
   private void initialRequest( BitfileRequest req )
           throws InterruptedException {
       String ioType = req.getType() ; 
       Session s = _sessionHandler.getSession(0) ;
       try{    
          if(      ioType.equals("put")  )
                     _eurostore.initialPutRequest(s.get(),req ) ;
          else if( ioType.equals("get")  )
                     _eurostore.initialGetRequest(s.get(),req ) ;
          else if( ioType.equals("remove") )
                     _eurostore.initialRemoveRequest(s.get(),req ) ;
          else req.setReturnValue( 11 , "Operation not found : "+ioType ) ; ;
       }catch( Throwable t ){
          req.setReturnValue( 666 , t.toString() ) ;
       }finally{
          _sessionHandler.releaseSession(s) ;
       }
   }
   
   public void messageToForward( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       say( "Message to forward : "+obj ) ;
       if( obj instanceof BitfileRequest ){
           returnedRequest( msg , (BitfileRequest) obj ) ;  
       }
   }
   private void returnedRequest( CellMessage msg , BitfileRequest req ){
      
      try{
         finalRequest( req ) ;
      }catch( InterruptedException ei ){
         req.setReturnValue(56,"Final storing was interrupted" );
         try{
            msg.nextDestination() ;
            sendMessage( msg ) ;
         }catch( Exception e ){
            esay( "Can't forward final response to Door"+e) ;
         }
         return ;
      }
      try{
         msg.nextDestination() ;
         sendMessage( msg ) ;
      }catch( Exception e ){
         esay( "Can't forward final response to Door"+e) ;
         
         req.setReturnValue( 55 , e.toString() ) ;
         //
         // final request called the second time.
         // but there is no other way to let the
         // request fail.
         //
         try{finalRequest( req ) ;}
         catch(InterruptedException iee){
            esay( "Final request (database) was interrupted" ) ;
         }
      }
   
   }
   public String hh_ls_group = "[-l] <storageGroup>" ;
   public String ac_ls_group_$_1( Args args ) throws Exception {
      StringBuffer sb    = new StringBuffer();
      String       group = args.argv(0) ;
      
      boolean longList = args.optc() > 0 ;
      sb.append( "  Bitfile List\n" ) ;
      
      Session s = _sessionHandler.getSession(1) ;
      try{
         Enumeration e =   
            _eurostore.getBfidsByStorageGroup(s.get(), group , 0 ) ;
         dumpBfs( s.get() , sb , e , longList ) ;
      }finally{
         _sessionHandler.releaseSession(s) ;
      }
      
      return sb.toString();
   }
   public String hh_ls_volume = "[-l] <volume>" ;
   public String ac_ls_volume_$_1( Args args ) throws Exception {
      StringBuffer sb    = new StringBuffer();
      String       volume = args.argv(0) ;
      
      boolean longList = args.optc() > 0 ;
      sb.append( "  Bitfile List\n" ) ;
      
      Session s = _sessionHandler.getSession(1) ;
      try{
         Enumeration e = _eurostore.getBfidsByVolume(s.get(),volume , 0 ) ;
         dumpBfs( s.get() , sb , e , longList ) ;
      }finally{
         _sessionHandler.releaseSession(s) ;
      }
      
      return sb.toString();
   }
   private void dumpBfs( 
                         StorageSessionable sessionable ,
                         StringBuffer sb , 
                         Enumeration e , boolean longList ){
      DateFormat   df    = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      while( e.hasMoreElements() ){
         String bfid = (String)e.nextElement() ;
         if( longList ){
            BfRecordable bitfileid = 
               _eurostore.getBitfileRecord(sessionable,bfid ) ;
            sb.append(bitfileid.getBfid()).append("  ").
               append( bitfileid.getStatus().charAt(0) ) ;
            sb.append( "  "+bitfileid.getFileSize() ).append( "  " ) ;
            sb.append( df.format(bitfileid.getCreationDate()) ).append( "\n" ) ;
            if( bitfileid.getStatus().equals( "persistent" ) ){

                sb.append("   ") ;
                sb.append( Formats.field( ""+bitfileid.getAccessCounter() , 
                           4 , Formats.LEFT) ) ;
                sb.append( Formats.field( bitfileid.getVolume()         ,
                           10 , Formats.LEFT) ) ;
                sb.append( Formats.field( bitfileid.getFilePosition()   ,
                           10 , Formats.LEFT) ) ;
                sb.append( df.format( bitfileid.getLastAccessDate())) ;
                sb.append( "\n" ) ;

            }
         }else{
             sb.append( "     "+bfid+"\n" ) ;
         }
      }
   
   }
   public String hh_ls_groups = "" ;
   public String ac_ls_groups( Args args ) throws Exception {
      Session s = _sessionHandler.getSession(1);
      StringBuffer sb = new StringBuffer();
      try{
         Enumeration  e  = _eurostore.getStorageGroups(s.get(),0) ;      

         while( e.hasMoreElements() )
            sb.append( e.nextElement().toString() ).append("\n" ) ;
      }finally{
         _sessionHandler.releaseSession(s) ;
      }  
      return sb.toString();
   }
   public String hh_ls_bfid = "<bfid>" ;
   public String ac_ls_bfid_$_1( Args args ) throws Exception {
      String     bfid      = args.argv(0);
      DateFormat df        = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      
      BfRecordable bfred = null ;
      Session s = _sessionHandler.getSession(1) ;
      try{
         bfred = _eurostore.getBitfileRecord(s.get(), bfid ) ;
      }finally{
         _sessionHandler.releaseSession(s) ;
      }
      if( bfred == null )return "Bfid not found : "+bfid  ;
      StringBuffer sb = new StringBuffer() ;
      sb.append("Bfid          : ").
         append( bfred.getBfid() ).append("\n") ;
      sb.append("Status        : ").
         append( bfred.getStatus() ).append("\n") ;
      sb.append("Volume        : ").
         append( bfred.getVolume() ).append("\n") ;
      sb.append("Position      : ").
         append( bfred.getFilePosition() ).append("\n") ;
      sb.append("StorageGroup  : ").
         append( bfred.getFilePosition() ).append("\n") ;
      sb.append("Access Count  : ").
         append( bfred.getAccessCounter() ).append("\n") ;
      sb.append("Parameter     : ").
         append( bfred.getParameter() ).append("\n") ;
      sb.append("Last Access   : ").
         append( df.format( bfred.getLastAccessDate())).append("\n") ;
      sb.append("Creation Data : ").
         append( df.format( bfred.getCreationDate())).append("\n") ;
      return sb.toString() ;
   
   }
   
   private class ListVolumeController implements Runnable {
      private CellMessage  _msg ;
      private StoreRequest _request ;
      private Thread       _thread ;
      ListVolumeController( CellMessage msg , StoreRequest request ){
         _request = request ;
         _msg     = msg ;
         
         _nucleus.newThread(this,"LVC").start() ;
      }
      private class Bf {
          String  bfid ;
          int     position ;
          String  key ;
          Bf( String bfid , int position , String key ){
             this.bfid      = bfid ;
             this.position  = position ;
             this.key       = key ;
          }
      }
      public void run(){
         String    volume = _request.getVolume() ;
         Session s = null ;
         
         try{
             s = _sessionHandler.getSession(1) ;
         }catch( InterruptedException ie ){
             esay( "waiting for session (listvolume) interrupted" );
             // this will let the client hang.
             return ;      
         }
         
         Enumeration e = null ;
      
         
         String [] group  = null ;
         String [] bfid   = null ;
         Vector    bfids  = new Vector() ;
         BfRecordable id     = null ;
         Socket    socket = null ;
         DataOutputStream out = null ;
         try{
            e = _eurostore.getBfidsByVolume(s.get(),volume,0) ;
            while( e.hasMoreElements() ){
              String bfidx = (String)e.nextElement() ;
              bfids.addElement( _eurostore.getBitfileRecord(s.get(),bfidx) ) ;
            }
            
            say( ""+bfids.size()+" Bitfiles found on volume "+volume );
            if( bfids.size() == 0 )
              throw new
              IllegalArgumentException( "No bf's on volume "+volume ) ;
            Bf [] bflist  = new Bf[bfids.size()] ;
            String params = null ;
            String tok    = null ;
            StringTokenizer st = null ;
            for( int i = 0 ; i < bflist.length ; i++ ){
               id     = (BfRecordable)bfids.elementAt(i) ;
               params = id.getParameter() ;
               if( ( params == null ) || ( params.equals("") ) ){
                   params = "none" ;
               }else{
                   st = new StringTokenizer( params , ";" ) ;
                   while( st.hasMoreTokens() ){ 
                      tok = st.nextToken() ;                    
                      if( ( tok.length() > 4 ) &&
                          tok.startsWith("key=")    ){
                          tok = tok.substring(4) ;
                          break ;
                      }
                      tok = null ;
                   }
                   if( tok == null )tok = "none" ;
               }
               bflist[i] = new Bf( id.getBfid() ,
                                   Integer.parseInt( id.getFilePosition()),
                                   tok ) ;
            
            }
            for( int o = bflist.length-1 ; o > 0 ; o-- ){
               for( int i = 0 ; i < o ; i++ ){
                  if( bflist[i].position > bflist[i+1].position ){
                     Bf t = bflist[i] ;
                     bflist[i] = bflist[i+1] ;
                     bflist[i+1] = t ;
                  }
               }
            }
            say( "Connecting to "+
                 _request.getHost()+":"+_request.getPort() ) ;
            socket = new Socket( _request.getHost() , 
                                 _request.getPort()    ) ;
            out = new DataOutputStream( socket.getOutputStream() ) ;
            out.writeUTF( "hello-store "+_request.getId() ) ;
            for( int i = 0 ; i < bflist.length ; i++ ){
               say( "bf - "+bflist[i].bfid ) ;
               out.writeUTF( bflist[i].bfid+" "+
                             bflist[i].position+" "+
                             bflist[i].key        ) ;
               out.flush();
            }
         }catch( IllegalArgumentException ex ){
            _request.setReturnValue( 44 , ex.getMessage() ) ;
         }catch( Exception ex ){
            _request.setReturnValue( 47 , ex.toString() ) ;
            ex.printStackTrace();
         }finally{
            if( socket != null){
               say( "Closing connection" ) ;
               try{ out.close() ; }catch(Exception ex ){}
               try{ socket.close() ; }catch(Exception ex ){}
            }
            _sessionHandler.releaseSession(s);
         }
         _msg.revertDirection() ;
         try{
            _nucleus.sendMessage(_msg);
         }catch(Exception ee){}         
      }
   }

}
