package eurogate.vehicles ;

import java.util.* ;
import eurogate.pnfs.* ;
import java.io.* ;

public class EurogateInfo implements HsmInfo {
   private PnfsFile  _pnfs  = null ;
   private Hashtable _hash  = new Hashtable() ;
   private String    _store = null ;
   private String    _group = null ;
   private String    _bfid  = null ;
   private String    _class = null ;
   private String    _info  = null ;
   public EurogateInfo( PnfsFile file ) throws IOException{
      _pnfs = file ;
      if( ! _pnfs.exists() )
         throw new 
         IllegalArgumentException( "Doesn't exist" ) ;
      if( ! _pnfs.isPnfs() )
         throw new 
         IllegalArgumentException( "Not a pnfs object" ) ;
      if( _pnfs.isDirectory() ){
      
          String [] temp = null ;
          String [] group = null ;
          if( ( ( temp  = _pnfs.getTag( "OSMTemplate" ) ) == null ) ||
              (   temp.length < 6 ) ||
              ( ( group = _pnfs.getTag( "sGroup"      ) ) == null ) ||
              (   group.length < 1 )  )
             throw new 
             IllegalArgumentException( 
             "Not a valid Eurogate direcotry" ) ;
          StringTokenizer st = null ;
          for( int i = 0 ; i < temp.length ; i++ ){
             st = new StringTokenizer( temp[i] ) ;
             try{
                _hash.put( st.nextToken() , st.nextToken() ) ;
             }catch(Exception e){}
          } 
          _store = (String)_hash.get( "StoreName" ) ;
          if( (_store == null ) || ( _store.length() < 1 ) ) 
             throw new 
             IllegalArgumentException( 
             "Not a valid Eurogate directory (StoreName missing)" ) ;
          _group = group[0] ;
          if( _group.length() < 1 )  
             throw new 
             IllegalArgumentException( 
             "Not a valid Eurogate direcotry (Invalid sGroup tag)" ) ;
          
          _info  = "eg:dir:"+_store+":"+_group ;
          _class = _store+":"+_group ;
      }else{
          File f = _pnfs.getLevelFile(1) ;
          
          BufferedReader br = null ;
          String line = null ;
          try{
             br = new BufferedReader( new FileReader( f ) ) ;
             line = br.readLine() ;
          }finally{
             try{ br.close() ; }catch(Exception e){};
          }
          if( line == null )
            throw new 
            IllegalArgumentException( "Not a leagal Eurogate file" ) ;
          StringTokenizer st = new StringTokenizer( line ) ;
          try{
             _store = st.nextToken() ;
             _group = st.nextToken() ;
             _bfid  = st.nextToken() ;
          }catch( Exception e ){
             throw new 
             IllegalArgumentException( "Not a leagal Eurogate file(2)" ) ;
          }
          _info  = "eg:file:"+_store+":"+_group+":"+_bfid ;
          _class = _store+":"+_group ;
      }
   }
   public String getStorageClass(){ return _class ; }
   public String getHsmInfo(){ return _group ;}
   public static void main( String [] args ){
      if( args.length < 1 ){
         System.err.println( "Usage : ... <pnfFile>|<pnfsDir>" ) ;
         System.exit(4);
      }
      try{
         EurogateInfo ei = new EurogateInfo( new PnfsFile( args[0] ) ) ;
         System.out.println( "Class : "+ei.getClass() ) ;
         System.out.println( "Info  : "+ei.getHsmInfo() ) ;
      }catch(IllegalArgumentException  ie ){
         System.err.println( "Problem : "+ie.getMessage() ) ;
         System.exit(5);
      }catch(Exception e ){
      
         System.err.println( "Problem : "+e ) ;
         e.printStackTrace();
         System.exit(5);
      }
   
   }

}
