/**
 *  main part of EuroStore HSM API implementation
 *
 *  @author  M.Gasthuber/DESY
 *  @version 0.1  8 May 98
 *  copyright DESY/Hamburg
 *
 */

#include <ES_api.h>
#include <ES_errno.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <errno.h>
#ifdef linux
#include <sys/time.h>
#include <sys/types.h>
#define MAXHOSTNAMELEN       256 
#endif
#ifndef __STDC__
#include <varargs.h>
#else
#include <stdarg.h>
#endif

#define MAX_W_CHUNKSIZE 1024*64
#define MAX_R_CHUNKSIZE 1024*64
#define MAXMSGLEN 2048  /* max # of bytes in messages received from door */

#define DOOR_MSG 1

#define DOOR_PORT 28000

#define DOORSOCKENV "DOORADDR"

#define HELLO_STR "SESSION WELCOME 0.1"
#define CLOSE_STR "SESSION CLOSE"
#define SESSACK_OK "WELCOME"
#define CMDACK_OK  "OK"
#define CMDACK_NOK "NOK"

#define SESSACK_TIMEOUT 20  /* wait 20 seconds for Door session init ack */
#define CMD_TIMEOUT 600     /* wait 10 minutes for cmd acks */
#define MACK_TIMEOUT 30     /* wait 10 seconds for Mover ack after transfer */
#define BBACK_TIMEOUT 40    /* wait 20 seconds for BB transfer ack */
#define REMOVE_TIMEOUT 40   /* wait 40 seconds for BB to remove a file */

/* Mover connection stuff definitions */
#define MOVER_HELLO_TIMEOUT 20
#define MOVER_HELLO_STR "Hello-EuroStore-Client"

#define DEBUG_ES_API 1    /* define this to get debug output to stderr */

#define MAXTOKENS 50

#define TSEP ' '
#define TQUOTE '"'
#ifndef True
#define True 1
#define False 0
#endif



/* temporary definition of Session structure, need extension for security */
/*
 * general notes for the session structure
 * this structure will be create in a first (initial) process or thread
 * any other API function might be called from a different process or thread
 * so we only set (write) to members of this structure while in
 * ES_CreateSession() and all other API functions only read from this
 * members
 */
typedef struct Session {
  char *storename;
  int  key;
  int  privs;
  unsigned int id_id;
  char *doorHost;
  int doorPort;
  int doorSocket;  /* opened in SessionCreate */
} Session;

typedef struct userRequest {
  int result;
  char errString[ES_ERRSTRLEN];
  char bfid[ES_BFID_STRSIZE];
  unsigned int requestId;
  unsigned64 bytesMoved;
  unsigned64 size;
} userRequest;

typedef struct context {
  Session *s;
  userRequest *r;
  char idStr[128];
  char stateStr[256];
  char msg[MAXMSGLEN];  /* holds the last received message */
  char *tok[MAXTOKENS + 1];
  int ntok;
  int doorSock;
  int listenPort;
  int listenSock;
  int moverSock;
  unsigned int state;  /* current state */
  int error;
  char *userErrorStr;  /* supplied by caller if needed */
  /*unsigned64 bytesMoved;*/
} context;

#define ST_UNKNOWN 0x0       /* unknown request state */
#define ST_PREINIT 0x1       /* before/during session create called */
#define ST_INIT 0x2          /* session initialized - ready to send commands */
#define ST_CMDSEND 0x4       /* command sent -> wait for 1 ACK */
#define ST_WAITPREACK 0x8    /* wait for pre-ack of command */
#define ST_PREACK 0x10       /* received 1 ACK -> wait for final ACK */
#define ST_MOVERWAIT 0x20    /* wait for mover connection */
#define ST_MOVERACCEPT 0x40  /* mover connection accepted */
#define ST_MOVERCONN 0x80    /* mover has connected */
#define ST_WAITMACK 0x100    /* wait for the Mover (MACK) message */
#define ST_WAITACK 0x200     /* wait for the final command ack */
#define ST_MOVERACK 0x400    /* received mover ACK */
#define ST_FINALACK 0x800    /* received final (BB) ACK */
#define ST_DONE 0x1000       /* command done */
#define ST_CANCELLED 0x2000  /* request has been cancelled by Door */


/* function type for the dispatch callbacks */
typedef int (*dfunc)(int fd, int tmOccured, context *c);


static int receiveAck(int, int, context *);
static int finalAck(int, int, context *);
static int writeUTF(int fd, char *str);

#ifndef __STDC__
static void DPrint(fmt, va_alist)
char *fmt;
va_dcl
#else
static void DPrint(char *fmt, ...)
#endif
{
  va_list args;

#ifndef __STDC__
  va_start(args);
#else
  va_start(args, fmt);
#endif

#ifdef DEBUG_ES_API
  (void) vfprintf(stderr, fmt, args);
#endif

  va_end(args);
}

