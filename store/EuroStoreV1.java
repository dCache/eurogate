package eurogate.store ;

import java.util.* ;
import dmg.util.* ;
import eurogate.misc.* ;
import eurogate.vehicles.* ;

public class EuroStoreV1 implements EuroStoreable {

    private Logable    _log  = null ;
    private Dictionary _env  = null ;
    private Args       _args = null ;
    private BitfileDatabase _dataBase    = null ;
    
    public EuroStoreV1( Args args , Dictionary env , Logable log )
           throws Exception {
       _args = args ;
       _env  = env ;
       _log  = log ;
       
       if( _args.argc() < 1 )
         throw new 
         IllegalArgumentException( "EuroStoreV1 : database name missing" ) ; 

       _dataBase = new BitfileDatabase( _args.argv(0) ) ;
       
    }
    private void say( String msg ){ _log.log( msg ) ; }
    private void esay( String msg ){ _log.elog( msg ) ; }
    public void initialPutRequest( BitfileRequest req ){
       //
       // assign a new bitfile to this request
       //
       String group = req.getStorageGroup() ;
       String bfid  = new Bfid().toString() ;
       long   size  = req.getFileSize() ;
       req.setBfid( bfid ) ;
       
       say( "ipr : bfid="+bfid+";group="+group+";size="+size ) ;
       
       BitfileId bitfile = new BitfileId( bfid , size )  ;
       bitfile.setParameter( req.getParameter() ) ;
       //
       // store the bitfile into the database
       try{
          _dataBase.storeBitfileId( group , bitfile ) ;
       }catch( DatabaseException dbe ){
           String error = "StorageGroup not found : "+group ;
           esay( error ) ;
           req.setReturnValue( 11 , error ) ;
       }
    
    }
    public void finalPutRequest( BitfileRequest req ){
       String group = req.getStorageGroup() ;
       String bfid  = req.getBfid() ;

       if( req.getReturnCode() != 0 ){
          say( "fpr : removing bfid from database : "+
               bfid+" : "+req.getReturnMessage() ) ;
          try{
             _dataBase.removeBitfileId( group , bfid )  ;
          }catch( DatabaseException dbe ){
             esay( "fpr : Couldn't remove bfid from database : "+bfid+" : "+dbe ) ;
          }            
          return ;
       }
       //
       // update the database entry
       //
       try{
          say( "fpr : storing bfid  : "+ bfid ) ;
          BitfileId bitfileid = _dataBase.getBitfileId( group , bfid ) ;
          bitfileid.setMode("persistent") ;
          bitfileid.setPosition( req.getFilePosition() ) ;
          bitfileid.setVolume( req.getVolume() ) ;
          _dataBase.storeBitfileId( group , bitfileid ) ;
       }catch( DatabaseException dbe ){
          esay( "fpr : Couldn't store bfid  : "+bfid+" : "+dbe ) ;
          req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
       }
    
    }
    public void initialGetRequest( BitfileRequest req ){
       String bfid = req.getBfid() ;
       say( "igr : Searching bfid : "+bfid ) ;
       BitfileId bitfile = null  ;
       String    group   = null ;
       try{
           group   = _dataBase.getGroupByBitfileId( bfid ) ;
           bitfile = _dataBase.getBitfileId( group , bfid ) ;
       }catch( DatabaseException dbe ){
           String error = "bfid not found : "+bfid ;
           esay( error ) ;
           req.setReturnValue( 11 , error ) ;
           return ;
       }
       say( "igr : Found bfid : "+bfid + " in group : "+group ) ;
       req.setStorageGroup( group ) ;
       req.setFileSize( bitfile.getSize() ) ;
       req.setFilePosition( bitfile.getPosition() ) ;
       req.setVolume( bitfile.getVolume() ) ;
    }
    public void finalGetRequest( BitfileRequest req ){
      try{
          BitfileId bitfile = _dataBase.getBitfileId( 
                                    req.getStorageGroup() ,
                                    req.getBfid()   ) ;
          bitfile.touch() ;
          _dataBase.storeBitfileId( req.getStorageGroup() , bitfile ) ;
      }catch( DatabaseException dbe ){
          esay( "fgr : updating bitfile failed : "+req.getBfid()+" : "+dbe ) ;
      }
    
    }
    public void initialRemoveRequest( BitfileRequest req ){
       BitfileId bitfile = null  ;
       String    group   = null ;
       String    bfid    = req.getBfid() ;
       try{
           group   = _dataBase.getGroupByBitfileId( bfid ) ;
           bitfile = _dataBase.getBitfileId( group , bfid ) ;
       }catch( DatabaseException dbe ){
           String error = "irr : bfid not found : "+bfid ;
           esay( error ) ;
           req.setReturnValue( 11 , error ) ;
           return ;
       }
       say( "irr : Found bfid : "+bfid + " in group : "+group ) ;
       req.setStorageGroup( group ) ;
       req.setFileSize( bitfile.getSize() ) ;
       req.setFilePosition( bitfile.getPosition() ) ;
       req.setVolume( bitfile.getVolume() ) ;
    }
    public void finalRemoveRequest( BitfileRequest req ){
      if( req.getReturnCode() == 0 ){
         try{
            _dataBase.removeBitfileId( req.getStorageGroup() ,
                                       req.getBfid()  ) ;
            say( "frr : bfid : "+req.getBfid() + " removed") ;
         }catch( DatabaseException dbe ){
            req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
            say( "frr : bfid : "+req.getBfid() + " failed to remove : "+dbe) ;
         }
      }else{
         say( "frr : bfid : "+req.getBfid() + 
              " not removed : "+req.getReturnMessage()) ;
      }
    
    }

