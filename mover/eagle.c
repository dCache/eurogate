/*
 * Drive interface for STK Eagle on SGI/IRIX6.5 and Solaris 2.6  -mg  DESY
 *
 */

#ifndef lint
static char rcsid[] = "$Id: eagle.c,v 1.1 1999-10-05 16:12:05 cvs Exp $";
#endif

/*
 * SWITCHES:
 *  OSM or ES  for OSM ss code or EuroStore Mover code
 *  IRIX or SOLARIS26  to switch between IRIX and SOLARIS 2.6 versions
 */


#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/param.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>

#ifdef IRIX
#include <sys/scsi.h>
#include <dslib.h>
#endif

#ifdef SOLARIS26
#include <sys/scsi/generic/mode.h>
#include <sys/scsi/generic/commands.h>
#include <sys/scsi/impl/types.h>
#include <sys/scsi/impl/uscsi.h>
#include <sys/scsi/generic/status.h>
#include <sys/scsi/generic/sense.h>
#include <limits.h>
#endif

/* OSM includes */
#ifdef OSM
#include <osmlog.h>
#include <osmerrno.h>
#include <osmlimits.h>
#endif

#include <eagle.h>

#define MAXNFD 255
#define True 1
#define False 0

#define hex(x) "0123456789ABCDEF" [ (x) & 0xF ]


/* structure for strings descr. the secondary sense bytes for 4791 */
static struct {
  int senseKey;
  int secSenseCode1;
  int secSenseCode2;
  char *description;
} secSenseTab[] = {
  {0x0, 0x0, 0x0, "no additional sense information"},
  {0x0, 0x0, 0x1, "filemark detected on Space-Blocks,Read"},
  {0x0, 0x0, 0x2,
   "End of partition/medium detected"},
  {0x0, 0x0, 0x4, "BOT detected on reverse motion cmd"},
  {0x0, 0x0, 0x5, "EOD detected on forward motion cmd"},
  {0x0, 0x0, 0x17, "Cleaning requested\n"},
  {0x0, 0x20, 0x0,
   "Illegal length indication (read block size did not match request"},
  {0x0, 0x5b, 0x2, "Log counter at maximum"},

  {0x2, 0x4, 0x0, "no Cartridge mounted"},
  {0x2, 0x4, 0x1, "executing immediate command (BUSY)"},
  {0x2, 0x5, 0x0, "drive is offline by front panel | not operational"},
  {0x2, 0x6, 0x0, "drive not configured to controller"},
  {0x2, 0x53, 0x0, "Media load or reject"},

  {0x3, 0xc, 0x0, "Write error (write data check)"},
  {0x3, 0x11, 0x0,
   "unrecoverd Read error, permanent Read errors, retr. exhausted"},
  {0x3, 0x11, 0x1, "Read retries exhausted (read data check)"},
  {0x3, 0x11, 0x2,
   "Error too long to correct"},
  {0x3, 0x11, 0x80,
   "unrecovered Read error, attempt. Read on extended record"},
  {0x3, 0x11, 0x81, "unrecovered Read error, Read reverse on extended record"},
  {0x3, 0x11,0xe, "Decompression failure"},
  {0x3, 0x12, 0x0,
   "unrecovered Write error, permanent Write errors, retr. exh."},
  {0x3, 0x14, 0x0, "recorded entity not found"},
  {0x3, 0x14, 0x1, "record not found on Locate command"},
  {0x3, 0x14, 0x4, "Block sequence error"},
  {0x3, 0x15, 0x0, "Random positioning error"},
  {0x3, 0x30, 0x0, "incompatible medium mounted"},
  {0x3, 0x30, 0x1, "Cannot read medium, unknown format"},
  {0x3, 0x30, 0x2, "Cannot read medium, incompatible format"},
  {0x3, 0x31, 0x0, "Medium format corrupted"},
  {0x3, 0x33, 0x0, "incorrect Tape length, unusable"},
  {0x3, 0x38, 0x0, "block sequence error"},
  {0x3, 0x3a, 0x0, "Medium not present"},
  {0x3, 0x3b, 0x0, "Sequential position error"},
  {0x3, 0x3b, 0x1, "Tape positioning error at BOT"},
  {0x3, 0x3b, 0x8, "Reposition error (CU ERP failed and we are lost)"},
  {0x3, 0x51, 0x0, "Erase failure"},

  {0x4, 0x3, 0x0, "Peripheral device write fault"},
  {0x4, 0x4, 0x80, "Drive reported failure"},
  {0x4, 0x8, 0x0, "Logical unit or communication failure"},
  {0x4, 0x8, 0x1, "Logical unit timeout"},
  {0x4, 0x15, 0x1, "Mechanical positioning error (tape lost tension)"},
  {0x4, 0x40, 0x0, "diagnostic failure"},
  {0x4, 0x40, 0x80, "Diagnostic failure on component"},
  {0x4, 0x41, 0x0, "data rate over/underrun"},
  {0x4, 0x44, 0x0, "Internal target failure"},
  {0x4, 0x44, 0x1, "data buffer hardware error"},
  {0x4, 0x44, 0x2, "SCSI interface hardware error"},
  {0x4, 0x44, 0x3, "Read/Write path hardware error"},
  {0x4, 0x44, 0xb0, "Multiple bus drivers detected during buffer DMA"},
  {0x4, 0x44, 0xb1, "RAM port parity error detected during buffer DMA"},
  {0x4, 0x44, 0xb2, "366 port parity error detected during buffer DMA"},
  {0x4, 0x44, 0xb3, "CRC/LRC generation failed during buffer DMA"},
  {0x4, 0x44, 0xb4, "CRC/LRC check failed during buffer DMA"},
  {0x4, 0x44, 0xb5, "DMA zero byte count flag not set after completion"},
  {0x4, 0x44, 0xb6, "Tape drive detected a hardware error in the data path"},
  {0x4, 0x44, 0xb7, "Hardware error in the servo or a bad sensor"},
  {0x4, 0x44, 0xb8, "Permanent hardware malfunction in the Tape drive"},
  {0x4, 0x44, 0x84, "ICRC hardware error"},
  {0x4, 0x51, 0x0, "erase fault"},
  {0x4, 0x52, 0x0, "Cartridge fault"},
  {0x4, 0x53, 0x0, "Media Load/Eject failed"},
  {0x4, 0x53, 0x1, "UnLoad failure"},
  {0x4, 0x53, 0x2, "Load failure"},
  {0x4, 0x55, 0x0, "transport hardware error"},
  {0x4, 0x55, 0x1, "lost tape tension"},
  {0x4, 0x70, 0x0, "load display error"},

  {0x5, 0x1a, 0x0, "Parameter list length error"},
  {0x5, 0x20, 0x0, "Invalif Command operation code (command byte in CDB)"},
  {0x5, 0x20, 0x1, "illegal opcode"},
  {0x5, 0x20, 0x2, "illegal function or device type"},
  {0x5, 0x20, 0x3, "unsupported function"},
  {0x5, 0x20, 0x4, "reserved bit/field not zero"},
  {0x5, 0x20, 0x6, "CDB length error - too long"},
  {0x5, 0x20, 0x7, "CDB length error - too short"},
  {0x5, 0x20, 0x8, "illegal value in CDB field"},
  {0x5, 0x20, 0x9, "fixed bit set in variable block mode"},
  {0x5, 0x20, 0xa, "fixed bit not set in fixed mode"},
  {0x5, 0x20, 0xb, "immediate bit set in WFMK in unbuffered mode"},
  {0x5, 0x21, 0x0, "Logical block address out of range"},
  {0x5, 0x24, 0x0, "invalid field in CDB"},
  {0x5, 0x24, 0x80, "Fixed bit set in variable mode"},
  {0x5, 0x24, 0x81, "Fixed bit clear on fixed mode"},
  {0x5, 0x24, 0x82, "Media loaded in drive while attempt Write-Buffer"},
  {0x5, 0x25, 0x0, "Logical unit not supported"},
  {0x5, 0x26, 0x0, "invalid field in parameter list"},
  {0x5, 0x26, 0x2, "fixed length not in valid range"},
  {0x5, 0x26, 0x4, "Space command does not support EOD Space"},
  {0x5, 0x26, 0x7, "invalid block-id parameter"},
  {0x5, 0x26, 0x8, "invalid Load Display overlay byte"},
  {0x5, 0x26, 0x80, "Not compression capable"},
  {0x5, 0x2b, 0x0, "variable block length error"},
  {0x5, 0x2b, 0x1, "transfer length exceed max limit"},
  {0x5, 0x2c, 0x0, "command sequence error"},
  {0x5, 0x39, 0x0, "Saving parameters not supported"},
  {0x5, 0x3d, 0x0, "cartridge scratch loader error"},
  {0x5, 0x3d, 0x1, "CSL not exist on LUN"},
  {0x5, 0x3d, 0x2, "CSL not ready for Load command"},
  {0x5, 0x80, 0x0, "CSL not present"},
  {0x5, 0x80, 0x1, "Invalid CSL position requested"},
  {0x5, 0x80, 0x2, "CSL not ready (no cartridge loaded)"},
  {0x5, 0x80, 0x3, "Load command received and the load is in progress"},

  {0x6, 0x28, 0x0, "medium changed (not ready to ready transition)"},
  {0x6, 0x29, 0x0, "power-on reset or SCSI bus reset"},
  {0x6, 0x2a, 0x0, "Parameters changed"},
  {0x6, 0x2a, 0x1, "Mode parameters changed by another host"},
  {0x6, 0x2a, 0x2, "Log parameters changed by another host"},
  {0x6, 0x3f, 0x1, "Microcode has been changed"},

  {0x7, 0x27, 0x0, "Medium Write protected"},
  {0x7, 0x27, 0x80, "Unable to overwrite data"},
  {0x7, 0x30, 0x5, "Cannot write medium - incompatible format"},

  {0x8, 0x0, 0x0, "Media void encountered"},
  {0x8, 0x0, 0x5, "End-of-data detected"},
  {0x8, 0x11, 0x1, "Read retries exhausted"},
  {0x8, 0x14, 0x0,
   "Recorded entity not found (no EOD - but tape appears blank)"},
  {0x8, 0x2e, 0x0, "Blank-Check, no data on medium"},

  {0xb, 0x43, 0x0, "message retry error"},
  {0xb, 0x43, 0x1, "message parity error"},
  {0xb, 0x43, 0x2, "initiator detected error"},
  {0xb, 0x43, 0x3, "arb and non-arb initiator on bus"},
  {0xb, 0x47, 0x0, "SCSI parity error"},
  {0xb, 0x48, 0x0, "Initiator detected error message received"},
  {0xb, 0x49, 0x0, "Invalid message error"},
  {0xb, 0x49, 0x1, "inappropriate message"},
  {0xb, 0x49, 0x2, "illegal message"},
  {0xb, 0x49, 0x3, "timeout receiving message byte from initiator"},
  {0xb, 0x4a, 0x0, "command phase error"},
  {0xb, 0x4a, 0x1, "timeout receiving bytes from initiator"},
  {0xb, 0x4b, 0x0, "Data phase error"},
  {0xb, 0x4b, 0x1, "timeout receiving data from initiator"},
  {0xb, 0x4c, 0x0, "message reject failure"},
  {0xb, 0x4d, 0x0, "data in error"},
  {0xb, 0x4d, 0x1, "timeout sending data bytes to initiator"},
  {0xb, 0x4e, 0x0, "Overlapped commands attempted"},
  {0xb, 0x4e, 0x1, "timeout sending status bytes to initiator"},
  {0xb, 0x4f, 0x0, "message in error"},
  {0xb, 0x4f, 0x1, "timeout sending message bytes to initiator"},
  {0xb, 0x71, 0x0, "tape position lost, manual pressed rew/inl on panel"},

  {0xd, 0x0, 0x2,
   "End of partition/medium detected (unable to write all data to tape"},
  {0xd, 0x0, 0x4, "Beginning of partition/medium detected"},
  {0xd, 0x62, 0x0, "overflow error, physical EOT encountered"},

  {0x0, 0x0, 0x0, (char *) 0},
};

