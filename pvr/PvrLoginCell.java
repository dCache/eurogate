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
  private boolean          _terminated  = false ;
  private Gate             _finishGate  = new Gate(false) ;
  private String           _contextHashName = null ;

  private class RequestFrame {
     private CellMessage _message ;
     private PvrRequest  _request ;
     public RequestFrame( CellMessage msg , PvrRequest request ){
        _request = request ; 
        _message = msg ;
     }
     public PvrRequest  getRequest(){ return _request ; }
     public CellMessage getMessage(){ return _message ; }
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
       
       _listenThread  = new Thread( this , "listenThread" ) ;       
       _listenThread.start() ;
       
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
       if( _requestHash == null ){
          say( _contextHashName + " not found (puh); creating new one" ) ;
          _context.put( _contextHashName , _requestHash = new Hashtable() ) ;
       }else{
          say( _contextHashName + " found; will use it ( dumps follows )" ) ;
          Enumeration e = _requestHash.elements() ;
          for( int i = 0 ; e.hasMoreElements() ; i++ ){
             say( "["+i+"]="+(e.nextElement()).toString() ) ;
          }
       }
       //
       _finishGate.close() ;
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
         say( "Sending 'terminate' to pvr" ) ;
         try{
            _out.writeUTF( "terminate "+( ++_pvrRequestId ) ) ;
         }catch( Exception ee ){}
      }
      say( "Waiting for final gate to open" ) ;
      _finishGate.check() ;
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
      if( _requestHash.size() == 0 ){
          say( "No remainding requests ; removing : "+_contextHashName ) ;
          _context.remove( _contextHashName ) ;
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
        }
     }
     if( ! _terminated ){
         _terminated = true ;
         kill() ;
     }
     _finishGate.open() ;
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
      _requestHash.put( ""+_pvrRequestId , new RequestFrame( msg , request ) ) ;
      try{
         _out.writeUTF( pvrRequest ) ;
      }catch( Exception e ){
         String problem = "Problem while writing to pvr : "+e ;
         esay( problem ) ;
         request.setReturnValue( 999 , problem ) ;
         sendBack( msg ) ;
         _requestHash.remove( ""+_pvrRequestId ) ;
         return ;
      }
      _requestHash.put( ""+_pvrRequestId , new RequestFrame( msg , request ) ) ;
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
     _requestHash.put( ""+_pvrRequestId , 
                       new RequestFrame( getThisMessage() , null ) ) ;
     
     try{
         _out.writeUTF( sb.toString() ) ;
     }catch( IOException e ){
         String problem = "Problem while writing to pvr : "+e ;
         esay( problem ) ;
         _requestHash.remove( ""+_pvrRequestId ) ;
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
     
        
        String       id      = args.argv(1) ;
        RequestFrame frame   = (RequestFrame)_requestHash.remove( id ) ;
        
        if( frame == null ){
            esay( "Unidentified 'done' from pvr <"+line+">" ) ;
            return ;
        }
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
           frame.getMessage().setMessageObject( line ) ;
        }
        sendBack( frame.getMessage() ) ;
     }
  }
  private void sendBack( CellMessage msg ){
     msg.revertDirection() ;
     EurogateRequest req = (EurogateRequest)msg.getMessageObject() ;
     req.setActionCommand(req.getActionCommand()+"-ready") ;
     try{
        sendMessage( msg ) ;
     }catch( Exception msge ){
        esay( "PANIC : can't reply message : "+msge ) ;
     }
  }
} 
