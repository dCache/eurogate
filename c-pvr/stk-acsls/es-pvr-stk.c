/*
 * EuroStore PVR   -mg DESY
 * library dependent code for STK ACSLS controlled libraries
 *
 */

#ifndef lint
static char rcsid[] = "$Id: es-pvr-stk.c,v 1.2 1999-10-25 08:15:45 cvs Exp $";
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

#include <sys/ioctl.h>
#include <sys/mtio.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/param.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/time.h>

#include <es-pvr.h>

#include <acssys.h>
#include <acsapi.h>

/* time for retry is MAXTRIES x SLEEPSECS */
#define	MAXTRIES		20
#define	SLEEPSECS		30

/* timeouts for ACSLS calls */
#define RESP_TIMEOUT 30   /* timeout for acs_response() in seconds */
#define ACS_TIMEOUT 180   /* timeout used for acsWait() */
#define MAX_RESPONSE_TIMEOUT 10  /* max timeout counter for acsWait() */


/* global info */
static int InMount;
static char ACS_PACKET_VER[] = "ACSAPI_PACKET_VERSION=4";

#if 0  /* no longer in CSC 2.1 */
extern int acsapi_init;  /* comes from ACSAPI toolkit */
#endif

/* pers. custom. :--)) */
#ifndef False
#define False FALSE
#define True  TRUE;
#endif

#define ACS_DRIVE_FORMAT "%d:%d:%d:%d"  /* string format of ACS locations */
#define STK_LABEL_SIZE 6

/* define offsets for sequence numbers used for ACSAPI access */
#define ACS_SEQ_MOUNT 304
#define ACS_SEQ_DISMOUNT 405
#define ACS_SEQ_QUERYDRIVE 509
#define ACS_SEQ_CANCEL 609

/* to catch internal ACSAPI error messages */
int acs_error(char *msg) {
  LOG(ESPVR_WARNING, "ACSAPI(internal errormessage): %s", msg);
  return(0);
}

/* compare two STK drive locations */
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
static SEQ_NO sequenceNumber() {
  static SEQ_NO seq = 0;  /* SEQ_NO <--> unsigned short */

  return(seq++);
}

static char *time2str(time_t *tim) {
  struct tm *t;
  static char cTime[100];

  t = localtime(tim);
  (void) sprintf(cTime, "%02d/%02d-%02d:%02d:%02d",
		 t->tm_mday, t->tm_mon + 1,
		 t->tm_hour, t->tm_min, t->tm_sec);
  return(cTime);
}

static int strtostkdrive(char *s, DRIVEID *d) {
  int acs, lsm, panel, transp;

  if (sscanf(s, ACS_DRIVE_FORMAT, &acs, &lsm, &panel, &transp) != 4) {
    LOG(ESPVR_FATAL, "can't convert stk drive %s into ACS format", s);
    return(-1);
  }
  /* do some sanity checking here directly */
  if (acs < 0 || lsm < 0 || panel < 0 || transp < 0 ||
      acs > 127 || lsm > 127 || panel > 127 || transp > 127) {
    return(-1);
  }
  d->panel_id.lsm_id.acs = (char) acs & 0xff;
  d->panel_id.lsm_id.lsm = (char) lsm & 0xff;
  d->panel_id.panel      = (char) panel & 0xff;
  d->drive               = (char) transp & 0xff;
  return(0);
}

static void myusleep(long usecs) {
  struct timeval  tv;

  tv.tv_sec = usecs / 1000000;
  tv.tv_usec = usecs % 1000000;

  (void)select(0, 0, 0, 0, &tv);
}

/* force re-open of socket connection to 'ssi' for new child proc */
static int acsInit() {
#if 0   /* acs_init() not found in CSC 2.1 !!! */
  STATUS stat = acs_init();

  if (stat != STATUS_SUCCESS) {
    LOG(ESPVR_FATAL, "init. of ACSAPI client failed (%s)\n",
	    cl_status(stat));
    return(-1);
  }
#endif
  return(0);
}

/* flush and close socket 'ssi' connection */
static void acsClose() {
#if 0  /* no longer in CSC 2.1 */
  STATUS s = cl_ipc_destroy();
  
  if (s != STATUS_SUCCESS) {
    LOG(ESPVR_WARNING, "destroy of ACSAPI client failed (%s)\n", cl_status(s));
  }
  acsapi_init = 0;  /* dirty hack; but it works ..... (force init() work) */
#endif
}

/* wait for a specific return from 'ssi'
 * we know that one proc issue only requests to ssi, so any older
 * response could be ignored (more or less - stateless)
 * FIXME had to cancel request to clean up queue if something failed
 */

