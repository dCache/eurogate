/**
 * main include for EuroStore HSM API function
 *
 * @author M. Gasthuber/DESY
 * @version 0.1  8 May 98
 * copyright by DESY/Hamburg
 *
 */

#ifndef _ES_api_h_
#define _ES_api_h_

#include <ES_constants.h>

/* types */

typedef unsigned long unsigned64;  /* machines unsigned 64 bit integer */
typedef void *ES_session;          /* opaque reference */

typedef struct es_gather {
  unsigned64 offset;
  unsigned64 nbytes;
  struct es_gather *next;
} ES_rgatherlist;
/*
 * commnent on the gather list for the read call
 * it's put in here for completeness but the initial version will NOT
 * support 'partial reads' so the 'off_len' member will be ignored by the
 * current API implementation.
 */
typedef struct es_rlist {
  int result;
  char errString[ES_ERRSTRLEN];
  char bfid[ES_BFID_STRSIZE];  /* next two filled by caller */
  unsigned int requestId;
  unsigned64 bytesRead;
  unsigned64 size;

  ES_rgatherlist *off_len;
  char userTag[ES_TAGLEN];
} ES_readlist;

typedef struct es_wlist {
  int result;
  char errString[ES_ERRSTRLEN];
  char bfid[ES_BFID_STRSIZE];
  unsigned int requestId;
  unsigned64 bytesWritten;
  unsigned64 size;

  char storageGroup[ES_SGROUP_LEN];
  char migrationPath[ES_MIGPATH_LEN];
  char userTag[ES_TAGLEN];

} ES_writelist;

typedef struct es_remlist {
  int result;
  char errString[ES_ERRSTRLEN];
  char bfid[ES_BFID_STRSIZE];
  unsigned int requestId;
} ES_removelist;

typedef int (*ES_Write_callback)(int op, void *buf, int len,
				 ES_writelist *current, void *userPtr);

typedef int (*ES_Read_callback)(int op, void *buf, int len,
				ES_readlist *current, void *userPtr);

typedef int (*ES_Remove_callback)(int op,
				  ES_removelist *current, void *userPtr);

/* prototypes in ANSI notation */

/* mandantory call to create the session (security stuff & requested privs.) */
ES_session ES_CreateSession(const char *Store,
			    const void *Key,
			    const int Privs,
			    const char *DoorHostName,
			    const int DoorHostPort,
			    int *es_errno,
			    char *errString);


void ES_CloseSession(ES_session session);

/* read dataset */
int ES_ReadData(ES_session session,
		ES_readlist *list,
		int listLen,
		ES_Read_callback callback,
		void *userPtr,
		char *hostName,
		int timeout,
		char *errString);

/* write datasets */
int ES_WriteData(ES_session session,
		 ES_writelist *list,
		 int listLen,
		 ES_Write_callback callback,
		 void *userPtr,
		 char *hostName,
		 int timeout,
		 char *errString);

/* mandantory final procedure -:) */
int ES_Remove(ES_session session,
	      ES_removelist *list,
	      int listLen,
	      ES_Remove_callback callback,
	      void *userPtr,
	      char *errString);



#endif
