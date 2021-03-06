package eurogate.gate ;

import eurogate.vehicles.* ;
import eurogate.misc.* ;

import dmg.util.* ;
import dmg.cells.nucleus.* ;
import java.net.* ;
import java.util.* ;
import java.io.* ;

public class EuroGate extends CellAdapter implements Runnable {

    private DataInputStream   _input  = null ;
    private DataOutputStream  _output = null ;
    private Thread            _readerThread = null ;
    private String            _state   = "init" ;
    private Object            _ioLock  = new Object() ;
    private int     _removeCounter = 0 , 
                    _readCounter   = 0 ,
                    _writeCounter  = 0 ;
    private int     _replies       = 0 ;
    private Socket  _socket        = null ;
    private String  _remotePeer    = "" ;
    private CellNucleus  _nucleus  = null ;
    private int     _minorVersion  = 2 ;
    private int     _majorVersion  = 0 ;
    
    public EuroGate( String name , Socket socket ) throws IOException {
        super( name , "" , true ) ;
        
        _nucleus = getNucleus() ;
        try{
	   _socket = socket ;
           _input  = new DataInputStream( socket.getInputStream() ) ;
           _output = new DataOutputStream( socket.getOutputStream() ) ;
        }catch(IOException ee ){
           kill() ;
           throw ee ;
        }
        _readerThread = _nucleus.newThread( this ) ;
        _readerThread.start() ;
	_remotePeer   = _socket.getInetAddress().toString()+":"+
	                _socket.getPort() ;
        
    }
    public EuroGate( String name , StreamEngine engine ) throws Exception {
        super( name , "" , false ) ;
        
        _nucleus = getNucleus() ;
        try{
	   _socket = null ;
           _input  = new DataInputStream( engine.getInputStream() ) ;
           _output = new DataOutputStream( engine.getOutputStream() ) ;
        }catch(Exception ee ){
           start() ;
           kill() ;
           throw ee ;
        }
        _readerThread = _nucleus.newThread( this , "reader" ) ;
        _readerThread.start() ;
	_remotePeer   = engine.getInetAddress().toString()  ;
        start() ;
    }
    public void cleanUp(){
       say( "clean up triggerd" ) ;
       if( _input != null )try{ 
           _input.close() ; 
           _input = null ;
       }catch(Exception ee ){}
       if( _output != null )try{ 
           _output.close() ; 
           _output = null ;
       }catch(Exception ee ){}
    }
    public String toString(){
      return "Peer="+_remotePeer+";Req="+_replies ;
    }
    public void getInfo( PrintWriter pw ){
       pw.println( "Connection from : "+_remotePeer ) ;
       pw.println( "  -- Counters -- " ) ;
       pw.println( "   major Version  : "+_majorVersion ) ;
       pw.println( "   minor Version  : "+_minorVersion ) ;
       pw.println( "     Write  : "+_writeCounter ) ;
       pw.println( "     Write  : "+_writeCounter ) ;
       pw.println( "     Read   : "+_readCounter ) ;
       pw.println( "     Remove : "+_removeCounter ) ;
       pw.println( "     Reply  : "+_replies ) ;
    }
    public void run(){
       if( Thread.currentThread() == _readerThread ){
       
          runReaderThread() ;
          
       }
    
    }
    