static int tokenize(char *in, char **tok) {
  int i, ntok = 0, len = strlen(in);

  if (in == NULL || in[0] == '\0')
    return(0);
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

static void newContext(context *c) {
  if (c == (context *) 0)
    return;
  memset((char *) c, 0, sizeof(context));
  c->doorSock = c->moverSock = c->listenPort = c->listenSock -1;
}

static void stateString(context *c) {
  c->stateStr[0] = '\0';
  if (c->state & ST_UNKNOWN)
    strcat(c->stateStr, "UNKNOWN ");
  if (c->state & ST_PREINIT)
    strcat(c->stateStr, "PREINIT ");
  if (c->state & ST_INIT)
    strcat(c->stateStr, "INIT ");
  if (c->state & ST_CMDSEND)
    strcat(c->stateStr, "CMDSEND ");
  if (c->state & ST_WAITPREACK)
    strcat(c->stateStr, "WAITPREACK ");
  if (c->state & ST_PREACK)
    strcat(c->stateStr, "PREACK ");
  if (c->state & ST_MOVERWAIT)
    strcat(c->stateStr, "MOVERWAIT ");
  if (c->state & ST_MOVERACCEPT)
    strcat(c->stateStr, "MOVERACCEPT ");
  if (c->state & ST_MOVERCONN)
    strcat(c->stateStr, "MOVERCONN ");
  if (c->state & ST_WAITMACK)
    strcat(c->stateStr, "WAITMACK ");
  if (c->state & ST_WAITACK)
    strcat(c->stateStr, "WAITACK ");
  if (c->state & ST_MOVERACK)
    strcat(c->stateStr, "MOVERACK ");
  if (c->state & ST_FINALACK)
    strcat(c->stateStr, "FINALACK ");
  if (c->state & ST_DONE)
    strcat(c->stateStr, "DONE ");
  if (c->state & ST_CANCELLED)
    strcat(c->stateStr, "CANCELLED ");
}

static int validateSession(ES_session session) {
  Session *s = (Session *) session;

  if (s == (Session *) 0)
    return(-1);
  /* here we just check if we can access the given structure !!!! */
  /* this function needs heavy extension for the final version */
  if (s->key != -9999)
    return(-1);
  return(0);
}

/* generic dispatch function listening on the given (max 2) sockets */
int dispatch(int fd1, int *tm1, dfunc f1,
	     int fd2, int *tm2, dfunc f2,
	     context *c) {
  struct timeval tv, *tvp;
  fd_set fdset;
  int maxfd = -1, sret;
  time_t start, end;
  int tmAct = -1, ret;


startDispatch:
  FD_ZERO(&fdset);
  tv.tv_usec = 0;
  tv.tv_sec = -1;
  if (fd1 >= 0) {
    FD_SET(fd1, &fdset);
    maxfd = fd1;
    if (tm1 && *tm1 >= 0) {
      tv.tv_sec = *tm1;
      tmAct = 1;
    }
  }
  if (fd2 >= 0) {
    FD_SET(fd2, &fdset);
    if (fd2 > maxfd)
      maxfd = fd2;
    if (tm2 && *tm2 >= 0) {
      if (*tm2 < *tm1) {
	tv.tv_sec = *tm2;
	tmAct = 2;
      }
    }
  }
  if (tv.tv_sec >= 0)
    tvp = &tv;
  else
    tvp = (struct timeval *) 0;

  start = end = time((time_t *) 0);

  sret = select(maxfd+2, &fdset, (fd_set *) 0, (fd_set *) 0, tvp);

  end = time((time_t *) 0);

  if (sret == 0) { /* timeout */
    if (tmAct == 1) {  /* fd1 times out */
      DPrint("fd1 TIMEOUT\n");
      if (tm1)
	*tm1 = -2;
      if ((ret = (*f1)(fd1, 1, c)) == ES_CONTINUE)
	goto startDispatch;
      return((ret == ES_OK) ? ES_TIMEOUT : ret);
    } else if (tmAct == 2) { /* fd2 time out */
      DPrint("fd2 TIMEOUT\n");
      if (tm2)
	*tm2 = -2;
      if ((ret = (*f2)(fd2, 1, c)) == ES_CONTINUE)
	goto startDispatch;
      return((ret == ES_OK) ? ES_TIMEOUT : ret);
    } else {
      DPrint("unknown timeout source %d\n", tmAct);
      return(ES_TCPIO);
    }
  } else if (sret < 0) {
    DPrint("select error %d (%s)\n", errno, strerror(errno));
    return(ES_TCPIO);
  }

  /* adjust timeouts */
  if (tm1 && *tm1 >= 0)
    *tm1 -= (end - start);
  if (tm2 && *tm2 >= 0)
    *tm2 -= (end - start);

  /* at least one fd is ready */
  if (fd1 >= 0 && FD_ISSET(fd1, &fdset)) {
    if ((ret = (*f1)(fd1, 0, c)) == ES_CONTINUE)
      goto startDispatch;
    if (ret == ES_CONNEOF)
      return(-1);  /* indicate fd1 EOF condition (crazy method !!) */
    return(ret);
  } else if (fd2 >= 0 && FD_ISSET(fd2, &fdset)) {
    if ((ret = (*f2)(fd2, 0, c)) == ES_CONTINUE)
      goto startDispatch;
    if (ret == ES_CONNEOF)
      return(-2);
    return(ret);
  } else {
    DPrint("unkwown fd ready - confused select()\n");
    return(ES_TCPIO);
  }
}

/* generate unique id for the mover connection */
void makeIdentification(context *c) {
  (void) sprintf(c->idStr, "ES-API-V0.1(%d,%d)%d",
		 getuid(), getgid(), c->s->id_id);
  c->s->id_id++;
}

static int readStream(int fd, void *buf, int bytes) {
  char *ptr = (char *) buf;
  int iread = 0, i, toread = bytes;
  int _lCount = 0;

  while(666) {
    /*if ((i = read(fd, ptr, toread)) <= 0)*/
    if ((i = recv(fd, ptr, toread, 0)) <= 0)
      return(i);
    iread += i;
    if (iread >= bytes)
      return(iread);
    ptr += i;
    toread -= i;
    _lCount++;
  }
}

/* MIGHT NEED TO BE CHANGED ON LITTLE ENDIAN MACHINES ...
   we use the JAVA DataInputStream.readUTF() method */
static int readUTF(int fd, char *buff, int len, int timeout) {
  int sret, i;
  fd_set fdset;
  struct timeval tv, *tvp;
  unsigned short mlen = 0, nb = 0;
  time_t start, end;
  int tmActive = 0;

  memset(buff, 0, len);
  start = time((time_t *) 0);
  while(666) {
    if (timeout >= 0) {
      FD_ZERO(&fdset);
      FD_SET(fd, &fdset);
      tv.tv_sec = timeout;
      tv.tv_usec = 0;
      tvp = &tv;
      tmActive = 1;
    } else {
      tvp = (struct timeval *) 0;
    }

    if (tvp != (struct timeval *) 0) {
      sret = select(fd + 2, &fdset, (fd_set *) 0, (fd_set *) 0, tvp);
    } else {
      sret = 1;  /* fd IS ready !!! (checked prior) */
    }
    /*DPrint("readUTF:select returns %d  errno %d\n", sret, errno); */
    
    if (sret == 1) { /* something arrives */
      if (mlen == 0) { /* read length of message bytes */
	if ((i = readStream(fd, &mlen, 2)) != 2) {
	  if (i == 0) {
	    DPrint("readUTF: EOF on fd %d\n", fd);
	    return(ES_CONNEOF);
	  } else {
	    DPrint("readUTF: messagelen read failed %d (%d) -> %u\n",
		   i, errno, mlen);
	    return(ES_TCPIO);
	  }
	}
        mlen = htons( mlen) ;
	DPrint("readUTF: message len %d\n", mlen);
	if (mlen == 0)
	  return(ES_BADMESSAGE);
	if (mlen > len) {
	  DPrint("message too large !!! (%u)\n", mlen);
	  mlen = (unsigned short) len;
	}
      } else { /* read the message bytes itself */
	if ((i = readStream(fd, buff, mlen)) != mlen) {
	  DPrint("readUTF: message read failed %d (%d)\n", i, errno);
	  return(ES_TCPIO);
	}
	buff[i] = '\0';
	DPrint("readUTF: message -> '%s'\n", buff);
	return(ES_OK);
      }
    } else if (sret == 0) {  /* timeout */
      return(ES_TIMEOUT);
    } else { /* other select error */
      return(ES_TCPIO);
    }
    if (tmActive) {
      end = time((time_t *) 0);
      if ((timeout -= (end - start)) <= 0)
	return(ES_TIMEOUT);
    }
  }
}

static int readUTFToken(int sock, int timeout,
			char *msg, int msgLen, char **tok, int *ntok) {
  /*char msg[512];*/
  int len;
  char **t;

  /**tok = (char **) 0;*/
  *ntok = 0;
  if ((len = readUTF(sock, msg, msgLen, timeout)) != ES_OK) {
    DPrint("readUTF failed (%d)\n", len);
    return(len);
  }
  if ((*ntok = tokenize(msg, tok)) < 0)
    return(ES_BADMESSAGE);
  return(ES_OK);
}

static int createMoverSocket(context *c) {
  struct sockaddr_in saddr;
  int sock, addrLen;

  memset((char *) &saddr, 0, sizeof(saddr));
  saddr.sin_family = AF_INET;
  saddr.sin_port = 0;
  saddr.sin_addr.s_addr = htonl(INADDR_ANY);
  addrLen = sizeof(saddr);
  if ((sock = socket(AF_INET, SOCK_STREAM, 0)) > 0 &&
      bind(sock, (struct sockaddr *) &saddr, sizeof(saddr)) == 0 &&
      listen(sock, 5) == 0 &&
      getsockname(sock, (struct sockaddr *) &saddr, &addrLen) == 0) {
    c->listenPort = htons(saddr.sin_port);
    c->listenSock = sock;
    return(ES_OK);
  } else {
    if (sock >= 0)
      (void) close(sock);
    if (c->userErrorStr)
      (void) sprintf(c->userErrorStr, 
		     "create/bind/listen the Mover socket failed %d (%s)",
		     errno, strerror(errno));
    c->error = ES_TCPIO;
    return(ES_TCPIO);
  }
}

static int identifyMover(context *c /*int fd, char *idStr*/) {
  char msg[1024];
  char *tok[MAXTOKENS];
  int ntok, i, ernum;

  if ((i = readUTFToken(c->moverSock, MOVER_HELLO_TIMEOUT,
			c->msg, sizeof(c->msg), c->tok, &c->ntok)) != ES_OK) {
    return(i);
  } else if (c->ntok != 2) {
    ernum = ES_BADMESSAGE;
  } else if (strcmp(c->tok[0], MOVER_HELLO_STR) != 0 ||
	     strcmp(c->tok[1], c->idStr) != 0) {
    ernum = ES_BADMOVER;
  } else {
    ernum = ES_OK;
  }
  /*freeToken(&tok);*/
  return(ernum);
}

/* emergency cancel - don't wait for reply here */
static int cancelRequest(context *c, int beQuite) {
  char msg[1024];
  int ernum;
  int tmout = CMD_TIMEOUT;

  (void) sprintf(msg, "CANCEL FORCE %u", c->r->requestId);
  /*
  (void) writeUTF(c->doorSock, msg, strlen(msg));
  */
  (void) writeUTF(c->doorSock, msg );

  return(0);
#if 0
  if (writeUTF(c->doorSock, msg, strlen(msg)) != strlen(msg)) {
    DPrint("writeUTF error on doorSocket %d (%s)\n", errno, strerror(errno));
    if (!beQuite) {
      (void) sprintf(c->r->errString,
		     "Error sending CANCEL command %d (%s)",
		     errno, strerror(errno));
      c->error = ES_TCPIO;
    }
    return(-1);
  }
  
  /*clToken(c);*/
  if ((ernum = dispatch(c->doorSock, &tmout, receiveAck, -1, NULL, NULL,
			c)) != ES_OK) {
    DPrint("CMD Ack failed ");
    if (c->tok)
      printToken(c->tok, 0);
    else
      DPrint("(NO TOKENS)\n");
    
    return(-1);
  }
  return(0);
#endif
}

static int moverAccept(int fd, int tmOccured, context *c) {
  int sret, i;
  int ernum, nsock, addrLen;
  struct sockaddr_in saddr;

  if (!(c->state & ST_MOVERWAIT))
    return(ES_BADSTATE);
  if (tmOccured) {
    DPrint("timeout waiting for Mover connection\n");
    (void) sprintf(c->r->errString,
		   "timeout waiting for Mover connection");
    return((c->error = ES_TIMEOUT));
  }

  if (!(c->state & ST_MOVERACCEPT)) {  /* aleady accepted ? */
    addrLen = sizeof(saddr);
    if ((nsock = accept(c->listenSock, (struct sockaddr *) &saddr,
			&addrLen)) < 0) {
      DPrint("accept error %d\n", errno);
      return(ES_CONTINUE);
    }
    c->moverSock = nsock;
    c->state |= ST_MOVERACCEPT;
    DPrint("Mover connected\n");
  }
  if (c->state & ST_MOVERACCEPT) { /* already sccepted -> verify */
    if (identifyMover(c /*c->moverSock, c->idStr*/) == ES_OK) {
      DPrint("Mover identified\n");
      c->state |= ST_MOVERCONN;
      return(ES_OK);
    } else {   /* not our mover !!!!! */
      (void) close(c->moverSock);
      c->moverSock = -1;
      c->state &= ~ST_MOVERACCEPT;
      return(ES_CONTINUE);
    }
  }
}

/* concat tokens (back to string) from given starting position */
static char *concatToken(char **tok, int start) {
  int i, len = 0;
  char *result;

  for(i=start; tok[i] != (char *) 0; i++) {
    len += (strlen(tok[i]) + 1);
  }
  if ((result = malloc(len)) == NULL) {
    return(NULL);
  }
  result[0] = '\0';
  for(i=start; tok[i] != (char *) 0; i++) {
    strcat(result, tok[i]);
    if (tok[i+1] != (char *) 0)
      strcat(result, " ");
  }
  return(result);
}

static int printToken(char **tok, int start) {
  int i;

  for(i=start; tok[i] != (char *) 0; i++) {
    DPrint("%s ", tok[i]);
  }
  DPrint("\n");
}

static int doorInitSession(Session *s, char *errStr) {
  struct sockaddr_in saddr;
  char msg[2048];
  int sock, ntok, ernum;
  char *tok[MAXTOKENS], *rstr;
  struct hostent *he;

  /* open network connection */
  if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    if (errStr)
      (void) sprintf(errStr, "socket creation failed %d (%s)",
		     errno, strerror(errno));
    return(ES_SOCKERR);
  }
  memset((char *) &saddr, 0, sizeof(saddr));
  saddr.sin_family = AF_INET;
  if (s->doorPort > 0)
    saddr.sin_port = htons((unsigned short) s->doorPort);
  else
    saddr.sin_port = DOOR_PORT;
  if (s->doorHost != (char *) 0 &&
      (he = gethostbyname(s->doorHost)) != NULL)
    memcpy((char *) &saddr.sin_addr.s_addr, he->h_addr, he->h_length);
  else
    saddr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  /* put here code to resolve given hostname of door and convert to s_addr */

  if (connect(sock, (struct sockaddr *) &saddr, sizeof(saddr)) < 0) {
    if (errStr)
      (void) sprintf(errStr, "DOOR (%s:%d) connect failed %d (%s)",
		     s->doorHost, s->doorPort, errno, strerror(errno));
    (void) close(sock);
    return(ES_TCPCONNECT);
  }

  /* send hello command to start session on the Door side */
  if (writeUTF(sock, HELLO_STR) < 0) {
    if (errStr)
      (void) sprintf(errStr, "DOOR (%s:%d) write UTF failed %d (%s)",
		     s->doorHost, s->doorPort, errno, strerror(errno));
    (void) close(sock);
    return(ES_TCPIO);
  }

  if ((ernum = readUTFToken(sock, SESSACK_TIMEOUT, msg,
			    sizeof(msg), tok, &ntok)) != ES_OK) {
    if (errStr)
      (void) sprintf(errStr, "DOOR (%s:%d) read UTF failed %d (%s)",
		     s->doorHost, s->doorPort, errno, strerror(errno));
    (void) close(sock);
    /*freeToken(&tok);*/
    return(ernum);
  }

  if (strcmp(tok[0], CMDACK_OK) != 0) {
    if (errStr && ntok >= 3) {
      DPrint("SESSION INIT FAILED: %d '%s'\n", atoi(tok[1]), tok[2]);
      strcpy(errStr, tok[2]);
    }
    (void) close(sock);
    return(ES_INVSESSION);
  }
  /*DPrint("session established");*/
  s->doorSocket = sock;
  DPrint("Door Session initialized\n");
  return(ES_OK);
}

