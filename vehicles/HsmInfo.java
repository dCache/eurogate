package eurogate.vehicles ;

public interface HsmInfo extends java.io.Serializable      {

   //
   // should provide the following constructor as well :
   //     <init>( PnfsFile file/dir ) 
   //
   public String getStorageClass() ;
   public String getHsmInfo() ;
   
}