    private void runReaderThread(){
       String command = null , reply = null ;
       try{
          say( "Going into read loop" ) ;
          while( true ){
             //
             command = _input.readUTF() ;
             say( "!!!  CLIENT : >"+command+"<" ) ;
             try{
                reply = (String)command( new Args( command ) ) ;
             }catch( Exception ce ){
                if( _minorVersion > 1 ){
                   String problem = "ABORT 666 \""+ce.toString()+"\"" ;
                   esay( problem ) ;
                   _output.writeUTF( problem ) ;
                }else{
                   String problem = "ABORT 666 "+ce.toString() ;
                   esay( problem ) ;
                   _output.writeUTF( "\""+problem+"\""  ) ;
                }
                break ;
             }
             if( reply == null )continue ;
             _output.writeUTF( reply ) ;
          }
       }catch(Exception e ){
          say( "Exception in readerThread : "+e ) ;
       }
       try{ _output.close() ; }catch(Exception e){} ;
       say( "Client thread closed" ) ;
       kill() ;
    }
    public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       if( obj instanceof RequestImpl ){
          RequestImpl req = (RequestImpl)obj ;
          pin( "<<< "+req ) ;
          _replies ++ ;
          if( req.getType().equals("remove") ){
             returnBbOK( req ) ;
             return ;
          }
          if( req.getActionCommand().equals( "BB-OK" ) ){             
             returnBbOK( req ) ;
          }else if( req.getActionCommand().endsWith("-ready" ) ){
             returnMoverOK( req ) ;
          }
       
       }else if( obj instanceof StoreRequest ){
          StoreRequest sr = (StoreRequest)obj ;
          say( "Store Request Reply : "+obj ) ;
          if( sr.getCommand().equals("list-volume") ){
             String result = null ;
             if( sr.getReturnCode() != 0 ){
                 result =  "failed "+sr.getId()+
                           " "+sr.getReturnCode()+
                           " \""+sr.getReturnMessage()+
                           "\"" ;
             }else{
                result = "ok "+sr.getId() ; 
             }
             try{
                _output.writeUTF( result ) ;
             }catch(Exception ee ){
                say( "Problem sending bbOK : "+ee ) ;
                kill() ;
             }
          }else if( sr.getCommand().equals("get-bfid") ){
             String result = null ;
             if( sr.getBitfileId() == null ){
                 result =  "failed "+sr.getId()+" 4 "+
                           "\"Bfid not found : "+
                           sr.getBfid()+"\""  ;
             }else{
                StringBuffer sb = new StringBuffer() ;
                BfRecordable bf = sr.getBitfileId() ;
                sb.append("ok ").append(sr.getId()).
                   append(" \"").append(sr.getBfid()).
                   append(" ").append(bf.getVolume()).
                   append(" ").append(bf.getFilePosition()).
                   append(" ").append(bf.getFileSize()).
                   append("\"") ;
                result = sb.toString() ; 
             }
             try{
                _output.writeUTF( result ) ;
             }catch(Exception ee ){
                say( "Problem sending bbOK : "+ee ) ;
                kill() ;
             }
          }
       }else if( obj instanceof NoRouteToCellException ){
          esay( "Cell couldn't be reached : "+obj.toString() ) ;
       }
    }
    private void returnBbOK( RequestImpl req ){
       try{
          if( req.getReturnCode() == 0 ){
             if( req.getType().equals("put") ){
                _output.writeUTF( "OK "+
                                req.getServerReqId()+" "+
                                req.getBfid()+" "+
                                req.getClientReqId()     ) ;
             }else if( req.getType().equals("get") ){
                _output.writeUTF( "OK "+
                                req.getServerReqId()+" "+
                                req.getFileSize() + " "+
                                req.getClientReqId()     ) ;
             }else if( req.getType().equals("remove") ){
                String id = req.getClientReqId() ;
                if( id.equals("none") ){
                    _output.writeUTF( "OK " ) ;
                }else{
                    _output.writeUTF( "OK "+id ) ;
                }
             }
          }else{
             _output.writeUTF( "NOK "+
                                req.getReturnCode()+" \""+
                                req.getReturnMessage()+"\"" ) ;
          }
       }catch(Exception ee){
          say( "Problem sending bbOK : "+ee ) ;
          kill() ;
       }
    }
    private void returnMoverOK( RequestImpl req ){
       try{
          if( req.getReturnCode() == 0 ){
             _output.writeUTF( "BBACK "+
                                req.getServerReqId()+" "+
                                req.getBfid() +" "+ 
                                req.getReturnCode()+" \""+
                                req.getReturnMessage()+"\"" ) ;
          }else{
             _output.writeUTF( "CANCEL "+
                                req.getServerReqId()+" "+
                                req.getReturnCode()+" \""+
                                req.getReturnMessage()+"\"" ) ;
          }
       }catch(Exception ee){
          say( "Problem sending bbOK : "+ee ) ;
          kill() ;
       }

    }
    public String ac_get_bfid_$_3( Args args )
           throws CommandException                      {
       String id    = args.argv(0) ;
       String store = args.argv(1) ;
       String bfid  = args.argv(2) ;
       pin(  "get-bfid : "+id+" "+store+" "+bfid ) ;
       
       StoreRequest request = 
           new StoreRequest("get-bfid" , id , store , bfid      ) ;
       sendIt( request ) ;
       return null ;
    }
    public String ac_list_volume_$_5( Args args )
           throws CommandException                      {
       String id      = args.argv(0) ;
       String store   = args.argv(1) ;
       String volume  = args.argv(2) ;
       String host    = args.argv(3) ;
       int    port    = 0 ;
       try{
          port = Integer.parseInt( args.argv(4) ) ;
       }catch(Exception ee ){
          throw new 
          CommandException( 22 , "Port Not a number : "+args.argv(4) ) ;
       }
       pin(  "list-volume : "+id+" "+store+" "+volume ) ;
       
       StoreRequest request = 
           new StoreRequest("list-volume" , id , store , volume ) ;
       request.setHost( host , port ) ;
       sendIt( request ) ;
       return null ;
    }
    public String ac_SESSION_WELCOME_$_1( Args args ){
       String version = args.argv(0) ;
       int pos = version.indexOf(".") ;
       if( ( version.length() < 3 ) || ( pos < 0 )  ||
           ( pos == 0 ) || ( pos == (version.length()-1) ) ) {
           
           return "NOK 44 \"Illegal protocol version : "+version+"\"" ;
       }else{
           try{
              _majorVersion = Integer.parseInt(version.substring(0,pos));
              _minorVersion = Integer.parseInt(version.substring(pos+1));
           }catch( Exception ee ){
              return "NOK 45 \"Illegal protocol version : "+version+"\"" ;
           }
       }
       if( _majorVersion != 0 )
          return "NOK 45 \"Illegal major protocol version : "+
                 _majorVersion+"\"" ;
       
       pin(  "Session created : Version = "+version ) ;
       pin(  "Session created : major=0 minor="+_minorVersion ) ;
       return "OK 0" ;
    }
    public String ac_SESSION_CLOSE( Args args ){
       pin(  "Session closed" ) ;
       return "OK 0"  ;
    }
    public String ac_REMOVEDATASET_$_2( Args args ) 
           throws CommandException                      {

       String store     = args.argv(0) ;
       String bfid      = args.argv(1) ;
       _removeCounter ++ ;
       pin( "REMOVE  bfid="+bfid) ;
       RequestImpl shuttle =
           new RequestImpl( "remove" , store , bfid , "none" ) ;

       sendIt( shuttle ) ;
       return null ;
    }
    public String ac_REMOVEDATASETX_$_3( Args args ) 
           throws CommandException                      {

       String store      = args.argv(0) ;
       String bfid       = args.argv(1) ;
       String sessionID  = args.argv(2) ;
       _removeCounter ++ ;
       pin( "REMOVE  bfid="+bfid) ;
       RequestImpl shuttle =
           new RequestImpl( "remove" , store , bfid , sessionID ) ;

       sendIt( shuttle ) ;
       return null ;
    }
    public String ac_WRITEDATASET_$_9( Args args ) 
           throws CommandException                      {
    
       String store     = args.argv(0) ;
       String group     = args.argv(1) ;
       String migration = args.argv(2) ;
       long   size  = Long.parseLong( args.argv(3) ) ;
       String host  = args.argv(4) ;
       int    port  = Integer.parseInt( args.argv(5) ) ;
       String sessionID  = args.argv(7) ;
       String params     = args.argv(8) ;
       _writeCounter ++ ;
       pin( "WRITE sg="+group+
                    ";size="+size+
		    ";host="+host+":"+port+
		    ";cid="+sessionID        ) ;
       RequestImpl shuttle =
           new RequestImpl( "put" ,
                               host , port ,
                               store , group ,migration ,
                               size ,
                               sessionID   ) ;

       shuttle.setParameter( params ) ;
       sendIt( shuttle ) ;
       return null ;
    
    }
    public String ac_READDATASET_$_7( Args args ) 
           throws CommandException                      {
    
       String store     = args.argv(0) ;
       String bfid      = args.argv(1) ;
       String host      = args.argv(2) ;
       int    port      = Integer.parseInt( args.argv(3) ) ;
       String sessionID  = args.argv(5) ;
       String params     = args.argv(6) ;
       _readCounter++ ;
       pin( "READ bfid="+bfid+
		    ";host="+host+":"+port+
		    ";cid="+sessionID        ) ;
       RequestImpl shuttle =
           new RequestImpl( "get" ,
                               host , port ,
                               store , bfid ,
                               sessionID   ) ;
       shuttle.setParameter( params ) ;
       sendIt( shuttle ) ;
       return null ;
    
    }
    private void sendIt( RequestImpl shuttle )throws CommandException {
        try{
           synchronized( _ioLock ){
              _state = "sendRequest" ;
              CellPath path = new CellPath( shuttle.getStore()+"-store" ) ;
              say( "Sending request to "+path ) ;
              sendMessage( new CellMessage( path , shuttle ) ) ;
           }
        }catch( NoRouteToCellException nrtc ){
            throw new 
            CommandException( 4 , "Store not found " ) ;
        }catch( Exception e ){
            throw new 
            CommandException( 5 , "Problem sending 'put' request : "+e ) ;
        
        }         
    }
    private void sendIt( StoreRequest shuttle )throws CommandException {
        try{
           synchronized( _ioLock ){
              _state = "sendRequest" ;
              CellPath path = new CellPath( shuttle.getStore()+"-store" ) ;
              say( "Sending request to "+path ) ;
              sendMessage( new CellMessage( path , shuttle ) ) ;
           }
        }catch( NoRouteToCellException nrtc ){
            throw new 
            CommandException( 4 , "Store not found " ) ;
        }catch( Exception e ){
            throw new 
            CommandException( 5 , "Problem sending 'put' request : "+e ) ;
        
        }         
    }
}
