package eurogate.store ;

import dmg.util.db.* ;

import java.util.* ;
import java.io.* ;

public class BfidHandler {

   private  Hashtable _handlerHash = new Hashtable() ;
   private  File      _dataSource  = null ;
   
   public BfidHandler( File dataSource ){
   
       if( ! dataSource.isDirectory() )
         throw new 
         IllegalArgumentException( "Not a directory : "+dataSource ) ;
         
       _dataSource = dataSource ;      
   
   }
   public String toString(){
      StringBuffer sb = new StringBuffer() ;
      sb.append( "Cache sizes\n" ) ;
      Enumeration e = _handlerHash.keys() ;
      for( ; e.hasMoreElements() ; ){
         String name = (String)e.nextElement() ;
         DbResourceHandler handler = 
             (DbResourceHandler)_handlerHash.get( name ) ;
         sb.append( "  "+name+" -> "+handler.getCacheSize()+"\n" );
      }
      return sb.toString() ;
   }
   public BfidHandle createBfid( String bfidName )
           throws DbException, InterruptedException  {
       
       String [] bfid = splitBfidName( bfidName ) ;
       if( bfid.length < 3 )
         throw new 
         IllegalArgumentException( "Not a valid bfidname : "+bfidName ) ;
       
       DbResourceHandler handler = updateCache( bfid ) ;

       DbResourceHandle handle = handler.createResource( bfidName ) ;
       
       handle.open( DbGLock.WRITE_LOCK ) ;
       handle.setAttribute( "status" , "<init>" ) ;
       handle.setAttribute( "creationTime" , new Date().toString() ) ;
       handle.setAttribute( "size" , "0" ) ;
       handle.close() ;
       
       return new BfidHandle( handle , bfidName , bfid[1] ) ;
   }
   public BfidHandle getBfidByName( String bfidName )
           throws DbException, InterruptedException   {   
           
       String [] bfid = splitBfidName( bfidName ) ;
       if( bfid.length < 3 )
         throw new 
         IllegalArgumentException( "Not a valid bfidname : "+bfidName ) ;
         
       DbResourceHandler handler = updateCache( bfid ) ;

       DbResourceHandle handle = handler.getResourceByName( bfidName ) ;
       
       return new BfidHandle( handle , bfidName , bfid[1] ) ;
   }
   public String [] getBfidNames(){
      //
      // this is a very very nasty method ( who cares ? ) ;
      //
      String [] subNames = _dataSource.list() ;
      Vector    all      = new Vector() ;
      File      subDir   = null ;
      String [] bfids    = null ;
      int       total    = 0 ;
      
      for( int i = 0 ; i < subNames.length ; i++ ){
          subDir = new File( _dataSource , subNames[i] ) ;
          if( ! subDir.isDirectory() )continue ;
          all.addElement( bfids = subDir.list() ) ;
          total += bfids.length ; 
      }
      String [] longList = new String[total] ;
      int position = 0 ;
      for( int i = 0 ; i < all.size() ; i++ ){
         Object [] source = (Object[]) all.elementAt(i) ;
         
         System.arraycopy( source   , 0 ,
                           longList , position ,
                           source.length ) ;
         position += source.length ;
      } 
      
      return longList ;                   
   }
   public void removeBfid( BfidHandle handle )
           throws DbLockException , InterruptedException {
    
               
   }
   private DbResourceHandler updateCache( String [] bfid )
           throws DbException {
   
       //
       // is already in cache ?
       //
       DbResourceHandler handler =
            (DbResourceHandler)_handlerHash.get( bfid[1] ) ;
            
       if( handler == null ){
          //
          // check for the subdirectory
          //
          File dir = new File( _dataSource , bfid[1] ) ;
          if( ( ! dir.isDirectory() ) &&
              ( ! dir.mkdir()       )    )

               throw new DbException( "Couldn't create : "+dir ) ;

          handler = new DbResourceHandler( dir , false ) ;
          
          _handlerHash.put( bfid[1] , handler ) ;
       }
       
       return handler ;
   }
   private static String [] splitBfidName( String bfidName ){
      StringTokenizer st = new StringTokenizer( bfidName , "." ) ;
      int count = st.countTokens() ;
      String [] tokens = new String[count] ;
      for( int i = 0 ; i < count ; i++ )
         tokens[i] = st.nextToken() ;
         
      return tokens ;
   }

}