static int doorConnect(context *c) {
  if (c) { /* called by !init function */
    c->doorSock = c->s->doorSocket;
    return((c->doorSock >= 0) ? ES_OK : ES_INVSESSION);
  }
  return(ES_INVSESSION);
}

ES_session ES_CreateSession(const char *Store,
			    const void *Key,
			    const int Privs,
			    const char *doorHostName,
			    const int doorPortNumber,
			    int *ES_errno,
			    char *eStr) {
  Session *s;
  char *env = (char *) 0;
  int ernum;

  if (Store == (char *) 0 ||
      Store[0] == '\0' ||
      Key == (void *) 0 ||
      Privs & (ES_PRIV_READ | ES_PRIV_WRITE | ES_PRIV_MODIFY) == 0) {
    if (ES_errno != NULL)
      *ES_errno = ES_BADPARAM;
    return((ES_session) 0);
  }
  s = (Session *) malloc(sizeof(Session));
  if (s == (Session *) 0) {
    if (ES_errno != NULL)
      *ES_errno = ES_NOMEM;
    return((ES_session) 0);
  }
  memset((char *) s, 0, sizeof(Session));
  s->storename = strdup(Store);
  s->privs = Privs;
  s->key = -9999;  /* temp. stuff */
  s->id_id = (unsigned int) time((time_t *) 0);
  s->doorSocket = -1;
  if (ES_errno != NULL)
    *ES_errno = ES_OK;

  /*
   * look for the environment if the Door to connect to is specified
   * format should be <hostname>:<portnumber>
   */
  if (doorHostName == (char *) 0 ||
      doorPortNumber <= 0) {
    if ((env = getenv(DOORSOCKENV)) != NULL) {
      char *en = strdup(env);
      char *delim;
      
      if ((delim = (char *) index(en, ':')) != NULL) {
	*delim = '\0';
	s->doorHost = strdup(en);
	s->doorPort = atoi(++delim);
	DPrint("using DOOR <%s:%d>\n", s->doorHost, s->doorPort);
      }
      free(en);
    }
  } else {
      s->doorHost = strdup(doorHostName);
      s->doorPort = doorPortNumber;
  }

  /* connect to door and init session */
  if ((ernum = doorInitSession(s, eStr)) != 0) {
    DPrint("door connection failed %d\n", ernum);
    (void) free(s);
    return((ES_session) 0);
  }


  return((ES_session) s);
}


