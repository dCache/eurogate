package eurogate.gate ;

import java.io.* ;
import java.net.* ;
import java.util.* ;

import dmg.util.Args ;

import eurogate.pnfs.* ;

public class Egcopy {

   public static void main( String [] argsString ){
   
      String  host    = "localhost" ;
      int     port    = 28000 ;
      String  store   = "MAIN" ;
      boolean debug   = false ;
      int     ret     = 0 ;
      String  mp      = "/pnfs/fs" ;
      String  command = "copy" ;
      String  group   = "raw" ;
      EuroSyncClient euro  = null ;
      Hashtable      setup = null ;
   
      
      try{
         Args args = new Args( argsString ) ;
         if( args.argc() < 1 )throw new IllegalArgumentException("") ;
         
         command = args.argv(0) ;
         
         String opt       = null ;
         String replyHost = "localhost" ;
         //
         // debug flag only taken from 'command line'
         //
         if( ( opt = args.getOpt("debug") ) != null )debug = true ;
         //
         // try to get the local hostname
         //
         try{
            replyHost = InetAddress.getLocalHost().getHostName() ;
         }catch( Exception ee ){}
         //
         // now, set the options by scanning the setuptfile
         //
         String setupFile = "/etc/egSetup" ;
         
         if( ( opt = args.getOpt("setup") ) != null )setupFile = opt ;
         setup = EuroSyncClient.readSetup( setupFile ) ;

         if( ( opt = (String)setup.get("mountpoint") ) != null )mp = opt ;
         if( ( opt = (String)setup.get("host")  ) != null )host = opt ;
         if( ( opt = (String)setup.get("store") ) != null )store = opt ;
         if( ( opt = (String)setup.get("reply") ) != null )replyHost = opt ;
         if( ( opt = (String)setup.get("group") ) != null )group = opt ;
         if( ( opt = (String)setup.get("port")  ) != null ){
            try{ port = Integer.parseInt( opt ) ;
            }catch(Exception x){ }
         }
         
         if( ( opt = args.getOpt("mountpoint") ) != null )mp = opt ;
         if( ( opt = args.getOpt("host")  ) != null )host = opt ;
         if( ( opt = args.getOpt("store") ) != null )store = opt ;
         if( ( opt = args.getOpt("reply") ) != null )replyHost = opt ;
         if( ( opt = args.getOpt("group") ) != null )group = opt ;
         if( ( opt = args.getOpt("port")  ) != null ){
            try{
               port = Integer.parseInt( opt ) ;
            }catch(Exception x){ 
                throw new 
                IllegalArgumentException("Port must be decimal");
            }
         }
         
         if( debug )System.err.println( "Using Hostname : "+replyHost ) ;
         args.shift() ;
         
         euro = new EuroSyncClient( host , port ) ;
         
         euro.setDebug( debug ) ;
         euro.setReplyHost( replyHost ) ;
         
         if( command.equals( "get" ) ){
            if( args.argc() < 2 )
               throw new 
               IllegalArgumentException("Not enough arguments for get") ;
               
            PnfsFile mpoint = new PnfsFile( mp ) ;
            if( ! ( mpoint.isDirectory() && mpoint.isPnfs() ) )
               throw new 
               IllegalArgumentException("Not a pnfs directory : "+mp ) ;
               
            
            PnfsId pnfsId      = new PnfsId( args.argv(0) ) ;
            PnfsFile localFile = new PnfsFile( args.argv(1) ) ;
            PnfsFile pnfsFile  = mpoint.getFileByPnfsId( pnfsId ) ;
            
            File level1 = pnfsFile.getLevelFile(1) ;
            if( level1.length() == 0 )
               throw new
               IllegalArgumentException( 
               "File not yet on tape : "+pnfsId) ;
               
            String bfid = null ;
            BufferedReader br = new BufferedReader(
                                new FileReader( level1 ) ) ;
            try{
               String line = br.readLine() ;
               StringTokenizer st = new StringTokenizer(line) ;
               try{
                  store = st.nextToken() ;
                  group = st.nextToken() ;
                  bfid  = st.nextToken() ;
               }catch(Exception e){
                  throw new 
                  IllegalArgumentException( "Invalid HSM entry : "+line);
               }
            }finally{
               try{ br.close() ; }catch(Exception ee ){}
            }  
            if( localFile.exists()  )
               throw new
               IllegalArgumentException( 
               "File exists : "+localFile) ;
            
            EuroCompanion com = euro.get( localFile , 
                                          store , 
                                          bfid     ) ;

            if( ( ret = com.getReturnCode() ) == 0 ){
            }else{ 
               System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
            }              
         }else if( command.equals( "put" ) ){
            if( args.argc() < 2 )
               throw new 
               IllegalArgumentException("Not enough arguments for put") ;
               
            PnfsFile mpoint = new PnfsFile( mp ) ;
            if( ! ( mpoint.isDirectory() && mpoint.isPnfs() ) )
               throw new 
               IllegalArgumentException("Not a pnfs directory : "+mp ) ;
               
            
            PnfsId pnfsId      = new PnfsId( args.argv(0) ) ;
            PnfsFile localFile = new PnfsFile( args.argv(1) ) ;
            PnfsFile pnfsFile  = mpoint.getFileByPnfsId( pnfsId ) ;
            
            File level1 = pnfsFile.getLevelFile(1) ;
            if( level1.length() != 0 )
               throw new
               IllegalArgumentException( 
               "File already exists on tape : "+pnfsId) ;
               
            if( ( ! localFile.canRead() )   || 
                ( localFile.length() <= 0 ) ||
                ( localFile.isPnfs()      )    )
               throw new
               IllegalArgumentException( 
               "File empty, pnfsFile or not accessable : "+localFile) ;
            
            PrintWriter pw = new PrintWriter( 
                             new FileWriter( level1 ) ) ;
            try{
               pw.println( "in-progress"+ ( new Date() ) ) ;
            }finally{
               try{ pw.close() ; }catch(Exception ee ){}
            }
            EuroCompanion com = euro.put( localFile , 
                                          store , 
                                          group , 
                                          "key="+pnfsId ) ;

            if( ( ret = com.getReturnCode() ) == 0 ){
               pw = new PrintWriter( new FileWriter( level1 ) ) ;
               try{
                  pw.println( store+" "+group+" "+com.getBfid() ) ;
               }finally{
                  try{ pw.close() ; }catch(Exception ee ){}
               }
               String x = ".(pset)("+pnfsId+
                          ")(size)("+localFile.length()+")" ;
               OutputStream o = new FileOutputStream( new File(x) ) ;
               try{ o.close() ; }catch(Exception ie ){}
            }else{ 
               System.err.println( "Failed "+ret+" "+com.getReturnMessage() ) ;
               pw = new PrintWriter( new FileWriter( level1 ) ) ;
               try{ pw.close() ; }catch(Exception ee ){}
               
            }              
         }else
            throw new 
            IllegalArgumentException( "Command not found : "+command ) ;
      }catch( IllegalArgumentException nsee ){
          if( ! nsee.getMessage().equals("") ){
            System.err.println( "Problem : "+nsee.getMessage() ) ;
          }else{
             System.err.println( 
                 "Usage : ... [options] put|get <pnfsid> <filepath>" ) ;
             System.err.println( "" ) ;
             System.err.println( "  options :" ) ;
             System.err.println( "    -host=<hostname> -port=<portNumber> -debug" ) ;
             System.err.println( "    -reply=<replyHostName> -mountpoint=<mp>" ) ;
          }
          System.exit(3);
      }catch(Exception exx ){
          System.err.println( exx.toString() ) ;
          ret = 5 ;
          if( debug )exx.printStackTrace(System.err) ;
      }finally{
          if(euro!=null)try{ euro.close() ; }catch(Exception xxx ){}
      }
      System.exit(ret);
      
   }
}
