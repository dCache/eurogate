package  eurogate.vehicles ;



public class      MoverRequestImpl 
       extends    RequestImpl {


   public MoverRequestImpl( String action ){
      super( "" , action ) ;
   }
   public MoverRequestImpl( String action , String cartridge , String store ){
      super( "" , action ) ;
      setCartridge( cartridge ) ;
      setStore( store ) ;
   }

}
