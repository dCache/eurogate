package  eurogate.pvr ;

import   eurogate.db.pvr.* ;
import java.net.* ;
import java.io.* ;
import java.util.* ;

import dmg.util.* ;
import dmg.util.cdb.* ;

public class Pvr implements Runnable {

   private Socket            _socket         = null ;
   private Thread            _listenThread   = null ;
   private DataInputStream   _in      = null ;
   private DataOutputStream  _out     = null ;
   private String            _pvrName = null ;
   private PvrDb             _handler = null ;
   private Robotic           _robotic = new Robotic() ;
   
   private class CommandProcessor implements Runnable {
      private String _command = null ;
      public CommandProcessor( String command ){
         _command = command ;
         new Thread( this ).start() ;
      }
      public void run(){
          System.out.println( "Running command <"+_command+">" ) ;
          Args args = new Args( _command ) ;
          if( args.argc() < 2 ){
             System.out.println( "Illegal command received : "+_command );
             return  ;
          }
          String com = args.argv(0) ;
          if( com.equals( "mount" ) ){
             if( args.argc() < 5 ){
                problem( args.argv(1) , 3 , "Not enough arguments" ) ;
                return ;
             }             
             doTheMount( args.argv(1) ,
                         args.argv(2) , args.argv(3) , args.argv(4) ) ;
                         
          }else if( com.equals( "dismount" ) ){
             if( args.argc() < 5 ){
                problem( args.argv(1) , 3 , "Not enough arguments" ) ;
                return ;
             }             
             doTheDismount( args.argv(1) ,
                            args.argv(2) , args.argv(3) , args.argv(4) ) ;
          }else if( com.equals( "newdrive" ) ){
             if( args.argc() < 4 ){
                problem( args.argv(1) , 3 , "Not enough arguments" ) ;
                return ;
             }             
             doTheNewDrive( args.argv(1) , args.argv(2) , args.argv(3)  ) ;
             
          }else if( com.equals( "loglevel" ) ){
             if( args.argc() < 2 ){
                problem( args.argv(1) , 3 , "Not enough arguments" ) ;
                return ;
             }
             reply( "done "+args.argv(1)+" 1" ) ;             
          }else if( com.equals( "terminate" ) ){
             reply( "done "+args.argv(1) ) ;
             System.exit(0) ;
          }else{
             System.out.println( "Illegal command received : "+_command );
             return ;
          }
      }
   }
   public Pvr( String pvrName , 
               String host , int port ,
               String database          ) throws Exception {
   
      _pvrName  = pvrName ; 
      System.out.println( "Connecting to pvrProxy <"+host+":"+port+">" ) ;
      _socket   = new Socket( host , port ) ;
      System.out.println( "Connecting to database <"+database+">" ) ;
      _handler  = new PvrDb( new File( database ) , false ) ;
      
      try{
         _in  = new DataInputStream(  _socket.getInputStream() ) ;
         _out = new DataOutputStream( _socket.getOutputStream() ) ;
      }catch( IOException ioe ){
         System.out.println( "Problem in opening io streams : "+ioe) ;
         try{ _in.close() ; }catch(Exception e){}
         try{ _out.close() ; }catch(Exception e){}
         throw ioe  ;
      }

      
      try{
         _out.writeUTF( "hello "+_pvrName+ " "+_pvrName+"-type" ) ;
         String answer = _in.readUTF() ;
         Args args = new Args( answer ) ;
         if( args.argc() < 1 )throw new IOException( "No answer received" ) ;
         if( ! args.argv(0).equals("welcome") ){
             throw new IOException( "Incorrcet answer received : <"+answer+">" ) ;
         }
      }catch( IOException ioe ){
          System.out.println( "Problem in running pvr hello protocol : "+ioe) ;
          try{ _in.close() ; }catch(Exception e){}
          try{ _out.close() ; }catch(Exception e){}
          throw ioe  ;
      }
      System.out.println( "Pvr Hello Protocol successfully processed" ) ;
      
      System.out.println( "Starting net listener thread" ) ;
      
      _listenThread = new Thread( this ) ;
      _listenThread.start() ;
   }
   private void problem( String id , int errCode , String msg ){
      System.err.println( "Problem (id="+id+") : "+msg ) ;
      log( 2 , "ID="+id+" "+msg ) ;
      reply( "done "+id+" "+errCode ) ;
      return ;
   }
   private void doTheNewDrive( String id , 
                            String genericDrive ,
                            String specificDrive  ){
                            
      PvrDriveHandle drive = null ;
      String status = null ;
      try{
      
          
          drive = _handler.getDriveByName( specificDrive ) ;
          if( drive == null ){
             reply( "done "+id+" 0" ) ;
             return ;
          }
          drive.open( CdbLockable.WRITE ) ;
             status = (String)drive.getAttribute( "status" ) ;
             if( status == null ){
                drive.setAttribute( "status" , "empty" ) ;
                status = (String)drive.getAttribute( "status" ) ;
             } 
          drive.close( CdbLockable.COMMIT ) ;
          
          if( status.equals("empty") ){
             reply( "done "+id+" 1" ) ;
          }else{
             reply( "done "+id+" 3 "+status ) ;
          }

      }catch( Exception e ){
          problem( id , 0 , e.toString() ) ;
          return ;
      }      
   }
   private void doTheMount( String id , 
                            String cartridge ,
                            String genericDrive ,
                            String specificDrive  ){
                            
      PvrCartridgeHandle cart  = null ;
      PvrDriveHandle     drive = null ;
      try{
      
          
          drive = _handler.getDriveByName( specificDrive ) ;
          if( drive == null )
          throw new CdbException( "Drive not found : "+specificDrive ) ;
          
          drive.open( CdbLockable.WRITE ) ;
          try{
             String status = (String)drive.getAttribute( "status" ) ;
             if( status == null ){
                drive.setAttribute( "status" , "empty" ) ;
                status = (String)drive.getAttribute( "status" ) ;
             }
             //
             // is this drive in use
             // 
             if( ( ! status.equals( "empty" )       ) &&
                 ( ! status.equals( specificDrive ) )   )
                throw new CdbException( "Drive in use : "+status ) ;
             //
             // is this cartridge in another drive ?
             //
             String [] drives = _handler.getDriveNames() ;
             PvrDriveHandle d = null ;
             String         c = null ;
             boolean  found = false ;
             for( int i = 0 ; i < drives.length ; i++ ){
                if( drives[i].equals( specificDrive ) )continue ;
                d = _handler.getDriveByName( drives[i] ) ;
                d.open(CdbLockable.READ) ;
                c = (String)d.getAttribute("status") ;
                d.close(CdbLockable.COMMIT) ;
                if( ( c == null ) || ( c.equals("empty") ) )continue ;
                if( c.equals(cartridge) )
                   throw new CdbException( "Cartridge in other drive "+drives[i] ) ;
             }
             try{
                 cart = _handler.getCartridgeByName( cartridge ) ;
                 if( cart == null )
                    throw new 
                    CdbException( "Cartridge not found : "+cartridge ) ;
                 
                 cart.open( CdbLockable.WRITE) ;
                 //
                 // increment the usage count and set
                 // insert the drive into the cartridge
                 //
                    int count = cart.getIntAttribute( "usageCount" ) ;
                    count++ ;
                    cart.setAttribute( "usageCount" , count ) ;
                    cart.setAttribute( "drive" , genericDrive  ) ;
                 cart.close(CdbLockable.COMMIT) ;
             }catch( InterruptedException iidbe ){
                 throw iidbe ;
             }catch( CdbException idbe ){
                 throw idbe ;
             }
             drive.setAttribute( "status" , cartridge ) ;
          }catch( CdbException dbe2 ){
             throw dbe2 ;
          }catch( InterruptedException idbe2 ){
             throw idbe2 ;
          }finally{
             drive.close(CdbLockable.COMMIT) ;
          }
          
      }catch(Exception e ){
          problem( id , 100 , e.toString() ) ;
          return ;
      }
      System.out.println( "Waiting for Robotic "+id ) ;
      _robotic.waitForRobotic() ;
      System.out.println( "Robotic finished "+id ) ;
      reply( "done "+id+" 0" ) ;                      
   }
   private void doTheDismount( String id , 
                               String cartridge ,
                               String genericDrive ,
                               String specificDrive  ){
                            
      PvrCartridgeHandle cart  = null ;
      PvrDriveHandle     drive = null ;
      String status = "UNKNOWN" ;
      try{
      
          
          drive = _handler.getDriveByName( specificDrive ) ;
          if( drive == null )
          throw new CdbException( "Drive not found : "+specificDrive ) ;
          
          drive.open( CdbLockable.WRITE ) ;
          try{
             status = (String)drive.getAttribute( "status" ) ;
             if( status == null ){
                drive.setAttribute( "status" , "empty" ) ;
                status = (String)drive.getAttribute( "status" ) ;
             } 
             if( ! status.equals( "empty" ) ){
             
                try{
                    cart = _handler.getCartridgeByName( cartridge ) ;
                    if( cart == null )
                       throw new 
                       CdbException( "Cartridge not found : "+cartridge ) ;

                    cart.open( CdbLockable.WRITE ) ;
                    //
                    // the open is necessay to check if the
                    // cartridge really exists
                    //
                    cart.close(CdbLockable.COMMIT) ;
                }catch( InterruptedException iidbe ){
                    throw iidbe ;
                }catch( CdbException idbe ){
                    throw idbe ;
                }
             }
             drive.setAttribute( "status" , "empty" ) ;
          }catch( CdbException dbe2 ){
             throw dbe2 ;
          }catch( InterruptedException idbe2 ){
             throw idbe2 ;
          }finally{
             drive.close(CdbLockable.COMMIT) ;
          }
          
      }catch(Exception e ){
          problem( id , 101 , e.toString() ) ;
          return ;
      }
      System.out.println( "Waiting for Robotic "+id ) ;
      _robotic.waitForRobotic() ;
      System.out.println( "Robotic finished "+id ) ;
      reply( "done "+id+" 0 "+status ) ;                      
   }
   private void reply( String reply ){
     try{
         _out.writeUTF( reply ) ; 
     }catch( Exception e ){
        System.out.println( "Exception in reply UTF  : "+e ) ;
        System.exit(4) ;
     }
   }
   private void log( int id , String reply ){
     try{
         _out.writeUTF( "log "+id+" "+reply ) ; 
     }catch( Exception e ){
        System.out.println( "Exception in reply UTF  : "+e ) ;
        System.exit(4) ;
     }
   }
   public void run(){
      Thread myself = Thread.currentThread() ;
      
      if( myself == _listenThread ){
          
          runListener() ;
      
      }
   
   }
   private void runListener(){
       String line = null ;
       try{
          while( ( line = _in.readUTF() ) != null ){
             System.out.println( "Command received <"+line+">" ) ;
             new CommandProcessor( line ) ;
          }
       }catch( EOFException eofe ){
          System.out.println( "Remote node closed connection" ) ;
       }catch( IOException ioe ){
          System.err.println( "Problem in readUTF() : "+ioe ) ;
       }

       try{ _in.close() ; }catch(Exception e){}
       try{ _out.close() ; }catch(Exception e){}
   }
   public static void main( String [] args ) throws Exception {
      if( args.length < 4 ){
         System.err.println( 
             "        ... <pvr-name> <host> <port> <database>" ) ;
         System.exit(2);
      }else{
         int port = Integer.parseInt( args[2] ) ;
         new Pvr( args[0] , args[1]  , port , args[3] ) ;
      }
      
   }
}
