/* test EuroStore HSM API function */

#include <stdio.h>
#include <time.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <getline.h>

#include <ES_api.h>
#include <ES_errno.h>

ES_session s;

typedef int (*CMD)(int argc, char **argv);


static int fd;
static int dummyread;

static char lbfid[80];

int myWcallback(int op, void *buff, int len,
	       ES_writelist *current, void *up) {
  switch(op) {
  case ES_OP_INIT:
    printf("Wcallback ES_OP_INIT ");
    break;
  case ES_OP_END:
    printf("Wcallback ES_OP_END ");
    break;
  case ES_OP_FILLBUFFER:
    printf("Wcallback ES_OP_FILLBUFFER buff 0x%X len %d bfid '%s' ",
	   buff, len, current->bfid);
    if (current == NULL) {
      printf("-> no current writelist !\n");
    } else {
      printf("-> result: %d\n", current->result);
    }

    if (dummyread == 0 && fd >= 0) {
      int rbytes;
      if ((rbytes = read(fd, buff, len)) <= 0)
	return(rbytes);
      return(rbytes);
    } else {  /* no special data here */
      return(len);
    }
    break;
#define WS (64*1024)
  case ES_OP_WRITEDATA:
    {
      int FD = *((int *) buff);
      int num = (int) current->size, i = 0, ii, rbytes, want;
      char b[WS];
      
      while(i < num) {
	want = ((num - i) > WS) ? WS : num - i;
	
	if (dummyread == 0 && fd >= 0) {   /* fill buffer with useful data :-) */
	  if ((rbytes = read(fd, b, want)) <= 0)
	    return(ES_LREADFAILED);
	} else {
	  rbytes = want;
	}
	
	if ((ii = write(FD, b, rbytes)) != rbytes)
	  return(ES_TCPIO);
	i += rbytes;
      }
      current->bytesWritten = i;
    }
    return(ES_OK);
  case ES_OP_NEWDATASET:
    printf("Wcallback ES_OP_NEWDATASET bfid '%s' ", current->bfid);
    strcpy(lbfid, current->bfid);
    break;
  case ES_OP_IOCOMPLETED:
    printf("Wcallback ES_OP_IOCOMPLETED iostat %d bfid '%s' ",
	   len, current->bfid);
    break;
  case ES_OP_REMOVEDONE:
    printf("Wcallback ES_OP_REMOVEDONE bfid %s ", current->bfid);
    break;
  case ES_OP_TRANSFERSTART:
    printf("Wcallback ES_OP_TRANSFERSTART bfid '%s' ", current->bfid);
    break;
  default:
    printf("Wcallback UNKNOWN ");
    break;
  }
  if (current == NULL) {
    printf("-> no current writelist !\n");
  } else {
    printf("-> result: %d\n", current->result);
  }
  return(0);
}

int myRcallback(int op, void *buff, int len,
	       ES_readlist *current, void *up) {
  switch(op) {
  case ES_OP_INIT:
    printf("Rcallback ES_OP_INIT\n");
    break;
  case ES_OP_END:
    printf("Rcallback ES_OP_END\n");
    break;
  case ES_OP_FLUSHBUFFER:
    printf("Rcallback ES_OP_FLUSHBUFFER buff 0x%X len %d bfid %s\n",
	   buff, len, current->bfid);
    if (fd >= 0) {
      if (write(fd, buff, len) != len) {
	printf("write error %d (%s)\n", errno, strerror(errno));
	return(-1);
      }
    }
    return(len);
#define RS (64*1024)
  case ES_OP_READDATA:
    {
      int FD = *((int *) buff);
      int len = (int) current->size;
      char b[RS];
      int rbytes, i = 0, want;
      
      while(i < len) {
	want = ((len - i) > RS) ? RS : len - i;
	
	if ((rbytes = read(FD, b, want)) < 0)
	  return(ES_TCPIO);
	if (fd >= 0) {
	  if (write(fd, b, rbytes) != rbytes)
	    return(ES_LWRITEFAILED);
	}
	i += rbytes;
      }
      current->bytesRead = i;
    }
    return(ES_OK);
  case ES_OP_NEWDATASET:
    printf("Rcallback ES_OP_NEWDATASET bfid %s\n", current->bfid);
    break;
  case ES_OP_IOCOMPLETED:
    printf("Rcallback ES_OP_IOCOMPLETED iostat %d bfid %s\n",
	   len, current->bfid);
    break;
  case ES_OP_REMOVEDONE:
    printf("Rcallback ES_OP_REMOVEDONE bfid %s\n", current->bfid);
    break;
  case ES_OP_TRANSFERSTART:
    printf("Rcallback ES_OP_TRANSFERSTART bfid %s\n", current->bfid);
    break;
  default:
    printf("Rcallback UNKNOWN\n");
    break;
  }
  return(0);
}

