package eurogate.store ;

import eurogate.vehicles.* ;
import eurogate.misc.CookieEnumeration ;
public interface EuroStoreable {
    //
    // needs a constuctor : <init>( dmg.util.Args       args
    //                              java.util.Hashtable environment ,
    //                              dmg.util.Logable    output )
    // 
    public void initialPutRequest( BitfileRequest bfreq ) ;
    public void initialGetRequest( BitfileRequest bfreq ) ;
    public void initialRemoveRequest( BitfileRequest bfreq ) ;

    public void finalPutRequest( BitfileRequest bfreq ) ;
    public void finalGetRequest( BitfileRequest bfreq ) ;
    public void finalRemoveRequest( BitfileRequest bfreq ) ;
    
    public BfRecordable getBitfileRecord( String bfid ) ;
    public CookieEnumeration getBfidsByVolume( String volume , long cookie ) ;
    public CookieEnumeration getBfidsByStorageGroup( String storageGroup , long cookie) ;
    public CookieEnumeration getStorageGroups(long cookie ) ;

}