typedef struct Pos {
  u_char BOP:1;
  u_char EOP:1;
  u_char r1:3;
  u_char BPU:1;
  u_char r2:2;

  u_char partition;

  u_char r3[2];

  u_char fb[4];

  u_char lb[4];

  u_char r4;

  u_char nbufb[3];
  u_char nbytb[3];
} Pos;

typedef struct MSHeader {
  u_char dataLen;
  u_char mediumType;
  u_char WP:1;
  u_char bufferedMode:3;
  u_char speed:4;
  u_char blockDescLen;
} MSHeader;

typedef struct MSBlockDesc {
  u_char densityCode;
  u_char nb1;
  u_char nb2;
  u_char nb3;
  u_char r1;
  u_char bl1;
  u_char bl2;
  u_char bl3;
} MSBlockDesc;

typedef struct devconPage {
  u_char PS:1;
  u_char r1:1;
  u_char pageCode:6;

  u_char pageLen;

  u_char r2:1;
  u_char CAP:1;
  u_char CAF:1;
  u_char activeFormat:5;

  u_char activePartition;

  u_char writeRatio;

  u_char readRation;

  u_char delayTime[2];

  u_char DBR:1;
  u_char BIS:1;
  u_char RSMK:1;
  u_char AVC:1;
  u_char SOCF:2;
  u_char RBO:1;
  u_char REW:1;

  u_char gapSize;

  u_char EODDef:3;
  u_char EEG:1;
  u_char SEW:1;
  u_char r3:3;

  u_char earlyWarning[3];

  u_char compression;

  u_char r4;
} devconPage;

typedef struct MSense {
  MSHeader msh;
  MSBlockDesc msb;
#if 0 /* 3490 */
  u_char r1:6;
  u_char REBLK:1;
  u_char WRICRC:1;
#endif
  devconPage dp;
} MSense;

/* Request Sense data structure */

typedef struct SenseData {
  u_char  valid:1;
  u_char  errorClass:3;
  u_char  errorCode:4;
  u_char  segmentNumber;
  u_char  FMK:1;
  u_char  EOM:1;
  u_char  ILI:1;
  u_char  r1:1;
  u_char  senseKey:4;
  u_char  iB1;
  u_char  iB2;
  u_char  iB3;
  u_char  iB4;
  u_char  addSenseLen;
  u_char  r2[4];
  u_char  secSenseCode;
  u_char  secSenseQual;
  u_char  r3[4];
  u_short Fault1Error;
  u_short Fault2Error;
  u_short Fault3Error;
  u_char  nInitiator;
  u_char  r4:2;
  u_char  REBLK:1;
  u_char  WRICRC:1;
  u_char  lunAddr:4;
  u_char  rest1[12];
  u_char  transportERPCode;  /* byte 38  */
  u_char  r5[4];
  u_char  trAlert;
  u_char  trErrorBits;
  u_char  trServoState1;
  u_char  trServoState2;
  u_char  libraryState;
  u_char  rest2[60];
} SenseData;