/* changed to always wait for final response */
static int acsWait(int seqIn, void *rbuf) {
  SEQ_NO            rSeq;
  ACS_RESPONSE_TYPE rType;
  REQ_ID            rID;
  STATUS            s;
  int               timeoutCounter = 0;
  int               retryCount = 0;

  /* wait until receive the right one */
  while(666) {
    s = acs_response(ACS_TIMEOUT, &rSeq, &rID, &rType, rbuf);
    switch (s) {
    case STATUS_SUCCESS:
      if (rSeq != seqIn) {
	LOG(ESPVR_WARNING, "acsWait: wrong sequence # got %d expect %d",
	    rSeq, seqIn);
      }
      if (rType == RT_FINAL) { /* reached the end */
	return(0);
      }
      if (++retryCount > 8) {
	LOG(ESPVR_FATAL, "acsWait: retry-counter exceed; giving up...");
	return(-1);
      }
      break;
    case STATUS_IPC_FAILURE:  /* expect to be retrieable !!!! */
      LOG(ESPVR_FATAL, "acsWait: got IPC error");
      return(-1);
      break;
    case STATUS_NI_TIMEDOUT:
      LOG(ESPVR_WARNING, "acsWait: TIMEOUT waiting for ACK");
      
      if (++timeoutCounter >= MAX_RESPONSE_TIMEOUT) { /* reach limit */
	LOG(ESPVR_FATAL, "acsWait: got %d timeouts; giving up",
	    timeoutCounter);
	return(-2);
      }
      break;
    default:
      LOG(ESPVR_WARNING, "acsWait: unknown status %d (%s)", s, cl_status(s));
      return(-1);
    }
  }
}

static int checkDrive(char *driveName, char *genDriveName,
		      int *dState, char *volName) {
  DRIVEID drive_id;
  ACS_QUERY_DRV_RESPONSE *qp;
  QU_DRV_STATUS          *sp;
  SEQ_NO                 sequence;
  void                  *rbuf[2024];
  ACKNOWLEDGE_RESPONSE  *ap;
  char                   tbuf[EXTERNAL_LABEL_SIZE];
  int count;
  int                   wRet, retryCounter = 3;
  int                   status;
  
  *dState = DRV_UNAVAILABLE;   /* not available */
  volName[0] = '\0';
  
 retry:

/* convert drive (ASCII format) to STK native */
  if (strtostkdrive(driveName, &drive_id) < 0) {
    LOG(ESPVR_FATAL, "invalid drive name '%s'", driveName);
    return(1);
  }
  sequence = (SEQ_NO) ACS_SEQ_QUERYDRIVE + (SEQ_NO) drive_id.drive;

  status = acs_query_drive(sequence, &drive_id, 1);


  if (status != STATUS_SUCCESS) {
    LOG(ESPVR_FATAL, "acs_query_drive() error %d (%s)\n",
	   status, cl_status(status));
    return(-1);
  }

  if ((wRet = acsWait(sequence,  rbuf)) != 0) {
    if (wRet == -2) {
      LOG(ESPVR_WARNING, "checkDrive: error getting FINAL resp");
      if (retryCounter) {
	retryCounter--;
	LOG(ESPVR_WARNING, "checkDrive: retry checkDrive()");
	goto retry;
      } else {
	LOG(ESPVR_FATAL, "checkDrive: retry exceeded; giving up");
      }
    }
    LOG(ESPVR_FATAL, "checkDrive: error getting FINAL response; exiting");
    return(-1);
  }


  if ((qp = (ACS_QUERY_DRV_RESPONSE *)rbuf)->query_drv_status
      != STATUS_SUCCESS) {
    LOG(ESPVR_INFO, "checkDrive: drive: '%s' got: %s",
	driveName, cl_status(qp->query_drv_status));
    return(-1);
  }

  if (qp->count != 1) {
    LOG(ESPVR_WARNING, "checkDrive: drive count != 1 (%d)", qp->count);
    return(-1);
  }

  sp = &qp->drv_status[0];


  if (sp->state != STATE_ONLINE) {
    LOG(ESPVR_WARNING, "checkDrive: drive not ONLINE (%s)",
	cl_state(sp->state));
    *dState = DRV_UNAVAILABLE;
    return(0);
  }

  LOG(ESPVR_INFO, "checkDrive: drive status - %s", cl_status(sp->status));

  switch(sp->status) {
  case STATUS_DRIVE_AVAILABLE:  /* drive empty */
    LOG(ESPVR_INFO, "drive (%s - %s) empty", genDriveName, driveName);
    *dState = DRV_AVAILABLE_EMPTY;
    return(0);
  case STATUS_DRIVE_IN_USE:  /* filled drive */
    LOG(ESPVR_INFO, "drive (%s - %s) in use (%s)", genDriveName, driveName,
	sp->vol_id.external_label);
    if (sp->vol_id.external_label[0]) {
      *dState = DRV_AVAILABLE_FILLED_KNOWN;
      strcpy(volName, sp->vol_id.external_label);
    } else {
      *dState = DRV_AVAILABLE_FILLED_UNKNOWN;
    }
    return(0);
  default:
    LOG(ESPVR_WARNING, "drive (%s - %s) in BAD state (%s)",
	genDriveName, driveName, cl_status(sp->status));
    *dState = DRV_UNAVAILABLE;
    return(0);
  }
}

