/*
 * EuroStore PVR   -mg DESY
 * library independent code
 */

#ifndef lint
static char     rcsid[] = "";
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <netdb.h>
#include <sys/param.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/wait.h>
#ifndef __STDC__
#include <varargs.h>
#else
#include <stdarg.h>
#endif
#include <es-pvr.h>

#ifndef TRUE
#define TRUE 1
#define FALSE 0
#endif

#define ADD(head, tail, obj)                                            \
        {                                                               \
                if ((head) == NULL) {                                   \
                        (head) = (tail) = (obj);                        \
                        (obj)->prev = (obj)->next = NULL;               \
                } else {                                                \
                        (tail)->next = (obj);                           \
                        (obj)->prev = (tail);                           \
                        (obj)->next = NULL;                             \
                        (tail) = (obj);                                 \
                }                                                       \
        }
#define DEL(head, tail, obj)                                            \
        {                                                               \
                if ((obj) == (head)) {                                  \
                        if (((head) = (head)->next) != NULL)            \
                                (head)->prev = NULL;                    \
                        else                                            \
                                (tail) = NULL;                          \
                } else if ((obj) == (tail)) {                           \
                        if (((tail) = (tail)->prev) != NULL)            \
                                (tail)->next = NULL;                    \
                } else {                                                \
                        (obj)->prev->next = (obj)->next;                \
                        if ((obj)->next != NULL)                        \
                                (obj)->next->prev = (obj)->prev;        \
                }                                                       \
                (void)free((char *)obj);                                \
        }
/* do not free ptr to object */
#define DEL1(head, tail, obj)                                           \
        {                                                               \
                if ((obj) == (head)) {                                  \
                        if (((head) = (head)->next) != NULL)            \
                                (head)->prev = NULL;                    \
                        else                                            \
                                (tail) = NULL;                          \
                } else if ((obj) == (tail)) {                           \
                        if (((tail) = (tail)->prev) != NULL)            \
                                (tail)->next = NULL;                    \
                } else {                                                \
                        (obj)->prev->next = (obj)->next;                \
                        if ((obj)->next != NULL)                        \
                                (obj)->next->prev = (obj)->prev;        \
                }                                                       \
        }


#define DEBUG_ES_PVR 1

#define NULLPVR 1   

#define MAX_TOKENS 50
#define MAX_DRIVE_NAMELEN 256
#define MAX_VOL_NAMELEN 256
#define TSEP ' '
#define TQUOTE '"'

#define MAX_MOUNT_SECS 10
#define MAX_DISMOUNT_SECS 14

/* async process states */
#define STATE_RUNNING 0x01
#define STATE_STOPPED 0x02
/* async process op task */
#define OP_MOUNT 0x01
#define OP_DISMOUNT 0x02
#define OP_DRIVESTAT 0x03

typedef int (*PVRCmd)(int reqID, int argc, char **argv);

typedef struct AsyncOP {
  int   pid;             /* child pid working on that */
  char  driveName[MAXPATHLEN];        /* drive involved */
  char  genDriveName[MAX_DRIVE_NAMELEN];  /* generic drive name */
  char  volName[MAX_VOL_NAMELEN];         /* volume   " */
  int   type;            /* type of request [mount | unmount] */
  int   state;           /* state of AsyncRequest see above */
  int   requestID;       /* id supplied by remote caller */
  int   returnCode;      /* what waitpid() returns */
  struct AsyncOP *prev;  /* to manage with ADD... */
  struct AsyncOP *next;
} AsyncOP;

static AsyncOP *AHead = NULL;
static AsyncOP *ATail = NULL;

static int masterProc = TRUE;
static int pvlPort = -1;            /* port to connect to */
static char *pvlHost = (char *) 0;  /* hostname were pvl is running */
static char *pvrName = (char *) 0;
static int pvlSock = -1;
static char *progname = (char *) 0;
static char hwInfo[80];
static int logLevel = 0;
static int logIn = -1, logOut = -1;
static int libDummy = FALSE; /*set this to run as a dummy pvr - no robot cmds*/
static int terminateMaster = FALSE;
static int becomeDaemon = TRUE;
static char *_logFile = (char *) 0;


static void doSleep(int maxSecs) {
  static long ranSecs = 0;

  srand48(ranSecs);
  while(666) {
    if ((ranSecs = lrand48()) <= maxSecs && ranSecs > 0) {
      sleep((int) ranSecs);
      return;
    }
  }
}

