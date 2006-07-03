package  eurogate.pvr ;

import   eurogate.db.pvr.* ;

import java.util.*;
import java.io.* ;

import dmg.cells.nucleus.*; 
import dmg.util.*;
import dmg.util.cdb.* ;

import  diskCacheV111.util.* ;

import eurogate.vehicles.* ;
/**
 **
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 20 June 2007
  * 
 */
 public class EasyStackerExec extends EasyStackerAdapter  {
    private CellAdapter  _cell = null ;
    private String       _exec = null ;
    private Args         _args = null ;
    public EasyStackerExec( String ourName , CellAdapter cellAdapter ){
        super( ourName , cellAdapter );
        _cell = cellAdapter ;
        _args = _cell.getArgs() ;
        String execString = _args.getOpt("exec") ;
        try{
           if( ( execString == null ) || ( execString.equals("") ) )
             throw new
             IllegalArgumentException("-exec option not specified");

           if( ! new File(execString).exists() )
             throw new
             IllegalArgumentException("File : "+execString+" doesn't exist" );
        }catch(IllegalArgumentException e ){
           esay("EasyStackerExec reported : "+e);
           throw e;
        }  
        _exec = execString ;
    }
    private String createCommandLine( String command , 
              String driveName , String driveLocation , 
              String cartridgeName , String cartridgeLocation ){
                               
        return _exec+" "+
               command+" "+
               driveName+" "+driveLocation+ " "+
               cartridgeName+" "+cartridgeLocation ;
                      
    }
    private String createCommandLine( String property ){
                               
        return _exec+" getProperty "+ property;
                      
    }
    public void mount( String driveName , String driveLocation , 
                       String cartridgeName , String cartridgeLocation )
                       throws InterruptedException , EurogatePvrException {
                       
         mountDismount("mount",driveName,driveLocation,cartridgeName,cartridgeLocation) ;

    }
    public void dismount( String driveName , String driveLocation , 
                          String cartridgeName , String cartridgeLocation )

                       throws InterruptedException , EurogatePvrException{
         
         mountDismount("dismount",driveName,driveLocation,cartridgeName,cartridgeLocation) ;
                               
    }
    private void mountDismount(
                   String command ,
                   String driveName , String driveLocation , 
                   String cartridgeName , String cartridgeLocation )
            throws InterruptedException , EurogatePvrException {
                   
         say(command+" called in EasyStackerExec "+driveName+"["+driveLocation+"] "+
                    cartridgeName+"["+cartridgeLocation+"]" ) ;
                    
         String execCommand = createCommandLine(
                 command,driveName,driveLocation,cartridgeName,cartridgeLocation) ;
                               
         RunSystem run = new RunSystem( execCommand, 100 , 100000000 , this ) ;
         
         try{
         
             run.go() ;
         
             int rc = run.getExitValue() ;
             
             if( rc != 0 ){
                String error  = run.getErrorString() ;
                error = error == null ? ( "Error "+rc ) : error.trim() ;
                throw new 
                EurogatePvrException( rc , run.getErrorString() ) ;
             }
            
             
         }catch(IOException io ){
             new EurogatePvrException(22, "IOException : "+io.getMessage() ) ;
         }finally{      
            say(command+" finished in EasyStackerExec "+driveName+"["+driveLocation+"] "+
                       cartridgeName+"["+cartridgeLocation+"]" ) ;
         }
    } 
    public void getInfo( PrintWriter pw ){
        pw.println("      Executable : "+_exec);
        return ;
    }
    public String toString(){ return "None" ; }
    public int getNumberOfArms(){
       try{
          String rc = getProperty( "numberOfArms" ) ;
          say("getProperty of numberOfArms : "+rc) ;
          return Integer.parseInt(rc.trim()) ;
       }catch(Exception ee ){
          say("Exception in getProperty[numberOfArms] : "+ee);
          return 1 ;
       }
    }
    private String getProperty( String property )

            throws InterruptedException , EurogatePvrException {
                   
         say("get property called in EasyStackerExec property" ) ;
                    
         String execCommand = createCommandLine(property) ;
         String result      = null ;
                               
         RunSystem run = new RunSystem( execCommand, 100 , 100000000 , this ) ;
         
         try{
         
             run.go() ;
         
             int rc = run.getExitValue() ;
             
             if( rc != 0 )
                throw new 
                EurogatePvrException( rc , run.getErrorString() ) ;
            
            result = run.getOutputString() ;
            result = result == null ? "" : result.trim() ;
            
         }catch(IOException io ){
             new EurogatePvrException(22, "IOException : "+io.getMessage() ) ;
         }finally{      
            say("get property finished in EasyStackerExec result : "+(result==null?"NONE":result) ) ;
         }
         return result ;
    } 
    private boolean checkForError(){
        return new File( "/tmp/error" ).exists() ;
    }   
 }