int LIBDismount(char *volName, char *driveName, char *genDriveName) {
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
  char                  *ctmp, vname[80];
  int                   wRet, retryCounter = 3;

#if 0
  LOG(ESPVR_INFO, "LIBDismount '%s' '%s' '%s'",
      volName, driveName, genDriveName);
  sleep(10);
  return(0);
#endif

retry:

/* convert drive (ASCII format) to STK native */
  if (strtostkdrive(driveName, &drive_id) < 0) {
    LOG(ESPVR_FATAL, "invalid drive name '%s'", driveName);
    return(1);
  }

  sequence = (SEQ_NO) ACS_SEQ_DISMOUNT + (SEQ_NO) drive_id.drive;
  /* unmount without knowing the volume to unmount */
  if (strcmp(volName, "-") == 0) {  /* volumename unknown */
    strcpy(vname, "UNKNOWN");
    strcpy(volName, "UNKNOWN");
    strncpy(vol_id.external_label, "        ", EXTERNAL_LABEL_SIZE);
  } else {
    strcpy(vname, volName);
    strncpy(vol_id.external_label, volName, EXTERNAL_LABEL_SIZE);
  }

  /* Issue a FORCED dismount always */
  status = acs_dismount(sequence, lock_id, vol_id, drive_id, TRUE);
  if (status != STATUS_SUCCESS) {
    LOG(ESPVR_FATAL, "acs_dismount() error %d (%s)\n",
	   status, cl_status(status));
    goto failure;
  }

  if ((wRet = acsWait(sequence,  rbuf)) != 0) {
    if (wRet == -2) {
      LOG(ESPVR_WARNING, "acsDismount: error getting FINAL resp");
      if (retryCounter) {
	retryCounter--;
	LOG(ESPVR_WARNING, "acsDismount: retry acs_dismount()");
	goto retry;
      } else {
	LOG(ESPVR_FATAL, "acsDismount: retry exceeded; giving up");
      }
    }
    LOG(ESPVR_FATAL, "acsDismount: error getting FINAL response; exiting");
    goto failure;
  }

  if ((dp = (ACS_DISMOUNT_RESPONSE *)rbuf)->dismount_status != STATUS_SUCCESS)
    LOG(ESPVR_INFO, "acsDismount: drive: '%s' got: %s",
	   driveName, cl_status(dp->dismount_status));

  switch(dp->dismount_status) {

  case STATUS_SUCCESS:
    strcpy(volName, dp->vol_id.external_label);
    break;

  case STATUS_DRIVE_AVAILABLE:  /* it's already free FIXME check were it is */
    strcpy(volName, "EMPTY");
    break;

  case STATUS_LIBRARY_FAILURE:
    if (retryCounter) {
      retryCounter--;
      LOG(ESPVR_WARNING, "acsDismount failed (LIBRARY_FAILURE) - retry");
      goto retry;
    } else {
      LOG(ESPVR_FATAL, "acsDismount: retry exceeded; giving up");
      goto failure;
    }
    break;

  default:
    LOG(ESPVR_FATAL, "acsDismount: dismount failure %d (%s)",
	dp->dismount_status, cl_status(dp->dismount_status));
    goto failure;
  }

  if (!sameDrive(&(dp->drive_id), &drive_id)) {
    LOG(ESPVR_FATAL, "acsDismount: wrong drive '%s' should be '%s'",
	drive2str(&(dp->drive_id)), drive2str(&drive_id));
    goto failure;
  }

  LOG(ESPVR_INFO, "unmount volume %s", dp->vol_id.external_label);

  return(0); /* the best way -->> good results */

  failure:

  return(1);
}

