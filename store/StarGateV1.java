package eurogate.store ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.util.* ;
import java.text.* ;
import java.net.* ;

public class StarGateV1 extends CellAdapter  {

   private CellNucleus     _nucleus ;
   private Args            _args ;
   private long            _nextServerReqId = 100 ;
   private CellPath        _pvlPath     = null ;
   private BitfileDatabase _dataBase    = null ;
   
   public StarGateV1( String name , String args ){
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          if( _args.argc() < 2 ){
             start() ;
             kill() ;
             throw new 
             IllegalArgumentException( "Usage : ... <dbPath> <pvlPath>" ) ;
          }

          _dataBase = new BitfileDatabase( _args.argv(0) ) ;
          _pvlPath  = new CellPath( _args.argv(1) ) ;
          
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

       start() ;
   
   }
   public void say( String msg ){
       pin( msg ) ;
       super.say( msg ) ;
   }
   public void esay( String msg ){
       pin( "ERROR : "+msg ) ;
       super.say( msg ) ;
   }
    public class BfRecordV1 implements BfRecordable {
       private String _bfid  = null ;
       private String _group = "" ;
       private long   _size  = 0 ;
       private String _volume = null ;
       private String _filePosition = null ;
       private String _parameter    = null ;
       private Date   _lastDate     = null ;
       private Date   _creationDate = null ;
       private int    _counter      =  0 ;
       private String _status       = null ;
       
       private BfRecordV1( String bfid ){ _bfid = bfid ; }
       public String getBfid(){ return _bfid ; }
       public String getStorageGroup(){ return _group ; }
       public long   getFileSize(){ return _size ; }
       public String getVolume(){ return _volume ; }
       public String getFilePosition(){ return _filePosition ; }
       public String getParameter(){ return _parameter ; }

       public Date   getLastAccessDate(){ return _lastDate ; }
       public Date   getCreationDate(){ return _creationDate ; }
       public int    getAccessCounter(){ return _counter ; }