int myRemcallback(int op, ES_removelist *current, void *up) {
  switch(op) {
  case ES_OP_INIT:
    printf("RMcallback ES_OP_INIT\n");
    break;
  case ES_OP_END:
    printf("RMcallback ES_OP_END\n");
    break;
  case ES_OP_FILLBUFFER:
    printf("RMcallback ES_OP_FILLBUFFER bfid %s\n", current->bfid);
    return(0);
  case ES_OP_NEWDATASET:
    printf("RMcallback ES_OP_NEWDATASET bfid %s\n", current->bfid);
    break;
  case ES_OP_IOCOMPLETED:
    printf("RMcallback ES_OP_IOCOMPLETED bfid %s\n",
	   current->bfid);
    break;
  case ES_OP_REMOVEDONE:
    printf("RMcallback ES_OP_REMOVEDONE bfid %s result %d\n",
	   current->bfid, current->result);
    break;
  case ES_OP_TRANSFERSTART:
    printf("RMcallback ES_OP_TRANSFERSTART bfid %s\n", current->bfid);
    break;
  default:
    printf("RMcallback UNKNOWN\n");
    break;
  }
  return(0);
}

int getString(char *prompt, char *ret) {
  char *rstr;

  printf("%s", prompt); fflush(stdout);
  if ((rstr = fgets(ret, 20, stdin)) != ret)
    return(-1);
  ret[strlen(ret) - 1] = '\0';
  return(0);
}

int getNumber(char *prompt, int *num) {
  int i;
  char buf[80], *rbuf;

  printf("%s", prompt); fflush(stdout);
  if (fgets(buf, 79, stdin) != buf)
    return(-1);
  buf[strlen(buf) - 1] = '\0';
  *num = atoi(buf);
  return(0);
}

void initSession() {
  int i, cserrno;
  char errString[256];

  s = ES_CreateSession("MAIN", &i,
		       ES_PRIV_READ |
		       ES_PRIV_WRITE |
		       ES_PRIV_MODIFY,
		       NULL,
		       -1,
		       &cserrno,
		       errString);
  if (s == NULL) {
    fprintf(stderr, "error creating session %d (%s)\n", 
	    cserrno, errString);
    exit(1);
  }
}

void Exit(int rcode) {
  (void) ES_CloseSession(s);
  exit(rcode);
}

int quitOp(int argc, char **argv) {
  Exit(1);
}

int initOp(int argc, char **argv) {
  initSession();
  return(0);
}


/* write <sgroup> <size|filename> */
int writeOp(int argc, char **argv) {
  ES_writelist *wl;
  char bfid[ES_BFID_STRSIZE];
  char errString[256];

  if (argc != 3) {
    printf("invalid parameter list\n");
    return(0);
  }

  /* write */
  wl = (ES_writelist *) malloc(sizeof(ES_writelist) * 10);
  memset((char *) wl, 0, sizeof(ES_writelist) * 10);

  strcpy(wl[0].storageGroup, argv[1]);
  strcpy(wl[0].migrationPath, "default");
  wl[0].userTag[0] = '\0';

  if (isdigit(argv[2][0])) {
    wl[0].size = atoi(argv[2]);
    dummyread = 1;
  } else {
    struct stat st;
    if ((fd = open(argv[2], O_RDONLY)) < 0) {
      printf("can open '%s' -> %d\n", argv[2], errno);
      free(wl);
      return(0);
    }
    if (fstat(fd, &st) != 0) {
      printf("stat() failure %d (%s)\n", errno, strerror(errno));
      close(fd); fd = -1;
      return(0);
    }
    wl[0].size = st.st_size;
    dummyread = 0;
  }

/*   for(i=0; i<1; i++) { */
/*     wl[i].size = 9000 + (i * 99999); */
/*     strcpy(wl[i].storageGroup, "blackhole"); */
/*     strcpy(wl[i].migrationPath, "default"); */
/*     wl[i].userTag[0] = '\0'; */
/*   } */

  printf("ES_WriteData() return %d (%s)\n",
	 ES_WriteData(s, wl, 1, myWcallback, NULL, NULL, 600, errString),
	 errString);

  if (dummyread == 0 && fd >= 0)
    (void) close(fd);
  fd = -1;
  return(0);
}

