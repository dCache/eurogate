package eurogate.misc ;

import   dmg.cells.services.* ;
import   dmg.cells.nucleus.* ;
import   dmg.cells.network.* ;
import   dmg.util.* ;

import java.util.* ;
import java.text.* ;
import java.io.* ;
import java.net.* ;

public class HttpEurogateService implements HttpResponseEngine {

   private CellNucleus _nucleus    = null ;
   private String   [] _args       = null ;

   public HttpEurogateService( CellNucleus nucleus , String [] args ){
       _nucleus = nucleus ;
       _args    = args ;
   }
   public void queryUrl( PrintWriter pw , String [] urlItems )
          throws Exception {
          
      if( urlItems.length < 2 ){
         printDummyHttpHeader( pw ) ;
         printDirectory( pw ) ;
         return ;
      }
      String      command = urlItems[1] ;
      if( command.equals( "drives" ) ){
         printDummyHttpHeader( pw ) ;
         printPvrs( pw ) ;
         return ;
      }else
         throw new 
         Exception( "Url : "+command+" not found on this server" ) ;
   }
   private Object sendRequest( String path , String command )
           throws Exception {
      CellMessage msg = _nucleus.sendAndWait( 
            new CellMessage( new CellPath(path)  , command ) ,
                                  3000                          ) ;
                      
      if( msg == null )
          throw new Exception( "Response Timed Out" ) ;
         
      return msg.getMessageObject() ;
      
           
   }
   private static final String [] __driveHead =
      { "Drive Name" , "Cartridge" , "Action" , "Status" , "Owner" } ;
      
   private void printPvrs( PrintWriter pw ) throws Exception {
                      
      Object obj = sendRequest( "pvl" , "x lsdrive" ) ;
      System.out.println( "Object : "+obj ) ;
      try{
      Object [] pvrs = (Object []) obj ;
             
      pw.println( "<html><head><title>Eurogate Drives</title></head>");
      pw.println( "<body bgcolor=blue>" ) ;
      pw.println( "<font color=red>" ) ;
      pw.println( "<center><h1>The Eurogate Drives</h1></center>") ;
      for( int i = 0 ; i < pvrs.length ; i++ ){
         Object [] pvr = (Object [])pvrs[i] ;
         String pvrName = (String) pvr[0] ;
         pw.println( "<center><h2>"+pvrName+"</h2></center>" ) ;
         pw.print( "<center><table border=2 bgcolor=yellow" ) ;
         pw.println( "cellspacing=5 cellpadding=5>" ) ;
         pw.println( "<tr>" ) ;
         for( int j = 0 ; j < __driveHead.length ; j++ )
            pw.println( "<th><font size=+5>"+__driveHead[j]+"</font></th>");
         pw.println("</tr>" ) ;
         for( int j = 1 ; j < pvr.length ; j++ ){
            String [] attr = (String[])pvr[j] ;
            pw.println( "<tr>" ) ;
            pw.print( "<th><a href=/system/show%20pinboard%20200?" ) ;
            pw.print( attr[0]+"><font color=green size=+5>" ) ;
            pw.println(attr[0]+"</font></a></th>" ) ;
            for( int l = 1 ; l < 5 ; l++ ){
               pw.println( "<td><font color=green size=+5>" ) ;
               pw.println( attr[l]+"</font></td>" ) ;
            }
            pw.println( "</tr>" ) ;
         } 
         pw.println( "</table></center>" ) ;
      
      }
      }catch( Exception eeee ){
         eeee.printStackTrace() ;
         throw eeee ;
      }
      pw.println( "</font>") ;
      pw.println( "</body></html>" ) ;
   }
   private void printDirectory( PrintWriter pw ){
      pw.println( "<html><head><title>Eurogate Directory</title></head>");
      pw.println( "<body bgcolor=yellow>" ) ;
      pw.println( "<font color=red>" ) ;
//      pw.println( "<center><h1>The Eurogate Directory</h1></center>") ;
      pw.println( "<center><img src=/images/eurostore1.GIF></center>") ;
      pw.println( "<center><h1><a href=drives>Drives</a></h1></center>");
      pw.println( "</font>") ;
      pw.println( "</body></html>" ) ;
   }
   private void printDummyHttpHeader( PrintWriter pw ){
      pw.println( "HTTP/1.0 200 Document follows" );
      pw.println( "MIME-Version: 1.0" ) ;
      pw.println( "Server: Java Cell Server" ) ;
      pw.println( "Date: Thursday, 02-Jul-97 09:29:49 GMT" ) ;
      pw.println( "Content-Type: text/html" ) ;
      pw.println( "Content-Length: 0" ) ;
      pw.println( "Last-Modified: Thursday, 03-Jul-97 10:01:00 GMT\n" ) ;
   }
   private static void printException( PrintWriter pw , Exception ee ){
      pw.println( "<h1><font color=red>"+
                   "An internal error occured"+
                   "</font></h1>" ) ;
      pw.println( "<h4>The Exception was : <font color=red>"+
                   ee.getClass().getName()+"</font></h4>" ) ;
      pw.println( "<h4>The message was : </h4>" ) ;
      pw.println( "<pre>" ) ;
      pw.println( ee.getMessage() ) ;
      pw.println( "</pre>" ) ;
   }


} 
