/*
 * Copyrighted as an unpublished work.
 * (c) Copyright 1992, 1993 Lachman Technology, Inc.
 * All rights reserved.
 *
 *
 * RESTRICTED RIGHTS
 *
 * These programs are supplied under a license.  They may be used,
 * disclosed, and/or copied only as permitted under such license
 * agreement.  Any copy must contain the above copyright notice and
 * this restricted rights notice.  Use, copying, and/or disclosure
 * of the programs is strictly prohibited unless otherwise provided
 * in the license agreement.
 */
/*
 * STK Powderhorn & Eagle RMS Library Server -mg DESY
 */

#ifndef lint
static char rcsid[] = "$Id: ls-powderhorn.c,v 1.1 1999-08-27 13:53:28 cvs Exp $";
#endif

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <memory.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <setjmp.h>
#include <signal.h>
#include <time.h>

#ifndef __STDC__
#include <varargs.h>
#else
#include <stdarg.h>
#endif

#include <sys/ioctl.h>
#include <sys/mtio.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/param.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/time.h>

#include <osmlimits.h>
#include <osmlicense.h>
#include <ls.h>
#include <label_api.h>
#include <dbops.h>
#include <osmerrno.h>
#include <osmlog.h>
#include <osmconf.h>
#include <util.h>
#include <rms/lsent.h>
#include <rms/mtent.h>
#include <rms/rmsapi.h>

#include <acsapi.h>
#include <eagle.h>

static int unmount(struct drive *);

#define SMALLBUF		256
#define	DEV_START		"/dev/"

/* time for retry is MAXTRIES x SLEEPSECS */
#define	MAXTRIES		20
#define	SLEEPSECS		30

/* timeouts for ACSLS calls */
#define RESP_TIMEOUT 30   /* timeout for acs_response() in seconds */
#define ACS_TIMEOUT 180   /* timeout used for acsWait() */
#define MAX_RESPONSE_TIMEOUT 10  /* max timeout counter for acsWait() */

/* define this if ls-grau should generate a file with mount statistics */
#define MOUNT_STAT "/osm/log/mountstat"

/* global info */
int InMount;
static char ACS_PACKET_VER[] = "ACSAPI_PACKET_VERSION=2";
struct drive   *drives = NULL, *drives_tail = NULL;
struct volume  *newvolumes = NULL;
struct volume  *missingvolumes = NULL;
/* static pointer to local 'cached' volume information (mirrored to DB) */
static Elements *elems = NULL, *elems_tail = NULL;
jmp_buf         env;


struct feature_entry {
  char *product_name;
  char *feature_name;
};

static void	find_owner();

char           *get_rmshome();

FILE       *popen();
int        select();
int        fclose();
int        pclose();
int        system();
int        ioctl();
/* int             mt_asf1(); */
static int RewindUnloadTape(char *);
void       lspowderhorn_exit(int);

extern int      errno;
extern char    *lsname;

extern int manualMount;
extern char operatorDisplay;
extern int acsapi_init;  /* comes from ACSAPI toolkit */

/* pers. custom. :--)) */
#ifndef False
#define False FALSE
#define True  TRUE;
#endif

#define ACS_DRIVE_FORMAT "%d:%d:%d:%d"  /* string format of ACS locations */
#define DESY_LABEL_FORMAT "%s:%s"  /* external versus real label */
#define STK_LABEL_SIZE 6

/* define offsets for sequence numbers used for ACSAPI access */
#define ACS_SEQ_MOUNT 304
#define ACS_SEQ_DISMOUNT 405
#define ACS_SEQ_VOLINFO 509
#define ACS_SEQ_CANCEL 609

/* to catch internal ACSAPI error messages */
int acs_error(char *msg) {
  osmlog(LOG_ERR, "ACSAPI(internal errormessage): %s", msg);
}


#ifndef __STDC__
static void MountStatEntry(fmt, va_alist)
char *fmt;
va_dcl
#else
static void MountStatEntry(char *fmt, ...)
#endif
{
  va_list args;
  FILE *f;

#ifndef __STDC__
  va_start(args);
#else
  va_start(args, fmt);
#endif

#ifdef MOUNT_STAT
  if ((f = fopen(MOUNT_STAT, "a+")) == NULL) {
    osmlog(LOG_DEBUG, "can't open mount-stat file %s (%s)",
	   MOUNT_STAT, strerror(errno));
    return;
  }
  (void) vfprintf(f, fmt, args);
  (void) fclose(f);
#endif

  va_end(args);
}

/* 
 * historic stuff - not really needed any longer
 */
static char *RealLabel(char *extlabel) {
  static char Label[100];

  strcpy(Label, extlabel);
  return(Label);
}

/* compare two drive locations */
static int sameDrive(DRIVEID *d1, DRIVEID *d2) {
  return(d1->panel_id.lsm_id.acs == d2->panel_id.lsm_id.acs &&
         d1->panel_id.lsm_id.lsm == d2->panel_id.lsm_id.lsm &&
         d1->panel_id.panel == d2->panel_id.panel &&
         d1->drive == d2->drive);
}

static char *drive2str(DRIVEID *d) {
  static char msg1[20];
  static char msg2[20];
  static char *r, *m = msg1;

  r = m;
  (void) sprintf(m, "%02d:%02d:%02d:%02d", d->panel_id.lsm_id.acs,
		 d->panel_id.lsm_id.lsm, d->panel_id.panel, d->drive);
  m = (m == msg1) ? msg2 : msg1;
  return(r);
}

/* return unique sequence number for ACSLS access */
SEQ_NO sequenceNumber() {
  static SEQ_NO seq = 0;  /* SEQ_NO <--> unsigned short */

  return(seq++);
}

static char *time2str(time_t *tim)
{
  struct tm *t;
  static char cTime[100];

  t = localtime(tim);
  (void) sprintf(cTime, "%02d/%02d-%02d:%02d:%02d",
		 t->tm_mday, t->tm_mon + 1,
		 t->tm_hour, t->tm_min, t->tm_sec);
  return(cTime);
}

/* return unique and next free .addr member for element */
int getNextFreeAddr()
{
  static int lastAddr = (-1);
  Elements *e;

  if (lastAddr < 0) { /* not init yet */
    if (!elems) {  /* nobody has filled it up yet */
      lastAddr = 0;  /* so let's start here (could be an empty library) */
    } else {
      for(e = elems; e; e = e->next) {
	if (e->el.addr > lastAddr)
	  lastAddr = e->el.addr;
      }
      lastAddr++;
    }
  } else {
    lastAddr++;
  }
  return(lastAddr);
}