    public BfRecordable  getBitfileRecord( String bfid ){
       BitfileId bitfile = null  ;
       String    group   = null ;
       try{
           group   = _dataBase.getGroupByBitfileId( bfid ) ;
           bitfile = _dataBase.getBitfileId( group , bfid ) ;
           BfRecordV1 bfr    = new BfRecordV1( bfid ) ;
           bfr._group        = group ; 
           bfr._size         = bitfile.getSize() ;
           bfr._volume       = bitfile.getVolume() ;
           bfr._filePosition = bitfile.getPosition() ;
           bfr._parameter    = bitfile.getParameter() ;
           bfr._status       = bitfile.getMode() ;
           bfr._lastDate     = bitfile.getLastAccessDate() ;
           bfr._creationDate = bitfile.getCreationDate() ;
           bfr._counter      = bitfile.getAccessCount() ;
           return bfr ;
       }catch( DatabaseException dbe ){
           throw new IllegalArgumentException( dbe.getMessage() ) ;
       }
    }
    private class ListCookieEnumeration implements CookieEnumeration {
       private Object [] _list = null ;
       private int       _p = 0 ;
       private ListCookieEnumeration( Object [] list , long cookie ){
          _p    = (int)cookie ;
          _list = new Object[list.length] ;
          System.arraycopy( list  , 0 , _list , 0 , list.length ) ;
       }
       public boolean hasMoreElements(){
          return _p < _list.length ;
       }
       public Object nextElement() throws NoSuchElementException {
          if( ! hasMoreElements() )
             throw new NoSuchElementException( "No more elements" ) ;
           return _list[_p++] ;
       }
       public long getCookie(){
           return _p ;
       }
    }
    public CookieEnumeration 
           getBfidsByVolume( String volume , long cookie ){
           
       String [] group  = null ;
       String [] bfid   = null ;
       Vector    bfids  = new Vector() ;
       BitfileId id     = null ;
       try{
          group = _dataBase.getGroups() ;
          for( int i = 0 ; i < group.length ; i++ ){
             bfid  = _dataBase.getBitfileIds( group[i] ) ;
             for( int j = 0 ; j < bfid.length ; j++ ){
                id =  _dataBase.getBitfileId( group[i] , bfid[j] ) ;
                if( id.getVolume().equals( volume ) )
                    bfids.addElement( bfid[j] ) ;
             }
          }
       }catch( DatabaseException dbe ){
           throw new IllegalArgumentException( dbe.getMessage() ) ;
       }
       say( ""+bfids.size()+" Bitfiles found on volume "+volume );
       Object [] result = new Object[bfids.size()] ;
       bfids.copyInto( result ) ;
       return new  ListCookieEnumeration( result , cookie );
    }
    public CookieEnumeration 
           getBfidsByStorageGroup( String storageGroup , long cookie){
       try{
           String [] list = _dataBase.getBitfileIds(storageGroup) ;
           return new  ListCookieEnumeration( list , cookie );
       }catch( DatabaseException dbe ){
           throw new IllegalArgumentException( dbe.getMessage() ) ;
       }
    }
    public CookieEnumeration 
           getStorageGroups(long cookie ){
           
       try{
           String [] list = _dataBase.getGroups() ;
           return new  ListCookieEnumeration( list , cookie );
       }catch( DatabaseException dbe ){
           return new  ListCookieEnumeration(new Object[0] , 0 );
       }
    }

}