typedef struct inqData {
  u_char csl:1;
  u_char pDeviceType:7;
  u_char rmb:1;
  u_char deviceTypeQ:7;
  u_char ISOversion:2;
  u_char ECMAversion:3;
  u_char ANSIversion:3;
  u_char r1;
  u_char addLength;
  u_char r2[3];
  u_char vendor[8];
  u_char product[16];
  u_char revision[4];
  u_char r3:5;
  u_char t36:1;
  u_char ICRC:1;
  u_char CSL:1;
} inqData;

typedef struct logSenseHeader {
  u_char r1:2;
  u_char pageCode:6;

  u_char r2;

  u_short pageLength;
} logSenseHeader;

typedef struct logSenseParameterFormat {
  u_short parameterCode;

  u_char DU:1;
  u_char DS:1;
  u_char TSD:1;
  u_char ETC:1;
  u_char TMC:2;
  u_char r1:1;
  u_char LP:1;

  u_char parameterLength;
} logSenseParameterFormat;

typedef struct writeErrorCounterPage {
  logSenseHeader header;
  logSenseParameterFormat f0;
  u_int c0;
  logSenseParameterFormat f1;
  u_int c1;
  logSenseParameterFormat f2;
  u_int c2;
  logSenseParameterFormat f3;
  u_int c3;
  logSenseParameterFormat f4;
  u_int c4;
  logSenseParameterFormat f5;
  u_char c5[8];
  logSenseParameterFormat f6;
  u_int c6;
} writeErrorCounterPage;

typedef struct readErrorCounterPage {
  logSenseHeader header;
  logSenseParameterFormat f0;
  u_int c0;
  logSenseParameterFormat f1;
  u_int c1;
  logSenseParameterFormat f2;
  u_int c2;
  logSenseParameterFormat f3;
  u_int c3;
  logSenseParameterFormat f4;
  u_int c4;
  logSenseParameterFormat f5;
  u_char c5[8];
  logSenseParameterFormat f6;
  u_int c6;
} readErrorCounterPage;

typedef struct nonMediumErrorPage {
  logSenseHeader header;
  logSenseParameterFormat f0;
  u_int c0;
} nonMediumErrorPage;

typedef struct sequentialAccessDevicePage {
  logSenseHeader header;
  logSenseParameterFormat f0;
  u_char c0[8];
  logSenseParameterFormat f1;
  u_char c1[8];
  logSenseParameterFormat f2;
  u_char c2[8];
  logSenseParameterFormat f3;
  u_char c3[8];
  logSenseParameterFormat f4;
  u_short c4;
} sequentialAccessDevicePage;

typedef struct vendorPage {
  logSenseHeader header;
  logSenseParameterFormat f0;
  u_int c0;
  logSenseParameterFormat f1;
  u_int c1;
  logSenseParameterFormat f2;
  u_int c2;
  logSenseParameterFormat f3;
  u_int c3;
  logSenseParameterFormat f4;
  u_int c4;
  logSenseParameterFormat f5;
  u_int c5;
  logSenseParameterFormat f6;
  u_int c6;
  logSenseParameterFormat f7;
  u_int c7;
  logSenseParameterFormat f8;
  u_int c8;
  logSenseParameterFormat f9;
  u_int c9;
  logSenseParameterFormat f10;
  u_int c10;
  logSenseParameterFormat f11;
  u_int c11;
  logSenseParameterFormat f12;
  u_int c12;
  logSenseParameterFormat f13;
  u_int c13;
  logSenseParameterFormat f14;
  u_int c14;
  logSenseParameterFormat f15;
  u_char c15[8];
  logSenseParameterFormat f16;
  u_char c16[8];
  logSenseParameterFormat f17;
  u_char c17[8];
  logSenseParameterFormat f18;
  u_char c18[8];
  logSenseParameterFormat f19;
  u_int c19;
  logSenseParameterFormat f20;
  u_int c20;
  logSenseParameterFormat f21;
  u_int c21;
  logSenseParameterFormat f22;
  u_int c22;
} vendorPage;

typedef struct displayDataFormat {
  u_char overlay:3;
  u_char alt:1;
  u_char blink:1;
  u_char LH:1;
  u_char r1:2;

  u_char message1[8];
  u_char message2[8];
} displayDataFormat;


#ifdef OSM
#define LOGG osmlog
#endif
#ifdef ES
#define LOGG LOG
/* mapping of syslog priorities */
#define LOG_DEBUG ESMVR_INFO
#define LOG_INFO ESMVR_INFO
#define LOG_WARNING ESMVR_WARNING
#define LOG_ERR ESMVR_FATAL
#endif

#ifdef SOLARIS26
typedef struct dsreq {
  int fd;
  struct uscsi_cmd ucmd;
  u_char cmd[10];   /* max 10 byte CDBs */
} dsreq;

#define getfd(_x_) ((_x_)->fd)
#define CMDBUF(_x_) ((_x_)->cmd)
#define DSRQ_WRITE USCSI_WRITE
#define DSRQ_READ USCSI_READ
#define DATASENT(_x_) ((_x_)->ucmd.uscsi_buflen - (_x_)->ucmd.uscsi_resid)
#define SENSESENT(_x_) (sizeof(SenseData) - (_x_)->ucmd.uscsi_rqresid)
#define B(s,i) ((u_char)((s) >> i))
#define B1(s) ((u_char)(s))
#define B2(s) B((s),8), B1(s)
#define B3(s) B((s),16), B((s),8), B1(s)
#define B4(s) B((s),24), B((s),16), B((s),8), B1(s)

#define G0_MSEN 0x1A   /* Mode Sense */
#define  G0_LOAD         0x1B   /* Load/Unload          */ 
#define  G0_MSEL         0x15   /* Mode Select          */ 
#define  G0_SPAC         0x11   /* Space                */ 
#define  G0_WRIT         0x0A   /* Write                */ 
#define  G0_READ         0x08   /* Read                 */ 
#define  G0_REWI         0x01   /* Rewind               */ 
#define  G0_TEST         0x00   /* Test Unit Ready      */  
#define  G0_INQU         0x12   /* Inquiry              */
#define  G0_WF           0x10   /* Write Filemark       */ 
#define  G1_LOCA    0x2B        /* Locate               */ 
#define  G1_RPOS    0x34        /* Read Position        */ 

static void fillg0cmd(struct dsreq *dsr, u_char *cmd, u_char b0, u_char b1,
		      u_char b2, u_char b3, u_char b4, u_char b5) {

  memset((char *) cmd, 0, 10);
  memset((char *) &(dsr->ucmd), 0, sizeof(struct uscsi_cmd));

  dsr->ucmd.uscsi_cdb = (caddr_t) cmd;
  dsr->ucmd.uscsi_cdblen = 6;
  cmd[0] = b0;
  cmd[1] = b1;
  cmd[2] = b2;
  cmd[3] = b3;
  cmd[4] = b4;
  cmd[5] = b5;
}

static void fillg1cmd(struct dsreq *dsr, u_char *cmd, u_char b0, u_char b1,
		      u_char b2, u_char b3, u_char b4, u_char b5,
		      u_char b6, u_char b7, u_char b8, u_char b9) {
  fillg0cmd(dsr, cmd, b0, b1, b2, b3, b4, b5);
  dsr->ucmd.uscsi_cdblen = 10;
  cmd[6] = b6;
  cmd[7] = b7;
  cmd[8] = b8;
  cmd[9] = b9;
}

static void filldsreq(struct dsreq *dsr, void *data, u_int datalen,
		      u_int flags) {
  dsr->ucmd.uscsi_bufaddr = (caddr_t) data;
  dsr->ucmd.uscsi_buflen = datalen;
  dsr->ucmd.uscsi_flags = flags;
}

#endif /* EuroStore defines */

/* globals */
static SenseData sd;
static char *fd_mappings[MAXNFD];    /* mapping of fd to devname */
static struct dsreq *dsrGlobal[MAXNFD];
static int quiet = False;