int LIBMount(char *volName, char *driveName, char *genDriveName) {  
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
  char                 *ctmp;
  int                  wRet, retryCounter = 3;


#if 0
  LOG(ESPVR_INFO, "LIBMount '%s' '%s' '%s'", volName, driveName, genDriveName);
  sleep(10);
  return(0);
#endif

retry:

/* convert drive (ASCII format) to STK native */
  if (strtostkdrive(driveName, &drive_id) < 0) {
    LOG(ESPVR_FATAL, "invalid drive name '%s'", driveName);
    return(1);
  }

  sequence = (SEQ_NO) ACS_SEQ_MOUNT + (SEQ_NO) drive_id.drive;

  strncpy(vol_id.external_label, volName, EXTERNAL_LABEL_SIZE);

  status = acs_mount(sequence, lock_id, vol_id, drive_id, FALSE, 1);
  if (status != STATUS_SUCCESS) {
    LOG(ESPVR_FATAL, "acs_mount() failed %d (%s)", status, cl_status(status));
    goto failure;
  }

  if ((wRet = acsWait(sequence,  rbuf)) != 0) {
    if (wRet == -2) {
      LOG(ESPVR_WARNING, "acsMount: error getting FINAL resp");
      if (retryCounter) {
	retryCounter--;
	LOG(ESPVR_INFO, "acsMount: retry acs_mount()");
	goto retry;
      } else {
	LOG(ESPVR_FATAL, "acsMount: retry exceeded; giving up");
      }
    }
    LOG(ESPVR_FATAL, "acsMount: error getting FINAL response; exiting");
    goto failure;
  }

  if ((mp = (ACS_MOUNT_RESPONSE *)rbuf)->mount_status != STATUS_SUCCESS)
    LOG(ESPVR_INFO, "acsMount: vol '%s' drive '%s' got %s",
      volName, genDriveName, cl_status(mp->mount_status));

  switch(mp->mount_status) {

  case STATUS_SUCCESS:
    break;

  case STATUS_DRIVE_IN_USE:

    /* volume in cell and drive filled with other volume */

    LOG(ESPVR_FATAL, "acsMount: drive %s (%s) IN-USE",
	genDriveName, driveName);
    /* force dismount here, and assume a previous dismount failed */
    goto failure;

  case STATUS_DRIVE_OFFLINE:
    LOG(ESPVR_FATAL, "acsMount: drive %s (%s) offline (disable)",
	genDriveName, driveName);
    goto failure;

  case STATUS_INVALID_VOLUME:
    LOG(ESPVR_FATAL, "acsMount: invalid volume '%s'", vol_id.external_label);
    goto failure;

  case STATUS_VOLUME_NOT_IN_LIBRARY:
    LOG(ESPVR_FATAL, "acsMount: volume not online %s", volName);
    goto failure;

  case STATUS_VOLUME_IN_DRIVE:
  case STATUS_VOLUME_IN_USE:

    /*
     * volume NOT in cell drive might be filled or not - response include
     * information where the volume is located (what drive)
     */

    if (sameDrive(&(mp->drive_id), &drive_id)) {
      LOG(ESPVR_FATAL, "acsMount: volume '%s' already mounted on '%s'",
	  volName, genDriveName);
      break;
    }
    LOG(ESPVR_FATAL, "acsMount: volume '%s' in drive '%s' should be '%s'",
	volName, drive2str(&(mp->drive_id)), drive2str(&drive_id));
    goto failure;

  default:
    LOG(ESPVR_FATAL, "acsMount: (FINAL RESPONSE) failed %d (%s)",
	mp->mount_status, cl_status(mp->mount_status));
    goto failure;
  }

  if (strncmp(mp->vol_id.external_label, vol_id.external_label,
	      EXTERNAL_LABEL_SIZE) != 0) {
    LOG(ESPVR_FATAL, "FINAL RESPONSE vol_id failure. got:%s  exp:%s",
	mp->vol_id.external_label, vol_id.external_label);
    goto failure;
  }

  if (!sameDrive(&(mp->drive_id), &drive_id)) {
    LOG(ESPVR_FATAL, "acsMount: got wrong drive '%s' should be '%s'",
	drive2str(&(mp->drive_id)), drive2str(&drive_id));
    goto failure;
  }

  return(0);  /* long way, but we got it ..... */

  failure:

  return(1);
}

int LIBDriveStatus(char *driveName, char *genDriveName, char *volName) {
  int dState;
  int ret = checkDrive(driveName, genDriveName, &dState, volName);

  if (ret != 0)
    dState = DRV_UNAVAILABLE;
  return(dState);
}

int LIBStatus() {
  LOG(ESPVR_INFO, "status called");
  return(0);
}

int LIBInit(char *options, char *hwInfo) {
  LOG(ESPVR_INFO, "init called");
  strcpy(hwInfo, "STK-ACSLS");
  (void) putenv(ACS_PACKET_VER);
  return(0);
}

/* EOF */
