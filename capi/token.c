/* tokenize string (space sep) honoring quotes */

#include <stdio.h>

#define MAXTOKENS 50

static char *tok[MAXTOKENS];

#define TSEP ' '
#define TQUOTE '"'
#ifndef True
#define True 1
#define False 0
#endif

#define TBEGIN 1
#define TEND 2
#define TEAT

int tokenize(char *in, char **tok) {
  int i, ntok = 0, len = strlen(in);

  /* clear old stuff */
  for(i=0; i<MAXTOKENS; i++)
    tok[i] = (char *) 0;

  /* eat up initial leading spaces */
  for(i=0; i<len; i++)
    if (in[i] != TSEP)
      break;

  while(i<len) {
    if (in[i] == TQUOTE) {
      tok[ntok++] = &in[++i];
      if (ntok >= MAXTOKENS)
	break;
      for(;i<len; i++)
	if (in[i] == TQUOTE)
	  break;
      in[i++] = '\0';
      /* eat up spaces */
      for(;i<MAXTOKENS; i++)
	if (in[i] != TSEP)
	  break;
    } else {
      tok[ntok++] = &in[i];
      if (ntok >= MAXTOKENS)
	break;
      for(; i<len; i++)
	if (in[i] == TSEP)
	  break;
      in[i++] = '\0';
      /* hunt the end TSEP */
      for(; i<len; i++)
	if (in[i] != TSEP)
	  break;
    }
  }
  /* in[i] = '\0'; */
  return(ntok);
}

 main(int argc, char **argv) {
   char msg[2048];
   char *tok[50];
   int ntok, i;

   printf("size: %d\n", sizeof(msg));
   while(666) {
     if (fgets(msg, 2000, stdin) == NULL)
       exit(1);
     msg[strlen(msg) - 1] = '\0';

     printf("MSG '%s'\n", msg);
     ntok = tokenize(msg, tok);
     for(i=0; i<ntok; i++) {
       printf("TOK[%d] '%s'\n", i, tok[i]);
     }
   }
 }