int strtostkdrive(char *s, DRIVEID *d) {
  int acs, lsm, panel, transp;

  if (sscanf(s, ACS_DRIVE_FORMAT, &acs, &lsm, &panel, &transp) != 4) {
    osmlog(LOG_CRIT, "can't convert stk drive %s into ACS format", s);
    return(-1);
  }
  /* do some sanity checking here directly */
  if (acs < 0 || lsm < 0 || panel < 0 || transp < 0 ||
      acs > 127 || lsm > 127 || panel > 127 || transp > 127) {
    osmlog(LOG_CRIT, "illegal component of ACS address; acs %d lsm %d panel %d drive %d", acs, lsm, panel, transp);
    return(-1);
  }
  d->panel_id.lsm_id.acs = (char) acs & 0xff;
  d->panel_id.lsm_id.lsm = (char) lsm & 0xff;
  d->panel_id.panel      = (char) panel & 0xff;
  d->drive               = (char) transp & 0xff;
#if 0
  osmlog(LOG_DEBUG, "driveMap: FROM %s TO acs %d lsm %d panel %d drive %d\n",
	  s, acs, lsm, panel, transp);
#endif
  return(0);
}

struct drive *mkdrive()
{
  struct drive   *drive;

  if ((drive = (struct drive *)calloc(1, sizeof(*drive))) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  drive->name = NULL;
  drive->device = NULL;
  drive->rdevice = NULL;
  drive->sgdev = NULL;
  drive->host = NULL;
  drive->mediatype = NULL;
  drive->mediaent = NULL;
  drive->mountp = NULL;
  ADD(drives, drives_tail, drive);
  return drive;
}

void rmdrive(struct drive *drive)
{
  if (drive->name != NULL)
    (void)free((char *)drive->name);
  if (drive->device != NULL)
    (void)free((char *)drive->device);
  if (drive->rdevice != NULL)
    (void)free((char *)drive->rdevice);
  if (drive->sgdev != NULL)
    (void)free((char *)drive->sgdev);
  if (drive->host != NULL)
    (void)free((char *)drive->host);
  if (drive->mediatype != NULL)
    (void)free((char *)drive->mediatype);
  if (drive->mediaent != NULL)
    (void)free((char *)drive->mediaent);
  if (drive->mountp != NULL)
    (void)free((char *)drive->mountp);
  DEL(drives, drives_tail, drive);
}

struct volume *mkvolume(char *name, char *mediatype, char *allocBy,
			u_int flags, long capacity)
{
  struct volume  *volume;
  
  if ((volume = (struct volume *) calloc(1, sizeof(struct volume))) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  
  volume->next = NULL;
  volume->sides = 1;

  if ((volume->nameA = strdup(name)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->nameB = strdup("")) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->mediatype = strdup(mediatype)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->allocatedby = strdup(allocBy)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  volume->capacity = capacity;
  return(volume);
}

struct volume *mkvolumebyelem(struct element *el)
{
  struct volume  *volume;
  
  if ((volume = (struct volume *) calloc(1, sizeof(struct volume))) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  
  volume->next = NULL;
  volume->sides = 1;

  if ((volume->nameA = strdup(el->nameA)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->nameB = strdup("")) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->mediatype = strdup(el->mediatype)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  if ((volume->allocatedby = strdup(el->allocatedby)) == NULL) {
    osmerrno = ENOMEMORY;
    return NULL;
  }
  volume->capacity = el->capacity;
  return(volume);
}

void rmvolume(struct volume *volume)
{
  if (volume->nameA != NULL)
    (void)free((char *)volume->nameA);
  if (volume->nameB != NULL)
    (void)free((char *)volume->nameB);
  if (volume->mediatype != NULL)
    (void)free((char *)volume->mediatype);
  if (volume->allocatedby != NULL)
    (void)free((char *)volume->allocatedby);
  (void)free((char *)volume);
}

/* new version for dynamic allocations */
struct element *findvolumebyfullname(char *name)
{
  Elements*el = elems;

  osmerrno = ENOSUCHVOLUME;
  /* check first if list is existing */
  if (!el) {   /* easiest case */
    return((struct element *) 0);
  }
  while(el) {
    if (strcmp(el->el.nameA, name) == 0) {
      osmerrno = 0;
      return(&el->el);
    }
    el = el->next;
  }
  return((struct element *) 0);
}

struct drive *finddrivebyaddr(int addr)
{
  struct drive *drive;

  for (drive = drives; drive != NULL; drive = drive->next)
    if (drive->addr == addr)
      return drive;
  osmerrno = ENOSUCHDRIVE;
  return(NULL);
}

void
myusleep(usecs)
  long            usecs;
{
  struct timeval  tv;

  tv.tv_sec = usecs / 1000000;
  tv.tv_usec = usecs % 1000000;

  (void)select(0, 0, 0, 0, &tv);
}

int checkDeferredLabel(AsyncMount *as)
{
  struct element *el = findvolumebyfullname(as->volname);
  if (!el) {
    osmlog(LOG_ERRD, "can't find element of volume %s", as->volname);
    return(-1);
  }
    
  if (el->flags & NEEDS_LABEL_UPDATE) {
    /* turn off the update flag and commit back to database */
    el->flags &= ~NEEDS_LABEL_UPDATE;
    if (db_store(el) == -1) {
      osmlog(LOG_ERRD, "DB update failed for %s", as->volname);
      return(-1);
    }
  }
  return(0);
}

int load_conf()
{
  FILE           *fp;
  char            line[100], **argv, pwd[MAXPATHLEN + 2];
  struct drive   *drive, *nextdrive;
  struct mtent   *mtent;

  if (getcwd(pwd, sizeof(pwd)) == NULL) {
    osmlog(LOG_CRIT, "can't get working directory: %s\n", pwd);
    return -1;
  }
  for (drive = drives; drive != NULL; drive = nextdrive) {
    nextdrive = drive->next;
    rmdrive(drive);
  }
  drives = NULL;

  if ((fp = fopen(CONFIGFILE, "r")) == NULL) {
    osmerrno = OSMSYSERR;
    osmlog(LOG_CRIT, "can't open %s/%s: %s\n", pwd, CONFIGFILE, osmstrerr());
    return -1;
  }

  while (fgets(line, sizeof line, fp) != NULL) {
    line[strlen(line) - 1] = '\0';
    argv = tokens(line, 9);
    /* skip empty lines and comments */
    if (argv[0][0] == '\0' || argv[0][0] == '#') {
      free((char *)argv);
      continue;
    }

/*<id> <device> <sgdevice> <host> <wait> <hog> <release> <mediafamily> <mediatype> */
    if ((drive = mkdrive()) == NULL) {
      osmerrno = OSMSYSERR;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      free((char *)argv);
      return -1;
    }
    if ((drive->name = strdup(argv[0])) == NULL) {
      osmerrno = ENOMEMORY;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }
    drive->addr = atoi(drive->name);

    if ((drive->device = strdup(argv[1])) == NULL) {
      osmerrno = ENOMEMORY;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }

    if ((drive->sgdev = strdup(argv[2])) == NULL) {
      osmerrno = ENOMEMORY;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }
    if ((drive->host = strdup(argv[3])) == NULL) {
      osmerrno = ENOMEMORY;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }
    drive->waittime = atoi(argv[4]);
    drive->hogtime = atoi(argv[5]);
    drive->releasetime = atoi(argv[6]);
    if ((drive->mediatype = strdup(argv[7])) == NULL) {
      osmerrno = ENOMEMORY;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }
    if (argv[8] != NULL && *argv[8] != '\0') {
      if ((mtent = getmtbyname(argv[8])) != NULL) {
	if ((drive->mediaent = 
	     (struct mtent *)malloc(sizeof *drive->mediaent))== NULL) {
	  osmerrno = ENOMEMORY;
	  osmlog(LOG_CRIT, "%s\n", osmstrerr());
	  rmdrive(drive);
	  free((char *)argv);
	  return -1;
	}
	*drive->mediaent = *mtent;
      }
      else {
	osmlog(LOG_ERRD,
	       "unknown specific mediatype `%s' configured for drive %d\n",
	       argv[8], drive->addr);
	return -1;
      }
    } else {
      osmlog(LOG_ERRD,
	     "no specific mediatype has been configured for drive %d\n",
	     drive->addr);
      return(-1);
    }
    if (strlen(drive->mediatype) > MEDIATYPELEN - 1) {
      osmerrno = EMEDIATYPETOOLONG;
      osmlog(LOG_CRIT, "%s\n", osmstrerr());
      rmdrive(drive);
      free((char *)argv);
      return -1;
    }
    drive->mountp = NULL;
    free((char *)argv);
    osmlog(LOG_DEBUG, "enable Drive %d (%s) with ACS address %s",
	   drive->addr, drive->device, drive->sgdev);
  }
  if (fclose(fp) == EOF) {
    osmerrno = OSMSYSERR;
    osmlog(LOG_CRIT, "can't close %s/%s: %s", pwd, CONFIGFILE, osmstrerr());
    return -1;
  }
  return 0;
}

int
ls_connect()
{
  return(0);  /* we believe everybody !!! */
}

/* more or less meaningless on GRAU systems (cause AMU keeps track of it)
 * notable here that we only should check if all drives are present
 * and ready to go
 */
int ls_inventory()
{
/*  fprintf(stderr, "ls_inventory() called\n");  */
  osmlog(LOG_DEBUG, "ls_inventory() called");
  return(0);
}

int ls_getinfo(arg_drives, arg_volumes)
struct drive  **arg_drives;
struct volume **arg_volumes;
{
  int             addr;
  struct volume  *volume, *lastvolume;
  static struct volume *volumes = NULL;
  Elements *el;

  *arg_drives = drives;

  while (volumes != NULL) {
    volume = volumes;
    volumes = volumes->next;
    rmvolume(volume);
  }

  lastvolume = NULL;
  for(el = elems; el; el = el->next) {
    if (el->el.nameA[0] != '\0') {
      if ((volume = mkvolumebyelem(&el->el)) == NULL)
	return -1;

/*      fprintf(stderr, "ls_getinfo(): vol %s\n", volume->nameA);  */

      if (lastvolume != NULL)
	lastvolume->next = volume;
      else
	volumes = volume;
      lastvolume = volume;
    }
  }
  *arg_volumes = volumes;
  return 0;
}

int ls_init(arg_drives, arg_volumes)
struct drive  **arg_drives;
struct volume **arg_volumes;
{
  int addr, slots, db_slots = 0;

  char   *product, *tvol;
  struct drive   *drive_p;
  struct volume  *volume_p;
  struct sigaction sa;

/* set ACSAPI need PACKET-version as environment */
  if (putenv(ACS_PACKET_VER) != 0) {
    osmlog(LOG_ERR, "setting env '%s' failed (%s)",
	   ACS_PACKET_VER, strerror(errno));
  }

  if (load_conf() == -1)  /* here the global *drives is init */
    return(-1);

  sa.sa_flags = 0;
  sa.sa_handler = lspowderhorn_exit;
  (void) sigemptyset(&sa.sa_mask);
  (void) sigaction(SIGHUP, &sa, (struct sigaction *) 0);
  (void) sigaction(SIGINT, &sa, (struct sigaction *) 0);
  (void) sigaction(SIGQUIT, &sa, (struct sigaction *) 0);
  (void) sigaction(SIGTERM, &sa, (struct sigaction *) 0);
  (void) sigaction(SIGUSR1, &sa, (struct sigaction *) 0);

  if (ls_connect() == -1)
    return -1;

  if (db_open(VOLUMESDB, O_RDWR | O_CREAT | O_SYNC) == -1)
    return -1;

  if (db_loadall(&elems, &elems_tail) == -1)
    return -1;

  if (passdb_open(VOLPASSES, O_RDWR|O_SYNC) == -1) {
    osmlog(LOG_CRIT, "can't open vol_passes database: %s", osmstrerr());
    return(-1);
  }

  /* we filled up drives from our local configuration and the volumes
   * only from the db contens (we trust them)
   */

  if (ls_getinfo(arg_drives, arg_volumes) == -1)
    return(-1);

/*  (void) ls_unmount_all(); */
  osmlog(LOG_DEBUG, "ls_init (Powderhorn) done; READY for operation");

  return(0);
}

int ls_mount(char *volumename, char *driveaddrstr)
{
  return(0);
}

/* force re-open of socket connection to 'ssi' for new child proc */
int acsInit() {
  STATUS stat = acs_init();

  if (stat != STATUS_SUCCESS) {
    osmlog(LOG_CRIT, "init. of ACSAPI client failed (%s)", cl_status(stat));
    return(-1);
  }
  return(0);
}

/* flush and close socket 'ssi' connection */
void acsClose() {
  STATUS s = cl_ipc_destroy();
  
  if (s != STATUS_SUCCESS) {
    osmlog(LOG_CRIT, "destroy of ACSAPI client failed (%s)", cl_status(s));
  }
  acsapi_init = 0;  /* dirty hack; butt it works ..... (force init() work) */
}

static int resetAndRewind(int tfd)
{
  if (TReset(tfd) == 0) {
    osmlog(LOG_INFO, "resetting drive okay - waiting 15 seconds to come up");
    sleep(15);
    return(TRewind(tfd));
  }
  return(-1);
}

static int callOperator(char *cmd)
{
  int ret, ecode, estat;

  if ((ret = system(cmd)) != 0) {
    if (ret < 0) {
      osmlog(LOG_ERRD, "calling '%s' failed %d (%s)",
	     cmd, errno, strerror(errno));
      return(-1);
    } else {
      ecode = rt_exit(ret, &estat);
      if (ecode == 0) { /* normal exit */
	return(estat);
      } else {
	osmlog(LOG_ERRD, "OPERATOR call command exited ungracefully %d %d",
	       ecode, estat);
	return(-1);
      }
    }
  }
  osmlog(LOG_DEBUG, "OPERATOR command '%s' succeed", cmd);
  return(0);
}

/* check if robot is still alive !!!! */
int robotAlive() {
  int ret;

  return(0);
}

/* asynchronous mount */
int ls_mount_async(char *volumename, char *driveaddrstr)
{
  char            tmpname[VOLNAMELEN], *label;
  int             type = TAPE;
  int             driveaddr, sosmerrno;
  struct drive   *drive;
  label_data     *label_p;
  struct element *el;
  int             dfd = -1;  /* filedescriptor for opened drive */
  int             retried = FALSE;

  osmlog(LOG_DEBUG, "ls_mount_async(): drive '%s' volume '%s'",
	  driveaddrstr, volumename);

  if (strlen(volumename) >= VOLNAMELEN)
    return(-1);
  if ((el = findvolumebyfullname(volumename)) == NULL)
    return(-1);
  driveaddr = atoi(driveaddrstr);

  if ((drive = finddrivebyaddr(driveaddr)) == NULL)
    return(-1);

retry:

  if (!manualMount) {

    if (acsInit() != 0) {
      osmlog(LOG_ERR, "can't init ACSAPI client stuff");
      return(-1);
    }

    /* ACSAPI specific mount operation */
    if (acsMount(drive, el) != 0) {
      osmlog(LOG_ERRD, "ACSMount failed '%s' drive '%s'",
	     volumename, drive->device);
      osmerrno = (osmerrno == OSMOK) ? EVOLMOUNT : osmerrno;
      acsClose();
      return(-1);
    }
  } else { /* manual mount section */
    char cmd[2048];
    int  rstat;

    memset(cmd, 0, 2048);
    (void) sprintf(cmd, "/rms/bin/grauManual mount %s %s %s",
		   operatorDisplay, drive->device, el->nameA);
    rstat = callOperator(cmd);
    if (rstat == 0) {
      osmlog(LOG_DEBUG, "manual mount of '%s' on '%s' succeed",
	     el->nameA, drive->device);
    } else {
      osmlog(LOG_ERRD, "manual mount of '%s' on '%s' failed %d",
	     el->nameA, drive->device, rstat);
      osmerrno = EVOLMOUNT;
      return(-1);
    }
  }

  /* check READY data path */
  if ((dfd = TOpenReady(drive->device,
			(el->flags & NEEDS_LABEL_UPDATE) ?
			O_WRONLY : O_RDONLY, 200)) < 0) {
    if (errno == EACCES) { /* media is write-protected */
      osmlog(LOG_ERRD, "MOUNT: media '%s' on drive '%s' is write protected",
	     volumename, drive->device);
      osmerrno == EWRITEPROTECT;
      goto mountfail;
    }
    osmlog(LOG_CRIT, "drive %s not ready", drive->device);
    /* senddisabledrive(drive->name); */
    osmerrno = EDRIVEBUSY;
    goto mountfail;
  }

  osmlog(LOG_DEBUG, "ls_mount_async: drive %s (%s) Ready/Open",
	 drive->device, drive->sgdev);

  /* if this volume is fresh, label it here (and only here) */
  if (el->flags & NEEDS_LABEL_UPDATE) {
    char *label = RealLabel(volumename);

    if (TRewind(dfd) != 0) {
      osmlog(LOG_ERRD, "rewind error for writelabel on %s", volumename);
      goto mountfail;
    }

    osmlog(LOG_INFO, "Deferred label update needed on %s", volumename);
    if ((label_p = (label_data *) calloc(1, sizeof(label_data)))  ==
	(label_data *) NULL) {
      osmlog(LOG_CRIT, "allocating label memory failed for %s", volumename);
      goto mountfail;
    }
    /* NOTE: el->nameA == volumename */
    strcpy(label_p->label_name, el->nameA);
    /* just update the label struct we got back */
    strcpy(label_p->label_owner, el->allocatedby);
    osmlog(LOG_DEBUG, "Updating label on volume %s", volumename);
    if (writelabel(dfd, drive, label_p, type) == -1) {
      osmlog(LOG_ERRD, "volume <%s> label update has failed", volumename);
      (void) free(label_p);
      osmerrno = EIOWRITE;
      goto mountfail;
    }
    (void) free(label_p);
    /* updating the label flag is done in parent proc */
  }
  /* read label even if we just wrote it - be paranoid !!!*/
  if ((label_p = readlabel(dfd, type)) != NULL) {
    label = label_p->label_name;
    if (strcmp(label, volumename) != 0) {
      osmlog(LOG_ERRD, "volume <%s> is labeled as <%s>!", volumename, label);
      goto mountfail;
    }
  } else {
    osmlog(LOG_ERRD, "volume <%s> has a bogus label!", volumename);
    goto mountfail;
  }

mountokay:

  osmlog(LOG_DEBUG, "ls_mount_async: drive '%s' volume '%s' - done",
	 drive->device, volumename);

  (void) TClose(dfd);

  acsClose();

  return(0);   /* the main and only good return here */

mountfail:

  sosmerrno = osmerrno;

  osmlog(LOG_CRIT, "ls_mount_async: mount of %s on %s (%s) failed; unmounting",
	 volumename, drive->device, drive->sgdev);

  if (dfd >= 0) {
    (void) TRewind(dfd);
    (void) TClose(dfd);
  }
  if (unmount(drive) != 0) {
      
    /* FIXME print real fatal error message to operator !!!! */
    
    osmlog(LOG_ERRD, "ls_mount : can't unmount vol %s in ACS on drive %s",
	   volumename, drive->device);
    osmerrno = EVOLDISMOUNT;
  }
  acsClose();
  /* senddisabledrive(drive->name); */
  osmerrno = sosmerrno;   /* restore save error value */
  return(-1);
}

int ls_unmount(char *driveaddrstr)
{
  return(0);
}

static int unmount(struct drive *drive)
{
  int  aciret, dfd = -1;
  char v_name[80];

  osmlog(LOG_DEBUG, "unmount for '%s'", drive->device);

retry:

#if 0
  if (!manualMount) {
    if ((aciret = aciDriveMounted(drive, v_name)) != 1) {
      if (aciret == 0)  /* no volume in drive */
	goto done;
      else {
	osmlog(LOG_ERRD, "unmount: error getting drive '%s' status",
	       drive->sgdev);
	strcpy(v_name, "UNKNOWN");
	goto failed;
      }
    }
  }
#else
  strcpy(v_name, "UNKNOWN");
#endif

  /* open/unload/close sequence */
  if ((dfd = TOpenReady(drive->device, O_RDONLY, 10)) < 0 ||
      TUnload(dfd) != 0) {
    osmlog(LOG_ERRD, "unmount: error unloading %s -- continue", drive->device);
  }
  if (dfd >= 0)
    (void) TClose(dfd);

  if (!manualMount) {

    if (acsInit() != 0) {
      osmlog(LOG_ERR, "unmount: can't init ACSAPI client stuff");
      /* return(-1);*/
    }
    if (acsDismount(drive) != 0) {
      osmlog(LOG_ERRD, "unmount: acsDismount failed for drive %s (%s)",
	     drive->device, drive->sgdev);
      osmerrno = (osmerrno == OSMOK) ? EVOLDISMOUNT : osmerrno;
      acsClose();
    } else {
      acsClose();
      goto done;
    }
  } else { /* manual dismount section */
    char cmd[2048];
    int  rstat;

    memset(cmd, 0, 2048);
    (void) sprintf(cmd, "/rms/bin/grauManual dismount %s %s",
		   operatorDisplay, drive->device);
    rstat = callOperator(cmd);
    if (rstat == 0) {
      osmlog(LOG_DEBUG, "manual dismount on '%s' succeed",
	     drive->device);
      goto done;
    } else {
      osmlog(LOG_ERRD, "manual dismount on '%s' failed %d",
	     drive->device, rstat);
      osmerrno = EVOLDISMOUNT;
      goto failed;
    }
  }

  
  osmlog(LOG_ERRD, "unmount: acsDismount failed for drive %s (%s)",
	 drive->device, drive->sgdev);
  osmerrno = (osmerrno == OSMOK) ? EVOLDISMOUNT : osmerrno;

failed:
  
  unmountfailed(drive, v_name);
  
  if (drive->retryunmount == 0)  /* retries exceeded */
    return(-1);
  
  while(666) {
    time_t now = time((time_t *) 0);
    sleep(drive->retryunmountat - now);
    now = time((time_t *) 0);
    if (now >= drive->retryunmountat)
      goto retry;
  }

done:
  unmountsucceeded(drive);

  return(0);
}

int ls_unmount_async(char *driveaddrstr)
{
  struct drive *drive;
  int dfd = -1;

  osmlog(LOG_DEBUG, "ls_unmount_async for drive '%s'", driveaddrstr);

  if ((drive = finddrivebyaddr(atoi(driveaddrstr))) == NULL)
    return(-1);

  if (!manualMount) {
    if (acsInit() != 0) {
      osmlog(LOG_ERRD, "can't init ACS client stuff");
      /* return(-1); */
    }
  }

  return(unmount(drive));
}

static int emptyAllDrives()
{
  char v_name[80];
  struct drive *drive;
  int dfd = -1, aciret;

  /* go through list of active drives */
  if (acsInit() != 0) {
    osmlog(LOG_ERR, "can't init ACSAPI client stuff");
    return(-1);
  }

  for (drive = drives; drive != NULL; drive = drive->next) {
    osmlog(LOG_DEBUG, "emptyAllDrives: unmount for %s (%s)",
	   drive->device, drive->sgdev);

#if 0
    if ((aciret = aciDriveMounted(drive, v_name)) == 0) {
      osmlog(LOG_DEBUG, "amptyAllDrives: Mr. GRAU said %s in empty",
	     drive->sgdev);
      continue;
    } else if (aciret < 0) {
      osmlog(LOG_ERRD, "emptyAllDrives: error getting drive '%s' status",
	     drive->sgdev);
      senddisabledrive(drive->name);
      return(-1);
    }
#endif

    if ((dfd = TOpenReady(drive->device, O_RDONLY, 60)) < 0 ||
	TUnload(dfd) != 0) {
      osmlog(LOG_ERRD, "emptyAllDrives: error opening/unloading drive '%s'",
	     drive->device);
    }
    if (dfd >= 0)
      (void) TClose(dfd);

    if (acsDismount(drive) != 0) {
      osmlog(LOG_ERR, "emptyAllDrives: dismount failed for drive %s (%s)",
	     drive->device, drive->sgdev);
    }
  }
  return(0);
}

int ls_unmount_all()
{
  struct drive *drive;

#if 0
  /* go through list of active drives */
  for (drive = drives; drive != NULL; drive = drive->next) {
    /* check if we had an outstanding mount */
    if (findosmdrivebydevice(drive->device) != NULL) {
      osmlog(LOG_DEBUG, "unmount_all for %s\n", drive->sgdev);
      if (ls_unmount(drive->name) == -1) {
	return(-1);
      }
    }
  }
#endif
#if 0
  (void) emptyAllDrives();
#endif
  return 0;
}

int ls_allocvol(char *volumename, char *who)
{
  struct element *el;

  if ((el = findvolumebyfullname(volumename)) == NULL) {
    return(-1);
  }

  if (strlen(who) > CIDLEN - 1) {
    osmerrno = ECIDTOOLONG;
    return(-1);
  }

  osmlog(LOG_DEBUG, "ls_allocvol: allocating <%s> to <%s>\n", volumename, who);

  if (strcmp(who, NOBODY) == 0)
    el->allocatedby[0] = '\0';
  else
    strcpy(el->allocatedby, who);
/* no label update forced here
 * for doing this we need a reliable information that there is no data on
 * that volume
 */

/* but we think ls_allocvol() is only called on free tapes */
  el->flags |= NEEDS_LABEL_UPDATE;

  if (db_store(el) == -1)
    return(-1);
  else
    return(0);
}

int ls_freevol(char *volumename)
{
  struct element *el;

  if ((el = findvolumebyfullname(volumename)) == NULL) {
    return -1;
  }

  osmlog(LOG_DEBUG, "ls_freevol: freeing <%s> from <%s>\n",
	 volumename, el->allocatedby);

  el->allocatedby[0] = '\0';

  el->flags |= NEEDS_LABEL_UPDATE;

  if (db_store(el) == -1)
    return(-1);
  else
    return(0);
}

int ls_ejecttomail()
{
  osmerrno = ENOTSUPPORTED;
  return -1;
}

char *ls_rmvol2(char *volname)
{
  static char msg[200];

  if (findvolumebyfullname(volname) != (struct element *) 0) {
    sprintf(msg, "Volume-%s-will-logically-removed", volname);
    return(msg);
  }
  osmerrno = EVOLNOTFOUND;
  return(NULL);
}

/*ARGSUSED*/
int ls_rmvol3(volumename)
char *volumename;
{
  struct element *el;
  Elements *ell = elems;

  if ((el = findvolumebyfullname(volumename)) != (struct element *) 0) {
    while(ell) {
      if (&ell->el == el) {  /* entry found */
	if (db_remove(el) != 0) {
	  osmlog(LOG_ERRD, "ls_rmvol3(): DB error on remove of '%s'\n",
		 el->nameA);
	  return(-1);
	}
	DEL(elems, elems_tail, ell);
	return(0);
      }
      ell = ell->next;
    }
  }
  osmlog(LOG_ERRD, "ls_rmvol3(): can't locate volume '%s'\n", volumename);
  osmerrno = EVOLNOTFOUND;
  return(-1);
}

int ls_fullinventory()
{
  return(0);
#if 0
  osmerrno = ENOTSUPPORTED;
  return -1;
#endif
}

#if 0
/* return volname in given drive */
int aciDriveMounted(struct drive *d, char *v_name)
{
  int retryCount = 4, i;
  struct aci_drive_entry *de[ACI_MAX_DRIVE_ENTRIES];

retry:
  if (aci_drivestatus("", de) != 0) {
    osmlog(LOG_ERRD, "aci_drivestatus return %d (%s)",
	   d_errno, aci_strerror(d_errno));
    if (--retryCount < 0)
      return(-1);
    sleep(20);
    goto retry;
  }
  for(i=0; i<ACI_MAX_DRIVE_ENTRIES; i++) {
    if (strcmp(d->sgdev, de[i]->drive_name) == 0) {
      if (de[i]->volser && strlen(de[i]->volser) > 0) {
	strcpy(v_name, de[i]->volser);
	return(1);
      } else {
	return(0);
      }
    }
  }
  return(-1);
}
#endif

int acsCheckVolume(char *volname, int *inDrive, int *volStatus)
{
  ACS_QUERY_VOL_RESPONSE *qp;
  QU_VOL_STATUS          *sp;
  ACS    		 acs;
  int    		 i, index;
  VOLID                  vol_id[MAX_ID];
  STATUS                 status;
  void                   *rbuf[2024];  /* opaque buffer for return data */
  char                   vname[30], *ctmp;
  ACKNOWLEDGE_RESPONSE   *ap;
  ACS_RESPONSE_TYPE      type;
  SEQ_NO                 s, seq_nmbr;
  REQ_ID                 req_id;
  int                    retCode = (-2), count;

/* init (with illegal values) if present */
  if (inDrive)
    *inDrive = (-1);
  if (volStatus)
    *volStatus = (-1);

#if 0
  s = sequenceNumber();
#endif
  s = (SEQ_NO) ACS_SEQ_VOLINFO;

#if 0
/* map external volume-name to real ACS volume-id */
  if ((ctmp = RealLabel(volname)) == NULL) {
    osmlog(LOG_WARNING, "acsCheckVolume(): can't map %s\n", volname);
    return(-1);
  }
/* copy volname into private storage; for safety */
  strcpy(vname, ctmp);
#else
  strcpy(vname, volname);
#endif
  /* clear vol_id array */
  bzero((char *) vol_id, sizeof(vol_id));
  strncpy(vol_id[0].external_label, vname, EXTERNAL_LABEL_SIZE);
  count = 1;
  if ((status = acs_query_volume(s, vol_id, count)) != STATUS_SUCCESS) {
    osmlog(LOG_WARNING,
	   "acsCheckVolume(): acs_query_volume() failed %d (%s)\n",
	   status, cl_status(status));
    return(-1);
  }
  /* Wait for the Acknowledge response */
  if ((status = acs_response(RESP_TIMEOUT, &seq_nmbr, &req_id, &type, rbuf))
      != STATUS_SUCCESS) {
    osmlog(LOG_WARNING, "acsCheckVolume(): Acknowlegde Response failed %d (%s)\n",
	   status, cl_status(status));
    retCode = (-1);
  }
/* and now check as check can !!!!! ::--)) */
  if (seq_nmbr != s) {
    osmlog(LOG_WARNING, "acsCheckVolume(): sequence mismatch %d %d\n",
	   s, seq_nmbr);
    retCode = (-1);
  }
  if (type != RT_ACKNOWLEDGE) {
    osmlog(LOG_WARNING, "acsCheckVolume(): Acknowledge type mismatch %d\n",
	   type);
    retCode = (-1);
  }

  ap = (ACKNOWLEDGE_RESPONSE *)rbuf;

  if (ap->request_header.message_header.message_options != 
      (EXTENDED | ACKNOWLEDGE)) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE message_options failure %d\n", ap->request_header.message_header.message_options);
    retCode = (-1);
  }
  if (ap->request_header.message_header.version != acsapi_packet_version) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE version failure %d\n", ap->request_header.message_header.version);
    retCode = (-1);
  }
  if (ap->request_header.message_header.extended_options != 0) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE extended_options failure %d\n", ap->request_header.message_header.extended_options);
    retCode = (-1);
  }
  if (ap->request_header.message_header.lock_id != NO_LOCK_ID) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE lock_id failure %d\n", ap->request_header.message_header.lock_id);
    retCode = (-1);
  }
  if (ap->message_status.status != STATUS_VALID) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE status failure %d\n", ap->message_status.status);
    retCode = (-1);
  }
  if (ap->message_status.type != TYPE_NONE) {
    osmlog(LOG_WARNING, "acsCheckVolume(): ACKNOWLEDGE RESPONSE type failure %d\n", ap->message_status.type);
    retCode = (-1);
  }

