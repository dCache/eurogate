/**
 *  define API relevant error numbers
 *
 *  @author M.Gasthuber/DESY
 *  @version 0.1  8 May 98
 *  copyright  DESY/Hamburg
 *
 */

#ifndef _ES_errno_h_
#define _ES_errno_h_

#define ES_GENERICERROR -1
#define ES_OK 0
#define ES_BADPARAM 1        /* bad API parameter used */
#define ES_NOMEM 2           /* somebody has no memory... */
#define ES_USERCANCEL 3      /* transfer cancelled by API user (callback) */
#define ES_INVSESSION 4      /* invalid session given */
#define ES_EARLYEOF 5
#define ES_BADTRANSFER 6
#define ES_NOPRIV 7
#define ES_WRITEERR 8
#define ES_SOCKERR 9
#define ES_TCPCONNECT 10
#define ES_TCPIO 11
#define ES_BADHOSTNAME 12
#define ES_BADCMDSTR 13
#define ES_ERRCMD 14
#define ES_TIMEOUT 15
#define ES_BADMOVER 16
#define ES_BADMESSAGE 17
#define ES_ABORT 18
#define ES_NOACK 19
#define ES_FAILEDTRANSFER 20
#define ES_BADCLID 21
#define ES_BADRQID 22
#define ES_BADBFID 23
#define ES_WRONGBYTECOUNT 24
#define ES_CONTINUE 25
#define ES_BADSTATE 26
#define ES_CONNCLOSE 27
#define ES_CONNEOF 28
#define ES_LREADFAILED 29
#define ES_LWRITEFAILED 30

#endif