/* MIGHT NEED TO BE CHANGED ON LITTLE ENDIAN MACHINES ...
   we use the JAVA DataInputStream.readUTF() method */
static int writeUTF(int fd, char *str) {
  unsigned short len = strlen(str);
  unsigned short nlen = htons(len);

  if (len > 0) {
    if (write(fd, &nlen, 2) != 2 ||
	write(fd, str, len) != len)
      return(-1);
  }
  DPrint("writeUTF: sent '%s'\n", str);
  return(len);
}

static int getHostName(char *hn, char *hName) {
  if (hn != (char *) 0 && strlen(hn) <= MAXHOSTNAMELEN) {
    strcpy(hName, hn);
    return(0);
  }
  if (gethostname(hName, MAXHOSTNAMELEN) == 0)
    return(0);
  return(-1);
}

static int receiveAck(int fd, int timeoutOcc, context *c) {
  int ernum;

  if ((ernum = readUTFToken(fd, -1, c->msg,
			    sizeof(c->msg), c->tok, &c->ntok)) != ES_OK) {
    c->error = ernum;
    return(ernum);
  }

  DPrint("receiveAck: read "); printToken(c->tok, 0);

  if (c->ntok <= 0) {
    c->error = ES_BADMESSAGE;
    return(ES_BADMESSAGE);
  }

  if (strcmp(c->tok[0], CMDACK_OK) != 0) { /* not OK ! */
    if (c->ntok >= 3 && c->state & ST_WAITPREACK) {
      strncpy(c->r->errString, c->tok[2], ES_ERRSTRLEN);
    }
    return((c->error = atoi(c->tok[1])));
  }
  /* command reply comes with "OK" .... fine */
  if (c->state & ST_WAITPREACK)
    c->state |= ST_PREACK;
  return(ES_OK);  
}

static void closeDoor(int sock) {
  if (sock >= 0) {
    (void) writeUTF(sock, CLOSE_STR);
    (void) close(sock);
  }
  DPrint("door session closed (%d)\n", sock);
}

static int receiveMover(int fd, int tmOccured, context *c) {
  int ernum;

  if (tmOccured)
    return(ES_TIMEOUT);

  if ((ernum = readUTFToken(fd, -1, c->msg, sizeof(c->msg),
			    c->tok, &c->ntok)) != ES_OK) {
    c->error = ernum;
    return(ernum);
  }

  DPrint("receiveMover: read "); printToken(c->tok, 0);

  if (c->ntok <= 0) {
    return((c->error = ES_BADMESSAGE));
  }

  if (strcmp(c->tok[0], "MACK") == 0) {
    if (c->state & ST_WAITMACK &&
	strcmp(c->idStr, c->tok[1]) == 0) {
      ernum = atoi(c->tok[2]);
      if (ernum == 0 && c->r->bytesMoved == atoll(c->tok[3])) {
	DPrint("received MACK -> OK\n");
	c->state |= ST_MOVERACK;
	if (c->state & ST_FINALACK)
	  c->state |= ST_DONE;
	return(ES_OK);
      } else if (ernum != ES_OK) {
	strncpy(c->r->errString, c->tok[3], ES_ERRSTRLEN);
	return((c->error = ernum));
      }
    }
    return(ES_CONTINUE);
  } else {
    return(ES_CONTINUE);
  }
}

