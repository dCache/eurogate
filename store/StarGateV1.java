package eurogate.store ;

import eurogate.misc.* ;
import eurogate.vehicles.* ;

import dmg.cells.nucleus.* ;
import dmg.util.* ;

import java.io.* ;
import java.util.* ;
import java.text.* ;

public class StarGateV1 extends CellAdapter  {

   private CellNucleus _nucleus ;
   private Args        _args ;
   private long        _nextServerReqId = 100 ;
   private CellPath        _pvlPath     = null ;
   private BitfileDatabase _dataBase    = null ;
   
   public StarGateV1( String name , String args ){
   
       super( name , args , false ) ;
       _args = getArgs() ;
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
   public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       if( obj instanceof RequestImpl ){
           say( "Message arrived : "+obj ) ;
           RequestImpl req = (RequestImpl) obj ;
           addRequest( msg , req ) ;
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
       req.setPosition( bitfile.getPosition() ) ;
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
       req.setPosition( bitfile.getPosition() ) ;
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