  /* Wait for FINAL response */
  status = acs_response(RESP_TIMEOUT, &seq_nmbr, &req_id, &type, rbuf);
  
  if (status != STATUS_SUCCESS) {
    osmlog(LOG_WARNING, "acsCheckVolume(): (FINAL RESPONSE) failed %d (%s)\n",
	   status, cl_status(status));
    retCode = (-1);
  }
  if (seq_nmbr != s) {
    osmlog(LOG_WARNING,
	   "acsCheckVolume(): FINAL RESPONSE sequence mismatch got:%d expected:%d\n",
	   seq_nmbr, s);
    retCode = (-1);
  }
  if (type != RT_FINAL) {
    osmlog(LOG_WARNING, "acsCheckVolume(): FINAL RESPONSE type failure %d\n",
	   type);
    retCode = (-1);
  }
  
  qp = (ACS_QUERY_VOL_RESPONSE *) rbuf;
  
  if (qp->query_vol_status != STATUS_SUCCESS) {
    osmlog(LOG_WARNING, "acsCheckVoluume(): FINAL RESPONSE query_vol_status failure %d\n", qp->query_vol_status);
    retCode = (-1);
  }
  if (qp->count != count) {
    osmlog(LOG_WARNING, "acsCheckVolume(): FINAL RESPONSE count failure %d\n",
	   qp->count);
    retCode = (-1);
  }