static int receiveDoor(int fd, int tmOccured, context *c) {
  int ernum;
  char *rstr;

  DPrint("receiveDoor called (%d)\n", tmOccured);

  if (tmOccured) {
    return(ES_TIMEOUT);
  }

  if ((ernum = readUTFToken(fd, -1, c->msg, sizeof(c->msg),
			    c->tok, &c->ntok)) != ES_OK) {
    if (ernum == ES_TCPIO ||
	ernum == ES_CONNEOF ||
	ernum == ES_BADMESSAGE)
      c->error = ernum = ES_INVSESSION;
    else
      c->error = ernum;
    return(ernum);
  }

  stateString(c);
  DPrint("receiveDoor: read "); printToken(c->tok, 0);
  DPrint("receiveDoor: current state: %s\n", c->stateStr);

  if (c->ntok <= 0) {
    c->error = ES_BADMESSAGE;
    return(ES_BADMESSAGE);
  }

  /* switch depending on current state */
  if (strcmp(c->tok[0], CMDACK_OK) == 0) {
    if (c->ntok != 4) {
      DPrint("receiveDoor: unknown 'OK' command\n");
    } else if (c->state & ST_CMDSEND && 
	       c->state & ST_WAITPREACK &&
	       !(c->state & ST_PREACK)) {
      /* check client identification */
      if (strcmp(c->tok[3], c->idStr) == 0) {
	c->state |= ST_PREACK;
	DPrint("receiveDoor: PREACK receive OK\n");
	return(ES_OK);
      } else {
	DPrint("receiveDoor: unwanted PREACK '%s' waited for '%s'\n",
	       c->tok[3], c->idStr);
      }
    }
    return(ES_CONTINUE);
  } else if (strcmp(c->tok[0], CMDACK_NOK) == 0) {
    if (c->ntok >= 3) {
      char *rstr = concatToken(c->tok, 2);
      if (rstr) {
	strncpy(c->r->errString, rstr, ES_ERRSTRLEN);
	(void) free(rstr);
      }
    }
    return((c->error = atoi(c->tok[1])));
  } else if (strcmp(c->tok[0], "CANCEL") == 0) {
    DPrint("CANCEL message (%d)\n", c->ntok);
    if (c->ntok >= 3) {
      if (c->r->requestId == (unsigned int) atol(c->tok[1])) {
	char *rstr = concatToken(c->tok, 3);
	c->state |= (ST_CANCELLED & ST_DONE);
	c->error = atoi(c->tok[2]);
	if (c->error == ES_OK)  /* did we get a proper error ? */
	  c->error = ES_ABORT;
	if (rstr) {
	  strcpy(c->r->errString, rstr);
	  (void) free(rstr);
	} else {
	  strcpy(c->r->errString, "unkown CANCEL reason");
	}
	DPrint("CANCEL request %u (%d)\n", c->r->requestId, c->error);
	return(c->error);
      }
    }
    return(ES_CONTINUE);
  } else if (strcmp(c->tok[0], "BBACK") == 0) {
    DPrint("BBACK detect (%d) '%s'", c->r->requestId, c->stateStr);
    if (c->state & ST_WAITACK && !(c->state & ST_DONE) &&
	c->r->requestId == (unsigned int) atol(c->tok[1])) {
      DPrint("BBACK state checked %s\n", c->tok[1]);
      c->state |= ST_FINALACK;
      if (c->state & ST_MOVERACK)
	c->state |= ST_DONE;
      if ((c->error = atoi(c->tok[3])) != ES_OK) {
	char *rstr = concatToken(c->tok, 2);
	if (rstr) {
	  strncpy(c->r->errString, rstr, ES_ERRSTRLEN);
	  (void) free(rstr);
	}
      }
      return(c->error);
    }
    return(ES_CONTINUE);
  } else {
    DPrint("receiveDoor: unknown cmd: "); printToken(c->tok, 0);
    return(ES_CONTINUE);
  }
}

void ES_CloseSession(ES_session es) {
  Session *s = (Session *) es;

  if (validateSession(s) != 0)
    return;
  if (s->doorHost != (char *) 0)
    free(s->doorHost);
  (void) closeDoor(s->doorSocket);
  (void) free(s);
  DPrint("Session closed\n");
}

void cleanup(context *c) {
  if (c->moverSock >= 0)
    (void) close(c->moverSock);
  if (c->listenSock >= 0)
    (void) close(c->listenSock);
  c->moverSock = c->listenSock = -1;
}

  /*
   * callback sequence is:
   *  1. call once for global init (when starting)
   *  2. call at the beginning of every new BFID write (hand out bfid)
   *  3. call when the data transfer starts
   *  4. call for each chunk of data
   *  5. call at the end of each transferred dataset
   *  6. call at the end of all data transfers
   */

#define CHKINV(_x_) if ((_x_) == ES_INVSESSION) invSession = 1;