/* functions !!! */
void bprint(unsigned char *s, int n, int nperline, int space) {
  int   i, x;
  char  *sp = (space) ? " ": "";
  char  line[300], buf[40];

  line[0] = '\0';
  for(i=0;i<n;i++)  {
    x = s[i];
    sprintf(buf, ((i%4==3)?"%c%c%s%s":"%c%c%s"),
            hex(x>>4), hex(x), sp, sp);
    strcat(line, buf);
    if ( i%nperline == (nperline - 1) ) {
      LOGG(LOG_DEBUG, "%s", line);
      line[0] = '\0';
    }
  }
  if (line[0] != '\0')
    LOGG(LOG_DEBUG, "%s", line);
}

/* return appr. size from given KB number */
char *printSize(unsigned long long int bytes) {
  static char l[40];

  if (bytes >= 1099511627776)     /* more than 1 tera */
    (void) sprintf(l, "%.2f TB", (double) bytes / 1099511627776.0);
  else if (bytes >= 1073741824)   /* more than 1 gig */
    (void) sprintf(l, "%.2f GB", (double) bytes / 1073741824.0);
  else if (bytes >= 1048576)      /* more than 1 meg */
    (void) sprintf(l, "%.2f MB", (double) bytes / 1048576.0);
  else if (bytes >= 1024)         /* more than 1 kilo */
    (void) sprintf(l, "%.2f KB", (double) bytes / 1024.0);
  else
    (void) sprintf(l, "%llu B", bytes);
  return(l);
}

static char *devn(int fd) {
  if (fd < MAXNFD && fd >= 0 && fd_mappings[fd] && fd_mappings[fd][0])
    return(fd_mappings[fd]);
  else
    return("NODEV");
}

static struct dsreq *DSR(int fd) {
  if (dsrGlobal[fd]) {
    return(dsrGlobal[fd]);
  } else {
    errno = EBADF;
#ifdef OSM
    osmerrno = EINVALARG;
#endif
    LOGG(LOG_ERR, "invalid file-descriptor %d - not mapped", fd);
    return((struct dsreq *) 0);
  }
}

#ifdef OSM
static int Reset(struct dsreq *dsr) {
  int fd, ret;
  unsigned long dummy = 0;
  int scsiBus;
  struct scsi_ha_op sop;
  struct stat st;
  char hwp[80];
  int  fdhw;

  fd = getfd(dsr);
  if (fstat(fd, &st) != 0) {
    LOGG(LOG_ERR, "stat() failed %d\n", errno);
    return(-1);
  }
  scsiBus = (st.st_rdev >> 7) - 1;  /* SGI starts at zero */

  (void) sprintf(hwp, "/hw/scsi_ctlr/%d/bus", scsiBus);

  /* printf("0x%X -- 0x%X -- controler %d\n",
     st.st_dev, st.st_rdev, scsiBus); */

  if ((fdhw = open(hwp, O_RDWR)) < 0) {
    LOGG(LOG_ERR, "open of '%s' failed %d\n", hwp, errno);
    return(-1);
  }

  memset((char *) &sop, 0, sizeof(sop));
  if (ioctl(fdhw, SOP_RESET, &sop) != 0) {
    LOGG(LOG_ERR, "ioctl(SOP_RESET) failed %d\n", errno);
    (void) close(fdhw);
    return(-1);
  }
  (void) close(fdhw);
  return(0);
}
#endif
#ifdef SOLARIS26
static int Reset(struct dsreq *dsr) {
  return(1);
}
#endif

static void printExtSenseData(SenseData *sd) {
  int es1 = (int) sd->secSenseCode;
  int es2 = (int) sd->secSenseQual;
  int psc = (int) sd->senseKey;
  int i = 0;

  while(secSenseTab[i].description) {
    if (psc == secSenseTab[i].senseKey &&
	es1 == secSenseTab[i].secSenseCode1 &&
	es2 == secSenseTab[i].secSenseCode2) {
	  LOGG(LOG_ERR, "0x%X 0x%X 0x%X : %s",
		  psc, es1, es2, secSenseTab[i].description);
	  return;
	}
    i++;
  }
  LOGG(LOG_ERR, "unknown secondary sense bytes 0x%X 0x%X 0x%X",
	  psc, es1, es2);
}


static void printSense(SenseData *s, struct dsreq *dsr) {
  u_int iB = 0;;

  LOGG(LOG_ERR, "Valid %u ErrorClass %u ErrorCode %u",
	 (u_int) s->valid, (u_int) s->errorClass, (u_int) s->errorCode);
#ifdef IRIX
  LOGG(LOG_ERR, "FMK %u EOM %u ILI %u SenseKey %u (%s)",
	 (u_int) s->FMK, (u_int) s->EOM, (u_int) s->ILI, (u_int) s->senseKey,
	 ds_ctostr((unsigned long) s->senseKey , sensekeytab));
#endif
#ifdef SOLARIS26
  LOGG(LOG_ERR, "FMK %u EOM %u ILI %u SenseKey %u",
	 (u_int) s->FMK, (u_int) s->EOM, (u_int) s->ILI, (u_int) s->senseKey);
#endif
  iB = s->iB1 << 24; iB |= s->iB2 << 16; iB |= s->iB3 << 8; iB |= s->iB4;
  LOGG(LOG_ERR, "information byte 0x%x", iB);
  LOGG(LOG_ERR, "additional sense len %u", (u_int) s->addSenseLen);
  printExtSenseData(s);
  bprint((unsigned char *) s, sizeof(SenseData), 16, 1);
}

#ifdef IRIX
static void printFailure(struct dsreq *dsr) {
  LOGG(LOG_ERR, "'%s' failed with %d (%s)",
	 ds_vtostr( (unsigned long) (CMDBUF(dsr))[0], cmdnametab),
	 (int) STATUS(dsr),
	 ds_vtostr((unsigned long) STATUS(dsr), cmdstatustab));
  LOGG(LOG_ERR, "SCSI RET 0x%x (%s) - SCSI MSG %u", (int) RET(dsr),
	 ds_vtostr((unsigned long) RET(dsr), dsrtnametab),
	 (u_int) MSG(dsr));
}
#endif
#ifdef SOLARIS26
static void printFailure(struct dsreq *dsr) {
  LOG(ESMVR_FATAL,
      "cmd (0x%X) failed: status: %d resid: %d rqstatus %d rqresid: %d",
      (int) dsr->cmd[0],
      dsr->ucmd.uscsi_status, dsr->ucmd.uscsi_resid,
      dsr->ucmd.uscsi_rqstatus, dsr->ucmd.uscsi_rqresid);
}
#endif

