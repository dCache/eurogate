package  eurogate.pvr ;

import java.lang.reflect.* ;
import java.net.* ;
import java.io.* ;
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
 
public class      PvrLoginCell 
       extends    CellAdapter
       implements Runnable  {

  private String       _cellName ;
  private CellNucleus  _nucleus ;
  private StreamEngine _engine ;
  private Thread       _listenThread ;
  private int          _pvrRequestId = 1000 ;
  private Dictionary   _context  = null ;
  private DataOutputStream _out  = null ;
  private DataInputStream  _in   = null ;
  private String           _pvrName  = null ;
  private String           _pvrType  = null ;
  private Hashtable        _requestHash  ;
  private Hashtable        _recoveryHash ;
  private boolean          _terminated  = false ;
  private Gate             _finishGate  = new Gate(false) ;
  private Gate             _recoveryGate= new Gate(false) ;
  private String           _contextHashName = null ;
  private Object           _hashLock = new Object() ;
  private Thread           _recoveryThread ;
  private boolean          _recoveryThreadOk = true ;

  private class RequestFrame {
     private CellMessage _message      = null ;
     private PvrRequest  _request      = null ;
     private boolean     _recovery     = false ;
     private String      _linkId       = null ;
     private boolean     _sendNewdrive = false ;
     private String      _frameType    = "" ;
     public RequestFrame( String linkId , String frameType ){
        _linkId   = linkId ;
        _recovery = true ;
        _frameType = frameType ;
        
     }
     public RequestFrame( String linkId ){
        _linkId   = linkId ;
        _recovery = true ;
        _frameType = "newdrive" ;
        
     }
     /*
     public RequestFrame( PvrRequest request , String linkId ){
        _request  = request ;
        _linkId   = linkId ;
        _recovery = true ;
        
     }
     */
     public RequestFrame( CellMessage msg , PvrRequest request ){
        _request  = request ; 
        _message  = msg ;
        _recovery = false ;
        _frameType = "org" ;
     }
     public String      getFrameType(){ return _frameType ; }
     public void        sendNewdrive( boolean s ){ _sendNewdrive = s ; }
     public boolean     sendNewdrive(){ return _sendNewdrive ; }
     public PvrRequest  getRequest(){ return _request ; }
     public CellMessage getMessage(){ return _message ; }
     public boolean     isRecovery(){ return _recovery ; }
     public void        setRecovery( boolean rec ){ _recovery = rec ; }
     public String      getLinkId(){ return _linkId ; }
  }

  /**
  */
  public PvrLoginCell( String name , StreamEngine engine ) throws Exception {
       super( name , "" , false ) ;
       
       _nucleus       = getNucleus() ;
       _cellName      = name ;
       
       try{
          _in  = new DataInputStream(  engine.getInputStream() ) ;
          _out = new DataOutputStream( engine.getOutputStream() ) ;
       
       }catch( Exception e ){
          //
          // something went wrong, so we start, kill and exit
          //
          start() ;
          kill() ;
             
          throw e ;
       }
       //
       // start the listener thread
       //
       _context       = getDomainContext() ;
       
       
//       _nucleus.setPrintoutLevel( 0xf ) ;
       
       if( engine instanceof PvrStreamEngine ){
          PvrStreamEngine pvrEngine = (PvrStreamEngine)engine ;
          _pvrName = pvrEngine.getPvrName() ; 
          _pvrType = pvrEngine.getPvrType() ;
       }else{ 
          _pvrName = _cellName ;
          _pvrType = "Generic" ;
       }
       //
       // we need to keep the information about the outstanding
       // requests, although the c-pvr may disconnect/connect.
       // Therefor we store the hashtable ( the only stateless part)
       // within the domain context.
       //
       _contextHashName = _pvrName+"("+_pvrType+")" ;
       say( "Checking for contextHash entry : "+_contextHashName ) ;
       _requestHash = (Hashtable)_context.get( _contextHashName ) ;
       //
       _recoveryHash = new Hashtable() ;
       if( _requestHash == null ){
          say( _contextHashName + " not found (puh); creating new one" ) ;
          _context.put( _contextHashName , _requestHash = new Hashtable() ) ;
       }else{
          say( _contextHashName + " found; will use it ( dump follows )" ) ;
          synchronized( _hashLock ){
             Hashtable tmp = new Hashtable() ;
             Enumeration e = _requestHash.keys() ;
             for( int i = 0 ; e.hasMoreElements() ; i++ ){
                //
                // make sure we only recover from 
                // i) PvrRequests
                // ii) mount and dismount
                //
                String key = (String)e.nextElement() ;
                say( "   found ["+i+","+key+"]" ) ;
                RequestFrame frame   = (RequestFrame)_requestHash.get( key ) ;
                Object req = frame.getRequest() ;
                if( ! ( req instanceof PvrRequest ) )continue ;
                String com = ((PvrRequest)req).getActionCommand() ;
                if( com.equals("mount") || com.equals("dismount") ){
                   frame.sendNewdrive(true) ;
                   _recoveryHash.put( key , frame ) ;
                   tmp.put( key , frame ) ;
                    say( "   added ["+i+","+key+"]="+req ) ;
                }
             }
             _context.put( _contextHashName , _requestHash = tmp ) ;
          }
       }
       String reqIdString = (String)_context.get( _cellName+"-pvrRequestId" ) ;
       if( reqIdString != null ){
          try{
             _pvrRequestId = Integer.parseInt( reqIdString ) ;
          }catch(Exception e){
             _pvrRequestId = 10000; 
          }      
       }
       say( "Using next RequestId : "+_pvrRequestId ) ;
       //
       _finishGate.close() ;
       _recoveryGate.close() ;
       _listenThread  = new Thread( this , "listenThread" ) ;       
       _listenThread.start() ;
       _recoveryThread  = new Thread( this , "recoveryThread" ) ;       
       _recoveryThread.start() ;
       start() ;
  }
  public void setPrintoutLevel( int level ){ _nucleus.setPrintoutLevel( level ) ; }
  public String toString(){
     if( _pvrName == null )return "Initializing" ;
     return "pvrName="+_pvrName+
            ";pvrType="+_pvrType+
            ";rc="+_requestHash.size() ;
  }
  public void getInfo( PrintWriter pw ){
      pw.println( "PvrName     : "+_pvrName ) ;
      pw.println( "PvrType     : "+_pvrType ) ;
      pw.println( " Outstanding requests" ) ;
      Enumeration e = _requestHash.keys() ;
      while( e.hasMoreElements() ){
         String id = (String)e.nextElement() ;
         RequestFrame frame = (RequestFrame)_requestHash.get( id ) ;
         if( frame == null )continue ;
         PvrRequest   req = frame.getRequest() ;
         CellMessage  msg = frame.getMessage() ;
         pw.println( "    <"+id+">  "+msg.getSourcePath()+" : "+req ) ;
      }
      return ;
  }
  public void cleanUp(){
      if( ! _terminated ){
         _terminated = true ;
         String pvrRequest = "terminate "+( ++_pvrRequestId ) ;
         try{
            say( "toPvr : "+pvrRequest ) ;
            _out.writeUTF( pvrRequest ) ;
         }catch( Exception ee ){}
      }
      say( "Storing next pvrRequestId in context 'pvrRequestId'" ) ;
      _context.put( _cellName+"-pvrRequestId" , ""+_pvrRequestId ) ;
      //
      // stopping recoveryThread ...
      //
      _recoveryThreadOk = false ;
      try{
         _recoveryThread.interrupt() ;
      }catch(Exception rti ){
         esay( "Problem interrupting recoveryThread : "+rti ) ;
      }
     
      say( "Waiting for final gates to open" ) ;
      _finishGate.check() ;
      _recoveryGate.check() ;
      say( "Final gate opened; closing I/O streams" ) ;
      try{ _out.close() ; }catch(Exception ioe){}
      try{ _in.close() ; }catch(Exception ioe){}
      /*
       * this part would be O.K. if the other components 
       * were prepared for 'cancel-request' which they are
       * not. 
       * So  our solution is to store all persistent infos
       * in the domain context.
       *
      if( _requestHash.size() > 0 ){
         say( "Still "+_requestHash.size()+" requests pending" ) ;
         Enumeration  e     = _requestHash.elements() ;
         while( e.hasMoreElements() ){
            RequestFrame frame = (RequestFrame)e.nextElement() ;
            frame.getRequest().setReturnValue( 121 , "Pvr went down" ) ;
            sendBack( frame.getMessage() ) ;
         }
      }
      */
      synchronized( _hashLock ){

         if( _requestHash.size() == 0 ){
             say( "No remainding requests ; removing : "+_contextHashName ) ;
             _context.remove( _contextHashName ) ;
         }
      }
      say( "Pvr : "+_cellName+" finished" ) ;
  }
  public void run(){
     if( Thread.currentThread() == _listenThread ){
        String line = null ;
        Args   args = null ;
        try{
           while( true ){
              line = _in.readUTF() ;
              if( line == null )break ;
              runInterpreter( line ) ;
           }
        }catch( Exception e ){
           esay( "Problem in worker thread : "+e ) ;
//           StringWriter sw = new StringWriter() ;
//           e.printStackTrace( new PrintWriter( sw ) ) ;
//           say( "Worker Thread stack Trace ---> " ) ;
//           say( sw.toString() ) ;
        }
        if( ! _terminated ){
            _terminated = true ;
            kill() ;
        }
        say( "Listener Thread finished" ) ;
        _finishGate.open() ;
     }else if( Thread.currentThread() == _recoveryThread ){
         synchronized( _hashLock ){
           while( _recoveryThreadOk ){
              if( _recoveryHash.size() == 0 )break ;
              try{
                 sendRecoveryNewdrives() ;
                 _hashLock.wait(10000) ;
              }catch(Exception ie ){ 
                 esay( "recoveryThread problem : "+ie ) ;
                 break ;
              }
           }
        }
        say( "Recovery Thread finished" ) ;
        _recoveryGate.open() ;
     }
  }
  private void sendRecoveryNewdrives() throws Exception {
     say( "recovery Hashsize still : "+_recoveryHash.size() ) ;
     synchronized( _hashLock ){
        Vector removeVector = new Vector() ;
        Enumeration e = _recoveryHash.keys() ;
        while( e.hasMoreElements() ){
           String       key   = (String)e.nextElement() ;
           RequestFrame frame = (RequestFrame)_recoveryHash.get(key) ;
           
           //
           // if the frame links to an original frame
           // which does no longer exist, we schedule it
           // for removal.
           //
           String linkId = frame.getLinkId() ;
           if( ( linkId != null ) &&
               ( _recoveryHash.get(linkId) == null ) )
                  removeVector.addElement(linkId) ;
                  
           if( ! frame.sendNewdrive() ){
              say( "recovery : newdrive disabled for "+key ) ;
              continue ;
           }
           //
           PvrRequest r = frame.getRequest() ;
           frame.sendNewdrive(false) ;
           _pvrRequestId ++ ;
           String pvrRequestString  = 
                "newdrive "+_pvrRequestId+
                " "+r.getGenericDrive()+
                " "+r.getSpecificDrive() ;

           _recoveryHash.put( 
               ""+_pvrRequestId , 
               new RequestFrame( key ) 
                             ) ;
                             
           say( "toPvr : "+pvrRequestString ) ;
           _out.writeUTF( pvrRequestString ) ;
             
        }
        e = removeVector.elements() ;
        while( e.hasMoreElements() )
               _recoveryHash.remove((String)e.nextElement());
     }
  }
  public void messageArrived( CellMessage msg ){
     Object req = msg.getMessageObject() ;
     if( req instanceof PvrRequest ){

        processPvrRequest( msg , (PvrRequest)req  ) ;

     }else{
        esay( "Unidentified message arrived : "+req.getClass().getName() ) ;
        return ;
     }
  }
  private void processPvrRequest( CellMessage msg , PvrRequest request ){
      String command    = request.getActionCommand() ;
      String pvrRequest = null ;
      
      _pvrRequestId ++ ;
      
      if( command.equals("newdrive") ){
         pvrRequest = "newdrive "+_pvrRequestId+
                      " "+request.getGenericDrive()+
                      " "+request.getSpecificDrive() ;
                      
      }else if( command.equals( "terminate" ) ){
         pvrRequest = "terminate " + ( ++ _pvrRequestId ) ;
      }else if( command.equals( "mount" ) ){
         pvrRequest = "mount "+_pvrRequestId+
                      " "+request.getCartridge()+
                      " "+request.getGenericDrive()+
                      " "+request.getSpecificDrive() ;
      }else if( command.equals( "dismount" ) ){
         pvrRequest = "dismount "+_pvrRequestId+
                      " "+request.getCartridge()+
                      " "+request.getGenericDrive()+
                      " "+request.getSpecificDrive() ;
      }else{
         String problem = "Illegal request received : "+command ;
         esay( problem ) ;
         request.setReturnValue( 999 , problem ) ;
         sendBack( msg ) ;
         return ;
      }
      synchronized( _hashLock ){
         _requestHash.put( ""+_pvrRequestId , new RequestFrame( msg , request ) ) ;
      
         try{
            say( "toPvr : "+pvrRequest ) ;
            _out.writeUTF( pvrRequest ) ;
         }catch( Exception e ){
            String problem = "Problem while writing to pvr : "+e ;
            esay( problem ) ;
            request.setReturnValue( 999 , problem ) ;
            sendBack( msg ) ;
            _requestHash.remove( ""+_pvrRequestId ) ;           
            return ;
         }
      }
  }
  public void say( String str ){
     super.say( str ) ;
     pin( str ) ;
  }
  public void esay( String str ){
     super.esay( str ) ;
     pin( str ) ;
  }
  public String hh_send = "<command> <args...>" ;
  
  public String ac_send_$_1_99(  Args args ) throws Exception {
     StringBuffer sb = new StringBuffer() ;
     _pvrRequestId ++ ;
     sb.append( args.argv(0) ).append( " "+_pvrRequestId+ " " ) ;
     for( int i = 1 ; i < args.argc() ; i++ ){
        sb.append( args.argv(i) ).append( " " ) ;
     }
     synchronized( _hashLock ){

         _requestHash.put( ""+_pvrRequestId , 
                           new RequestFrame( getThisMessage() , null ) ) ;
     }
     try{
         say( "toPvr : "+sb.toString() ) ;
         _out.writeUTF( sb.toString() ) ;
     }catch( IOException e ){
         String problem = "Problem while writing to pvr : "+e ;
         esay( problem ) ;
         synchronized( _hashLock ){
            _requestHash.remove( ""+_pvrRequestId ) ;
         }
         return problem ;
     }
     return null ;
  }
  private void runInterpreter( String line ){
     say( "From pvr : "+line ) ;
     Args args = new Args( line ) ;
     if( args.argc() < 2 ){
        esay( "Not enough arguments in pvr message <"+line+">" ) ;
        return ;
     }
     String command = args.argv(0) ;
     if( command.equalsIgnoreCase( "log" ) ){
     
         say( "LOGMESSAGE : "+line ) ;
         
     }else if( command.equalsIgnoreCase( "done" ) ){
     
        synchronized( _hashLock ){
           String       id     = args.argv(1) ;
           RequestFrame frame  = (RequestFrame)_requestHash.remove( id ) ;
           if( frame != null ){
                //
                // found in original hashtable.
                // remove it from _recovery as well.
                //
                say( "Is original message -> "+id ) ;
                _recoveryHash.remove( id ) ;
                processDone( frame , args ) ;
                
                return ;
                
           }else{
              //
              // should be in recovery hash
              //
              frame = (RequestFrame)_recoveryHash.remove( id ) ;
              if( frame == null ){
                  esay( "Unidentified 'done' from pvr <"+line+">" ) ;
                  return ;
              }else{
                  String ft = frame.getFrameType() ;
                  if( ft.equals( "org" ) ){
                     //
                     // this shouldn't happen.
                     //
                     esay( "recovery : panic -> org msg not found in table" ) ;
                     return ;
                  }else if( ft.equals( "mount" ) ){
                      mountAnswerArrived( frame , args ) ;
                  }else if( ft.equals( "dismount" ) ){
                  
                  }else if( ft.equals( "newdrive" ) ){
                      newdriveAnswerArrived( frame , args ) ;
                  }
              
              }
           
           }
         }
     }
  }
  private void processDone( RequestFrame frame , Args args ){
     PvrRequest   request = frame.getRequest() ;
     if( request != null ){
        int    errno  = 0 ;
        String errmsg = "O.K." ;

        if( args.argc() > 2 ){
           errmsg = "Error="+args.argv(2) ;
           try{
              errno = Integer.parseInt( args.argv(2) ) ;
           }catch( Exception e ){
              errno = 666 ;
           }
        }
        if( args.argc() > 3 )errmsg = args.argv(3) ;

        request.setReturnValue( errno , errmsg ) ;
        if( request.getActionCommand().equals("newdrive") ){
           request.setCartridge(errmsg) ;
        }
     }else{
        frame.getMessage().setMessageObject( args.toString() ) ;
     }
     sendBack( frame.getMessage() ) ;
  }
  private void mountAnswerArrived( RequestFrame frame , Args args ){
      //
      // get the original request 
      //
      String linkId = frame.getLinkId() ;
      RequestFrame linkFrame = (RequestFrame)_recoveryHash.remove(linkId) ;
      if( linkFrame == null ){
         esay( "Recover for id "+linkId+" obsolete" ) ;
         return ; 
      }
      processDone( linkFrame , args ) ;
      
  }    
  private void newdriveAnswerArrived( RequestFrame frame , Args args ){
      //
      // get the original request 
      //
      String linkId = frame.getLinkId() ;
      RequestFrame linkFrame = (RequestFrame)_recoveryHash.get(linkId) ;
      if( linkFrame == null ){
         esay( "Recover for id "+linkId+" obsolete" ) ;
         return ; 
      }
      String type = args.argv(0) ;
      String id   = args.argv(1) ;     
      String rc   = args.argv(2) ;
      
      String cartInDrive = "empty" ;
      if( rc.equals( "1" ) ){
         cartInDrive = "empty" ;
      }else if( rc.equals( "2" ) ){
         cartInDrive = "unknown" ;
      }else if( rc.equals( "3" ) ){
         cartInDrive = args.argv(3) ;      
      }else{
         say( "recovery id-"+id+" -> unknown result : "+rc ) ;
         return ;
      }
      say( "recovery packet : type="+type+
           ";id="+id+";cart="+cartInDrive ) ;
      //
      // get the original request
      //
      PvrRequest req    = (PvrRequest)linkFrame.getRequest() ;
      String     action = req.getActionCommand() ;
      if( action.equals("mount") ){
          //
          //
          String cart = req.getCartridge() ;
          if( rc.equals( "1" ) ){
             // 
             // we have to resubmit a 'mount'.
             // we store the original 'request' together
             // with the new 'pvrRequestId' and link
             // them together, and we switch off the
             // newdrive requests.
             //
             _pvrRequestId++ ;
             String pvrRequest = "mount "+_pvrRequestId+
                          " "+req.getCartridge()+
                          " "+req.getGenericDrive()+
                          " "+req.getSpecificDrive() ;
                          
             RequestFrame nmf = new RequestFrame( linkId , "mount" ) ;
             nmf.sendNewdrive(false) ;
             _recoveryHash.put(  ""+_pvrRequestId , nmf ) ;
             try{
                say( "toPvr : "+pvrRequest ) ;
                _out.writeUTF( pvrRequest ) ;
             }catch(Exception ee ){
                esay( "Problem sending "+_pvrRequestId+" : "+ee ) ;
                _recoveryHash.remove( ""+_pvrRequestId ) ;
             }
             
          }else if( rc.equals( "2" ) ){
             //
             // we have to continue with our 'newdrive'
             //             
             say( "recovery : enabling newdrive for : "+linkId);
             linkFrame.sendNewdrive(true);
             return ;
          }else if( rc.equals( "3" ) ){
             //
             // a cartridge is mounted ( the correct one ? )
             //
             if( cart.equals( cartInDrive ) ){
                say( "recovery id-"+id+" original mount "+linkId+" ok "+cart ) ; 
                _requestHash.remove( linkId ) ;
                _recoveryHash.remove( linkId ) ;               
                CellMessage msg = linkFrame.getMessage() ;
                req.setReturnValue(0,"O.K.") ;
                sendBack( msg ) ;
                return ;
             }else{
                //
                // not yet the required answer. 
                // it difficult to do the right thing here,
                // so we let the request fail
                // and dismount the drive.
                //
                say( "recovery id-"+id+
                     " original mount "+linkId+" bad "+cart+"<->"+cartInDrive ) ; 
                _pvrRequestId++ ;
                 String pvrRequest = "dismount "+_pvrRequestId+
                              " *"+
                              " "+req.getGenericDrive()+
                              " "+req.getSpecificDrive() ;
                 try{
                    say( "toPvr : "+pvrRequest ) ;
                    _out.writeUTF( pvrRequest ) ;
                 }catch(Exception ee ){
                    esay( "Problem sending "+_pvrRequestId+" : "+ee ) ;
                 }
                 _requestHash.remove( linkId ) ;
                 _recoveryHash.remove( linkId ) ;
                CellMessage msg = linkFrame.getMessage() ;
                req.setReturnValue(44,"pvr seems to be confused") ;
                sendBack( msg ) ;
                return ;
             }
          }
      }else if( action.equals("dismount") ){
          //
          // the original request was a dismount, so we have
          // to wait until the drive is empty
          //
          if( cartInDrive.equals( "empty" ) ){
             say( "recovery id-"+id+
                  " original dismount "+linkId+" ok "+cartInDrive ) ; 
             synchronized( _hashLock ){
                _requestHash.remove( linkId ) ;
                _recoveryHash.remove( linkId ) ;
             }
             CellMessage msg = linkFrame.getMessage() ;
             req.setReturnValue(0,"O.K.") ;
             sendBack( msg ) ;
          }else{
             say( "recovery id-"+id+
                  " original dismount "+linkId+
                  " bad not yet empty <->"+cartInDrive ) ; 
             //
             // not yet the required answer. lets wait...
             //
             return ;
          }
      }else{
          esay( "Suspicious request in recovery : "+action ) ;
          return ;
      }


  }
  private void sendBack( CellMessage msg ){
     msg.revertDirection() ;
     Object returnObject = msg.getMessageObject() ;
     if( returnObject instanceof EurogateRequest ){
        EurogateRequest req = (EurogateRequest)returnObject ;
        req.setActionCommand(req.getActionCommand()+"-ready") ;
     }
     try{
        sendMessage( msg ) ;
     }catch( Exception msge ){
        esay( "PANIC : can't reply message : "+msge ) ;
     }
  }
} 