  sp = &qp->vol_status[0];

  if (strncmp(sp->vol_id.external_label, vol_id[0].external_label,
	      EXTERNAL_LABEL_SIZE) != 0) {
    osmlog(LOG_WARNING, "acsCheckVolume(): FINAL RESPONSE vol_id failure: %s %s\n", sp->vol_id.external_label, vname);
    retCode = (-1);
  }

  /* check location */
  if (sp->location_type != LOCATION_DRIVE &&
      sp->location_type != LOCATION_CELL) {
    osmlog(LOG_WARNING, "acsCheckVolume(): got illegal location %d\n",
	   sp->location_type);
    retCode = (-1);
  }
  if (sp->status != STATUS_VOLUME_HOME) {
    osmlog(LOG_WARNING,
	   "acsCheckVolume(): Warning Volume %s not home %d (%s)\n",
	   sp->vol_id.external_label, sp->status, cl_status(sp->status));
    /* check further locations */
    if ((sp->status != STATUS_VOLUME_IN_TRANSIT) &&
	(sp->status != STATUS_VOLUME_IN_DRIVE)) { /* hard stuff */
      retCode = (-1);
    }
  }

  /* now we are pretty sure that the volume is present and accessible */
  if (retCode != (-1)) {  /* no previous error found */
    if (inDrive)
      *inDrive = (sp->location_type == LOCATION_DRIVE);
    if (volStatus)
      *volStatus = sp->status;
    return(0);
  } else {
    return(-1);
  }
}