#ifdef IRIX
/* copy of the dslib function */
int doscsireq1( int fd, struct dsreq *dsp) {
  int   cc;
  int   retries = 10;
  uchar_t       sbyte;
  u_char *cmdb = &(((u_char *) dsp->ds_cmdbuf)[1]);

  /*
   *  loop, issuing command
   *    until done, or further retry pointless
   */

/*  printf("before: 0x%x  ", (u_int) *cmdb); */
#if 0
  *cmdb |= (LUN << 5);
#endif
/*  printf("after: 0x%x\n", (u_int) *cmdb); */


  /* fprintf(stderr, "doscsireq1\n"); */
  while ( --retries > 0 )  {
    
    caddr_t sp;
    
    sp =  SENSEBUF(dsp);
#if 0
    if(DATALEN(dsp))
      DSDBG(printf("data xfer %s, ",  dsp->ds_flags&DSRQ_READ 
		    ?
		    "in" : "out"));
    DSDBG(printf("cmdbuf   =  ");
	  bprint(CMDBUF(dsp),CMDLEN(dsp),16,1));
    if ( (dsp->ds_flags & DSRQ_WRITE) )
      DSDBG(bprint( DATABUF(dsp), min(50,DATALEN(dsp)),16,1 ));
    
    DSDBG(printf("databuf datalen %x %d\n",DATABUF(dsp), DATALEN(dsp)));
#endif
    cc = ioctl( fd, DS_ENTER, dsp);
    if ( cc < 0)  {
      if (quiet == False) {
	LOGG(LOG_ERR, "ioctl return %d (%s) %d",
	       cc, strerror(errno), errno);
      }
      RET(dsp) = DSRT_DEVSCSI;
      return(-1);
    }

#if 0
    DSDBG(printf("cmdlen after ioctl=%d\n",CMDLEN(dsp)));
    DSDBG(printf("ioctl=%d ret=%x %s",
		  cc, RET(dsp), 
		  RET(dsp) ? ds_vtostr((unsigned long)RET(dsp),dsrtnametab) : ""));
    DSDBG(if (SENSESENT(dsp)) printf(" sensesent=%d",
				      SENSESENT(dsp)));
    
    DSDBG(printf(" cmdsent=%d datasent=%d sbyte=%x %s\n",
		  CMDSENT(dsp), DATASENT(dsp), STATUS(dsp),
		  ds_vtostr((unsigned long)STATUS(dsp), cmdstatustab)));
    DSDBG(if ( FLAGS(dsp) & DSRQ_READ )
	  bprint( DATABUF(dsp), min(16*16,DATASENT(dsp)), 16,1));
#endif

    if ( RET(dsp) == DSRT_NOSEL ) {
      if (quiet == False) {
	LOGG(LOG_WARNING, "ioctl RET with NOSEL");
      }
      continue;         /* retry noselect 3X */
    }

#if 0
    /* decode sense data returned */
    if ( SENSESENT(dsp) )  {
      DSDBG(printf("sense key %x - %s\n", SENSEKEY(sp),
		    ds_ctostr( (unsigned long)SENSEKEY(sp), sensekeytab));
	    bprint( SENSEBUF(dsp), min(100, SENSESENT(dsp)), 16,1);
	);
    }
    DSDBG(printf("sbyte %x\n", STATUS(dsp)));
#endif

    /* decode scsi command status byte */
    sbyte = STATUS(dsp);
    switch (sbyte)  {
      case 0x08:                /*  BUSY */
      case 0x18:                /*  RESERV CONFLICT */
	if (quiet == False)
	  LOGG(LOG_WARNING, "ioctl: STATUS for retry 0x%x", sbyte);
        sleep(2);
        continue;
      case 0x00:                /*  GOOD */
      case 0x02:                /*  CHECK CONDITION */
      case 0x10:                /*  INTERM/GOOD */
	return(sbyte);
      default:
	if (quiet == False)
	  LOGG(LOG_WARNING, "ioctl return 0x%x (%s) %d",
		 sbyte, strerror(errno), errno);
        return(sbyte);
    }
  }
  if (quiet == False)
    LOGG(LOG_ERR, "SCSI ioctl retry limit exceed");
  return(-1);    /* fail retry limit */
}
#endif

#ifdef IRIX
int doScsi(struct dsreq *dsr) {
  int ret = 0;

  TIME(dsr) = 60*1000 * 60;   /* TODO timeout should be command dependent */

/* FLAGS(dsr) |= DSRQ_CTRL2; */

  SENSEBUF(dsr) = (caddr_t) &sd;
  SENSELEN(dsr) = sizeof(SenseData);
  FLAGS(dsr) |= DSRQ_SENSE;

  switch((ret = doscsireq1(getfd(dsr), dsr))) {
  case 0:
    return(0);
  default:
    if (quiet == False) {
      LOGG(LOG_ERR, "SCSI cmd exit with 0x%x @ %s\n", ret, devn(getfd(dsr)));
      printFailure(dsr);
      if (SENSESENT(dsr) > 0) {
	LOGG(LOG_ERR, "Sense data size %d\n", SENSESENT(dsr));
	printSense(&sd, dsr);
      } else {
	LOGG(LOG_ERR, "NO SENSE DATA SENT\n");
	/* RequestSense(); */
      }
    }
    return(-1);
  }
}
#endif
#ifdef SOLARIS26
static int doScsi(struct dsreq *dsr) {
  int ret, fd = getfd(dsr);

  dsr->ucmd.uscsi_timeout = 60 * 60;  /* 1 hour */
  dsr->ucmd.uscsi_rqbuf = (caddr_t) &sd;
  dsr->ucmd.uscsi_rqlen = sizeof(sd);
  dsr->ucmd.uscsi_flags |= (USCSI_ISOLATE | USCSI_DIAGNOSE | USCSI_RQENABLE);
  errno = 0;
  
  if ((ret = ioctl(fd, USCSICMD, &(dsr->ucmd))) < 0 &&
      (errno != EIO || dsr->ucmd.uscsi_status == 0)) {
    LOGG(LOG_ERR, "sending CDB failed (%d) status %d (errno: %d (%s))",
	ret, dsr->ucmd.uscsi_status, errno, strerror(errno));
    if (dsr->ucmd.uscsi_rqstatus == 0)
      printSense(&sd, dsr);
    return(-1);
  }
  if (dsr->ucmd.uscsi_status != 0) {
    if (dsr->ucmd.uscsi_status == 0x2) { /* CHECK CONDITION */
      printSense(&sd, dsr);
    } else {
      LOGG(LOG_ERR, "SCSI cmd failed status 0x%X", dsr->ucmd.uscsi_status);
    }
    return(-1);
  }

  if (ret != 0 || dsr->ucmd.uscsi_status != 0 || errno != 0)
    LOGG(LOG_ERR, "scsi command bad %d %d %d", ret, dsr->ucmd.uscsi_status,
	 errno);

  return(0);
}
#endif


void DisplayEagle(int fd, const char *m1, const char *m2) {
  int ret;
  displayDataFormat df;
  int len;
  static char lastM2[10];
  struct dsreq *dsr = DSR(fd);

  if (!dsr || m1 == NULL || m1[0] == '\0')
    return;

  memset((char *) &df, 0, sizeof(df));
  df.overlay = 0x7;
  df.alt = 1;
  df.blink = 0;
  df.LH = 0;

  len = strlen(m1);
  memcpy(df.message1, m1, (len < 8) ? len : 8);
  if (m2 != NULL && m2[0] != '\0') {
    len = strlen(m2);
    memcpy(df.message2, m2, (len < 8) ? len : 8);
    memcpy(lastM2, m2, (len < 8) ? len : 8);
  } else {
    memcpy(df.message2, lastM2, 8);
  }

  fillg0cmd(dsr, CMDBUF(dsr), B1(0x6), B3(0), B1(0x11), 0);
  filldsreq(dsr, (caddr_t) &df, 0x11, DSRQ_WRITE);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "LoadDisplay: failed @ %s", devn(getfd(dsr)));
  }
}

/* convert string into Eagle native position information */
int loc2binEagle(const char *loc, unsigned long *pos) {
  char *ptr, *cptr;
  char ll[100];
  unsigned long n;

  strncpy(ll, loc, 99);

  errno = 0;
  if ((n = strtoul(ll, &cptr, 16)) < 0 || cptr == ll ||
      (n == ULONG_MAX && errno != 0)) {
#ifdef OSM
    osmerrno = EINVALTAPEPOS;
#endif
    return(-1);
  }
  *pos = (int) n;
  return(0);
}

/* and vice versa (loc must be allocated to at least BFLOCLEN lenght) */
int loc2strEagle(unsigned long pos, char *loc) {
  if (pos < 0 || pos == ULONG_MAX) {
#ifdef OSM
    osmerrno = EINVALTAPEPOS;
#endif
    return(-1);
  }
  (void) sprintf(loc, "%x:0", pos);
  return(0);
}