int readOp(int argc, char **argv) {
  ES_readlist *rl;
  char bfid[ES_BFID_STRSIZE];
  char errString[256];

  fd = -1;
  if (argc < 2) {
    printf("invalid parameter list\n");
    return(0);
  }

  if (argc == 3) { /* write to a file */
    if ((fd = open(argv[2], O_CREAT | O_WRONLY, 0644)) < 0) {
      printf("error creating '%s' %d (%s)\n",
	     argv[2], errno, strerror(errno));
      return(0);
    }
  }

  /* write */
  rl = (ES_readlist *) malloc(sizeof(ES_readlist) * 10);
  memset((char *) rl, 0, sizeof(ES_readlist) * 10);

  strncpy(rl[0].bfid, argv[1], ES_BFID_STRSIZE);
  rl[0].userTag[0] = '\0';

  printf("ES_ReadData() return %d (%s)\n",
	 ES_ReadData(s, rl, 1, myRcallback, NULL, NULL, 600, errString),
	 errString);

  if (fd >= 0) {
    (void) close(fd);
    fd = -1;
  }

  return(0);
}

int removeOp(int argc, char **argv) {
  ES_removelist *reml;
  char errString[256];

  if (argc != 2) {
    printf("invalid parameter list\n");
    return(0);
  }

  reml = (ES_removelist *) malloc(sizeof(ES_removelist) * 10);

  strncpy(reml[0].bfid, argv[1], ES_BFID_STRSIZE);
  errString[0] = '\0';

  printf("ES_Remove() return %d (%s)\n",
	 ES_Remove(s, reml, 1, myRemcallback, NULL, errString),
	 errString);
  return(0);
}

int loop(int argc, char **argv) {
  ES_writelist *wl;
  ES_readlist *rl;
  ES_removelist *reml;
  char errString[256];
  int fsize = 4444;
  
  if (argc != 2) {
    printf("invalid parameter list\n");
    return(0);
  }

  /* write */
  wl = (ES_writelist *) malloc(sizeof(ES_writelist) * 10);
  memset((char *) wl, 0, sizeof(ES_writelist) * 10);
  reml = (ES_removelist *) malloc(sizeof(ES_removelist) * 10);

  rl = (ES_readlist *) malloc(sizeof(ES_readlist) * 10);
  memset((char *) rl, 0, sizeof(ES_readlist) * 10);

  strcpy(wl[0].storageGroup, argv[1]);
  strcpy(wl[0].migrationPath, "default");
  wl[0].userTag[0] = rl[0].userTag[0] = '\0';
  

  for(;;) {
    wl[0].size = fsize;
    (void) ES_WriteData(s, wl, 1, myWcallback, NULL, NULL, 600, errString);
    strcpy(rl[0].bfid, lbfid);
    (void) ES_ReadData(s, rl, 1, myRcallback, NULL, NULL, 600, errString);
    strncpy(reml[0].bfid, lbfid, ES_BFID_STRSIZE);
    (void) ES_Remove(s, reml, 1, myRemcallback, NULL, errString);

    fsize += 1024;

    if (fsize > 1024*512)
      fsize = 4444;
  }
}

int helpOp(int argc, char **argv) {
  printf("available commands and parameters\n");
  printf("  (w)rite <sgroup> <size|file>\n");
  printf("  (r)ead <bfid> <filename> (filename -> copy to)\n");
  printf("  remove <bfid>\n");
  printf("  (i)init\n");
  printf("  quit\n");
  printf("  help|?\n");
  return(0);
}