int acsDismount(struct drive *d)
{
  LOCKID                lock_id = NO_LOCK_ID;
  ACS_DISMOUNT_RESPONSE *dp;
  VOLID                 vol_id;
  DRIVEID               drive_id;
  SEQ_NO                seq_nmbr, sequence;
  ACS_RESPONSE_TYPE     type;
  REQ_ID                req_id;
  STATUS                status;
  void                  *rbuf[2024];
  ACKNOWLEDGE_RESPONSE  *ap;
  char                  *ctmp, *vname;
  int                   oerr = OSMOK, wRet, retryCounter = 3;
  time_t                startTime, endTime;

retry:

  osmerrno = OSMOK;
/* convert drive (ASCII format) to STK native */
  if (strtostkdrive(d->sgdev, &drive_id) < 0)
    return(-1);

  sequence = (SEQ_NO) ACS_SEQ_DISMOUNT + (SEQ_NO) d->addr; /* sequenceNum */
  /* unmount without knowing the volume to unmount */
  strncpy(vol_id.external_label, "      ", EXTERNAL_LABEL_SIZE);

  vname = NULL;
  startTime = time((time_t) 0);

  /* Issue a FORCED dismount always */
  status = acs_dismount(sequence, lock_id, vol_id, drive_id, TRUE);
  if (status != STATUS_SUCCESS) {
    osmlog(LOG_CRIT, "acs_dismount() error %d (%s)\n",
	   status, cl_status(status));
    goto failure;
  }

  if ((wRet = acsWait(sequence,  rbuf)) != 0) {
    if (wRet == -2) {
      osmlog(LOG_ERRD, "acsDismount: error getting FINAL resp");
      if (retryCounter) {
	retryCounter--;
	osmlog(LOG_ERRD, "acsDismount: retry acs_mount()");
	goto retry;
      } else {
	osmlog(LOG_ERRD, "acsDismount: retry exceeded; giving up");
      }
    }
    osmlog(LOG_CRIT, "acsDismount: error getting FINAL response; exiting");
    osmerrno = EVOLMOUNT;
    goto failure;
  }

  if ((dp = (ACS_DISMOUNT_RESPONSE *)rbuf)->dismount_status != STATUS_SUCCESS)
    osmlog(LOG_ERRD, "acsDismount: drive: '%s' got: %s",
	   d->device, cl_status(dp->dismount_status));

  switch(dp->dismount_status) {

  case STATUS_SUCCESS:
    break;

  case STATUS_DRIVE_AVAILABLE:  /* it's already free FIXME check were it is */
    goto okay;

  default:
    osmlog(LOG_CRIT, "acsDismount: dismount failure %d (%s)",
	   dp->dismount_status, cl_status(dp->dismount_status));
    goto failure;
  }

  if (!sameDrive(&(dp->drive_id), &drive_id)) {
    osmlog(LOG_ERRD, "acsDismount: wrong drive '%s' should be '%s'",
	   drive2str(&(dp->drive_id)), drive2str(&drive_id));
    goto failure;
  }

  osmlog(LOG_DEBUG, "unmount volume %s", dp->vol_id.external_label);
  vname = dp->vol_id.external_label;

  okay:

  vname = (vname) ? vname : "NONE";
  osmerrno = OSMOK;

  endTime = time((time_t) 0);
  MountStatEntry("%x %s DISMOUNT %s %s %d %s SUCCESS\n",
		 (u_int) startTime, time2str(&startTime), vname, 
		 drive2str(&drive_id) , endTime - startTime, lsname);

  return(0); /* the best way -->> good results */

  failure:

  endTime = time((time_t) 0);
  MountStatEntry("%x %s DISMOUNT NONE %s %d %s FAILED\n",
		 (u_int) startTime, time2str(&startTime),
		 drive2str(&drive_id), endTime - startTime, lsname);

  return(-1);
}

