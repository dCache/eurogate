#ifndef _es_pvr_h_
#define _es_pvr_h_

/*
 * some definitions used withing both (lib dependent and undependent) C codes
 * DESY -mg
 */

/* severity level for logging - thru the Java PVL proxy */
#define ESPVR_INFO 1
#define ESPVR_WARNING 2
#define ESPVR_FATAL 3

/*
 * define drive states returned by NewDrive call
 */
#define DRV_UNAVAILABLE 0
#define DRV_AVAILABLE_EMPTY 1
#define DRV_AVAILABLE_FILLED_UNKNOWN 2
#define DRV_AVAILABLE_FILLED_KNOWN 3


/* exit status of child which no done message sent by parent (silent death) */
#define SILENT_DEATH 200

/* prototype for the logging function */
extern void LOG(int severityLevel, char *format, ...);

#endif
