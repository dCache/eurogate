package eurogate.misc.users ;

import java.util.* ;
import eurogate.misc.* ;

public class InMemoryUserRelation implements UserRelationable {

   private class DEnumeration implements Enumeration {
       public boolean hasMoreElements(){ return false ; }
       public Object nextElement(){ return null ;}
   }
   
   private class ElementItem {
      private Hashtable _parents = null ;
      private Hashtable _childs  = null ;
      private void addParent(String parent){
         if( _parents == null )_parents = new Hashtable() ;
         _parents.put(parent,parent) ;
      }
      private void addChild( String child ){
         if( _childs == null )_childs = new Hashtable() ;
         _childs.put(child,child) ;
      }
      private Enumeration parents(){ 
          return _parents == null ? new DEnumeration() : _parents.keys() ;
      }
      private Enumeration children(){ 
          return _childs == null ? new DEnumeration() : _childs.keys() ;
      }
      private boolean isParent(String parent){
          return ( _parents != null ) && ( _parents.get(parent)!=null ) ;
      }
      private boolean isChild(String child){
          return ( _childs != null ) && ( _childs.get(child)!=null ) ;
      }
      private void removeChild( String child ){
         if( _childs == null )return ;
         _childs.remove(child);
      }
      private void removeParent( String parent ){
         if( _parents == null )return ;
         _parents.remove(parent);
      }
   }
   
   private TopDownUserRelationable _db = null ;
   private Hashtable _elements = null ;
   public InMemoryUserRelation( TopDownUserRelationable db )
          throws DatabaseException {
      _db = db ;
      _loadElements() ;
   }
   public synchronized Enumeration getContainers() {
      //
      // falsch falsch falsch
      //
      return _elements.keys() ;
   }
   public synchronized Enumeration getParentsOf( String element )
          throws NoSuchElementException {
       
      ElementItem item = (ElementItem)_elements.get(element) ;
      if( item == null )
         throw new
         NoSuchElementException(element) ;
         
      
      return item.parents()  ;
   }
   public boolean isParentOf( String element , String container ) 
          throws NoSuchElementException {
          
      ElementItem item = (ElementItem)_elements.get(element) ;
      if( item == null )
         throw new
         NoSuchElementException(element) ;
         
      
      return item.isParent(container)  ;
   }      
   public void createContainer( String container )
       throws DatabaseException {
       _db.createContainer(container) ;
       _elements.put( container , new ElementItem() ) ;
   }
   public Enumeration getElementsOf( String container ) 
       throws NoSuchElementException {
      ElementItem item = (ElementItem)_elements.get(container) ;
      if( item == null )
         throw new
         NoSuchElementException(container) ;
         
      
      return item.children()  ;
   }
   public boolean isElementOf( String container , String element )
       throws NoSuchElementException {
      ElementItem item = (ElementItem)_elements.get(container) ;
      if( item == null )
         throw new
         NoSuchElementException(container) ;
         
      
      return item.isChild(element)  ;
   }
   public void addElement( String container , String element )
       throws NoSuchElementException {
       
      _db.addElement( container , element ) ;
      
      ElementItem item = (ElementItem)_elements.get(container) ;
      if( item == null )
         throw new
         NoSuchElementException(container) ;
         
      item.addChild( element ) ;
      
      item = (ElementItem)_elements.get(element) ;
      if( item == null )
         _elements.put( element , item = new ElementItem() ) ;
      
      item.addParent(container);
      return ;
   }
   public void removeElement( String container , String element )
       throws NoSuchElementException {
       
      _db.removeElement( container , element ) ;
      ElementItem item = (ElementItem)_elements.get(container) ;
      if( item == null )
         throw new
         NoSuchElementException(container) ;
         
      item.removeChild(element) ;
      
      item = (ElementItem)_elements.get(element) ;
      if( item == null )return ;
      
      item.removeParent(container);
      
      return ;
   }
   public void removeContainer( String container )
       throws NoSuchElementException ,
              DatabaseException {
      
      _db.removeContainer( container ) ;
      _elements.remove( container ) ;
      return ;
   } 
   private void _loadElements() throws DatabaseException{
         
        Hashtable hash = new Hashtable() ;
        Enumeration e = _db.getContainers() ;
        
        while( e.hasMoreElements() ){
           String container = (String)e.nextElement() ;
           ElementItem item  = null , x = null;
           if( ( item = (ElementItem)hash.get( container ) ) == null ){
              hash.put( container , item = new ElementItem() ) ;
           }
           try{
              Enumeration f = _db.getElementsOf(container) ;
              while( f.hasMoreElements() ){
                  String name = (String)f.nextElement() ; 
                  item.addChild(name) ;
                  if( ( x = (ElementItem)hash.get(name) ) == null ){
                      hash.put(name , x = new ElementItem() ) ;
                  }
                  x.addParent( container ) ;
               }
           }catch( Exception ie ){
           }
        }
        _elements = hash ;
    }

}