/* wait for a specific return from 'ssi'
 * we know that one proc issue only requests to ssi, so any older
 * response could be ignored (more or less - stateless)
 * FIXME had to cancel request to clean up queue if something failed
 */

/* changed to always wait for final response */
int acsWait(int seqIn, void *rbuf)
{
  SEQ_NO            rSeq;
  ACS_RESPONSE_TYPE rType;
  REQ_ID            rID;
  STATUS            s;
  int               timeoutCounter = 0, oerr = OSMOK;
  int               retryCount = 0;

  /* wait until receive the right one */
  while(666) {
    oerr = OSMOK;
    s = acs_response(ACS_TIMEOUT, &rSeq, &rID, &rType, rbuf);
    switch (s) {
    case STATUS_SUCCESS:
      if (rSeq != seqIn) {
	osmlog(LOG_CRIT, "acsWait: wrong sequence # got %d expect %d",
	       rSeq, seqIn);
	oerr = OSMSYSERR;
      }
      if (rType == RT_FINAL) { /* reached the end */
	return((oerr == OSMOK) ? 0 : -1);
      }
      if (++retryCount > 8) {
	osmlog(LOG_CRIT, "acsWait: retry-counter exceed; giving up...");
	return(-1);
      }
      break;
    case STATUS_IPC_FAILURE:  /* expect to be retrieable !!!! */
      osmlog(LOG_CRIT, "acsWait: got IPC error");
      return(-1);
      break;
    case STATUS_NI_TIMEDOUT:
      osmlog(LOG_ERRD, "acsWait: TIMEOUT waiting for ACK");

      if (++timeoutCounter >= MAX_RESPONSE_TIMEOUT) { /* reach limit */
	osmlog(LOG_CRIT, "acsWait: got %d timeouts; giving up",
	       timeoutCounter);
	return(-2);
      }
      break;
    default:
      osmlog(LOG_CRIT, "acsWait: unknown status %d (%s)", s, cl_status(s));
      return(-1);
    }
  }
  osmlog(LOG_DEBUG, "exit acsWait() SHOULD NOT BE HERE !!!");
  return(-1);
}

