package eurogate.store ;

import eurogate.vehicles.* ;
import eurogate.misc.CookieEnumeration ;
/**
  * EuroStoreable is the interface to all database accesses.
  * It covers the initial and the final passage of the
  * BitfileRequest through the Store for <em>put</em>, <em>get</em> and
  * <em>remove</em>. In addition it supports the most common
  * "user queries" like :
  * <ul>
  * <li>get the BfRecordable by the bitfileid.
  * <li>get the Enumeration of all bitfileids on a particular volume.
  * <li>get the Enumeration of all bitfileids belonging to pariticular storeGroup.
  * <li>get the Enumeration of all storageGroups of this store.
  * </ul>
  * <bf>The elements of all Enumerations of this interface are assumed to
  * return java.lang.String only</bf>. 
  * <br>
  * The implementation of this interface needs to provide a specific 
  * constructor to allow the autoinitiation of the class by the eurostore
  * framework.
  * <table border=2>
  * <tr><th>Position</th><th>Class</th><th>Purpose</th></tr>
  * <tr><td>1</td><td><a href=http://watphrakeo.desy.de/cells/dmg.util.Args.html>dmg.util.Args</a></td><td>args</td></tr>
  * <tr><td>2</td><td>java.util.Hashtable</td><td>environment</td>
  * <tr><td>3</td><td><a href=http://watphrakeo.desy.de/cells/dmg.util.Logable.html>dmg.util.Logable</a></td><td>output</td>
  * </table>
  * <ul>
  * <li>
  * The first argument, dmg.util.Args, holds the store specific
  * arguments from the 'create' command line. This may be typically 
  * the name or the path of the related database.
  * <li>
  * The second argument points to a hashtable holding the
  * the DomainContext.
  * <li>
  * The third argument  points to an object implementing the
  * dmg.util.Logable interface to allow the impelmentation of the
  * EuroStoreable interface (us) to perform logging output.
  * </ul>
  */
public interface EuroStoreable {
    /**
      *  initialPutRequest is called by the Store Framework
      *  on  arrival of a put request. The BitfileRequest is
      *  provided and assumed to be stored with the 'transient' flag.
      *  On return of this method the eurostoreI protocol proceeds 
      *  only if the errorCode of this request is still 'zero'.
      *  Otherwise the appropriate actions will be taken to let the
      *  request fail. The method may use the following 
      *  <bf>get</bf> methods of the BitfileRequest.
      *  <br><bf>getStorageGroup,getFileSize,getParameter</bf>.
      *  This method is assumed to use the <bf>BitfileRequest.setBfid</bf>
      *  method to allow the protocol to proceed.
      */
    public void initialPutRequest( BitfileRequest bfreq ) ;
    /**
      *  initialGetRequest is called by the Store Framework
      *  on  arrival of a get request. The BitfileRequest is
      *  provided and assumed to be filled with all information listed below.
      *  Only the bitfileid <bf>getBfid</bf> can be used to
      *  assemble those information out of the database.
      *  NOTE: The storage group (getStorageGroup) is not yet available.
      *  <br>Information which have to be provided :
      *  <bf>setStorageGroup, setFileSize, setFilePosition, setVolume</bf>.
      */
    public void initialGetRequest( BitfileRequest bfreq ) ;
    /**
      *  initialRemoveRequest is called by the Store Framework
      *  on  arrival of a remove request. The BitfileRequest is
      *  provided. Only the getBfid method returns a valid result.
      *  <br>Information which have to be provided :
      *  <bf>setStorageGroup, setFileSize, setFilePosition, setVolume</bf>.
      *  <br>NOTE: The bfid should not yet be deleted because
      *  additional action might be necessary. 
      *  Wait for the 'finalRemoveRequest' to finally remove the
      *  bitfilerecord from the database.
      */
    public void initialRemoveRequest( BitfileRequest bfreq ) ;
    /**
      *   finalPutRequest is called by the Store Framework
      *   if a put request could be finished successfully or
      *   if it failed. (Check errorCode for either).
      *   If successful, the following information must be
      *   added to the bitfilerecord :
      *   <bf>getFilePosition,getVolume</bf>.
      *   <br>If the errorCode of the BitfileRequest indicates
      *   a failure the bitfilerecord should be removed from the
      *   database otherwise the record should be declared 'persistant'.
      */
    public void finalPutRequest( BitfileRequest bfreq ) ;
    /**
      *   finalGetRequest is called by the Store Framework
      *   if a get request could be finished successfully or
      *   if it failed. (Check errorCode for either).
      *   The implementation is free to update appropriate pointers
      *   within the database. No other actions are needed.
      */
    public void finalGetRequest( BitfileRequest bfreq ) ;
    /**
      *    finalRemoveRequest is called by the Store Framework
      *    if a remove request could be finished sucessfully or
      *    if it failed ( check errorCode for either )
      *    If the request failed, the implementation if free to
      *    store this information somehow in  the bitfilerecord
      *    within the database. The implementation should by 
      *    no means delete the bitfilerecord under this condition.
      *    If sucessful the bitfilerecord might be deleted or at
      *    least maked 'deleted'.
      */
    public void finalRemoveRequest( BitfileRequest bfreq ) ;
    /**
      * Returns an object implementing the BfRecordable interface of
      * the bitfile record represented by the bitfileid.
      */
    public BfRecordable getBitfileRecord( String bfid ) ;
    /**
      * Returns a CookieEnumeration of String elements containing the
      * ordered list of all bfids of the specified volume.
      */
    public CookieEnumeration getBfidsByVolume( String volume , long cookie ) ;
    /**
      * Returns the CookieEnumeration of String elements containing the
      * list of all bitfiles of the specified storage group.
      */
    public CookieEnumeration getBfidsByStorageGroup( String storageGroup , long cookie) ;
    /**
      * Returns the CookieEnumeration of String elements containing the
      * list of all StorageGroups of this <bf>Store</bf>. 
      */
    public CookieEnumeration getStorageGroups(long cookie ) ;

}
