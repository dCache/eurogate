#include <eurogate_mover_cpio.h>

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

/*
 * implementation of CPIO stuff -mg DESY
 */

/*
 * Archive file status mode bit definitions.
 */
#define AM_IFMT         0170000 /* File type mask */
#define AM_IFMT_S       12      /* File type shift */
#define AM_IFDIR        0040000 /* Directory */
#define AM_IFCHR        0020000 /* Character special */
#define AM_IFBLK        0060000 /* Block special */
#define AM_IFREG        0100000 /* Regular file */
#define AM_IFIFO        0010000 /* Named pipe (fifo) */
#define AM_IFLNK        0120000 /* Symbolic link */

#define M_STRLEN 6              /* Length of ASCII magic number */
#define M_ASCII "070707"        /* ASCII magic number */
#define H_STRLEN 70             /* ASCII header string length */

#define TRAILER "TRAILER!!!"    /* Archive trailer (cpio compatible) */
#define TRAILZ  11              /* Trailer pathname length (including null) */

#define padding(number, bound)  \
        ((number) == 0 ? 0 : ((bound) - 1) - (((number) - 1) % (bound)))


/* create header return size */
JNIEXPORT jint JNICALL Java_eurogate_mover_cpio_createHeader(JNIEnv *env,
							     jobject obj,
							     jbyteArray buf) {
  jint ret = -1;
  jboolean isCopy0 = JNI_FALSE;
  jboolean isCopy1 = JNI_FALSE;
  /*jboolean isCopy2 = JNI_FALSE;*/
  jclass class = (*env)->GetObjectClass(env, obj);
  jfieldID fid_uid = (*env)->GetFieldID(env, class, "_uid", "I");
  jfieldID fid_gid = (*env)->GetFieldID(env, class, "_gid", "I");
  jfieldID fid_size = (*env)->GetFieldID(env, class, "_fileSize", "J");
  jfieldID fid_fileName = (*env)->GetFieldID(env, class,
					     "_fileName1",
					     "Ljava/lang/String;");
  jstring j_fileName = (*env)->GetObjectField(env, obj, fid_fileName);

  /* get buffer address */
  jbyte *buff = (*env)->GetByteArrayElements(env, buf, &isCopy0);
  /* get _fileName0 string */
  const char *fName = (*env)->GetStringUTFChars(env, j_fileName, &isCopy1);
  /* get filesize */
  jlong fSize = (*env)->GetIntField(env, obj, fid_size);
  /* get uid and gid */
  jint uid = (*env)->GetIntField(env, obj, fid_uid);
  jint gid = (*env)->GetIntField(env, obj, fid_gid);

  int namesize = strlen(fName) + 1;
  /* now we can start :-) */
  /* write cpio header into memory */

  /* NOTE we always assume that buff size is greater than header-size !!!! */

  ret = M_STRLEN + H_STRLEN + namesize;
  memset(buff, 0, ret + 1);
  (void) sprintf((char *) buff,
                 "%s%06o%06o%06o%06o%06o%06o%06o%011lo%06o%011llo%s",
                 M_ASCII,
		 /*_cp.dev*/ 0,
		 /*_cp.ino*/ 1, 
		 /*_cp.mode*/ 0644 | AM_IFREG,
		 /*_cp.uid*/ uid,
                 /*_cp.gid*/ gid,
		 /*_cp.nlink*/ 1,
		 /*_cp.rdev*/ 0,
		 /*_cp.mtime*/ (unsigned long) time((time_t *) 0),
                 /*_cp.namesize*/ namesize,
		 /*_cp.filesize*/ fSize,
		 /*_cp.bfid*/ fName);

  if (isCopy0 == JNI_TRUE)
    (*env)->ReleaseByteArrayElements(env, buf, buff, 0);
  if (isCopy1 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, j_fileName, fName);

  return(ret);
}