int acsMount(struct drive *d, struct element *el)
{  
  LOCKID               lock_id = NO_LOCK_ID;
  VOLID                vol_id;
  DRIVEID              drive_id;
  ACS_MOUNT_RESPONSE   *mp;
  SEQ_NO               seq_nmbr, sequence;
  REQ_ID               req_id;
  ACS_RESPONSE_TYPE    type;
  STATUS               status;
  void                 *rbuf[2024];
  ACKNOWLEDGE_RESPONSE *ap;
  char                 *ctmp, vname[100];
  int                  wRet, retryCounter = 3;
  time_t               startTime, endTime;

retry:

  osmerrno = OSMOK;
/* convert drive (ASCII format) to STK native */
  if (strtostkdrive(d->sgdev, &drive_id) < 0)
    return(-1);

  sequence = (SEQ_NO) ACS_SEQ_MOUNT + (SEQ_NO) d->addr;

  strcpy(vname, el->nameA);
  strncpy(vol_id.external_label, vname, EXTERNAL_LABEL_SIZE);
  vol_id.external_label[6] = '\0';

  startTime = time((time_t) 0);

  status = acs_mount(sequence, lock_id, vol_id, drive_id, FALSE);
  if (status != STATUS_SUCCESS) {
    osmlog(LOG_CRIT, "acs_mount() failed %d (%s)", status, cl_status(status));
    osmerrno = EVOLMOUNT;
    goto failure;
  }

  if ((wRet = acsWait(sequence,  rbuf)) != 0) {
    if (wRet == -2) {
      osmlog(LOG_ERRD, "acsMount: error getting FINAL resp");
      if (retryCounter) {
	retryCounter--;
	osmlog(LOG_ERRD, "acsMount: retry acs_mount()");
	goto retry;
      } else {
	osmlog(LOG_ERRD, "acsMount: retry exceeded; giving up");
      }
    }
    osmlog(LOG_CRIT, "acsMount: error getting FINAL response; exiting");
    osmerrno = EVOLMOUNT;
    goto failure;
  }

  if ((mp = (ACS_MOUNT_RESPONSE *)rbuf)->mount_status != STATUS_SUCCESS)
    osmlog(LOG_ERRD, "acsMount: vol '%s' drive '%s' got %s",
	   vname, d->device, cl_status(mp->mount_status));

  switch(mp->mount_status) {

  case STATUS_SUCCESS:
    break;

  case STATUS_DRIVE_IN_USE:

    /* volume in cell and drive filled with other volume */

    osmlog(LOG_CRIT, "acsMount: drive %s (%s) IN-USE", d->device, d->sgdev);
    /* force dismount here, and assume a previous dismount failed */
    if (unmount(d) != 0) {
      osmlog(LOG_ERRD, "dismount of drive in-use failed");
      osmerrno = EDRIVEBUSY;
      goto failure;
    } else {
      goto retry;
    }
    osmerrno = EDRIVEBUSY;
    goto failure;

  case STATUS_DRIVE_OFFLINE:
    osmlog(LOG_CRIT, "acsMount: drive %s (%s) offline (disable)",
	   d->device, d->sgdev);
    osmerrno = EDRIVEBUSY;
    senddisabledrive(d->name);
    goto failure;

  case STATUS_INVALID_VOLUME:
    osmlog(LOG_CRIT, "acsMount: invalid volume '%s'", vol_id.external_label);
    osmerrno = ENOSUCHVOLUME;
    goto failure;

  case STATUS_VOLUME_NOT_IN_LIBRARY:
    osmlog(LOG_CRIT, "acsMount: volume not online %s", vname);
    osmerrno = ENOSUCHVOLUME;
    goto failure;

  case STATUS_VOLUME_IN_DRIVE:
  case STATUS_VOLUME_IN_USE:

    /*
     * volume NOT in cell drive might be filled or not - response include
     * information where the volume is located (what drive)
     */

    if (sameDrive(&(mp->drive_id), &drive_id)) {
      osmlog(LOG_ERRD, "acsMount: volume '%s' already mounted on '%s'",
	     vname, d->device);
      break;
    }
    osmlog(LOG_CRIT, "acsMount: volume '%s' in drive '%s' should be '%s'",
	   vname, drive2str(&(mp->drive_id)), drive2str(&drive_id));
    osmerrno = EDRIVEBUSY;
    goto failure;

  default:
    osmlog(LOG_CRIT, "acsMount: (FINAL RESPONSE) failed %d (%s)",
	   mp->mount_status, cl_status(mp->mount_status));
    goto failure;
  }

  if (strncmp(mp->vol_id.external_label, vol_id.external_label,
	      EXTERNAL_LABEL_SIZE) != 0) {
    osmlog(LOG_CRIT, "(M) FINAL RESPONSE vol_id failure. got:%s  exp:%s",
	   mp->vol_id.external_label, vol_id.external_label);
    goto failure;
  }

  if (!sameDrive(&(mp->drive_id), &drive_id)) {
    osmlog(LOG_CRIT, "acsMount: got wrong drive '%s' should be '%s'",
	   drive2str(&(mp->drive_id)), drive2str(&drive_id));
    goto failure;
  }

  osmerrno = OSMOK;

  endTime = time((time_t) 0);
  MountStatEntry("%x %s MOUNT %s %s %d %s SUCCESS\n",
		 (u_int) startTime, time2str(&startTime), vname,
		 drive2str(&drive_id), endTime - startTime, lsname);

  return(0);  /* long way, but we got it ..... */

  failure:

  endTime = time((time_t) 0);
  MountStatEntry("%x %s MOUNT %s %s %d %s FAILED\n",
		 (u_int) startTime, time2str(&startTime), vname,
		 drive2str(&drive_id), endTime - startTime, lsname);

  return(-1);
}

