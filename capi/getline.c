
#include <stdio.h>

#include "getline.h"


char * getline( char * prompt ){
   static char line[256] ;
   printf( "%s " , prompt ) ;
   fgets( line , sizeof(line) , stdin ) ;
   return line ;

}
 