/* report true number of bytes occupied on the real media */
/* use the sequential access device page */
int BytesAccessOnMediaEagle(int fd, unsigned int *rb, unsigned int *wb) {
  struct dsreq *dsr = DSR(fd);
  sequentialAccessDevicePage sap;
  unsigned long long int wrb, reb;
  int ret;

  if (!dsr)
    return(-1);

  fillg1cmd(dsr, CMDBUF(dsr), B1(0x4D), B1(0), B1(0x40 | 0xC),
	    0, 0, 0, 0, B2(sizeof(sap)), 0);
  filldsreq(dsr, (caddr_t) &sap, sizeof(sap), DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "BytesOnMediaEagle(%s) Log-Sense failed", devn(fd));
    return(-1);
  }
  memcpy((char *) &wrb, sap.c1, 8);
  memcpy((char *) &reb, sap.c2, 8);
  *rb = (unsigned int) reb;
  *wb = (unsigned int) wrb;
  return(0);
}

/* do the initial Mode Sense Page setup we need */
/*
 *  1. clear Log Sense cumulative parameters
 */
static int SetupDevice(struct dsreq *dsr, int mode) {
  int ret;
  MSense ms;

  /* clear log-sense counter */
  fillg1cmd(dsr, CMDBUF(dsr), B1(0x4C), B1(2), B1(0xC0), 0, 0, 0, 0, 0, 0, 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "SetupDevice Log Select command failed @ %s",
	   devn(getfd(dsr)));
    return(-1);
  }


  fillg0cmd(dsr, CMDBUF(dsr), G0_MSEN, 0, 0x10, 0, B1(sizeof(MSense)), 0);
  filldsreq(dsr, (caddr_t) &ms, sizeof(MSense), DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "SetupDevice: error doing Mode Sense @ %s",
	   devn(getfd(dsr)));
    return(-1);
  }

#if 0
  /* check write-protected media !!! */
  if ((mode & O_WRONLY || mode & O_RDWR) && ms.msh.WP)
    return(-2);
  /* clear bits reserved for mode-select */
  ms.msh.WP = 0;
  ms.msh.dataLen = ms.msh.mediumType = 0;
  ms.msh.bufferedMode = 1;
  ms.msh.speed = 0;
  ms.msb.nb1 = ms.msb.nb2 = ms.msb.nb3 = 0;
  ms.msb.bl1 = ms.msb.bl2 = ms.msb.bl3 = 0;
  ms.REBLK = 0;
  ms.WRICRC = 1;
#endif

  /* set compression ON */
  if (ms.dp.compression == 0)
    LOGG(LOG_DEBUG, "compression was OFF @ %s", devn(getfd(dsr)));
  ms.dp.compression = 1;
  ms.dp.SOCF = 1;            /* stop on first filemark */
  /* clear some fields */
  ms.msh.dataLen = ms.msh.mediumType = 0;
  ms.msh.WP = 0;
  ms.msh.bufferedMode = 1; ms.msh.speed = 0;
  ms.msb.nb1 = ms.msb.nb2 = ms.msb.nb3 = ms.msb.r1 =
    ms.msb.bl1 = ms.msb.bl2 = ms.msb.bl3 = 0;
  ms.msb.densityCode = 0x42;


  /* and bring the stuff in */
  fillg0cmd(dsr, CMDBUF(dsr), G0_MSEL, 0x10, 0, 0, B1(sizeof(MSense)), 0);
  filldsreq(dsr, (caddr_t) &ms, sizeof(MSense), DSRQ_WRITE);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "SetupDevice: Mode Select failed @ %s", devn(getfd(dsr)));
    return(-1);
  }

  return(0);
}

static int isWritable(struct dsreq *dsr) {
  int ret;
  MSense ms;
  
  if (!dsr)
    return(-1);
  
  memset((char *) &ms, 0, sizeof(MSense));
  fillg0cmd(dsr, CMDBUF(dsr), G0_MSEN, 0, 0, 0, B1(sizeof(MSense)), 0);
  filldsreq(dsr, (caddr_t) &ms, sizeof(MSense), DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "isWritable: mode sense failed @ %s", devn(getfd(dsr)));
    return(-1);
  }
  if (ms.msh.bufferedMode == 0)
    LOGG(LOG_DEBUG, "Warning Eagle '%s' in non-buffered mode",
	   devn(getfd(dsr)));
  return((ms.msh.WP == 0) ? 0 : -1);
}

int OpenEagle(const char *device, int mode) {
  struct dsreq *dsr;
  int fd;

#ifdef IRIX
  if ((dsr = dsopen(device, mode)) == NULL) {
    osmerrno = EDRIVEERROR;
    LOGG(LOG_ERR, "can't open '%s' -> %d (%s)", device,
	   errno, strerror(errno));
    return(-1);
  }
#endif
#ifdef SOLARIS26
  if ((dsr = (struct dsreq *) malloc(sizeof(struct dsreq))) == NULL) {
    errno = ENOMEM;
    return(-1);
  }

  if ((fd = open(device, mode | O_NDELAY)) < 0) {
    (void) free(dsr);
    return(-1);
  }
  dsr->fd = fd;
#endif
  fd = getfd(dsr);
  if (dsrGlobal[fd]) { /* fd already in use !!! */
    LOGG(LOG_ERR, "OpenEagle: fd (%d) already in use (closing)", fd);
    (void) CloseEagle(fd);
    errno = EBADF;
#ifdef OSM
    osmerrno = EINVALARG;
#endif
    return(-1);
  }
  dsrGlobal[fd] = dsr;
  if (fd < MAXNFD)
    fd_mappings[fd] = strdup(device);
  return(fd);
}

int OpenReadyEagle(const char *device, int mode, int timeout) {
  int fd = -1, ret, nFailed = 0;
  int sleepTime = 2;
  struct dsreq *dsr = (struct dsreq *) 0;

#ifdef OSM
  osmerrno = OSMOK;
#endif

  if ((fd = OpenEagle(device, mode)) < 0)
    return(-1);

  if ((dsr = DSR(fd)) == (struct dsreq *) 0) {
    (void) CloseEagle(fd);
    return(-1);
  }

  /* loop until device returns READY state */
  while(666) {
    if (fd < 0) {
      if ((fd = OpenEagle(device, mode)) >= 0)
	dsr = DSR(fd);
    }
    if (dsr != (struct dsreq *) 0) {
      if (testunitready(dsr) == 0)
	break;
    }

    nFailed++;
    if (--timeout <= 0) {
      LOGG(LOG_ERR, "OPEN: timeout opening '%s' %d (%s) [%d]",
	   device, errno, strerror(errno), nFailed);
      if (fd >= 0)
	(void) CloseEagle(fd);
      return(-1);
    }
    if (sleepTime != ((nFailed / 10) + 2))
      LOGG(LOG_DEBUG, "OPEN: open(%s) failure %d -- increase waittime",
	   device, nFailed);
    /* sleep longer in case of heavy failure rate ..... */
    sleepTime = (nFailed / 10) + 2;
    (void) sleep(sleepTime);
  }

  /* check if tape is write protected or not */
  if (mode & O_RDWR || mode & O_WRONLY) {
    if (isWritable(dsr) != 0) {
      LOGG(LOG_ERR, "tape in drive '%s' not writable (write protected)",
	     device);
      (void) CloseEagle(fd);
      errno = EACCES;
#ifdef OSM
      osmerrno = EWRITEPROTECT;
#endif
      return(-1);
    }
  }

  if (SetupDevice(dsr, mode) != 0) {
    LOGG(LOG_ERR, "Setup Device on '%s' failed", device);
    (void) CloseEagle(fd);
    return(-1);
  }

  LOGG(LOG_DEBUG, "OPEN: open(%s) done (fd: %d)", device, fd);
#ifdef OSM
  osmerrno = 0;
#endif
  return(fd);
}