/* create trailer return size */
JNIEXPORT jint JNICALL Java_eurogate_mover_cpio_createTrailer
  (JNIEnv *env, jobject obj, jbyteArray buf) {
  jint ret = -1;
  jboolean isCopy0 = JNI_FALSE;
  jboolean isCopy1 = JNI_FALSE;
  jboolean isCopy2 = JNI_FALSE;
  jboolean isCopy3 = JNI_FALSE;
  jclass class = (*env)->GetObjectClass(env, obj);
  jfieldID fid_uid = (*env)->GetFieldID(env, class, "_uid", "I");
  jfieldID fid_gid = (*env)->GetFieldID(env, class, "_gid", "I");
  jfieldID fid_PathName = (*env)->GetFieldID(env, class,
					     "_fileName2",
					     "Ljava/lang/String;");
  jfieldID fid_FSInfo = (*env)->GetFieldID(env, class,
					   "_fileName3",
					   "Ljava/lang/String;");
  jfieldID fid_fileName = (*env)->GetFieldID(env, class,
					     "_fileName1",
					     "Ljava/lang/String;");

  jstring j_fileName = (*env)->GetObjectField(env, obj, fid_fileName);
  jstring j_pathName = (*env)->GetObjectField(env, obj, fid_PathName);
  jstring j_fsInfo = (*env)->GetObjectField(env, obj, fid_FSInfo);

  /* get buffer address */
  jbyte *buff = (*env)->GetByteArrayElements(env, buf, &isCopy0);
  /* get _fileName0 string */
  const char *fName = (*env)->GetStringUTFChars(env, j_fileName, &isCopy3);
  /* get _fileName1 string */
  const char *pName = (*env)->GetStringUTFChars(env, j_pathName, &isCopy1);
  /* get _fileName2 string */
  const char *fsInfo = (*env)->GetStringUTFChars(env, j_fsInfo, &isCopy2);
  /* get uid and gid */
  int uid = (*env)->GetIntField(env, obj, fid_uid);
  int gid = (*env)->GetIntField(env, obj, fid_gid);

  int pNameSize = strlen(pName) + 1;
  int fsInfoSize = strlen(fsInfo) + 1;
  int fSize = strlen(fName) + 1;

  int symLinkSize = strlen(fName) + 3;
  int size1 = M_STRLEN + H_STRLEN + pNameSize + symLinkSize;
  int size2 = M_STRLEN + H_STRLEN + fsInfoSize + symLinkSize;
  int size3 = M_STRLEN + H_STRLEN + TRAILZ;

  char *ptr = (char *) buff;

  ret = size1 + size2 + size3;

  memset(ptr, 0, ret + 1);  /* clear buffer area */

  /* NOTE we always assume that buff size is greater than header-size !!!! */

  /* write cpio header for symbolic linked PATHNAME file */

  /* write symlink*/
  (void) sprintf(ptr,
                 "%s%06o%06o%06o%06o%06o%06o%06o%011lo%06o%011lo%s%c../%s",
                 M_ASCII,
		 /*_cp.dev*/ 0,
		 /*_cp.ino*/ 1,
		 /*_cp.mode*/ AM_IFLNK | 0644,
		 /*_cp.uid*/ uid,
                 /*_cp.gid*/ gid,
		 /*_cp.nlink*/ 1,
		 /*_cp.rdev*/ 0,
		 /*_cp.mtime*/ (unsigned long) time((time_t *) 0),
                 /*_cp.namesize*/ pNameSize,
		 /*_cp.filesize*/ symLinkSize,
                 /*_cp.filename*/ pName,
		 '\0',
		 /*_cp.bfid*/ fName);

  ptr += size1;

  (void) sprintf(ptr,
                 "%s%06o%06o%06o%06o%06o%06o%06o%011lo%06o%011lo%s%c../%s",
                 M_ASCII,
		 /*_cp.dev*/ 0,
		 /*_cp.ino*/ 1,
		 /*_cp.mode*/ AM_IFLNK | 0644,
		 /*_cp.uid*/ uid,
                 /*_cp.gid*/ gid,
		 /*_cp.nlink*/ 1,
		 /*_cp.rdev*/ 0,
		 /*_cp.mtime*/ (unsigned long) time((time_t *) 0),
                 /*_cp.namesize*/ fsInfoSize,
		 /*_cp.filesize*/ symLinkSize,
                 /*_cp.filename*/ fsInfo,
		 '\0',
		 /*_cp.bfid*/ fName);

  ptr += size2;

  (void) sprintf(ptr,
                 "%s%06o%06o%06o%06o%06o%06o%06o%011lo%06o%011lo%s",
                 M_ASCII, 0, 0, 0, 0, 0, 1, 0,
		 (unsigned long)0, TRAILZ, 0, TRAILER);

  if (isCopy0 == JNI_TRUE)
    (*env)->ReleaseByteArrayElements(env, buf, buff, 0);
  if (isCopy1 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, j_pathName, pName);
  if (isCopy2 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, j_fsInfo, fsInfo);
  if (isCopy3 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, j_fileName, fName);

  return(ret);
}

/* check header in buffer return size */
JNIEXPORT jint JNICALL Java_eurogate_mover_cpio_checkHeader
  (JNIEnv *env, jobject obj, jbyteArray buf) {

  jint ret = -1;
  jboolean isCopy0 = JNI_FALSE;
  jboolean isCopy1 = JNI_FALSE;
  /*jboolean isCopy2 = JNI_FALSE;*/
  jclass class = (*env)->GetObjectClass(env, obj);
  jfieldID fid_uid = (*env)->GetFieldID(env, class, "_uid", "I");
  jfieldID fid_gid = (*env)->GetFieldID(env, class, "_gid", "I");
  jfieldID fid_size = (*env)->GetFieldID(env, class, "_fileSize", "J");
  jfieldID fid_fileName = (*env)->GetFieldID(env, class,
					     "_fileName1",
					     "Ljava/lang/String;");
  jstring j_fileName = (*env)->GetObjectField(env, obj, fid_fileName);

  /* get buffer address */
  jbyte *buff = (*env)->GetByteArrayElements(env, buf, &isCopy0);
  /* get _fileName0 string */
  const char *fName = (*env)->GetStringUTFChars(env, j_fileName, &isCopy1);
  /* get filesize */
  jlong fSize = (*env)->GetIntField(env, obj, fid_size);
  /* get uid and gid */
  jint uid = (*env)->GetIntField(env, obj, fid_uid);
  jint gid = (*env)->GetIntField(env, obj, fid_gid);

  /* NOTE we always assume that buff size is greater than header-size !!!! */

  char *ptr = (char *) buff;

  ret = -1;

  if (strncmp(ptr, M_ASCII, M_STRLEN) == 0 &&
      strncmp(ptr + (M_STRLEN + H_STRLEN), fName, strlen(fName)) == 0) {
    int dev, ino, mode, uid, gid, nlink, rdev, mtime, namesize;
    jlong filesize;
    (void) sscanf(&ptr[M_STRLEN],
		  "%6lo%6o%6o%6o%6o%6o%6lo%11lo%6o%11llo",
		  &dev, &ino, &mode,
		  &uid, &gid, &nlink,
		  &rdev, &mtime, &namesize, 
		  &filesize);
    if (fSize == filesize && namesize == (strlen(fName) + 1))
      ret = M_STRLEN + H_STRLEN + namesize;
  }

  if (isCopy0 == JNI_TRUE)
    (*env)->ReleaseByteArrayElements(env, buf, buff, 0);
  if (isCopy1 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, j_fileName, fName);

  return(ret);

}