static char *time2str(time_t *tim) {
  struct tm *t;
  static char cTime[100];
  time_t tt, *ttt;

  if (tim == (time_t *) 0) {  /* get current time */
    tt = time((time_t *) 0);
    ttt = &tt;
  } else {
    ttt = tim;
  }
    
  t = localtime(ttt);
  (void) sprintf(cTime, "%02d/%02d-%02d:%02d:%02d",
                 t->tm_mday, t->tm_mon + 1,
                 t->tm_hour, t->tm_min, t->tm_sec);
  return(cTime);
}

#ifndef __STDC__
void LOG(level, fmt, va_alist)
int level;
char *fmt;
va_dcl
#else
void LOG(int level, char *fmt, ...)
#endif
{
  va_list args;
  char buf[512], *ptr;
  int len;

  if (level <= logLevel)
    return;

#ifndef __STDC__
  va_start(args);
#else
  va_start(args, fmt);
#endif

#if 0
  (void) vfprintf(stderr, fmt, args);
  fprintf(stderr, "\n");
#endif

  (void) sprintf(buf, "log %d \"(%s) ", level, hwInfo); 
  ptr = &buf[strlen(buf)]; 
  (void) vsprintf(ptr, fmt, args); 
  len = strlen(buf); 
  if (buf[len - 1] == '\n') 
    buf[(len--) - 1] = '\0'; 
  buf[len++] = '"'; buf[len] = '\0'; 

  /* sent log messages to PVL proxy */
  if (logIn >= 0 && pvlSock >= 0) {
    unsigned short nlen;

#if 0  /* see above */
    (void) sprintf(buf, "log %d \"(%s) ", level, hwInfo);
    ptr = &buf[strlen(buf)];
    (void) vsprintf(ptr, fmt, args);
    len = strlen(buf);
    if (buf[len - 1] == '\n')
      buf[(len--) - 1] = '\0';
    buf[len++] = '"'; buf[len] = '\0';
#endif

    nlen = htons(len);
    if (len > 0) {
      int fd = (masterProc == TRUE) ? pvlSock : logIn;
      (void) write(fd, &nlen, 2);
      (void) write(fd, buf, len);
    }
  }

  if (becomeDaemon == FALSE) { /* sent also to stdout if not a daemon */ 
    if (level == ESPVR_INFO) { 
      printf("%s: %s\n", time2str(NULL), buf); fflush(stdout); 
    } else { 
      fprintf(stderr, "%s: %s\n", time2str(NULL), buf); 
    } 
  }

  /* check if we got a logfile name as cmdline param and write to this file */
  if (_logFile != NULL) { 
    FILE *f = fopen(_logFile, "a+"); 
    if (f != NULL) { 
      fprintf(f, "%s: %s\n", time2str(NULL), buf); 
      fclose(f); 
    } 
  }

  va_end(args);
}

/* let the child process sending messages to proxy via parent process */
int ForwardMessage(char *msg) {
  int len = strlen(msg);
  unsigned short nlen;

  if (masterProc == TRUE || logIn < 0)  /* no way */
    return(-1);
  if (len <= 0)
    return(0);
  nlen = htons(len);
  
  if (write(logIn, &nlen, 2) != 2 ||
      write(logIn, msg, len) != len)
    return(-1);
  return(0);
}