/* check if we are at the beginning of given fsn */
static int VerifyFSN(int fd, int fsn) {
  return(0);
}

/* format a Eagle inserted tape with defaults as:
 * one single A partition with 3 system zones (small) and VFI including the
 * given six byte volume id */
int FormatEagle(int fd, char *data) {
  return(0);
}

int WriteEagle(int fd, const void *buf, unsigned int n) {
  int ret, serrno = -1;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);

  if (n <= 0 || !buf || (n & 0xff000000)) {
    errno = EINVAL;
    return(-1);
  }
#if 0
  LOGG(LOG_DEBUG, "WriteEagle(%s) buf: 0x%X len: %u",
	 devn(fd), buf, n);
#endif
  fillg0cmd(dsr, CMDBUF(dsr), G0_WRIT, 0, B3(n), B1(0));
  filldsreq(dsr, (void *) buf, n, DSRQ_WRITE);
  if ((ret = doScsi(dsr)) != 0) {
#ifdef SOLARIS26
    LOGG(LOG_DEBUG, "sensesent %d rqresid %d", SENSESENT(dsr), dsr->ucmd.uscsi_rqresid);
#endif
    if (SENSESENT(dsr) > 0 && sd.valid == 1 &&
	((sd.senseKey == 0 && sd.secSenseCode == 0 && sd.secSenseQual == 2) ||
	 (sd.EOM == 1 && sd.valid == 1) ||
	 (sd.senseKey == 0xd) )) {
      LOGG(LOG_ERR, "WriteEagle: EOM condition @ %s", devn(fd));
#ifdef OSM
      osmerrno = ENOSPACE;  /* mark end of media with ENNOSPACE */
#endif
      errno = ENOSPC;
    } else {
      LOGG(LOG_ERR, "WriteEagle: write error @ %s", devn(fd));
#ifdef OSM
      osmerrno = EIOWRITE;
#endif
      errno = EIO;
    }
    return(-1);
  } else { 
    if (n != DATASENT(dsr)) {
      LOGG(LOG_ERR, "WriteEagle: incorrect byte-count %d should be %d @ %s",
	     DATASENT(dsr), n, devn(fd));
      errno = EIO;
#ifdef OSM
      osmerrno = EIOWRITE;
#endif
      return(-1);
    }
  }
  return(n);
}

int ReadEagle(int fd, void *buf, unsigned int n) {
  int ret;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);
#if 0
  LOGG ( LOG_DEBUG , "ReadEagle(%s) buff 0x%X len: %u",
	 devn(fd), buf, n);
#endif
  if (n <= 0 || !buf || (n & 0xff000000)) {
    errno = EINVAL;
    return(-1);
  }

  fillg0cmd(dsr, CMDBUF(dsr), G0_READ, B1(2), B3(n), B1(0));
  filldsreq(dsr, buf, n, DSRQ_READ);
  if ((ret = doScsi(dsr)) != 0) {
    LOGG(LOG_ERR, "ReadEagle: read error @ %s", devn(fd));
    errno = EIO;
#ifdef OSM
    osmerrno = EIOREAD;
#endif
    return(-1);
  } else {
    /*return((DATASENT(dsr) != n) ? -1 : n);*/
    return(DATASENT(dsr));
  }
}

/* return human readable string of device specific location string */
char *LocationStringEagle(const char *loc) {
  static char s[100];
  unsigned long pos;

  if (loc2binEagle(loc, &pos) != 0) {
    (void) sprintf(s, "invalid Eagle location '%s'", loc);
  } else {
    (void) sprintf(s, "SCSI-BLOCK: %X", pos);
  }
  return(s);
}

int LocateEagle(int fd, char *ptr) {
  unsigned long pos;
  struct dsreq *dsr = DSR(fd);
  int ret;

  if (!dsr)
    return(-1);

  if (loc2binEagle(ptr, &pos) != 0) {
    LOGG(LOG_ERR, "LocateEagle: loc2bin(%s) invalid position string '%s'",
	   devn(fd), ptr);
    return(-1);
  }

  LOGG(LOG_DEBUG, "LocateEagle: start locate %s to '%s' -> %X",
	 devn(fd), ptr, pos);

  fillg1cmd(dsr, CMDBUF(dsr), G1_LOCA, B1(0), 0, B4(pos), 0, 0, 0);
  /* use SCSI logical block address - device specific not supported */
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "LocateEagle to %X (%u) failed @ %s", pos, pos, devn(fd));
#ifdef OSM
    osmerrno = EMTSETPOS;
#endif
    return(-1);
  }
  LOGG(LOG_DEBUG, "LocateEagle: position '%s' to (%X) - done",
	 devn(fd), pos);
  return(0);
}

#if 0
/*
 * make fdIs the same as fdShould with a lot of dups
*/
static int dupToSame(int fdIs, int fdShould) {
  int fd = -1, i, j;
  int *fds;

  if (fdIs == fdShould)
    return(0);   /* already done */

  if ((fds = (int *) malloc((fdIs + fdShould) * sizeof(int))) == NULL) {
    LOGG(LOG_ERRD, "dup(%s) out of memory", devn(fdIs));
    return(-1);
  }
  for(i=0; i<(fdIs + fdShould); i++)
    fds[i] = -1;

  LOGG(LOG_DEBUG, "dupToSame: start for %d %d", fdIs, fdShould);
  i = 0;
  while(666) {
    if ((fds[i] = dup(fdIs)) < 0) {
      LOGG(LOG_ERRD, "dup(%s) failed %d (%s)", devn(fdIs),
	     errno, strerror(errno));
      break;
    }
    LOGG(LOG_DEBUG, "dupToSame: duped %d to %d", fdIs, fds[i]);
    if (fds[i] > fdShould) {
      LOGG(LOG_ERRD, "dup(%s): target FD too high (%d, %d)",
	     devn(fdIs), fdIs, fdShould);
      break;
    }
    if (fds[i] == fdShould) {  /* got it */
      char devname[100];

      strcpy(devname, devn(fdIs));
      (void) CloseEagle(fdIs);
      fd = fds[i];
      fds[i] = -1;
      /* arrange devname structure */
      if (fd < MAXNFD)
	fd_mappings[fd] = strdup(devname);
      LOGG(LOG_DEBUG, "dupToSame: duped %d successfuly to %d", fdIs, fd);
      break;
    }
    i++;
  }
  for(j=0; j<(fdIs + fdShould); j++) {
    if (fds[j] >= 0)
      (void) close(fds[j]);
  }
  (void) free(fds);
  return((fd >= 0) ? 0 : -1);
}
#endif

int ResetEagle(int fd) {
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);
  return(Reset(dsr));
}

int RewindEagle(int fd) {
  int ret;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);

  fillg0cmd(dsr, CMDBUF(dsr), G0_REWI, B1(0), 0, 0, 0, 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "REWIND: RewindEagle(%s) failed", devn(fd));
    return(-1);
  }
  return(0);
}

int UnloadEagle(int fd) {
  int ret;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);

  fillg0cmd(dsr, CMDBUF(dsr), G0_LOAD, 0, 0, 0, 0, 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "UnloadEagle(%s) failed", devn(fd));
    return(-1);
  }
  return(0);
}

/*
 * NOTE: ptr must be allocated with at least BFLOCLEN(16) chars */
