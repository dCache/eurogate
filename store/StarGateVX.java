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
   public StarGateVX( String name , String args ) throws Exception {
   
       super( name , args , false ) ;
       _args    = getArgs() ;
       _nucleus = getNucleus() ;
       try{
          if( _args.argc() < 2 ){
             start() ;
             kill() ;
             throw new 
             IllegalArgumentException( 
             "Usage : ... <EuroStorableClass> <pvlPath> [EuroStorable specific infos]" ) ;
          }

          _euroClass  = _args.argv(0);
          _pvlPath    = new CellPath( _args.argv(1) ) ;
          _args.shift() ;
          _args.shift() ;
          
          loadStoreClass() ;
          
       }catch( IllegalArgumentException e ){
          start() ;
          kill() ;
          throw e ;
       }catch( Exception ee ){
          throw ee ;
       }catch( Throwable de ){
          start() ;
          kill() ;
          throw new 
          IllegalArgumentException( de.toString() ) ;
       }

       start() ;
   
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
                 sr.setBitfileId( _eurostore.getBitfileRecord(sr.getBfid()) ) ;
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
   private void addRequest( CellMessage msg , BitfileRequest req ){

       req.setServerReqId( _nextServerReqId++ ) ;
        
       initialRequest( req ) ;
       
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
             
             finalRequest( req ) ;
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

          finalRequest( req ) ;

          msg.revertDirection();
          req.setActionCommand( "Mover-OK" ) ;  
          try{
             sendMessage( msg ) ;                 
          }catch(Exception nrtc ){
             esay( "Door cell seems to have disappeared" ) ;
          }

       }
          
       
   }
   private void finalRequest( BitfileRequest req ){
       String ioType = req.getType() ; 
       try{    
          if(      ioType.equals("put")  )_eurostore.finalPutRequest( req ) ;
          else if( ioType.equals("get")  )_eurostore.finalGetRequest( req ) ;
          else if( ioType.equals("remove") )_eurostore.finalRemoveRequest( req ) ;
          else req.setReturnValue( 11 , "Operation not found : "+ioType ) ; ;
       }catch( Throwable t ){
          req.setReturnValue( 666 , t.toString() ) ;
       }
   }
   private void initialRequest( BitfileRequest req ){
       String ioType = req.getType() ; 
       try{    
          if(      ioType.equals("put")  )_eurostore.initialPutRequest( req ) ;
          else if( ioType.equals("get")  )_eurostore.initialGetRequest( req ) ;
          else if( ioType.equals("remove") )_eurostore.initialRemoveRequest( req ) ;
          else req.setReturnValue( 11 , "Operation not found : "+ioType ) ; ;
       }catch( Throwable t ){
          req.setReturnValue( 666 , t.toString() ) ;
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
       
      finalRequest( req ) ;
 
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
         finalRequest( req ) ;
      }
   
   }
   public String hh_ls_group = "[-l] <storageGroup>" ;
   public String ac_ls_group_$_1( Args args ) throws Exception {
      StringBuffer sb    = new StringBuffer();
      String       group = args.argv(0) ;
      
      boolean longList = args.optc() > 0 ;
      sb.append( "  Bitfile List\n" ) ;
      
      
      Enumeration e = _eurostore.getBfidsByStorageGroup( group , 0 ) ;

      dumpBfs( sb , e , longList ) ;
      
      return sb.toString();
   }
   public String hh_ls_volume = "[-l] <volume>" ;
   public String ac_ls_volume_$_1( Args args ) throws Exception {
      StringBuffer sb    = new StringBuffer();
      String       volume = args.argv(0) ;
      
      boolean longList = args.optc() > 0 ;
      sb.append( "  Bitfile List\n" ) ;
      
      Enumeration e = _eurostore.getBfidsByVolume( volume , 0 ) ;

      dumpBfs( sb , e , longList ) ;
      
      return sb.toString();
   }
   private void dumpBfs( StringBuffer sb , Enumeration e , boolean longList ){
      DateFormat   df    = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      while( e.hasMoreElements() ){
         String bfid = (String)e.nextElement() ;
         if( longList ){
            BfRecordable bitfileid = _eurostore.getBitfileRecord( bfid ) ;
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
   
      Enumeration  e  = _eurostore.getStorageGroups(0) ;      
      StringBuffer sb = new StringBuffer();
      
      while( e.hasMoreElements() )
         sb.append( e.nextElement().toString() ).append("\n" ) ;
         
      return sb.toString();
   }
   public String hh_ls_bfid = "<bfid>" ;
   public String ac_ls_bfid_$_1( Args args ) throws CommandException {
      String     bfid      = args.argv(0);
      DateFormat df        = new SimpleDateFormat("MMM d, hh.mm.ss" ) ;
      
      BfRecordable bfred = _eurostore.getBitfileRecord( bfid ) ;
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
         Enumeration e = _eurostore.getBfidsByVolume(volume,0) ;
         
         
         String [] group  = null ;
         String [] bfid   = null ;
         Vector    bfids  = new Vector() ;
         BfRecordable id     = null ;
         Socket    socket = null ;
         DataOutputStream out = null ;
         try{
            while( e.hasMoreElements() ){
              String bfidx = (String)e.nextElement() ;
              bfids.addElement( _eurostore.getBitfileRecord(bfidx) ) ;
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
         }
         _msg.revertDirection() ;
         try{
            _nucleus.sendMessage(_msg);
         }catch(Exception ee){}         
      }
   }

}