/* helper functions .... what else :--)) */
void freeToken(char **t)
{
  char **tt = t;

  if (!tt)
    return;
  while(*tt) {
    (void) free(*tt);
    tt++;
  }
  if (t)
    (void) free(t);
}


char **splitToken(char *p, int *ntoks, char *delim)
{
  int len, i;
  char *ptr = (char *) 0;
  char *tok, **t, *last;

  *ntoks = 0;
  if (strlen(p) <= 0)
    return((char **) 0);
  if ((ptr = strdup(p)) == NULL)
    return((char **) 0);
  if ((tok = (char *) strtok_r(ptr, delim, &last)) == (char *) 0)
    goto bad;
  (*ntoks)++;
  while (strtok_r(NULL, delim, &last))
    (*ntoks)++;
  if ((t = (char **) malloc((*ntoks + 1) * sizeof(*t))) == NULL)
    goto bad;
  strcpy(ptr, p);
  t[0] = strdup((const char *) strtok_r(ptr, delim, &last));
  for(i=1; i<(*ntoks); i++) {
    t[i] = strdup((const char *) strtok_r(NULL, delim, &last));
  }
  (void) free(ptr);
  t[i] = (char *) 0;
  return(t);

bad:
  if (ptr)
    (void) free(ptr);
  *ntoks = 0;
  return((char **) 0);
}

static struct {
  char cmd[20];
  CMD  func;
} cmdList[] = {
  {"write", writeOp},
  {"w", writeOp},
  {"loop", Loop},
  {"read", readOp},
  {"r", readOp},
  {"remove", removeOp},
  {"init", initOp},
  {"i", initOp},
  {"quit", quitOp},
  {"help", helpOp},
  {"?", helpOp},
  {NULL, NULL},
};

main(int argc, char **argv) {
  int cserrno, i;
  ES_removelist *reml;
  ES_readlist *rl;
  char bfid[ES_BFID_STRSIZE];
  char cmd[256], *rcmd;
  char **tok = (char **) 0;
  int ntok;


  fd = -1;
  while(666) {
    rcmd = (char *) getline("ES-Test: ");
    if (rcmd[0] == '\0')
      Exit(1);
    if (tok)
      freeToken(tok);
    strncpy(cmd, rcmd, 255);
/*
    cmd[strlen(cmd) - 1] = '\0'; 
*/

    gl_histadd(rcmd);
    if ((tok = splitToken(cmd, &ntok, " ")) != (char **) 0 &&
	ntok >= 1) {
      for(i=0; cmdList[i].cmd[0] != '\0'; i++) {
	if (strcmp(cmdList[i].cmd, tok[0]) == 0) {  /* found command */
	  if ((*cmdList[i].func)(ntok, tok) != 0)
	    Exit(1);
	  break;
	}
      }
      if (cmdList[i].cmd[0] == '\0')
	printf("unknown command '%s'\n", tok[0]);
    }
  }

  /* read */
/*   rl = (ES_readlist *) malloc(sizeof(ES_readlist) * 10); */
/*   for(i=0; i<2; i++) { */
/*     (void) sprintf(bfid, "EUROSTOREBFID-DUMMY-%015d-%03d", */
/* 		   time((time_t) 0), i); */
/*     bfid[ES_BFID_STRSIZE - 1] = '\0'; */
/*     strncpy(rl[i].bfid, bfid, ES_BFID_STRSIZE); */
/*   } */

/*   printf("ES_ReadData() return %d\n", */
/* 	 ES_ReadData(s, rl, 2, myRcallback, NULL, NULL, 300)); */

  
  /*remove */
/*   reml = (ES_removelist *) malloc(sizeof(ES_removelist) * 10); */
/*   for(i=0; i<2; i++) { */
/*     (void) sprintf(bfid, "EUROSTOREBFID-DUMMY-%015d-%03d", */
/* 		   time((time_t) 0), i); */
/*     bfid[ES_BFID_STRSIZE - 1] = '\0'; */
/*     strncpy(reml[i].bfid, bfid, ES_BFID_STRSIZE); */
/*   } */

/*   printf("ES_Remove() return %d\n", */
/* 	 ES_Remove(s, reml, 2, myRemcallback, NULL)); */


}