int ReadPositionEagle(int fd, char *ptr) {
  Pos pos;
  int ret, i;
  struct dsreq *dsr = DSR(fd);
  int bytesIn = 0;
  int blocksIn = 0;
  unsigned int fb, lb;

  if (!dsr)
    return(-1);

#ifdef OSM
  osmerrno = EMTGETPOS;
#endif
  fillg1cmd(dsr, CMDBUF(dsr), G1_RPOS, B1(0), 0, 0, 0, 0, 0, 0, 0, 0);
  /* read SCSI logical block address - device specific not supported */
  filldsreq(dsr, (caddr_t) &pos, sizeof(Pos), DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "ReadPosition failed @ %s", devn(fd));
    return(-1);
  }
  if (pos.BPU) {
    LOGG(LOG_ERR, "BPU bit on !!! @ %s", devn(fd));
    return(-1);
  }

  blocksIn = pos.nbufb[2] << 16;
  blocksIn |= pos.nbufb[1] << 8;
  blocksIn |= pos.nbufb[0];
  bytesIn = pos.nbytb[2] << 16;
  bytesIn |= pos.nbytb[1] << 8;
  bytesIn |= pos.nbytb[0];

  memcpy((char *) &fb, &pos.fb[0], 4);
  memcpy((char *) &lb, &pos.lb[0], 4);

  if (bytesIn == 0 && blocksIn == 0 && fb == lb) {
    if (loc2strEagle(lb, ptr) != 0) {
      LOGG(LOG_ERR, "loc2str() failed @ %s for %u", devn(fd), lb);
      return(-1);
    }
    LOGG(LOG_DEBUG, "ReadPosition(%s) %u -> '%s' done", devn(fd), lb, ptr);

#ifdef OSM
    osmerrno = OSMOK;
#endif
    return(0);
  } else {
    LOGG(LOG_ERR, "buffered data: %d %d %u %u @ %s", bytesIn,
	 blocksIn, fb, lb, devn(fd));
    return(-1);
  }
}

int UnitReadyEagle(int fd, int timeout) {
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);
  while(666) {
    if (testunitready(dsr) == 0) {
      return(0);
    } else {
      if (timeout-- <= 0)
	return(-1);
      sleep(1);
    }
  }
}

int testunitready(struct dsreq *dsr) {
  int ret;

  fillg0cmd(dsr, CMDBUF(dsr), G0_TEST, 0, 0, 0, 0, 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);

  quiet = True;
  ret = doScsi(dsr);
  quiet = False;

  return(ret);
}

int CloseEagle(int fd) {
  int ret;
  struct dsreq *dsr = DSR(fd);
  /* gather statistics here + evaluation of that */

  if (!dsr)
    return(-1);

#ifdef IRIX
  dsclose(dsr);
#endif
#ifdef SOLARIS26
  (void) close(fd);
  (void) free(dsr);
#endif

  if (fd < MAXNFD && fd_mappings[fd]) {
    free(fd_mappings[fd]);
    fd_mappings[fd] = (char *) 0;
  }
  if (dsrGlobal[fd]) {
    dsrGlobal[fd] = (struct dsreq *) 0;
  }

  return(0);
}

int WriteFileMarkEagle(int fd, int nfmk) {
  int ret;
  int serrno = -1;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);
  
  fillg0cmd(dsr, CMDBUF(dsr), G0_WF, 0, B3(nfmk), 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "WriteFileMarkEagle failed (count: %d) @ %s",
	 nfmk, devn(fd));
    if (SENSESENT(dsr) > 0 && sd.valid == 1 &&
	((sd.senseKey == 0 && sd.secSenseCode == 0 && sd.secSenseQual == 2) ||
	 (sd.EOM == 1 && sd.valid == 1) ||
	 (sd.senseKey == 0xd) )) {
      LOGG(LOG_ERR, "WriteFileMarkEagle: EOM condition @ %s", devn(fd));
#ifdef OSM
      osmerrno = ENOSPACE;  /* mark end of media with ENNOSPACE */
#endif
      errno = ENOSPC;
    } else {
      errno = EIO;
#ifdef OSM
      osmerrno = EIOWRITE;
#endif
    }
    return(-1);
  }
  return(0);
}

int FlushBufferEagle(int fd) {
  return(WriteFileMarkEagle(fd, 0));
}

int SpaceEagle(int fd, char w, int n) {
  int type, ret;
  struct dsreq *dsr = DSR(fd);

  if (!dsr)
    return(-1);

  if (n == 0)
    return(0);

  switch(w) {
  case 'b':
    type = 0;
    break;
  case 'f':
    type = 1;
    break;
  case 's':
    type = 3;
    break;
  default:
    LOGG(LOG_ERR, "SPACE: unknown command '%c' for %s", w, devn(fd));
    return(-1);
  }

  fillg0cmd(dsr, CMDBUF(dsr), G0_SPAC, B1(type), B3(n), 0);
  filldsreq(dsr, 0, 0, DSRQ_READ);
  ret = doScsi(dsr);
  if (ret != 0) {
    LOGG(LOG_ERR, "SpaceEagle failed @ %s for %c:%d", devn(fd), w, n);
#ifdef OSM
    osmerrno = EMTSETPOS;
#endif
    return(-1);
  }
  return(0);
}

/* return capacity in KB or zero -> don't change capacity */
int RemainingCapacityEagle(int fd) {
#if 1
  return(0);
#else
  return(-1);
#endif
}

int VerifyVolumeIDEagle(int fd, char *vid) {
  return(0);
}

/* zero means after label */
int PositionEOREagle(int fd, char *p) {
  char *aptr, *fptr, *sep, sp[100];
  unsigned long pos;

  strncpy(sp, p, 99);
#ifdef ES
  if (sp[0] == '-') {
    pos = 0;
  } else {
#endif
    if (loc2binEagle(sp, &pos) != 0) {
      LOGG(LOG_ERR, "illegal position string '%s'", p);
      return(-1);
    }
#ifdef ES
  }
#endif

  LOGG(LOG_DEBUG, "PositionEOREagle: '%s' -> 0x%X @ %s", p, pos, devn(fd));

  if (pos == 0) {  /* after label */
    LOGG(LOG_DEBUG, "PositionEOREagle: position after label @ %s", devn(fd));
    if (RewindEagle(fd) == 0 &&
	SpaceEagle(fd, 'f', 1) == 0) {
      return(0);
    }
#ifdef OSM
    osmerrno = EMTSETPOS;
#endif
    return(-1);
  } else {  /* got the FSN */
    return(LocateEagle(fd, p));
  }
}

/*
 * get RCF and PCF traces and put them together in provided buffer */
int GetTracesEagle(int fd, char *buffer, int *bufsize) {
  *bufsize = 0;
  return(0);
}

#ifdef ES
int GetPosition(int fd, char *locString, int locStringLen) {
  return(ReadPositionEagle(fd, locString));
} 
  
int BytesOnMedia(int fd, unsigned64 *bytes) {
  unsigned int rb, wb;

  if (BytesAccessOnMediaEagle(fd, &rb, &wb) == 0) {
    *bytes = (unsigned64) (rb > 0) ? rb : wb;
    return(0);
  }
  return(-1);
}

int PrintablePosition(char *locString,
                      char *printablePosition,
                      int pPLen) {
  char *s = LocationStringEagle(locString);
  strncpy(printablePosition, s, pPLen);
  return(0);
}

int Ready(fd) { return(testunitready(DSR(fd))); }

int Init(char *devName, char *hwInfo) {
  if (devName) {
    struct stat st;
    if (stat(devName, &st) != 0)
      return(1);
  }
  strcpy(hwInfo, "STK-9840");
  return(0);
}

int Load(int fd) { return(0); }

int RemainingCapacity(int fd, unsigned64 *rCapacity) {
  return(1);  /* on eagle we can't find out */
}

#endif