static int tokenize(char *in, char **tok) {
  int i, ntok = 0, len = strlen(in);

  if (in == NULL || in[0] == '\0')
    return(0);
  /* clear old stuff */
  for(i=0; i<MAX_TOKENS; i++)
    tok[i] = (char *) 0;

  /* eat up initial leading spaces */
  for(i=0; i<len; i++)
    if (in[i] != TSEP)
      break;

  while(i<len) {
    if (in[i] == TQUOTE) {
      tok[ntok++] = &in[++i];
      if (ntok >= MAX_TOKENS)
        break;
      for(;i<len; i++)
        if (in[i] == TQUOTE)
          break;
      in[i++] = '\0';
      /* eat up spaces */
      for(;i<len; i++)
	if (in[i] != TSEP)
	  break;
    } else {
      tok[ntok++] = &in[i];
      if (ntok >= MAX_TOKENS)
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
  return(ntok);
}

static int readStream(int fd, void *buf, int bytes) {
  char *ptr = (char *) buf;
  int iread = 0, i, toread = bytes;

  while(666) {
    if ((i = read(fd, ptr, toread)) <= 0)
      return(i);
    iread += i;
    if (iread >= bytes)
      return(iread);
    ptr += i;
    toread -= i;
  }
}

static int readUTF(int fd, char *buff, int len) {
  int sret, i;
  unsigned short mlen = 0;

  memset(buff, 0, len);
  /* first read 2 byte message length */
  if ((i = readStream(fd, &mlen, 2)) != 2) {
    if (i == 0) {
      LOG(ESPVR_FATAL, "readUTF: EOF on fd %d", fd);
      return(-1);
    } else {
      LOG(ESPVR_FATAL, "readUTF: messagelen read failed %d (%d) -> %u",
	  i, errno, mlen);
      return(-1);
    }
  }
  /* and now the message itself */
  mlen = htons(mlen) ;
  if (mlen == 0)
    return(0);
  if (mlen > len) {
    LOG(ESPVR_FATAL, "message too large !!! (%u)", mlen);
    return(-1);
  }

  if ((i = readStream(fd, buff, mlen)) != mlen) {
    LOG(ESPVR_FATAL, "readUTF: message read failed %d (%d)", i, errno);
    return(-1);
  }
  buff[i] = '\0';
  return(mlen);
}

static int writeUTF(int fd, char *str) {
  unsigned short len = strlen(str);
  unsigned short nlen = htons(len);

  if (len > 0) {
    if (write(fd, &nlen, 2) != 2 ||
        write(fd, str, len) != len) {
      LOG(ESPVR_FATAL, "writeUTF: write error %d (%s)",
	  errno, strerror(errno));
      return(-1);
    }
  }
  /*LOG(ESPVR_INFO, "writeUTF: sent '%s'\n", str);*/
  return(len);
}

static int daemonize() {
  int fd, pid;
  
  /* fork and exit */
  if ((pid = fork()) == -1) {
    LOG(ESPVR_FATAL, "fork failed %d (%s)", errno, strerror(errno));
    return(-1);
  } else if (pid != 0) {
    exit(0);
  }

  if (setsid() == -1) {
    LOG(ESPVR_FATAL, "create session failed %d (%s)", errno, strerror(errno));
    return(-1);
  }
  /* do it again sam... session leader problem otherwise */
  if ((pid = fork()) == -1) {
    LOG(ESPVR_FATAL, "second fork failed %d (%s)", errno, strerror(errno));
    return(-1);
  } else if (pid != 0) {
    exit(0);
  }
  
  (void) chdir("/");
  (void) umask(0);

  /* close all file descriptors */
  for (fd = 0; fd < FD_SETSIZE; fd++) {
    if (fd != pvlSock && fd != logIn && fd != logOut)
      (void) close(fd);
  }
  
  /* redirect stdout and stderr to /dev/console */
  if ((fd = open("/dev/null", O_WRONLY)) == -1) {
    LOG(ESPVR_FATAL, "error open /dev/null: %d (%s)", errno, strerror(errno));
    return(-1);
  }
  if (dup(fd) != 1 || dup(fd) != 2) {
    LOG(ESPVR_FATAL, "error redirect stdout to /dev/null %d (%s)", errno, strerror(errno));
    return(-1);
  }
  (void) close(fd); /* close 0 == stdin */
  
  return(0);
}

static AsyncOP *createAsync(int typ, int reqID) {
  AsyncOP *as = (AsyncOP *) calloc(1, sizeof(AsyncOP));

  if (as == (AsyncOP *) 0)
    return(NULL);

  memset((char *) as, 0, sizeof(AsyncOP));
  as->type = typ;
  as->requestID = reqID;
  return(as);
}

static void removeAsync(AsyncOP *a) {
  if (a) {
    (void) free(a);
  }
}

static int makeChild(AsyncOP *a) {
  sigset_t ss;
  int      cpid;

  (void) sigemptyset(&ss);
  if (sigaddset(&ss, SIGCHLD) != 0)
    return(-1);
  if (sigprocmask(SIG_BLOCK, &ss, NULL) != 0) {
    LOG(ESPVR_FATAL, "can't BLOCK SIGCHLD %d (%s)", errno, strerror(errno));
    return(-1);
  }
  ADD(AHead, ATail, a);

  switch((cpid = fork())) {
  case -1:
    LOG(ESPVR_FATAL, "fork() failed %d (%s)", errno, strerror(errno));
    DEL1(AHead, ATail, a);
    if (sigprocmask(SIG_UNBLOCK, &ss, NULL) != 0) {
      LOG(ESPVR_FATAL, "can't UNBLOCK SIGCHLD %d (%s)",
	  errno, strerror(errno));
    }
    return(-1);
  case 0:  /* in child */
    masterProc = FALSE;
    LOG(ESPVR_INFO, "child process %d created", getpid());
    return(0);
  default:  /* parent */
    a->pid = cpid;
    a->state |= STATE_RUNNING;
    a->state &= ~STATE_STOPPED;
    if (sigprocmask(SIG_UNBLOCK, &ss, NULL) != 0) {
      LOG(ESPVR_FATAL, "can't UNBLOCK SIGCHLD %d (%s)", errno, strerror(errno));
    }
    return(1);
  }
}

/* async version of mount - create child to do dirty work */
/*
 *  parameters
 *  "<cartridgename> <generic drivename> <robot drivename>"
 */
static int ES_Mount(int reqID, int argc, char **argv) {
  AsyncOP *as;
  int ret;
 
  if (argc != 3) {
    LOG(ESPVR_FATAL, "ES_Mount: parameter count mismatch %d", argc);
    return(1);
  }

  if ((as = createAsync(OP_MOUNT, reqID)) == NULL) {
    LOG(ESPVR_FATAL, "ES_Mount: creating asynchronous process failed %d (%s)",
	errno, strerror(errno));
    return(1);
  }

  strcpy(as->volName, argv[0]);
  strcpy(as->genDriveName, argv[1]);
  strcpy(as->driveName, argv[2]);

  switch(makeChild(as)) {
  case 0:  /* child process - continue with mount op */
    if (libDummy) {  /* simulate PVR actions */
      doSleep(MAX_MOUNT_SECS);
      exit(0);
    } else {
      ret = LIBMount(as->volName, as->driveName, as->genDriveName);
      exit(ret);   /* DONE here */
    }
  case 1:  /* parent go home !!! */
    return(0);
  case -1:
    removeAsync(as);
    return(1);
    break;
  default:
    LOG(ESPVR_FATAL, "ES_Mount: garbage return from makeChild");
    removeAsync(as);
    return(1);
  }
}

/* <cartridgename> <generic drivename> <robot drivename> */
static int ES_Dismount(int reqID, int argc, char **argv)
{
  AsyncOP *as;
  int ret;
 
  if (argc != 3) {
    LOG(ESPVR_FATAL, "ES_Dismount: parameter count mismatch %d", argc);
    return(1);
  }

  if ((as = createAsync(OP_MOUNT, reqID)) == NULL) {
    LOG(ESPVR_FATAL,
	"ES_Dismount: creating asynchronous process failed %d (%s)",
	errno, strerror(errno));
    return(1);
  }

  strcpy(as->volName, argv[0]);
  strcpy(as->genDriveName, argv[1]);
  strcpy(as->driveName, argv[2]);

  switch(makeChild(as)) {
  case 0:  /* child process - continue with mount op */
    {
      char msg[256];
      
      if (!libDummy) {
	ret = LIBDismount(as->volName, as->driveName, as->genDriveName);
      } else { /* dummy actions */
	ret = 0;
	doSleep(MAX_DISMOUNT_SECS);
      }
      if (ret != 0)
	exit(ret);   /* DONE here */
      (void) sprintf(msg, "done %d %d %s", reqID, 0, as->volName);
      if (ForwardMessage(msg) != 0)
	exit(1);
      else
	exit(SILENT_DEATH);
    }
  case 1:  /* parent go home !!! */
    return(0);
  case -1:
    removeAsync(as);
    return(1);
    break;
  default:
    LOG(ESPVR_FATAL, "ES_Dismount: garbage return from makeChild");
    removeAsync(as);
    return(1);
  }
}

static int ES_NewDrive(int reqID, int argc, char **argv) {
  AsyncOP *as;
  int ret;
 
  LOG(ESPVR_INFO, "ES_NewDrive called");

  if (argc != 2) {
    LOG(ESPVR_FATAL, "ES_NewDrive: parameter count mismatch %d", argc);
    return(1);
  }

  if ((as = createAsync(OP_DRIVESTAT, reqID)) == NULL) {
    LOG(ESPVR_FATAL,
	"ES_NewDrive: creating asynchronous process failed %d (%s)",
	errno, strerror(errno));
    return(1);
  }

  strcpy(as->genDriveName, argv[0]);
  strcpy(as->driveName, argv[1]);

  switch(makeChild(as)) {
  case 0:  /* child process - continue with mount op */
    {
      char msg[256];

      as->volName[0] = '\0';
      if (!libDummy) {
	ret = LIBDriveStatus(as->driveName, as->genDriveName, as->volName);
      } else { /* dummy lib */
	ret = DRV_AVAILABLE_EMPTY;
      }
      if (ret == DRV_AVAILABLE_FILLED_KNOWN)
	(void) sprintf(msg, "done %d %d %s", reqID, ret, as->volName);
      else
	(void) sprintf(msg, "done %d %d", reqID, ret);
      if (ForwardMessage(msg) != 0)
	exit(1);
      else
	exit(SILENT_DEATH);   /* DONE here */
    }
  case 1:  /* parent go home !!! */
    return(0);
  case -1:
    removeAsync(as);
    return(1);
    break;
  default:
    LOG(ESPVR_FATAL, "ES_NewDrive: garbage return from makeChild");
    removeAsync(as);
    return(1);
  }
}

static AsyncOP *findAsync(int pid)
{
  AsyncOP *a = AHead;
  while(a) {
    if (a->pid == pid)
      return(a);
    a = a->next;
  }
  return(NULL);
}

static int exitStatus(int status, int *rval) {
  if (WIFEXITED(status)) {
    *rval = WEXITSTATUS(status);
    return (0);
  }
  if (WIFSIGNALED(status)) {
    *rval = WTERMSIG(status);
    return (1);
  }
  if (WIFSTOPPED(status)) {
    *rval = WSTOPSIG(status);
    return (-1);
  }
  return (-1);
}

static void childTerm(int sig) {
  int estat, ret, cid, ecode;
  AsyncOP *as;

  while((cid = waitpid(-1, &ret, WNOHANG)) > 0) {
    switch((ecode = exitStatus(ret, &estat))) {
    case 1:  /* signaled */
      LOG(ESPVR_WARNING, "got SIGNALED child death %d signal %d", cid, estat);
      break;
    case 0:  /* normal exit */
      break;
    case -1: /* stopped */
      LOG(ESPVR_WARNING, "got STOPPED child %d\n", cid);
      if (kill(cid, SIGKILL) != 0) {
	if (errno != ESRCH) {
	  LOG(ESPVR_WARNING, "can't kill STOPPED child %d got %d (%s)\n",
	      cid, errno, strerror(errno));
	}
      }
      return;
    default:
      LOG(ESPVR_WARNING, "unknown exit code %d from %d", ecode, cid);
      return;
    }
    LOG(ESPVR_INFO, "found dead child %d exitcode %d\n", cid, estat);

    /* find child in list and set exit status for them */
    if ((as = findAsync(cid)) != (AsyncOP *) 0) {
      as->state &= ~STATE_RUNNING;
      as->state |= STATE_STOPPED;
      as->returnCode = estat;
    } else {
      LOG(ESPVR_WARNING, "died child %d not in worker list !!", cid);
    }
  }
}

/* central exit function - kill all current worker childs here */
void pvr_exit(int code) {

  LOG(ESPVR_WARNING, "starting exit procedure (%d %d)", code, terminateMaster);

  if (masterProc) {  /* parent == 1 (init) -> master PVR process */
    AsyncOP *a = AHead;

    if (terminateMaster)  /* already done this before */
      return;
    while(a) {
      LOG(ESPVR_FATAL, "pvr_exit: killing child %d", a->pid);
      (void) kill(a->pid, SIGTERM);
      a = a->next;
    }
    terminateMaster = TRUE;
    if (pvlSock < 0) { /* we are not connected right now - loop in connectPVL() */
      LOG(ESPVR_FATAL, "es-pvr(%s) (%s) exit with code %d", pvrName, 
	  (masterProc) ? "Parent" : "Child", code); 
      exit(code);
    } 
  } else {  /* child bee */
    LOG(ESPVR_FATAL, "es-pvr(%s) (%s) exit with code %d", pvrName,
	(masterProc) ? "Parent" : "Child", code);
    exit(code);
  }
#if 0
  LOG(ESPVR_FATAL, "es-pvr(%s) (%s) exit with code %d", pvrName,
      (masterProc) ? "Parent" : "Child", code);
  exit(code);
#endif
}

static void done(int fd, int reqID, int retCode) {
  char buf[40];
  
  memset(buf, 0, sizeof(buf));
  (void) sprintf(buf, "done %d %d", reqID, retCode);
  (void) writeUTF(fd, buf);
}

/* loop through all childs in list and sent replies */
static void processWorker() {
  AsyncOP *as, *asNext;

  /* loop over all existing AsyncRequest and check */
  for(as = AHead; as; as = asNext) {
    asNext = as->next;

    if (as->state & STATE_STOPPED) {
      if (as->returnCode != SILENT_DEATH)
	done(pvlSock, as->requestID, as->returnCode);
      DEL1(AHead, ATail, as);
      removeAsync(as);
    }
  }
}

/* change verbosity of myself */
static int ES_LogLevel(int reqID, int argc, char **argv) {
  int nlevel;

  LOG(ESPVR_INFO, "LogLevel called with %d", argc);
  if (argc == 0)   /* query loglevel */
    return(logLevel + 1);
  if (argc != 1)
    return(1);
  nlevel = atoi(argv[0]);
  if (nlevel >= 0 && nlevel <= ESPVR_FATAL) {
    logLevel = nlevel;
    return(-1);
  }
  return(1);
}

static int ES_Terminate(int reqID, int argc, char **argv) {
  LOG(ESPVR_WARNING, "shutdown of PVR requested");
  pvr_exit(0);
  return(-1);
}

static struct {
  char cmd[40];
  PVRCmd  func;
} cmdList[] = {
  {"mount", ES_Mount},
  {"dismount", ES_Dismount},
  {"newdrive", ES_NewDrive},
  {"loglevel", ES_LogLevel},
  {"terminate", ES_Terminate},
  {NULL, NULL},
};

/* main loop processing requests */
static void dispatch() {
  int ntok, ret;
  char *tok[MAX_TOKENS];
  char buf[2048];
  fd_set fdset;
  int maxfd;

  LOG(ESPVR_INFO, "es-pvr(%s) ready accepting requests", pvrName);
  while(666) {
    if (terminateMaster) {
      if (AHead == NULL) { /* child list empty */
        LOG(ESPVR_FATAL, "es-pvr(%s) (Parent) exiting", pvrName);
	exit(1);
      }
    }
    FD_ZERO(&fdset);
    if (!terminateMaster)
      FD_SET(pvlSock, &fdset);
    FD_SET(logOut, &fdset);
    maxfd = (pvlSock > logOut) ? pvlSock + 1 : logOut + 1;
    ret = select(maxfd, &fdset, (fd_set *) 0, (fd_set *) 0, NULL);
    /*fprintf(stderr, "select -> %d (%d)\n", ret, errno);*/
    if (ret < 0) {
      if (errno == EINTR) {  /* could be SIGCGLD caught !! */
	processWorker();
	continue;
      }
      /* log select error here */
      LOG(ESPVR_FATAL, "select error on PVL connection - attempt reconnect");
      return;
    }
    if (ret == 0) { /* should not happen */
      continue;
    }

    /* fd is readable !!! */

    if (FD_ISSET(logOut, &fdset)) {  /* log message to forward */
      if ((ret = readUTF(logOut, buf, 2048)) > 0) {
	int len = strlen(buf);
	unsigned short nlen = htons(len);
	(void) write(pvlSock, &nlen, 2);
	(void) write(pvlSock, buf, len);
      }
      continue;
    }

    if ((ret = readUTF(pvlSock, buf, 2048)) <= 0) {
      /* log error message -> end of file */
      LOG(ESPVR_FATAL, "read error on PVL connection - attempt reconnect");
      return;
    }
    if ((ntok = tokenize(buf, tok)) >= 2) { /* <command> <reqID> ..... */
      int argc;
      char **argv;
      int reqID = atoi(tok[1]);
      char *cmd = tok[0];
      int  cret, i;

      /* lookup command and call */
      for(i=0; strlen(cmdList[i].cmd) > 0 != NULL; i++) {
	if (strcmp(cmdList[i].cmd, cmd) == 0) { /* get it */
	  argc = ntok - 2;
	  argv = &tok[2];
	  cret = (*cmdList[i].func)(reqID, argc, argv);
	  /*
	   * structure of cret
	   * ==  0  -> success (for async process) -> be quite
	   *  >  0  -> indicate error while preparing command
	   * == -1  -> success for immediate command
	   */
	  if (cret != 0) {
	    if (cret == -1)  /* special treatment of -1 for immed. commands */
	      cret = 0;
	    done(pvlSock, reqID, cret);
	  }
	  break;
	}
      }
      if (strlen(cmdList[i].cmd) == 0) { /* command not found */
	LOG(ESPVR_WARNING, "command '%s' not found\n", cmd);
	continue;
      }
    } else {
      LOG(ESPVR_INFO, "garbled message '%s'\n", buf);
    }
  }
}

static int connectPVL() {
  struct sockaddr_in saddr;
  char msg[2048];
  int sock = -1, ernum;
  char *rstr;
  struct hostent *he;

 connectLoop:

  /* open network connection */
  if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    LOG(ESPVR_FATAL, "socket creation failed %d (%s)",
	errno, strerror(errno));
    return(-1);
  }
  memset((char *) &saddr, 0, sizeof(saddr));
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons((unsigned short) pvlPort);
  if ((he = gethostbyname(pvlHost)) != NULL) {
    memcpy((char *) &saddr.sin_addr.s_addr, he->h_addr, he->h_length);
  } else {
    LOG(ESPVR_FATAL, "unknown PVL host '%s' -> %d (%s)\n",
	pvlHost, errno, strerror(errno));
    return(-1);
  }

  if (connect(sock, (struct sockaddr *) &saddr, sizeof(saddr)) < 0) {
     LOG(ESPVR_FATAL, "PVL (%s:%d) connect failed %d (%s)\n",
	 pvlHost, pvlPort, errno, strerror(errno));
     (void) close(sock);
     sock = -1;
     /*return(-1);*/
     goto doRetry;
  }

  /* send hello command to start session on the PVL side */
  (void) sprintf(msg, "hello %s %s", pvrName, hwInfo);
  if (writeUTF(sock, msg) < 0) {
    LOG(ESPVR_FATAL, "PVL (%s:%d) writeUTF failed %d (%s)\n",
	pvlHost, pvlPort, errno, strerror(errno));
    (void) close(sock);
    sock = -1;
    /*return(-1);*/
    goto doRetry;
  }
  if ((ernum = readUTF(sock, msg, sizeof(msg))) <= 0) {
    LOG(ESPVR_FATAL, "PVL (%s:%d) readUTF failed %d (%s)\n",
	pvlHost, pvlPort, errno, strerror(errno));
    (void) close(sock);
    sock = -1;
    goto doRetry;
    /*return(-1);*/
  }

  if (strcmp(msg, "welcome") != 0) {
    LOG(ESPVR_FATAL, "PVL-Session init failed: '%s'\n", msg);
    (void) close(sock);
    sock = -1;
    goto doRetry;
    /*return(-1);*/
  }
  pvlSock = sock;
  LOG(ESPVR_INFO, "PVL Session initialized");
  return(0);

 doRetry:
  LOG(ESPVR_INFO, "connect to PVL(%s:%d) failed - retry", pvlHost, pvlPort);
  sleep(5);
  goto connectLoop;
}

static void usage() {
  fprintf(stderr, "usage: %s <opions>\n", progname);
  fprintf(stderr, "   -n \"name of PVR instance\"\n");
  fprintf(stderr, "   -a \"hostname were PVL is running\"\n");
  fprintf(stderr, "   -p \"port were PVL is listening\"\n");
  fprintf(stderr, "   -o \"option string for specific PVR\"\n");
  fprintf(stderr, "   -d  don't run as daemon process\n");
  fprintf(stderr, "   -t  dummy mode - don't use real library\n");
  fprintf(stderr, "   -l  filename to put log message in\n");
}

int main(int argc, char **argv) {
  struct sigaction sAct;
  char *pvrOption = (char *) 0;
  int c;
  int pfd[2];
  int alreadyDaemon = FALSE;

  if ((progname = strrchr(argv[0], '/')) == NULL)
    progname = argv[0];
  else
    progname++;
  hwInfo[0] = '\0';

  while ((c = getopt(argc, argv, "dtn:p:a:o:l:")) != -1) {
    switch (c) {
    case 'd':
      becomeDaemon = FALSE;
      break;
    case 'n':   /* whats my instance name ? */
      pvrName = strdup(optarg);
      break;
    case 'p':
      pvlPort = atoi(optarg);
      break;
    case 'a':
      pvlHost = strdup(optarg);
      break;
    case 'o':
      pvrOption = strdup(optarg);
      break;
    case 'l':
      _logFile = strdup(optarg);
      break;
    case 't':
      libDummy = TRUE;
      break;
    case '?':
      usage();
      exit(1);
    }
  }

  if (!pvrName || !pvlHost || pvlPort < 0) {
    fprintf(stderr, "you must specify <name> <PVL-Host> <PVL-Port>\n");
    usage();
    exit(1);
  }

#if 0
  if (geteuid()) {
    fprintf(stderr, "Must be root to start %s\n", progname);
    exit(1);
  }
#endif

  sAct.sa_handler = pvr_exit;
  if (sigemptyset(&sAct.sa_mask) != 0)
    fprintf(stderr, "segemptyset failed %d (%s)\n", errno, strerror(errno));
  sAct.sa_flags = 0;
  if (sigaction(SIGUSR1, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGUSR1 failed (errno %d)\n", errno);
  if (sigaction(SIGTERM, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGTERM failed (errno %d)\n", errno);
  if (sigaction(SIGPIPE, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGPIPE failed (errno %d)\n", errno);


  if (sigemptyset(&sAct.sa_mask) != 0)
    fprintf(stderr, "segemptyset failed %d (%s)\n", errno, strerror(errno));
  sAct.sa_flags = 0;
	/* handle SIGCHILD  */
  sAct.sa_handler = childTerm;
  if (sigaction(SIGCHLD, &sAct, (struct sigaction *) 0) != 0) {
    fprintf(stderr, "sigaction() SIGCHLD failed (errno %d)\n", errno);
    exit(1);
  }

  sAct.sa_handler = SIG_IGN;
  if (sigemptyset(&sAct.sa_mask) != 0)
    fprintf(stderr, "segemptyset failed %d (%s)\n", errno, strerror(errno));
  sAct.sa_flags = 0;

  if (sigaction(SIGHUP, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGHUP failed (errno %d)\n", errno);
  if (sigaction(SIGINT, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGINT failed (errno %d)\n", errno);
  if (sigaction(SIGQUIT, &sAct, (struct sigaction *) 0) != 0)
    fprintf(stderr, "sigaction() SIGQUIT failed (errno %d)\n", errno);
  if (sigaction(SIGPIPE, &sAct, (struct sigaction *) 0) != 0) 
    fprintf(stderr, "sigaction() SIGPIPE failed (errno %d)\n", errno); 

  /* init library specific code - give him at least a chance to do */
  if (!libDummy) {
    if (LIBInit(pvrOption, hwInfo) != 0) {
      fprintf(stderr, "library initialization failed");
      exit(1);
    }
  } else { /* dummy lib */
    (void) sprintf(hwInfo, "DUMMY-PVR");
  }

  if (becomeDaemon && daemonize() != 0) { 
    LOG(ESPVR_FATAL, "daemonize myself failed"); 
    exit(4); 
  }

  while(666) {

    /* connect to given PVL */
    if (connectPVL() != 0) {
      LOG(ESPVR_FATAL, "connect to PVL (%s:%d) failed", pvlHost, pvlPort);
      exit(2);
    }
    
    /* open pipe to merge log output */
    if (pipe(pfd) != 0) {
      LOG(ESPVR_FATAL, "pipe call failed %d (%s)\n", errno, strerror(errno));
      exit(3);
    }
    logIn = pfd[0];
    logOut = pfd[1];
    
    dispatch();   /* do the dirty work */
    (void) close(pvlSock);
    (void) close(logIn);
    (void) close(logOut);
    pvlSock = logIn = logOut = -1;

  }  /* end of while overall :-) */

  /*pvr_exit(5);*/
}
