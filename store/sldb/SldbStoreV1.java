package eurogate.store.sldb ;

import java.io.* ;
import java.util.* ;

import dmg.util.* ;
import dmg.util.edb.* ;

import eurogate.store.* ;
import eurogate.vehicles.* ;
import eurogate.misc.CookieEnumeration ;

public class SldbStoreV1 implements  EuroStoreable {

    private Logable    _log      = null ;
    private Dictionary _env      = null ;
    private Args       _args     = null ;
    private File       _dataDir  = null ;
    private GroupMap   _groupMap = null ;
    
    private void say( String msg ){ _log.log( msg ) ; }
    private void esay( String msg ){ _log.elog( msg ) ; }
    private class GroupMap {
        private File      _map       = null ;
        private Hashtable _idHash    = new Hashtable() ;
        private Hashtable _groupHash = new Hashtable() ;
        
        private GroupMap( File groupMap ) throws IOException {
        
           _map = new File( groupMap , "EuroStore" ) ;
           
           if( ! _map.exists() ){ _createInitial() ; }
           
           _readMap() ;
        
        }
        private void _writeMap() throws IOException {
            PrintWriter pw = new PrintWriter( new FileWriter( _map ) ) ;
            Enumeration e  = _groupHash.keys() ;
            String  group = null ;
            Integer id    = null ;   
            try{
               while( e.hasMoreElements() ){
                   group = (String)e.nextElement() ;
                   id    = (Integer)_groupHash.get( group ) ;
                   pw.println( group+"="+id ) ;
               }
            }finally{
                try{ pw.close() ; }catch(Exception ee ){} 
            }
        }
        private void _readMap() throws IOException {
            BufferedReader br = new BufferedReader( new FileReader( _map ) ) ;
            try{
            
               String line = null , group =null ;
               StringTokenizer st = null ;
               Integer id = null  ;
               while( ( line = br.readLine() ) != null ){
                  st = new StringTokenizer( line , "=" ) ;
                  try{
                     group = st.nextToken() ;
                     id    = new Integer( st.nextToken() ) ;
                  }catch(Exception ii ){ 
                     continue ; 
                  }
                  _groupHash.put( group , id ) ;
                  _idHash.put( id , group ) ; 
               } 
            }finally{
               try{ br.close() ;  }catch(Exception eee){}
            }
        }
        private void _createInitial() throws IOException {
            PrintWriter pw = new PrintWriter( new FileWriter( _map ) ) ;
            try{
                pw.println( "$next$=0" ) ;
            }finally{
                try{ pw.close() ; }catch(Exception ee ){} 
            }
        }
        public int newId( String group ) throws IOException {
           if( _groupHash.get( group ) != null )
              throw new 
              IllegalArgumentException( "Group exists : "+group ) ;
              
           Integer id = (Integer)_groupHash.get("$next$") ;
           _idHash.remove( id ) ;
           
           _groupHash.put( group , id ) ;
           _idHash.put( id , group ) ;
           
           int i = id.intValue() ;
           id = new Integer( i+1 ) ;
           _idHash.put( id , "$next$" ) ;
           _groupHash.put( "$next$" , id ) ;
           _writeMap() ;
           return i ;
        }
        public int getId( String group ){
           Integer id = (Integer)_groupHash.get( group ) ;
           if( id == null )return -1  ;
           return id.intValue() ;
        }
    
    }
    public SldbStoreV1( Args args , Dictionary env , Logable log )
           throws Exception {
       _args = args ;
       _env  = env ;
       _log  = log ;
       
       if( _args.argc() < 1 )
         throw new 
         IllegalArgumentException( 
             "SldbStoreV1 : database name missing" ) ; 
       
       if( ! ( _dataDir = new File( _args.argv(0)  ) ).exists() )
         throw new 
         IllegalArgumentException( 
            "SldbStoreV1 : dir "+_dataDir+"doesn't exist" ) ; 
       
       _groupMap = new GroupMap( _dataDir ) ;
       
    }
    private class BfidString {
       private int _groupId = 0 ;
       private int _storeId = 0 ;
       private long _bfid    = 0 ;
       private BfidString( String id ){
          StringTokenizer st = new StringTokenizer( id , "." ) ;
          _storeId = Integer.parseInt(st.nextToken()) ;
          _groupId = Integer.parseInt(st.nextToken()) ;
          _bfid    = Long.parseLong(st.nextToken()) ;
       }
       private BfidString( int storeId , int groupId , long bfid ){
          _storeId = storeId ;
          _groupId = groupId ;
          _bfid    = bfid ;
       }
       private int  getGroupId(){ return _groupId ; }
       private long getBfid(){ return _bfid ; }
       private ing  getStoreId(){ return _storeId ; }
       public String toString(){
           return _storeId+"."+_groupId+"."+_bfid ;
       }
    }
    public StorageSessionable [] getStorageSessionable() {
       //
       // we don't need session , so just tell them.
       //
       return null ;
    }
    private static final int __bytesPerRecord  = 1024 ;
    private static final int __recordsPerBlock = 1024 ;
    
