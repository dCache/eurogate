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
        private File      _map       = null ,
                          _volumeMap = null ;
        private Hashtable _idHash    = new Hashtable() ;
        private Hashtable _groupHash = new Hashtable() ;
        private Hashtable _volumeHash = new Hashtable() ;
        private String    _mapName    = "EuroStore" ;
        private String    _volumeMapName = "Volumes" ;
        private File      _groupMap   = null ;
        private GroupMap( File groupMap ) throws IOException {
         
            _map       = new File( _groupMap = groupMap , _mapName ) ;
            _volumeMap = new File( _groupMap   , _volumeMapName ) ;

            if( ! _map.exists() ){ _createInitial() ; }

            _readMap() ;
            _readVolumeMap() ;
        
        }
        private Enumeration groups(){ return _groupHash.keys() ; }
        private void _writeVolumeMap() throws IOException {
            File tmp = new File( _groupMap ,  "."+_volumeMapName ) ;
            PrintWriter pw = new PrintWriter( new FileWriter(tmp ) );
            Enumeration e  = _volumeHash.keys() ;
            String  volume = null ;
            String  group  = null ;   
            try{
               while( e.hasMoreElements() ){
                   volume = (String)e.nextElement() ;
                   group  = (String)_volumeHash.get( volume ) ;
                   pw.println( volume+"="+group ) ;
               }
            }finally{
                try{ pw.close() ; }catch(Exception ee ){} 
                tmp.renameTo( _volumeMap ) ;
            }
        }
        private void _readVolumeMap() throws IOException {
            BufferedReader br = new BufferedReader( 
                                     new FileReader( 
                                          _volumeMap ) ) ;
            try{
            
               String line = null , group =null , volume = null ;
               StringTokenizer st = null ;
               while( ( line = br.readLine() ) != null ){
                  st = new StringTokenizer( line , "=" ) ;
                  try{
                     volume = st.nextToken() ;
                     group  = st.nextToken() ;
                  }catch(Exception ii ){ 
                     continue ; 
                  }
                  _volumeHash.put( volume , group  ) ;
               } 
            }finally{
               try{ br.close() ;  }catch(Exception eee){}
            }
        }
        private void addVolume( String group , String volume )
                throws IOException {
            String g = (String)_volumeHash.get(volume) ;
            if( ( g == null ) || ( ! g.equals(group) ) ){
                _volumeHash.put( volume , group ) ;
                _writeVolumeMap() ;
            }
            return ;
        }
        private String getGroupByVolume( String volume ){
            return (String)_volumeHash.get(volume) ;
        }
        private void _writeMap() throws IOException {
            File tmp = new File( _groupMap ,  "."+_mapName ) ;
            PrintWriter pw = new PrintWriter( new FileWriter(tmp ) );
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
                tmp.renameTo( _map ) ;
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
            pw = new PrintWriter( new FileWriter( _volumeMap ) ) ;
            try{ pw.close() ; }catch(Exception ee ){} 
            
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
        public String getName( Integer id ){
            return (String)_idHash.get(id) ;
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
       private int _groupId  = 0 ;
       private int _storeId  = 0 ;
       private long _bfid    = 0 ;
       private long _extend  = 0 ; 
       private BfidString( String id ){
          StringTokenizer st = new StringTokenizer( id , "." ) ;
          _storeId = Integer.parseInt(st.nextToken()) ;
          _groupId = Integer.parseInt(st.nextToken()) ;
          _bfid    = Long.parseLong(st.nextToken()) ;
          _extend  = Long.parseLong(st.nextToken()) ;
       }
       private BfidString( int storeId , int groupId , long bfid ){
          _storeId = storeId ;
          _groupId = groupId ;
          _bfid    = bfid ;
          _extend  = System.currentTimeMillis() ;
       }
       private int  getGroupId(){ return _groupId ; }
       private long getBfid(){ return _bfid ; }
       private int  getStoreId(){ return _storeId ; }
       public String toString(){
           return _storeId+"."+_groupId+"."+_bfid+"."+_extend ;
       }
    }
    public StorageSessionable [] getStorageSessionable() {
       //
       // we don't need session , so just tell them.
       //
       return null ;
    }
    private static final int __bytesPerRecord  = 256 ;
    private static final int __recordsPerBlock = 1024 ;
    
    public void initialPutRequest( StorageSessionable session ,
                                   BitfileRequest req ){
        Sldb   sldb  = null ;
        long start = System.currentTimeMillis() ;
        try{                        
           String group = req.getStorageGroup() ;
           int    id    = _groupMap.getId( group ) ;
           say( "initialPutRequest : "+group+"("+id+")" ) ;
           if( id < 0 ){
              
              id = _groupMap.newId( group ) ;
              say( "initialPutRequest : new id for "+group+" : "+id ) ;
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
           
           BfidRecord bitfile = new BfidRecord( group , bfid , size )  ;
           bitfile.setParameter( bitfile.getParameter() ) ;
           byte [] data = bitfile.getByteArray(__bytesPerRecord) ;
           sldb.put( entry , data , 0 , data.length ) ;
       }catch(Exception ee ){
           String error = "Database error : "+ee ;
           esay( error ) ;
           req.setReturnValue( 11 , error ) ;
       }finally{
           try{ if( sldb!=null )sldb.close() ; }catch(Exception ee ){}
       }
       say( "TIMING : initialPutRequest : "+(System.currentTimeMillis()-start));
       return ;
    }
    public void finalPutRequest( StorageSessionable session ,
                                 BitfileRequest req ){
       String group = req.getStorageGroup() ;
       BfidString bfidString = null ;
       long start = System.currentTimeMillis() ;
       try{
          bfidString = new BfidString( req.getBfid() ) ;
          int groupId = bfidString.getGroupId() ;
          long bfid   = bfidString.getBfid() ;
          
          if( req.getReturnCode() != 0 ){
             say( "fpr : removing bfid from database : "+
                  bfid+" : "+req.getReturnMessage() ) ;
             try{
               String groupName = _groupMap.getName(new Integer(groupId));
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
             say( "TIMING : finalPutRequest : "+(System.currentTimeMillis()-start));
             return ;
          }
          //
          // update the database entry
          //
          try{
             say( "fpr : storing bfid  : "+ bfid ) ;
             String groupName = _groupMap.getName(new Integer(groupId));
             Sldb sldb = new Sldb( new File( _dataDir , groupName ) ) ;
             try{
                SldbEntry entry = sldb.getEntry(bfid) ;
                byte []   data  = sldb.get(entry) ;
                BfidRecord bitfileid = new BfidRecord( groupName , data ) ;
                bitfileid.setMode("p") ;
                bitfileid.setPosition( req.getFilePosition() ) ;
                String  volume = req.getVolume() ;
                _groupMap.addVolume( groupName , volume ) ;
                bitfileid.setVolume( volume ) ;
                data = bitfileid.getByteArray(__bytesPerRecord) ;
                sldb.put(entry,data,0,data.length) ;
             }finally{
                try{ sldb.close() ; }catch(Exception ee){}
             }
          }catch( Exception dbe ){
             esay( "fpr : Couldn't store bfid  : "+bfid+" : "+dbe ) ;
//             req.setReturnValue( dbe.getCode() , dbe.getMessage() ) ;
          }
       }catch(Exception iie){
          esay( "Exception in initialPutRequest : "+iie ) ;       
       }
       say( "TIMING : finalPutRequest : "+(System.currentTimeMillis()-start));
    }
    public void initialGetRequest( StorageSessionable session ,
                                   BitfileRequest req ){
       
       BfidString bfidString = null ;
       long start = System.currentTimeMillis() ;
       try{
          say( "igr : searching bfid  : "+ req.getBfid() ) ;
          bfidString   = new BfidString( req.getBfid() ) ;
          int  groupId = bfidString.getGroupId() ;
          long bfid    = bfidString.getBfid() ;
          String groupName = _groupMap.getName(new Integer(groupId));
          Sldb   sldb      = new Sldb( new File( _dataDir , groupName ) ) ;
          try{
             SldbEntry entry = sldb.getEntry(bfid) ;
             byte []   data  = sldb.get(entry) ;
             BfidRecord bitfileid = new BfidRecord( groupName , data ) ;
             if( !bitfileid.getBfid().equals(req.getBfid()) )
                 throw new
                 IllegalArgumentException("Bfid not found" );
             req.setStorageGroup( groupName ) ;
             req.setFileSize( bitfileid.getFileSize() ) ;
             req.setFilePosition( bitfileid.getFilePosition() ) ;
             req.setVolume( bitfileid.getVolume() ) ;
             say( "igr : bfid : "+bfidString+ " in group : "+groupName ) ;
          }catch(Exception e){
             esay( "igr : problem finding bfid  : "+bfidString+" : "+e ) ;
             req.setReturnValue( 33 , e.getMessage() ) ;
          }finally{
             try{ sldb.close() ; }catch(Exception ee){}
          }
       }catch( Exception dbe ){
          esay( "igr : Couldn't update bfid  : "+req.getBfid()+" : "+dbe ) ;
          req.setReturnValue( 34 , dbe.getMessage() ) ;
       }
       say( "TIMING : initialGetRequest : "+(System.currentTimeMillis()-start));
       return ;
    }
    public void finalGetRequest( StorageSessionable session ,
                                 BitfileRequest req ){
       BfidString bfidString = null ;
       long start = System.currentTimeMillis() ;
       try{
          say( "fgr : searching bfid  : "+ req.getBfid() ) ;
          bfidString   = new BfidString( req.getBfid() ) ;
          int  groupId = bfidString.getGroupId() ;
          long bfid    = bfidString.getBfid() ;
          String groupName = _groupMap.getName(new Integer(groupId));
          Sldb   sldb      = new Sldb( new File( _dataDir , groupName ) ) ;
          try{
             SldbEntry entry = sldb.getEntry(bfid) ;
             byte []   data  = sldb.get(entry) ;
             BfidRecord bitfileid = new BfidRecord( groupName , data ) ;
             bitfileid.touch() ;
             data = bitfileid.getByteArray(__bytesPerRecord) ;
             sldb.put(entry,data,0,data.length) ;
             say( "fgr : bfid : "+bfidString+ " in group : "+groupName ) ;
          }catch(Exception e){
             esay( "fgr : problem finding bfid  : "+bfidString+" : "+e ) ;
             req.setReturnValue( 33 , e.getMessage() ) ;
          }finally{
             try{ sldb.close() ; }catch(Exception ee){}
          }
       }catch( Exception dbe ){
          esay( "fgr : Couldn't update bfid  : "+req.getBfid()+" : "+dbe ) ;
          req.setReturnValue( 34 , dbe.getMessage() ) ;
       }
       say( "TIMING : finalGetRequest : "+(System.currentTimeMillis()-start));
       return ;
       
       
       
    }
    public void initialRemoveRequest( StorageSessionable session ,
                                      BitfileRequest req ){
        say( "irr : removing bfid from database : "+
              req.getBfid()+" : "+req.getReturnMessage() ) ;
        initialGetRequest( session , req ) ;
        return ;
    }
    public void finalRemoveRequest( StorageSessionable session ,
                                    BitfileRequest req ){
       String group = req.getStorageGroup() ;
       BfidString bfidString = null ;
       long start = System.currentTimeMillis() ;
         say( "frr : removing bfid from database : "+
              req.getBfid()+" : "+req.getReturnMessage() ) ;
       try{
           bfidString  = new BfidString( req.getBfid() ) ;
           int groupId = bfidString.getGroupId() ;
           long bfid   = bfidString.getBfid() ;
           String groupName = _groupMap.getName(new Integer(groupId));
           Sldb sldb = new Sldb( new File( _dataDir , groupName ) ) ;
           try{
              sldb.remove( sldb.getEntry( bfid ) ) ;
           }finally{
              sldb.close() ;
           }
       }catch( Exception dbe ){
            esay( "frr : Couldn't remove bfid from database : "+
                  req.getBfid()+" : "+dbe ) ;
       }            
       say( "TIMING : finalRemoveRequest : "+(System.currentTimeMillis()-start));
       return ;
     }
    public BfRecordable getBitfileRecord( StorageSessionable session ,
                                          String bfidIn ){
       BfidString bfidString = null ;
       try{
          say( "getBitfileRecord : searching bfid  : "+ bfidIn ) ;
          bfidString   = new BfidString( bfidIn ) ;
          int  groupId = bfidString.getGroupId() ;
          long bfid    = bfidString.getBfid() ;
          String groupName = _groupMap.getName(new Integer(groupId));
          Sldb   sldb      = new Sldb( new File( _dataDir , groupName ) ) ;
          try{
             SldbEntry entry = sldb.getEntry(bfid) ;
             byte []   data  = sldb.get(entry) ;
             BfidRecord bitfileid = new BfidRecord( groupName , data ) ;
             if( !bitfileid.getBfid().equals(bfidIn) )
                 throw new
                 IllegalArgumentException("Bfid not found" );
             say( "igr : bfid : "+bfidString+ " in group : "+groupName ) ;
             return bitfileid ;
          }catch(Exception e){
             esay( "igr : problem finding bfid  : "+bfidString+" : "+e ) ;
             return null  ;
          }finally{
             try{ sldb.close() ; }catch(Exception ee){}
          }
       }catch( Exception dbe ){
          esay( "igr : Couldn't update bfid  : "+bfidIn+" : "+dbe ) ;
          return null ;
       }
    }
    private class ListCookieEnumeration implements CookieEnumeration {
       private Object [] _list = null ;
       private int       _p = 0 ;
       private long   [] _cookies = null ;
       private ListCookieEnumeration( Object [] list , long cookie ){
          _p    = (int)cookie ;
          _list = new Object[list.length] ;
          System.arraycopy( list  , 0 , _list , 0 , list.length ) ;
       }
       private ListCookieEnumeration( Object [] list , long [] cookies ){
          _cookies  = cookies ;
          _list     = new Object[list.length] ;
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
           return _cookies == null ? _p : _cookies[_p] ;
       }
    }
    private class VectorCookieEnumeration implements CookieEnumeration {
       private Vector    _list = new Vector() ;
       private int       _p = 0 ;
       private long      _thisCookie = 0L ;
       private VectorCookieEnumeration( ){}
       private void add( Object obj  , long cookie ){
            Object [] pair = new Object[2] ;
            pair[0] = obj ;
            pair[1] = new Long(cookie) ;
            _list.addElement( pair ) ;
   
       }
       public boolean hasMoreElements(){
          return _p < _list.size() ;
       }
       public Object nextElement() throws NoSuchElementException {
          if( ! hasMoreElements() )
              throw new NoSuchElementException( "No more elements" ) ;
              
          Object [] pair = (Object [])_list.elementAt(_p++) ;
          _thisCookie = ((Long)pair[1]).longValue() ;
          return pair[0] ;
       }
       public long getCookie(){ return _thisCookie ;}
    }
    public CookieEnumeration getBfidsByVolume( StorageSessionable session ,
                                               String volume , long cookie ){
                                               
         String storageGroup = _groupMap.getGroupByVolume(volume) ;
         if( ( storageGroup == null                ) ||
             ( _groupMap.getId( storageGroup ) < 0 )    )
             throw new
             IllegalArgumentException( "Volume not found : "+volume ) ;
          
         try{
             Sldb sldb = new Sldb( new File( _dataDir , storageGroup ) ) ;
             VectorCookieEnumeration e = new VectorCookieEnumeration() ;
             try{
                 SldbEntry entry = sldb.getEntry(cookie) ;
                 int counter = 0 ;
                 Vector dataV = new Vector() ;
                 Vector cookieV = new Vector() ;
                 for( counter = 0 , entry = sldb.nextUsedEntry(entry) ;
                      ( entry != null ) && ( counter < 20 ) ;
                      entry = sldb.nextUsedEntry(entry) ){

                     byte []    data      = sldb.get(entry) ;
                     BfidRecord bitfileid = new BfidRecord( storageGroup , data ) ;
                     if( ! bitfileid.getVolume().equals(volume) )continue ;
                     e.add( bitfileid.getBfid() , entry.getCookie() ) ;
                     counter++ ;
                 }
                 return e ;
             }finally{
               try{ sldb.close() ; }catch(Exception ee ){}
             } 
         }catch(Exception ee ){
             throw new
             IllegalArgumentException( "Group error : "+ee ) ;
         }
    }
    public CookieEnumeration getBfidsByStorageGroup(
                                 StorageSessionable session ,
                                 String storageGroup , long cookie) {
         if( _groupMap.getId( storageGroup ) < 0 )
             throw new
             IllegalArgumentException( "Group not found : "+storageGroup ) ;
          
         try{
             Sldb sldb = new Sldb( new File( _dataDir , storageGroup ) ) ;
             VectorCookieEnumeration e = new VectorCookieEnumeration() ;
             try{
                 SldbEntry entry = sldb.getEntry(cookie) ;
                 int counter = 0 ;
                 Vector dataV = new Vector() ;
                 Vector cookieV = new Vector() ;
                 for( counter = 0 , entry = sldb.nextUsedEntry(entry) ;
                      ( entry != null ) && ( counter < 20 ) ;
                      counter ++  , entry = sldb.nextUsedEntry(entry) ){

                     byte []    data      = sldb.get(entry) ;
                     BfidRecord bitfileid = new BfidRecord( storageGroup , data ) ;
                     e.add( bitfileid.getBfid() , entry.getCookie() ) ;
                 }
                 return e ;
             }finally{
               try{ sldb.close() ; }catch(Exception ee ){}
             } 
         }catch(Exception ee ){
             throw new
             IllegalArgumentException( "Group error : "+ee ) ;
         }
    }
    public CookieEnumeration getStorageGroups(
                                 StorageSessionable session , 
                                 long cookie ){
    
        if( cookie != 0L )
           return new 
           ListCookieEnumeration(new String[0],cookie);
        Enumeration e = _groupMap.groups() ;
        Vector v = new Vector() ;
        while( e.hasMoreElements() ){
           String group = e.nextElement().toString() ;
           if( ! group.equals("$next$")  )v.addElement( group ) ;
        }
        String [] vec = new String[v.size()] ;
        v.copyInto(vec) ;
        return new ListCookieEnumeration(vec,0) ; 
    }

    public void close(){
    
    }
    public static void main( String [] args ) throws Exception {
       if( args.length < 1 ){
          System.err.println( "Usage : ... <groupDbName> [<bfid(bfPart)>]" ) ;
          System.exit(4);
       }
       File sldbFile = new File( args[0] ) ;
       if( ! sldbFile.exists() ){
          System.err.println( "File not found : "+sldbFile ) ;
          System.exit(4);
       }
       byte [] data = null ;
       Sldb sldb = new Sldb( sldbFile ) ;
       if( args.length > 1 ){
          SldbEntry entry = sldb.getEntry(Long.parseLong(args[1]))  ;
          System.out.println( "Entry("+entry+")" ) ;
          data = sldb.get( entry ) ;
          BfidRecord bfid = new BfidRecord("Unknown",data) ;
          System.out.println( bfid.toLongString() ) ;
       }else{
          SldbEntry entry = sldb.getEntry(0L)  ;
          for( entry = sldb.nextUsedEntry(entry) ; 
               entry!= null ; 
               entry = sldb.nextUsedEntry( entry ) ){
               
              System.out.print( "Entry("+entry+")=" ) ;
              data = sldb.get( entry ) ;
              BfidRecord bfid = new BfidRecord("Unknown",data) ;
              System.out.println( bfid.toString() ) ;
          }
       }
       sldb.close() ;
    }
}
