
#include <stdio.h>

#include "getline.h"


char * getline( char * prompt ){
   static char line[256] ;
   char * result ;
   printf( "%s " , prompt ) ;
   result = fgets( line , sizeof(line) , stdin ) ;
   return  result == NULL ? "" : result ;

}
 