/* read dataset */
int ES_ReadData(ES_session session,
		ES_readlist *list,
		int listLen,
		ES_Read_callback callback,
		void *userPtr,
		char *hostName,
		int timeout,
		char *eStr) {


  int i, iostatus;
  char hostname[MAXHOSTNAMELEN + 2];
  Session *s;
  struct sockaddr_in saddr;
  int ernum;
  context c;
  int invSession = 0;
 
  if (eStr)
    eStr[0] = '\0'; 
  memset((char *) &c, 0, sizeof(c));
  c.userErrorStr = eStr;
  if (listLen <= 0 ||
      callback == (ES_Read_callback) 0 ||
      list == (ES_readlist *) 0)
    return(ES_BADPARAM);

  /* we allow only single IO per call (initially....) */
  if (listLen != 1)
    return(ES_BADPARAM);

  if (validateSession(session) != 0)
    return(ES_INVSESSION);

  c.s = (Session *) session;
  /* check priviledges */
  if ((c.s->privs & ES_PRIV_READ) == 0)
    return(ES_NOPRIV);

  if (timeout < 0)
    timeout = 0;

  /* whats my name of the game .... */
  if (getHostName(hostName, hostname) != 0) {
    return(ES_BADHOSTNAME);
  }

  /* create/bind/listen on TCP port for the Mover connection */
  if (createMoverSocket(&c) != 0) {
    return(c.error);
  }

  if (doorConnect(&c) != 0) {
    (void) close(c.listenSock);
    return(c.error);
  }

  c.state |= ST_INIT;

  /* LOOP OVER ALL DATASETS TO COPY */
  for(i=0; i<listLen; i++) {
    unsigned64 bytesdone;
    int  flushsize;
    char buff[MAX_W_CHUNKSIZE];   /* special alignment needed ???? */
    char cmdStr[2048];  /* stupid but simple :--)) */
    char msg[1024];

    list[i].result = ES_OK;
    list[i].errString[0] = '\0';
    c.r = (struct userRequest *) &list[i];


    makeIdentification(&c);

    /* create commandstring */
    (void) sprintf(cmdStr,
		   "READDATASET %s %s %s %d %d %s $u=%d,$g=%d,$T=%s",
		   c.s->storename, list[i].bfid,
		   hostname, c.listenPort, timeout, c.idStr,
		   getuid(), getgid(),
		   (list[i].userTag[0] == '\0') ? "none" : list[i].userTag);

    if (writeUTF(c.doorSock, cmdStr) < 0) {
      c.error = ES_INVSESSION;
      CHKINV(c.error);
      DPrint("writeUTF error on doorSocket %d (%s)\n", errno, strerror(errno));
      (void) sprintf(c.r->errString,
		     "Error sending READ command %d (%s)",
		     errno, strerror(errno));

      if ((*callback)(ES_OP_NEWDATASET, NULL, c.error, &list[i], userPtr) ==
	  ES_ABORT) {  /* bail out immed. */
	goto bailOut;
      }
      continue;  /* next file in list */
    }

    c.state |= ST_CMDSEND;
    c.state &= ~ST_PREACK;
    c.state |= ST_WAITPREACK;
    /*clToken(&c);*/

    /* wait for PREACK */
    if ((ernum = dispatch(c.doorSock, &timeout, receiveDoor,
			  -1, NULL, NULL,
			  &c)) != ES_OK) {
      ernum = (ernum < 0) ? ES_INVSESSION : ernum;
      CHKINV(ernum);

#if 0
      if (ernum < 0) {  /* EOF -> closing TCP connection */
	DPrint("unexpected EOF on  DOOR connection\n");
	list[i].result = ernum = ES_TCPIO;
	goto veryBAD;
      }
      DPrint("CMD Ack failed ");
      if (c.tok)
	printToken(c.tok, 0);
      else
	DPrint("(NO TOKENS)\n");
#endif

      list[i].result = ernum;

      /*clToken(&c);*/
      cleanup(&c);

      if ((ernum = (*callback)(ES_OP_NEWDATASET, NULL, ernum, &list[i],
			       userPtr)) != ES_OK) {
	if (ernum == ES_ABORT)
	  goto bailOut;
      }
      continue;  /* next file in list */
    }
      
    /* store new size and requestID */
    c.r->size = atoll(c.tok[2]);
    c.r->requestId = (unsigned int) atol(c.tok[1]);
    DPrint("GOT reqID: %u  bfid: %s\n", c.r->requestId, list[i].bfid);

    if ((ernum = (*callback)
	 (ES_OP_NEWDATASET, NULL, 0, &list[i], userPtr)) != ES_OK) {
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      list[i].result = ernum;
      CHKINV(ernum);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file */
    }
    
    /*
     * wait for data socket connection from mover and commands from DOOR
     */

    c.state |= ST_MOVERWAIT;
    ernum = ES_OK;
    DPrint("waiting for mover connection\n");
    while(!(c.state & ST_MOVERCONN) /*&& !(c.state & ST_CANCELLED)*/) {
      if ((ernum = dispatch(c.listenSock, &timeout, moverAccept,
			    c.doorSock, NULL, receiveDoor,
			    &c)) != ES_OK) {
	if (ernum == -1) { /* EOF on mover connection */
	  ernum = ES_BADMOVER;
	} else if (ernum == -2) { /* EOF on DOOR connection */
	  DPrint("unexpected EOF on Door connection\n");
	  ernum = ES_INVSESSION;
	  CHKINV(ernum);
	}
	list[i].result = ernum;
	cleanup(&c);
	if ((ernum = (*callback)(ES_OP_TRANSFERSTART, NULL, 0, &list[i],
				 userPtr)) != ES_OK) {
	  if (ernum == ES_ABORT)
	    goto bailOut;
	}
	break;
      }
    }
    if (ernum != ES_OK)
      continue;

    if ((ernum = (*callback)(ES_OP_TRANSFERSTART, NULL, 0, &list[i],
			     userPtr)) != ES_OK) {
      list[i].result = ernum;
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;    /* next file in list */
    }

    /* ship the data */
    bytesdone = 0;
    iostatus = 0;
    list[i].bytesRead = 0;

    DPrint("Start data movement...(%u)\n", c.r->requestId);

#if 0
    while(bytesdone < list[i].size) {
      int rbytes;
      int chunksize;

      chunksize = list[i].size - bytesdone;
      chunksize = (chunksize > MAX_R_CHUNKSIZE) ? MAX_R_CHUNKSIZE : chunksize;


      /* transfer data from mover .... */
      /* on failure set 'iostatus' and break !!! */
      if ((rbytes = read(c.moverSock, buff, chunksize)) <= 0) {
	DPrint("read error on Mover stream %d (%d)\n", rbytes, errno);
	if (rbytes == 0)
	  DPrint("premature EOF on Mover connection\n");
	list[i].result = iostatus = ernum = ES_TCPIO;
	break;
      }

      /* let the user drain the buffer */
      flushsize = (*callback)
	(ES_OP_FLUSHBUFFER, buff, rbytes, &list[i], userPtr);
      /* fillsize meanings:
       * <  0 indicates an error condition
       * == 0 indicate END_OF_DATA (should not happen currently)
       * >  0 amount of data filled in the buffer pointed by 'buff'
       */
      if (flushsize <= 0) {
	list[i].result = iostatus = ernum = ES_EARLYEOF;
	break;
      }


      bytesdone += flushsize;
      list[i].bytesRead = bytesdone;
    }  /* end of data movement loop */
    /* list[i].bytesRead = bytesdone; */
#else
    if ((ernum = (*callback)(ES_OP_READDATA, &c.moverSock, MAX_R_CHUNKSIZE,
			     &list[i], userPtr)) != ES_OK) {
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      list[i].result = iostatus = ernum;
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file in list */
    }
#endif

    DPrint("data reading done - waiting for MACK (%u)\n", c.r->requestId);

      /* wait for Mover and BB final ACK message */
    if (iostatus == ES_OK) {
      int mackTmout = MACK_TIMEOUT;
      int ackTmout = BBACK_TIMEOUT;
      
      c.state |= ST_WAITMACK;
      c.state |= ST_WAITACK;

      while(!((c.state & ST_MOVERACK) && (c.state & ST_FINALACK))) {
	if ((ernum = dispatch(c.doorSock, &mackTmout, receiveDoor,
			      c.moverSock, &ackTmout, receiveMover,
			      &c)) != ES_OK) {

	  if (ernum == -1) {  /* DOOR EOF */
	    if (c.state & ST_FINALACK) { /* fine ! */
	      if (c.state & ST_MOVERACK)
		break;
	      else
		continue;
	    }
	  } else if (ernum == -2) {  /* mover EOF */
	    (void) close(c.moverSock); c.moverSock = -1;
	    if (c.state & ST_MOVERACK) {
	      if (c.state & ST_FINALACK)
		break;
	      else
		continue;
	    }
	  }

	  DPrint("CMD Ack failed ");
	  if (c.tok)
	    printToken(c.tok, 0);
	  else
	    DPrint("(NO TOKENS)\n");
	  
	  if (ernum == ES_CONTINUE)
	    continue;

	  CHKINV(ernum);

	  list[i].result = ernum;
	  /*clToken(&c);*/
	  cleanup(&c);
	  if ((ernum = (*callback)(ES_OP_IOCOMPLETED, NULL, ernum, &list[i],
				   userPtr)) != ES_OK) {
	    if (ernum == ES_ABORT)
	      goto bailOut;
	  }
	  break; /* next file in list */
	}
      }
      if (ernum != ES_OK)
	continue;

      c.state |= ST_DONE;

    }

    if (iostatus == 0)
      list[i].result = ES_OK;
    
    if ((ernum = (*callback)(ES_OP_IOCOMPLETED, NULL, iostatus, &list[i],
			     userPtr)) != ES_OK) {
      cleanup(&c);
      (void) cancelRequest(&c, 1);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file in list */
    }
    if (c.moverSock >= 0)
      (void) close(c.moverSock);

    /*clToken(&c);*/

    DPrint("JOB DONE - read bfid '%s'!!!\n", list[i].bfid);

  }  /* end of loop for all datasets to write on this call */
  if (invSession == 1)
    goto bailOut;

  if (c.listenSock >= 0)
    (void) close(c.listenSock);
  return(ES_OK);

  /* gets called if the session become invalid (unusable from now on) */
bailOut:
  (void) (*callback)(ES_OP_END, NULL, 0, NULL, userPtr);
  cleanup(&c);
  return(ES_INVSESSION);

}

/*
 * callback sematics
 *
 *  INIT -> removed - not needed any longer
 *  END -> should be used if the Session gets invalidated by us and the
 *         calling thread should immed. stop using the current session
 *  NEWDATASET - as it is today
 *  0 or more FILLBUFFER
 *  IOCOMPLETED - will always (guaranteed) be called if NEWDATASET has been
 *  called before (if NEWDATASET than IOCOMPLETE must be called at the end)
 */

