package  eurogate.db.pvl ;

import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;
import   dmg.util.cdb.* ;

import java.util.* ;
import java.io.* ;
import java.net.* ;


/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 9 Feb 1999
  */
public class      PvlDbManager 
       extends    CellAdapter    {
 
   private PvlDb  _pvlDb     = null ;
   private Args   _args      = null ;
   private boolean _isNewDatabase = false ;
         
   public PvlDbManager( String name , String args )
          throws Exception {
          
       super( name , args , false ) ;
       _args = getArgs() ;
       try{
           String     dbName = null ;
           Dictionary dict   = getDomainContext() ;
           
           if( _args.argc() > 0 ){
              dbName  = _args.argv(0) ;
           }else{
              dbName  = (String) dict.get( "databaseName" ) ;
              if( dbName == null )
                 throw new
                 IllegalArgumentException( "databaseName not defined" ) ;
                 
           }
           File dbFile = new File( dbName ) ;
           try{
              //
              // try to open the database (no create )  
              //
              _pvlDb = new PvlDb( dbFile , false ) ;
              _isNewDatabase = false ;
              say( "Using existing database" ) ;
           }catch( Exception eee ){
              //
              // if it fails , try to create the database
              //
              esay( "Try to create database" ) ;
              _pvlDb = new PvlDb( dbFile , true ) ;
              _isNewDatabase = true ;
              say( "Using new database" ) ;
           }
           dict.put( "database" , _pvlDb ) ;
       
           addCommandListener( new PvlCommander( _pvlDb ) ) ;
           
       }catch( Exception e ){
          say( "Problem in <init> : "+e ) ;
          start();
          kill() ;
          throw e  ;
       }
    
       start() ;
       
       if( ! _isNewDatabase )return ;
       String auto = null ;
       try{
           auto = _args.getOpt("autoinstall") ;
           if( ( auto != null ) && ( auto.length() > 0 ) ){
               say( "Running autoinstall : "+auto ) ;
               executeDomainContext( auto ) ;
           }
       }catch( Exception ee ){
           esay( "Problem executing : "+auto+" : "+ee ) ;
       }
       
   }
    
}