       public String getStatus(){ return _status ; }
    
    }
   public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       if( obj instanceof RequestImpl ){
           say( "Message arrived : "+obj ) ;
           RequestImpl req = (RequestImpl) obj ;
           addRequest( msg , req ) ;
       }else if( obj instanceof StoreRequest ){
           StoreRequest sr = (StoreRequest)obj ;
           if( sr.getCommand().equals( "get-bfid" ) ){
              try{
                 BitfileId bitfile = _dataBase.getBitfileId(sr.getBfid()) ;
                 BfRecordV1 bfr    = new BfRecordV1(sr.getBfid()) ;
                 bfr._group        = "unknown" ; 
                 bfr._size         = bitfile.getSize() ;
                 bfr._volume       = bitfile.getVolume() ;
                 bfr._filePosition = bitfile.getPosition() ;
                 bfr._parameter    = bitfile.getParameter() ;
                 bfr._status       = bitfile.getMode() ;
                 bfr._lastDate     = bitfile.getLastAccessDate() ;
                 bfr._creationDate = bitfile.getCreationDate() ;
                 bfr._counter      = bitfile.getAccessCount() ;
                 sr.setBitfileId( bfr ) ;
              }catch( DatabaseException dbe ){
                 sr.setBitfileId(null);
              }
              msg.revertDirection() ;
              try{
                 sendMessage(msg);
              }catch(Exception ee){}
           }else if( sr.getCommand().equals( "list-volume" ) ){
              new ListVolumeController( msg , sr ) ;
           }
       }
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
             this.bfid = bfid ;
             this.position  = position ;
             this.key  = key ;
          }
      }
      public void run(){
         String [] group  = null ;
         String [] bfid   = null ;
         Vector    bfids  = new Vector() ;
         BitfileId id     = null ;
         String    volume = _request.getVolume() ;
         Socket    socket = null ;
         DataOutputStream out = null ;
         say( "Scanning groups" ) ;
         try{
            group = _dataBase.getGroups() ;
            for( int i = 0 ; i < group.length ; i++ ){
               bfid  = _dataBase.getBitfileIds( group[i] ) ;
               for( int j = 0 ; j < bfid.length ; j++ ){
                  id =  _dataBase.getBitfileId( group[i] , bfid[j] ) ;
                  if( id.getVolume().equals( volume ) )
                      bfids.addElement( id ) ;
               }
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
               id     = (BitfileId)bfids.elementAt(i) ;
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
               bflist[i] = new Bf( id.getName() ,
                                   Integer.parseInt( id.getPosition()),
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
         }catch( DatabaseException dbe ){
            _request.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
         }catch( IllegalArgumentException e ){
            _request.setReturnValue( 44 , e.getMessage() ) ;
         }catch( Exception e ){
            _request.setReturnValue( 44 , e.toString() ) ;
         }finally{
            if( socket != null){
               say( "Closing connection" ) ;
               try{ out.close() ; }catch(Exception e ){}
               try{ socket.close() ; }catch(Exception e ){}
            }
         }
         _msg.revertDirection() ;
         try{
            _nucleus.sendMessage(_msg);
         }catch(Exception ee){}         
      }
   }
   private void addRequest( CellMessage msg , RequestImpl req ){
       boolean ok = false ;
       //
       // create our internal representation of a request
       // and set the request id and the BB-OK.
       //
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
       // if ok, send the request to our pvl.
       //
       if( ok ){
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
              msg.revertDirection();
              req.setActionCommand( "Mover-OK" ) ;  
              try{
                 sendMessage( msg ) ;                 
              }catch(Exception nrtc ){
                 esay( "Door cell seems to have disappeared" ) ;
              }
           
           }
          
       }
   }
   public boolean addRemoveRequest( CellMessage msg , RequestImpl req ){
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
       req.setFilePosition( bitfile.getPosition() ) ;
       req.setVolume( bitfile.getVolume() ) ;
       
       return true ;
   }
   public boolean addRemoveRequestOld( CellMessage msg , RequestImpl req ){

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
       req.setFilePosition( bitfile.getPosition() ) ;
       req.setVolume( bitfile.getVolume() ) ;
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
       long   size  = req.getFileSize() ;
       req.setBfid( bfid ) ;
       
       say( "addPutRequest : bfid="+bfid+";group="+group+";size="+size ) ;
       
       BitfileId bitfile = new BitfileId( bfid , size )  ;
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
   public void messageToForward( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       say( "Message to forward : "+obj ) ;
       if( obj instanceof RequestImpl ){
           returnedRequest( msg , (RequestImpl) obj ) ;  
       }
   }
   private void returnedRequest( CellMessage msg , RequestImpl req ){
       
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
         bitfileid.setPosition( req.getPosition() ) ;
         bitfileid.setVolume( req.getVolume() ) ;
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
   public String hh_ls_bfid = "<bfid>" ;
   public String ac_ls_bfid_$_1( Args args ) throws CommandException {
      String     bfid      = args.argv(0);
      BitfileId  bitfileid = null ;
      DateFormat df        = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      try{
          bitfileid = _dataBase.getBitfileId(bfid) ;
      }catch( DatabaseException dbe ){
         throw new 
         CommandException( dbe.getCode() , dbe.getMessage() ) ;
      }
      StringBuffer sb = new StringBuffer() ;
      sb.append("Name          : ").
         append( bitfileid.getName() ).append("\n") ;
      sb.append("Mode          : ").
         append( bitfileid.getMode() ).append("\n") ;
      sb.append("Volume        : ").
         append( bitfileid.getVolume() ).append("\n") ;
      sb.append("Position      : ").
         append( bitfileid.getPosition() ).append("\n") ;
      sb.append("Access Count  : ").
         append( bitfileid.getAccessCount() ).append("\n") ;
      sb.append("Parameter     : ").
         append( bitfileid.getParameter() ).append("\n") ;
      sb.append("Last Access   : ").
         append( df.format( bitfileid.getLastAccessDate())).append("\n") ;
      sb.append("Creation Data : ").
         append( df.format( bitfileid.getCreationDate())).append("\n") ;
      
      return sb.toString() ;
   
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
               sb.append(bitfileid.getName()).append("  ").append( bitfileid.getMode().charAt(0) ) ;
               sb.append( "  "+bitfileid.getSize() ).append( "  " ) ;
               sb.append( df.format(bitfileid.getCreationDate()) ).append( "\n" ) ;
               if( bitfileid.getMode().equals( "persistent" ) ){
               
                   sb.append("   ") ;
                   sb.append( Formats.field( ""+bitfileid.getAccessCount() , 4 , Formats.LEFT) ) ;
                   sb.append( Formats.field( bitfileid.getVolume()         ,10 , Formats.LEFT) ) ;
                   sb.append( Formats.field( bitfileid.getPosition()       ,10 , Formats.LEFT) ) ;
                   sb.append( df.format( bitfileid.getLastAccessDate())) ;
                   sb.append( "\n" ) ;
                   
               }
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
