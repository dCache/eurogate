package eurogate.misc ;

import eurogate.pvl.* ;

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
   public void queryUrl( HttpRequest request )
          throws HttpException {

      PrintWriter pw     = request.getPrintWriter() ;
      String [] urlItems = request.getRequestTokens() ;
      int       offset   = request.getRequestTokenOffset() ;

      if( urlItems.length < 2 ){
         request.printHttpHeader(0) ;
         printHtmlTop(pw,"Eurogate Online");
         pw.println("<center><h1><font color=gray>Eurogate</font></h1></center>");
         printMenu(pw);
         return ;
      }
      request.printHttpHeader( 0 ) ;
      String      command = urlItems[1] ;
      if( command.equals( "drives" ) ){
         printHtmlTop(pw,"Eurogate Drives");
         printMenu(pw);
         try{
            printPvrs( pw ) ;
         }catch(Exception ee ){
            throw new HttpException( 66 , ee.toString() );
         }
         return ;
      }else if( command.equals( "requests" ) ){
         printHtmlTop(pw,"Eurogate Requests");
         printMenu(pw);
         try{
            printRequests( pw ) ;
         }catch(Exception ee ){
            throw new HttpException( 66 , ee.toString() );
         }
         return ;
      }else if( command.equals( "setup" ) ){
         printHtmlTop(pw,"Eurogate Setup");
         printMenu(pw);
         try{
            printVolumes( pw ) ;
         }catch(Exception ee ){
            throw new HttpException( 66 , ee.toString() );
         }
         return ;
      }else{
         throw new 
         HttpException( 33 , "Url : "+command+" not found on this server" ) ;
      }
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
   private void printVolumes( PrintWriter pw ) throws Exception {
      Object obj = sendRequest( "pvl" , "x ls volumes" ) ;
      if( ! ( obj instanceof  Map ) )
        throw new
          HttpException( 33 , "Unexpected reply arrived : "+obj.getClass().getName() ) ;

      Map volumeSets = (Map)obj ;
      String prefix = "<td align=center>";
      pw.println( "<center><h1><font color=gray>The Eurogate Volumes</font></h1></center>") ;
      pw.println("<center><table border=1 cellspacing=0 cellpadding=2 width=\"90%\">");
      pw.println("<tr>");
      pw.println("<th>Volume</th><th>Volume Set</th><th>Pvr Name</th>");
      pw.println("<th>File Count</th><th>EOR</th><th>Residual Bytes</th><th>Cartridge</th><th>Volume Descriptor</th>");
      pw.println("</tr>");
      for( Iterator sets = volumeSets.entrySet().iterator() ; sets.hasNext() ; ){
         Map.Entry entry = (Map.Entry)sets.next() ;
         String volumeSetName  = entry.getKey().toString() ;
         Map    pvrMap = (Map)entry.getValue() ;
         for( Iterator pvrs = pvrMap.entrySet().iterator() ; pvrs.hasNext() ; ){
            Map.Entry e = (Map.Entry)pvrs.next() ;
            String pvrName    = e.getKey().toString() ;
            Map    volumesMap = (Map)e.getValue() ;
            for( Iterator volumes = volumesMap.entrySet().iterator() ; volumes.hasNext() ; ){
               Map.Entry v = (Map.Entry)volumes.next() ;
               String volumeName = v.getKey().toString() ;
               String [] info    = (String[])v.getValue() ;
               pw.print("<tr>");
               pw.print(prefix);pw.print(volumeName);pw.println("</td>");
               pw.print(prefix);pw.print(volumeSetName);pw.println("</td>");
               pw.print(prefix);pw.print(pvrName);pw.println("</td>");
               for( int l = 0 ; l < info.length ; l++ ){
                  pw.print(prefix); pw.print(info[l]); pw.println("</td>");
               }
               pw.println("</tr>");
               
            }
         }
      }
      pw.println("</table></center>");
   }
   private String printValue( String value ){
     return ( value == null ) || ( value.equals("") ) ? "-" : value ;
   }
   private void printRequests( PrintWriter pw ) throws Exception {
      Object obj = sendRequest( "pvl" , "x ls request" ) ;
      if( ! ( obj instanceof  PvlResourceRequest [] ) )
        throw new
          HttpException( 33 , "Unexpected reply arrived : "+obj.getClass().getName() ) ;

      PvlResourceRequest [] reqs = (PvlResourceRequest [])obj ;

      pw.println( "<center><h1><font color=gray>The Eurogate Requests</font></h1></center>") ;

      pw.println( "<center><table width=\"90%\" border=1 cellspacing=0 cellpadding=5>" ) ;
      pw.println( "<tr>");
      pw.println( "<th>Request Id</th>");
      pw.println( "<th>Direction</th>");
      pw.println( "<th>Volume Set</th>");
      pw.println( "<th>File Size</th>");
      pw.println( "<th>Volume</th>");
      pw.println( "<th>Cartridge</th>");
      pw.println( "<th>Drive</th>");
      pw.println( "<th>Pvr</th>");
      pw.println( "<th>Parameter</th>");
      pw.println( "</tr>");
      String tmp = null ;
      String prefix = "<td align=center>" ;
      for( int i = 0 ; i < reqs.length ; i++ ){

         pw.println( "<tr>");
         pw.print(prefix); pw.print( reqs[i].getSerialId()  ) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getDirection() ) ) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getVolumeSet()  )  ); pw.print( "</td>");
         pw.print(prefix); pw.print( reqs[i].getFileSize()  ) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getVolume()  ) ) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getCartridge()   )) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getDrive()   )) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getPvr()  ) ) ; pw.print( "</td>");
         pw.print(prefix); pw.print( printValue( reqs[i].getParameter()  ) ) ; pw.print( "</td>");
         pw.println( "</tr>");
      }
      pw.println("</table>");
      pw.println("</body></html>");
      pw.flush() ;
      return ;

   }
   private static final String [] __driveHead =
      { "Drive Name" , "Cartridge" , "Action" , "Status" , "Owner" } ;
      
   private void printPvrs( PrintWriter pw ) throws Exception {
                      
      Object obj = sendRequest( "pvl" , "x lsdrive" ) ;
      System.out.println( "Object : "+obj ) ;
      try{
      Object [] pvrs = (Object []) obj ;
             
      pw.println( "<center><h1><font color=gray>The Eurogate Drives</font></h1></center>") ;
      for( int i = 0 ; i < pvrs.length ; i++ ){
         Object [] pvr = (Object [])pvrs[i] ;
         String pvrName = (String) pvr[0] ;
         pw.println( "<center><h2><font color=gray>"+pvrName+"</font></h2></center>" ) ;
         pw.print( "<center><table width=\"90%\" border=1 cellspacing=0 cellpadding=5>" ) ;
         pw.println( "<tr>" ) ;
         for( int j = 0 ; j < __driveHead.length ; j++ )
            pw.println( "<th>"+__driveHead[j]+"</th>");
         pw.println("</tr>" ) ;
         for( int j = 1 ; j < pvr.length ; j++ ){
            String [] attr = (String[])pvr[j] ;
            pw.println( "<tr>" ) ;
            pw.print( "<th><a href=/system/show%20pinboard%20?" ) ;
            pw.print( attr[0]+">" ) ;
            pw.println(attr[0]+"</a></th>" ) ;
            for( int l = 1 ; l < 5 ; l++ ){
               pw.println( "<td align=center>" ) ;
               if( ( l == 3 ) && ( attr[l].equals("disabled") ) ){
                   pw.println("<font color=red>"+attr[l]+"</font>");
               }else{
                   pw.println(attr[l]);
               }
               pw.println( "</td>" ) ;
            }
            pw.println( "</tr>" ) ;
         } 
         pw.println( "</table></center>" ) ;
      
      }
      }catch( Exception eeee ){
         eeee.printStackTrace() ;
         throw new HttpException( 35 , eeee.toString() ) ;
      }
      pw.println( "</font>") ;
      pw.println( "</body></html>" ) ;
      pw.flush();
   }
   private void printHtmlTop(PrintWriter pw , String title){
      pw.println("<html><head><title>");
      pw.println(title);
      pw.println("</title></head><body>");
      return ;
   }
   private void printMenu( PrintWriter pw ){
     pw.println("<center>");
     pw.println("<table border=0 width=\"90%\"><tr>");
     pw.println("<th><a href=/online/drives>Drives</a></th>");
     pw.println("<th><a href=/online/requests>Requests</a></th>");
     pw.println("<th><a href=/online/setup>Setup</a></th>");
     pw.println("</tr></table></center>");
   }
   private void printDirectory( PrintWriter pw ){
      pw.println( "<html><head><title>Eurogate Directory</title></head>");
      pw.println( "<body bgcolor=yellow>" ) ;
      pw.println( "<font color=red>" ) ;
//      pw.println( "<center><h1>The Eurogate Directory</h1></center>") ;
      pw.println( "<center><img src=/images/eurostore1.GIF></center>") ;
      pw.println( "<center><h1><a href=drives>Drives</a></h1></center>");
      pw.println( "<center><h1><a href=requests>Requests</a></h1></center>");
      pw.println( "</font>") ;
      pw.println( "</body></html>" ) ;
   }
/*
   private void printDummyHttpHeader( PrintWriter pw ){
      pw.println( "HTTP/1.0 200 Document follows" );
      pw.println( "MIME-Version: 1.0" ) ;
      pw.println( "Server: Java Cell Server" ) ;
      pw.println( "Date: Thursday, 02-Jul-97 09:29:49 GMT" ) ;
      pw.println( "Content-Type: text/html" ) ;
      pw.println( "Content-Length: 10000" ) ;
      pw.println( "Last-Modified: Thursday, 03-Jul-97 10:01:00 GMT\n" ) ;
   }
*/
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