/* we simply return a dummy string so we reach 'ls_addvol2()' */
char *ls_addvol1(char *volumename)
{
  static char message[200];

  (void) sprintf(message, "Volume-%s-has-to-be-imported", volumename);
  return(message);
}

/* here we check (ask ACSLS if volume is present in the ACS) */
struct volume *ls_addvol2(char *drivename, char *volumename, char *mediatype,
			  long capacity, int optflags, char *location)
{
  Elements     *el;
  struct mtent *MTP;

  /* here is the place to ask the REAL pvr about the volume status we should
   * import here */


  if (666) {   /* volume check disabled */    
    /* add volume into private linked list and update DB info */
    if ((el = (Elements *) calloc(1, sizeof(Elements))) == NULL) {
      osmerrno = ENOMEMORY;
      return((struct volume *) 0);
    }

    if (strlen(volumename) > VOLNAMELEN - 1) {
      osmerrno = EVOLNAMETOOLONG;
      return NULL;
    }
    if (strlen(mediatype) >= MEDIATYPELEN) {
      osmerrno = EMEDIATYPETOOLONG;
      return NULL;
    }
    if ((MTP = getmtbyname(mediatype)) == NULL) {
      osmerrno = EINVALMEDIATYPE;
      return NULL;
    }
    if (MTP->usesfs) {
      osmerrno = EINVALMEDIATYPE;
      return NULL;
    }
    if (capacity <= 0) {
      capacity = MTP->size;
    }
    if ((optflags & PART) || (optflags & MAKEFS)) {
      osmerrno = EINVALOPTIONS;
      return NULL;
    }

    el->el.addr = getNextFreeAddr();  /* assign a unique number to it here */
    el->el.type = 0;  /* no type is better than nothing ::--)) */
    strcpy(el->el.nameA, volumename);
    el->el.nameB[0] = '\0';
    el->el.flags |= NEEDS_LABEL_UPDATE;
    strcpy(el->el.mediatype, MTP->family);
    el->el.capacity = capacity;
    el->el.allocatedby[0] = '\0';

    if (db_store(&el->el) == 0) { /* DB ops seems okay */
      ADD(elems, elems_tail, el);
      return(mkvolumebyelem(&el->el));
    } else {  /* DB ops error */
      return((struct volume *) 0);
    }
  } else {
    osmerrno = EVOLNOTFOUND;
    return((struct volume *) 0);
  }
}

int ls_addvol3()
{
  return(0);  /* nothing to do for us here */
}

/* signal handler (indeed very smart isn't it ??) */
void lspowderhorn_exit(int value)
{
  ls_exit((InMount == 1) ? EVOLMOUNT : EVOLDISMOUNT);
}

/* EOF */
