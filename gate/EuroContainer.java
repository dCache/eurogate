package eurogate.gate ;

  class EuroContainer {
      private String _dir ; 
      private String _from ;
      private String _to ;
      private String _param = null;
      private EuroCompanion _ec = null ;
      EuroContainer( String dir , String from , String to , String param ){
        _dir   = dir ;
        _from  = from ;
        _to    = to ;
        _param = param ;
      }
      EuroContainer( String dir , String from , String to ){
        _dir   = dir ;
        _from  = from ;
        _to    = to ;
      }
      void addCompanion( EuroCompanion ec ){
         _ec = ec ;
      }
      EuroCompanion getCompanion(){ return _ec ; }
      String getDirection(){ return _dir ; }
      String getFilename(){ return _dir.equals("put") ? _from : _to ; }
      String getBfid(){ return _from ; }
      String getGroup(){ return _to ; }
      String getParam(){ return _param ; }	
      
      public String toString(){
        if( _dir.equals("put") ){
          return "EC="+_dir+";File="+_from+";group="+_to+";"+_ec ;
        }else if( _dir.equals("get") ){
          return "EC="+_dir+";File="+_to+";bfid="+_from+";"+_ec ;
        }else if( _dir.equals("rm") ){
          return "EC="+_dir+";bfid="+_from+";"+_ec ;
        }else{
           return "EuroContainer ???";
        }
        
      }
   }
 