    public void initialPutRequest( StorageSessionable session ,
                                   BitfileRequest req ){
        try{                        
           String group = req.getStorageGroup() ;
           Sldb   sldb  = null ;
           int    id    = _groupMap.getId( group ) ;
           if( id < 0 ){
              id = _groupMap.newId( group ) ;
              sldb = new Sldb( 
                           new File( _dataDir , group ) , 
                           __bytesPerRecord , 
                           __recordsPerBlock ) ;
           }else{
              sldb = new Sldb( new File( _dataDir , group ) ) ;
           }
           SldbEntry entry = sldb.getEntry() ;           
           String    bfid  = new BfidString( 3434 ,
                                            id,
                                            entry.getCookie()
                                           ).toString() ;
           long      size  = req.getFileSize() ;
           req.setBfid( bfid ) ;
           say( "ipr : bfid="+bfid+";group="+group+";size="+size ) ;
           
           BfidRecord bitfile = new BfidRecord( bfid , size )  ;
           bitfile.setParameter( bitfile.getParameter() ) ;
           byte [] data = bitfile.getByteArray() ;
           sldb.put( entry , data , 0 , data.length ) ;
       }catch(Exception ee ){
           String error = "Database error : "+ee ;
           esay( error ) ;
           req.setReturnValue( 11 , error ) ;
       }

        return ;
    }
    public void finalPutRequest( StorageSessionable session ,
                                 BitfileRequest req ){
       String group = req.getStorageGroup() ;
       BfidString bfidString = null ;
       try{
          bfidString = new BfidString( req.getBfid() ) ;
          int groupId = bfidString.getGroupId() ;
          long bfid   = bfidString.getBfid() ;
          
          if( req.getReturnCode() != 0 ){
             say( "fpr : removing bfid from database : "+
                  bfid+" : "+req.getReturnMessage() ) ;
            try{
               String groupName = _idHash.get(new Integer(groupId));
               Sldb sldb = new Sldb( new File( _dataDir , groupName ) ) ;
               try{
                  sldb.remove( sldb.getEntry( bfid ) ) ;
               }finally{
                  sldb.close() ;
               }
            }catch( Exception dbe ){
                esay( "fpr : Couldn't remove bfid from database : "+
                      bfid+" : "+dbe ) ;
            }            
             return ;
          }
          //
          // update the database entry
          //
          try{
             say( "fpr : storing bfid  : "+ bfid ) ;
             BfidRecord bitfileid =  null ; // _dataBase.getBitfileId( group , bfid ) ;
             bitfileid.setMode("persistent") ;
             bitfileid.setPosition( req.getFilePosition() ) ;
             bitfileid.setVolume( req.getVolume() ) ;
//             _dataBase.storeBitfileId( group , bitfileid ) ;
          }catch( Exception dbe ){
             esay( "fpr : Couldn't store bfid  : "+bfid+" : "+dbe ) ;
//             req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
          }
       }catch(Exception iie){
       
       }
    }
    public void initialGetRequest( StorageSessionable session ,
                                   BitfileRequest bfreq ){
        return ;
    }
    public void initialRemoveRequest( StorageSessionable session ,
                                      BitfileRequest bfreq ){
        return ;
    }
    public void finalGetRequest( StorageSessionable session ,
                                 BitfileRequest bfreq ){
         return ;
    }
    public void finalRemoveRequest( StorageSessionable session ,
                                    BitfileRequest bfreq ){
         return ;
    }
    public BfRecordable getBitfileRecord( StorageSessionable session ,
                                          String bfid ){
         return null ;
    }
    public CookieEnumeration getBfidsByVolume( StorageSessionable session ,
                                               String volume , long cookie ){
         return null ;
    }
    public CookieEnumeration getBfidsByStorageGroup(
                                 StorageSessionable session ,
                                 String storageGroup , long cookie) {
         return null ;
    }
    public CookieEnumeration getStorageGroups(
                                 StorageSessionable session , 
                                 long cookie ){
    
        return null ;
    }

    public void close(){
    
    }
}