/* write datasets */
int ES_WriteData(ES_session session,
		 ES_writelist *list,
		 int listLen,
		 ES_Write_callback callback,
		 void *userPtr,
		 char *hostName,
		 int timeout,
		 char *eStr) {

  int i, iostatus;
  char hostname[MAXHOSTNAMELEN + 2];
  Session *s;
  struct sockaddr_in saddr;
  int ernum;
  context c;
  int invSession = 0;
 
  if (eStr)
    eStr[0] = '\0'; 
  memset((char *) &c, 0, sizeof(c));
  c.userErrorStr = eStr;
  if (listLen <= 0 ||
      callback == (ES_Write_callback) 0 ||
      list == (ES_writelist *) 0)
    return(ES_BADPARAM);

  /* we allow only single IO per call (initially....) */
  if (listLen != 1)
    return(ES_BADPARAM);

  if (validateSession(session) != 0)
    return(ES_INVSESSION);

  c.s = (Session *) session;
  /* check priviledges */
  if ((c.s->privs & ES_PRIV_WRITE) == 0)
    return(ES_NOPRIV);

  if (timeout < 0)
    timeout = 0;

  /* whats my name of the game .... */
  if (getHostName(hostName, hostname) != 0) {
    return(ES_BADHOSTNAME);
  }

  /* create/bind/listen on TCP port for the Mover connection */
  if (createMoverSocket(&c) != 0) {
    return(c.error);
  }

  /* should be issued inside SessionCreate ... */
  if (doorConnect(&c) != 0) {
    (void) close(c.listenSock);
    return(c.error);
  }

  c.state |= ST_INIT;

  /* LOOP OVER ALL DATASETS TO COPY */
  for(i=0; i<listLen; i++) {
    unsigned64 bytesdone;
    int  fillsize;
    char buff[MAX_W_CHUNKSIZE];   /* special alignment needed ???? */
    char cmdStr[2048];  /* stupid but simple :--)) */
    char msg[1024];

    list[i].result = ES_OK;
    list[i].errString[0] = '\0';
    c.r = (struct userRequest *) &list[i];


    makeIdentification(&c);

    /* create commandstring */
    (void) sprintf(cmdStr,
		   "WRITEDATASET %s %s %s %u %s %d %d %s $u=%d,$g=%d,$T=%s",
		   c.s->storename, list[i].storageGroup,list[i].migrationPath,
		   list[i].size, hostname, c.listenPort, timeout, c.idStr,
		   getuid(), getgid(),
		   (list[i].userTag[0] == '\0') ? "none" : list[i].userTag);

    if (writeUTF(c.doorSock, cmdStr) < 0) {
      c.error = ES_INVSESSION;
      CHKINV(c.error);
      DPrint("writeUTF error on doorSocket %d (%s)\n", errno, strerror(errno));
      (void) sprintf(c.r->errString,
		     "Error sending WRITE command %d (%s)",
		     errno, strerror(errno));
      if ((*callback)(ES_OP_NEWDATASET, NULL, c.error, &list[i], userPtr) ==
	  ES_ABORT) {  /* bail out immed. */
	goto bailOut;
      }
      continue;  /* next file in list */
    }

    c.state |= ST_CMDSEND;
    c.state &= ~ST_PREACK;

    c.state |= ST_WAITPREACK;
    /*clToken(&c);*/
    if ((ernum = dispatch(c.doorSock, &timeout, receiveDoor,
			  -1, NULL, NULL,
			  &c)) != ES_OK) {
      ernum = (ernum < 0) ? ES_INVSESSION : ernum;
      CHKINV(ernum);

#if 0
      if (ernum < 0) {  /* EOF -> closing TCP connection */
	DPrint("unexpected EOF on  DOOR connection\n");
	list[i].result = ernum = ES_TCPIO;
	goto veryBAD;
      }
      DPrint("CMD Ack failed ");
      if (c.tok)
	printToken(c.tok, 0);
      else
	DPrint("(NO TOKENS)\n");
#endif

      list[i].result = ernum;
      (void) (*callback)(ES_OP_NEWDATASET, NULL, ernum, &list[i], userPtr);
      /*clToken(&c);*/
      cleanup(&c);

      if ((ernum = (*callback)(ES_OP_NEWDATASET, NULL, ernum, &list[i],
			       userPtr)) != ES_OK) {
	if (ernum == ES_ABORT)
	  goto bailOut;
      }
      continue;  /* next file in list */
    }
      
    /* store new created BFID in writelist structure */
    strncpy(list[i].bfid, c.tok[2], ES_BFID_STRSIZE);
    c.r->requestId = (unsigned int) atol(c.tok[1]);
    DPrint("GOT reqID: %u  bfid: %s\n", c.r->requestId, list[i].bfid);

    if ((ernum = (*callback)
	 (ES_OP_NEWDATASET, NULL, 0, &list[i], userPtr)) != ES_OK) {
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      list[i].result = ernum;
      CHKINV(ernum);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file */
    }
    
    /*
     * wait for data socket connection from mover and commands from DOOR
     */

    c.state |= ST_MOVERWAIT;
    ernum = ES_OK;
    DPrint("waiting for mover connection\n");
    while(!(c.state & ST_MOVERCONN)/* && !(c.state & ST_CANCELLED)*/) {
      if ((ernum = dispatch(c.listenSock, &timeout, moverAccept,
			    c.doorSock, NULL, receiveDoor,
			    &c)) != ES_OK) {
	if (ernum == -1) { /* EOF on mover connection */
	  ernum = ES_BADMOVER;
	} else if (ernum == -2) { /* EOF on DOOR connection */
	  DPrint("unexpected EOF on Door connection\n");
	  ernum = ES_INVSESSION;
	  CHKINV(ernum);
	}
	list[i].result = ernum;
	cleanup(&c);
	if ((ernum = (*callback)(ES_OP_TRANSFERSTART, NULL, 0, &list[i],
				 userPtr)) != ES_OK) {
	  if (ernum == ES_ABORT)
	    goto bailOut;
	}
	break;
      }
    }
    if (ernum != ES_OK)
      continue;

    if ((ernum = (*callback)(ES_OP_TRANSFERSTART, NULL, 0, &list[i],
			     userPtr)) != ES_OK) {
      list[i].result = ernum;
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;    /* next file in list */
    }

    /* ship the data */
    bytesdone = 0;
    iostatus = 0;
    list[i].bytesWritten = 0;

    DPrint("Start data movement...(reqID: %u  Size: %u)\n",
	   c.r->requestId, c.r->size);

#if 0
    while(bytesdone < list[i].size) {
      int wbytes;
      int chunksize = list[i].size - bytesdone;

      chunksize = (chunksize > MAX_W_CHUNKSIZE) ? MAX_W_CHUNKSIZE : chunksize;

      /* let the user fill the buffer */
      fillsize = (*callback)
	(ES_OP_FILLBUFFER, buff, chunksize, &list[i], userPtr);
      /* fillsize meanings:
       * <  0 indicates an error condition
       * == 0 indicate END_OF_DATA (should not happen currently)
       * >  0 amount of data filled in the buffer pointed by 'buff'
       */
      if (fillsize <= 0) {
	list[i].result = iostatus = ernum = ES_EARLYEOF;
	break;
      }

      /* transfer data to mover .... */
      /* on failure set 'iostatus' and break !!! */
      if ((wbytes = write(c.moverSock, buff, fillsize)) != fillsize) {
	DPrint("write error on Mover stream %d (%d)\n", wbytes, errno);
	list[i].result = iostatus = ernum = ES_TCPIO;
	break;
      }

      bytesdone += fillsize;
      list[i].bytesWritten = bytesdone;
    }  /* end of data movement loop */
    /*list[i].bytesMoved = bytesdone;*/
#else
    /* changed API to call caller with fd and #of bytes to push into fd */
    if ((ernum = (*callback)(ES_OP_WRITEDATA, &c.moverSock, MAX_W_CHUNKSIZE,
			     &list[i], userPtr)) != ES_OK) {
      list[i].result = iostatus = ernum;
      (void) cancelRequest(&c, 1);
      cleanup(&c);
      list[i].result = iostatus = ernum;
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file in list */
    }
#endif

    DPrint("data sending done - waiting for MACK (%u)\n", c.r->requestId);

      /* wait for Mover and BB final ACK message */
    if (iostatus == ES_OK) {
      int mackTmout = MACK_TIMEOUT;
      int ackTmout = BBACK_TIMEOUT;
      
      c.state |= ST_WAITMACK;
      c.state |= ST_WAITACK;

      while(!((c.state & ST_MOVERACK) && (c.state & ST_FINALACK))) {
	if ((ernum = dispatch(c.doorSock, &mackTmout, receiveDoor,
			      c.moverSock, &ackTmout, receiveMover,
			      &c)) != ES_OK) {

	  if (ernum == -1) {  /* DOOR EOF */
	    if (c.state & ST_FINALACK) { /* fine ! */
	      if (c.state & ST_MOVERACK)
		break;
	      else
		continue;
	    }
	  } else if (ernum == -2) {  /* mover EOF */
	    (void) close(c.moverSock); c.moverSock = -1;
	    if (c.state & ST_MOVERACK) {
	      if (c.state & ST_FINALACK)
		break;
	      else
		continue;
	    }
	  }

	  DPrint("CMD Ack failed ");
	  if (c.tok)
	    printToken(c.tok, 0);
	  else
	    DPrint("(NO TOKENS)\n");
	  
	  if (ernum == ES_CONTINUE)
	    continue;

	  CHKINV(ernum);

	  list[i].result = ernum;
	  /*clToken(&c);*/
	  cleanup(&c);

	  if ((ernum = (*callback)(ES_OP_IOCOMPLETED, NULL, ernum, &list[i],
				   userPtr)) != ES_OK) {
	    if (ernum == ES_ABORT)
	      goto bailOut;
	  }
	  break; /* next file in list */
	}
      }
      if (ernum != ES_OK)
	continue;

      c.state |= ST_DONE;

    }

    if (iostatus == 0)
      list[i].result = ES_OK;

    if ((ernum = (*callback)(ES_OP_IOCOMPLETED, NULL, iostatus, &list[i],
			     userPtr)) != ES_OK) {
      cleanup(&c);
      (void) cancelRequest(&c, 1);
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;  /* next file in list */
    }

    if (c.moverSock >= 0)
      (void) close(c.moverSock);
    if (c.listenSock >= 0)
      (void) close(c.listenSock);

    /*clToken(&c);*/

    DPrint("JOB DONE - created bfid '%s'!!!\n", list[i].bfid);

  }  /* end of loop for all datasets to write on this call */

  if (invSession == 1)
    goto bailOut;

  return(ES_OK);

  /* gets called if the session become invalid (unusable from now on) */
bailOut:
  (void) (*callback)(ES_OP_END, NULL, 0, NULL, userPtr);
  /*clToken(&c);*/
  cleanup(&c);
  return(ES_INVSESSION);
}

