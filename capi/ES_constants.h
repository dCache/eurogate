/**
 *
 *  EuroStore HSM constant definition include file
 *
 *  @author  M.Gasthuber/DESY
 *  @version 0.1 11 May 98
 *  copyright by DESY/Hamburg
 *
 */

#ifndef _ES_constants_h_
#define _ES_constants_h_

/* size of objects */
#define ES_BFID_STRSIZE 40  /* length of ascii bfid representation */
#define ES_SGROUP_LEN   30  /* max length of storage-group name */
#define ES_MIGPATH_LEN  30  /* max length of migration path */
#define ES_TAGLEN       30  /* max length of user supplied tag */
#define ES_ERRSTRLEN   128  /* max length of returned error desc. string */

/* privs. */
#define ES_PRIV_READ   0x01
#define ES_PRIV_WRITE  0x02
#define ES_PRIV_MODIFY 0x04

/* callback 'op' 'operations definitions */
#define ES_OP_FILLBUFFER    1
#define ES_OP_FLUSHBUFFER   2
#define ES_OP_NEWDATASET    3
#define ES_OP_IOCOMPLETED   4
#define ES_OP_INIT          5
#define ES_OP_END           6
#define ES_OP_REMOVEDONE    7
#define ES_OP_TRANSFERSTART 8
#define ES_OP_WRITEDATA     9
#define ES_OP_READDATA     10


#endif
