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
 
   private PvlDb          _pvlDb     = null ;
 
   public PvlDbManager( String name , String args )
          throws Exception {
          
       super( name , args , false ) ;
    
       setPrintoutLevel( 0xff ) ;
       try{
           Dictionary dict = getDomainContext() ;
           String dbName   = (String) dict.get( "databaseName" ) ;
           if( dbName == null )
              throw new
              IllegalArgumentException( "databaseName not defined" ) ;
           File dbFile = new File( dbName ) ;
           try{
              //
              // try to open the database (no create )  
              //
              _pvlDb = new PvlDb( dbFile , false ) ;
              
           }catch( Exception eee ){
              //
              // if it fails , try to create the database
              //
              esay( "Try to create database" ) ;
              _pvlDb = new PvlDb( dbFile , true ) ;
           }
           dict.put( "database" , _pvlDb ) ;
       
       }catch( Exception e ){
          say( "Problem in <init> : "+e ) ;
          start();
          kill() ;
          throw e  ;
       }
    
       start() ;
   }
    
}