/* remove bfid - session already established */
static int removeFile(context *c) {
  char msg[256], *tok[MAXTOKENS];
  int ernum, ntok;

  (void) sprintf(msg, "REMOVEDATASET %s %s",
		 c->s->storename, c->r->bfid);

  if (writeUTF(c->doorSock, msg) < 0) {
    DPrint("writeUTF error on doorSocket %d (%s)\n", errno, strerror(errno));
    (void) sprintf(c->r->errString,
		   "Error sending REMOVE command %d (%s)",
		   errno, strerror(errno));
    c->error = ES_INVSESSION;
    return(c->error);
  }
  if ((ernum = readUTFToken(c->doorSock, REMOVE_TIMEOUT, msg, sizeof(msg),
			    tok, &ntok)) != ES_OK) {
#if 0
    if (c->userErrorStr)
#endif
      (void) sprintf(c->r->errString, "DOOR (%s:%d) read UTF failed %d (%s)",
		     c->s->doorHost, c->s->doorPort, errno, strerror(errno));

    if (ernum == ES_TCPIO ||
	ernum == ES_CONNEOF ||
	ernum == ES_BADMESSAGE)
      ernum = ES_INVSESSION;
    
    c->error = ernum;
    freeToken(&tok);
    return(ernum);
  }

  if (strcmp(tok[0], CMDACK_NOK) == 0) {
    if (ntok >= 2) {
      if ((ernum = atoi(tok[1])) == ES_OK)
	ernum = ES_TCPIO;  /* FIXME - default error !! */
      if (ntok > 2) {
	strncpy(c->r->errString, tok[2], ES_ERRSTRLEN);
      }
    } else {
      ernum = ES_BADMESSAGE;
    }
  } else if (strcmp(tok[0], CMDACK_OK) == 0) {
    ernum = ES_OK;
  } else {
    if (ntok > 0) {
      char *rstr = concatToken(tok, 0);
      if (rstr) {
	strncpy(c->r->errString, rstr, ES_ERRSTRLEN);
	(void) free(rstr);
      }
    }
    ernum = ES_BADMESSAGE;
    DPrint("unknown reply from DOOR\n");
  }
  return(ernum);
}

/* mandantory final procedure -:) */
int ES_Remove(ES_session session,
	      ES_removelist *list,
	      int listLen,
	      ES_Remove_callback callback,
	      void *userPtr,
	      char *eStr) {

  int i, cret, removeResult;
  context c;
  int ernum, invSession;

  memset((char *) &c, 0, sizeof(c));

  if (eStr)
    eStr[0] = '\0';
  if (listLen < 0 ||
      list == (ES_removelist *) 0 ||
      callback == (ES_Remove_callback) 0)
    return(ES_BADPARAM);
  
  if (validateSession(session) != 0)
    return(ES_INVSESSION);

  c.s = (Session *) session;
  /* check priviledges */
  if ((((Session *) session)->privs & ES_PRIV_MODIFY) == 0)
    return(ES_NOPRIV);


  /* create session with door */
  if (doorConnect(&c) != 0) {
    return(c.error);
  }

  c.state |= ST_INIT;

  for(i=0; i<listLen; i++) {
 
    c.r = (struct userRequest *) &list[i];
    /* check and REMOVE bitfile here (result in 'removeResult') */
    list[i].result = removeResult = ernum = removeFile(&c);
    if (ernum == ES_INVSESSION)
      invSession = 1;

    /* call callback */
    if ((ernum = (*callback)
	 (ES_OP_REMOVEDONE, &list[i], userPtr)) != ES_OK) {
      if (ernum == ES_ABORT)
	goto bailOut;
      continue;
    }
  }

  if (invSession == 1)  /* we failed while removing */
    goto bailOut;

  return(ES_OK);

bailOut:
  (void) (*callback)(ES_OP_END, NULL, userPtr);
  return(ES_INVSESSION);

}
