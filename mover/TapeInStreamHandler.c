
#include <eurogate_mover_TapeInputStream.h>
#include <eagle.h>
#ifndef __STDC__
#include <varargs.h>
#else
#include <stdarg.h>
#endif

static JNIEnv *envi = 0;
static jobject obji = 0;

/*
 * Class:     es_mover_TapeInputStream
 * Method:    _init
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1init
  (JNIEnv *env, jobject obj, jstring deviceName) {
  int retCode = 0;
  jboolean isCopy0 = JNI_FALSE ;
  jboolean isCopy1 = JNI_FALSE ;
  jclass cls = (*env)->GetObjectClass(env, obj);
  jfieldID fid;
  jstring jstr;
  jboolean isCopy = JNI_FALSE;
  const char *str;
  const char *devName = (*env)->GetStringUTFChars(env,  deviceName, &isCopy0);
    

  fid = (*env)->GetFieldID(env, cls, "initString", "Ljava/lang/String;");
  if (fid == 0) {
    return;
  }
  jstr = (*env)->GetObjectField(env, obj, fid);
  str = (*env)->GetStringUTFChars(env, jstr, &isCopy1);
  /*printf("  c.s = \"%s\"\n", str);*/

  envi = env;
  obji = obj;
  
  retCode = Init((char *)devName, (char *)str );
  
  jstr = (*env)->NewStringUTF(env, str);
  (*env)->SetObjectField(env, obj, fid, jstr);

  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 devName      */
  if (isCopy0 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, deviceName, devName);
  if (isCopy1 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, jstr, str);

  return retCode;
}


/*
 * Class:     es_mover_TapeInputStream
 * Method:    open
 * Signature: (Ljava/lang/String;J)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1open
  (JNIEnv *env, jobject obj, jstring deviceName, jint mode) {
  int retCode = 0;
  jboolean isCopy = JNI_FALSE ;
  const char *devName = (*env)->GetStringUTFChars(env,  deviceName, &isCopy);
    
  envi = env;
  obji = obj;
  
  retCode = Open(devName, mode );
  
  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 devName      */
  if (isCopy == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, deviceName, devName);

  return retCode;
}


/*
 * Class:     es_mover_TapeInputStream
 * Method:    openReady
 * Signature: (Ljava/lang/String;JI)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1openReady
  (JNIEnv *env, jobject obj, jstring deviceName, jint mode, jint timeOut) {
  int retCode = 0;
  jboolean isCopy = JNI_FALSE;
  const char *devName = (*env)->GetStringUTFChars(env,  deviceName, &isCopy);
  
  envi = env;
  obji = obj;
  
  retCode = OpenReady(devName, mode, timeOut );
  
  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 devName      */
  if (isCopy == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, deviceName, devName);

  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    close
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1close
  (JNIEnv *env, jobject obj, jint fileDescr) {
  int retCode = 0;
  
  retCode = Close( fileDescr );
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    position
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1position
  (JNIEnv *env, jobject obj, jint fileDescr, jstring locString) {
  int retCode = 0;
  jboolean isCopy = JNI_FALSE;
  const char *locStr = (*env)->GetStringUTFChars(env,  locString, &isCopy);

  envi = env;
  obji = obj;
  
  retCode = Position( fileDescr, (char *)locStr );
  
  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 locString    */
  if (isCopy == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, locString, locStr);

  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    positionEOR
 * Signature: (ILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1positionEOR
  (JNIEnv *env, jobject obj, jint fileDescr, jstring locString) {
  int retCode = 0;
  jboolean isCopy = JNI_FALSE;
  const char *locStr = (*env)->GetStringUTFChars(env, locString, &isCopy);
  
  envi = env;
  obji = obj;
  
  retCode = Position( fileDescr, (char *)locStr );
  
  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 locString    */
  if (isCopy == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, locString, locStr);
    
  return retCode;
}


JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1display
  (JNIEnv *env, jobject obj, jint fileDescr, jstring m1, jstring m2) {
  int retCode = 0;
  jboolean isCopy1 = JNI_FALSE;
  jboolean isCopy2 = JNI_FALSE;
  const char *_m1 = (*env)->GetStringUTFChars(env, m1, &isCopy1);
  const char *_m2 = (*env)->GetStringUTFChars(env, m2, &isCopy2);
  
  envi = env;
  obji = obj;
  
  Display( fileDescr, _m1, _m2 );
  
  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 locString    */
  if (isCopy1 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, m1, _m1);
  if (isCopy2 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, m2, _m2);
    
  return(0);
}


/*
 * Class:     es_mover_TapeInputStream
 * Method:    getPosition
 * Signature: (ILjava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1getPosition
  (JNIEnv *env, jobject obj, jint fileDescr, jint locStringLength) {
  int retCode = 0;
  jclass cls = (*env)->GetObjectClass(env, obj);
  jfieldID fid;
  jstring jstr;
  jboolean isCopy = JNI_FALSE;
  const char *str;

  envi = env;
  obji = obj;
  
  fid = (*env)->GetFieldID(env, cls, "locationString", "Ljava/lang/String;");
  if (fid == 0) {
    return;
  }
  jstr = (*env)->GetObjectField(env, obj, fid);
  str = (*env)->GetStringUTFChars(env, jstr, &isCopy);

  retCode = GetPosition(fileDescr, (char *)str, locStringLength);
  
  jstr = (*env)->NewStringUTF(env, str);
  (*env)->SetObjectField(env, obj, fid, jstr);

  /* If additional memory was allocated beyond the java String-object, inform */
  /* the JVM that native code no longer needs access to the UTF8 String str   */
  if (isCopy == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, jstr, str);

  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    rewind
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1rewind
  (JNIEnv *env, jobject obj, jint fileDescr) {
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = Rewind( fileDescr );
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    unload
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1unload
  (JNIEnv *env, jobject obj, jint fileDescr) {
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = Unload( fileDescr );
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    load
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1load
  (JNIEnv *env, jobject obj, jint fileDescr) {
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = Load( fileDescr );
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    ready
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1ready
  (JNIEnv *env, jobject obj, jint fileDescr) {
  int retCode = 0;

  envi = env;
  obji = obj;
  
  retCode = Ready( fileDescr );
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    printablePosition
 * Signature: (Ljava/lang/String;Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1printablePosition
  (JNIEnv *env, jobject obj, jstring locString, /*, jstring printablePos,*/ jint printPosLen) {
  int retCode = 0;
  jclass cls = (*env)->GetObjectClass(env, obj);
  jfieldID fid;
  jstring jstr;
  jboolean isCopy0 = JNI_FALSE;
  jboolean isCopy1 = JNI_FALSE;
  const char *str;
  const char *locaString = (*env)->GetStringUTFChars(env,  locString, &isCopy0);
  
  envi = env;
  obji = obj;
  
  fid = (*env)->GetFieldID(env, cls, "printablePosition",
			   "Ljava/lang/String;");
  if (fid == 0) {
    return;
  }
  jstr = (*env)->GetObjectField(env, obj, fid);
  str = (*env)->GetStringUTFChars(env, jstr, &isCopy1);

  retCode = PrintablePosition((char *) locaString,
			   (char *)str, printPosLen);
  
  jstr = (*env)->NewStringUTF(env, str);
  (*env)->SetObjectField(env, obj, fid, jstr);

  /* If additional memory was allocated beyond the java String-object, inform the */
  /* JVM that native code no longer needs access to the UTF8 str and locaString   */
  if (isCopy0 == JNI_TRUE)   
    (*env)->ReleaseStringUTFChars(env, locString, locaString);
    
  if (isCopy1 == JNI_TRUE)
    (*env)->ReleaseStringUTFChars(env, jstr, str);

  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    _read
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1read
  (JNIEnv *env, jobject obj, jint fileDescr, jbyteArray buffer,
   jint start, jint length) {
  
  int retCode = 0;
  jboolean isCopy = JNI_FALSE;
  
  jint arraySize = (*env)->GetArrayLength(env, buffer);
   
#if 0
  jbyte* byteArrayElements = (*env)->GetByteArrayElements(env, buffer, &isCopy);
#else
  jbyte *byteArrayElements = (*env)->GetPrimitiveArrayCritical(
                                                              env,
                                                              buffer,
                                                              &isCopy);
  if (byteArrayElements == NULL) {
    LOG(ESMVR_FATAL,"out of memory (GetPrimitiveArrayCritical())");
    return(-1);
  }
#endif

  envi = env;
  obji = obj;

  retCode = Read(fileDescr, &byteArrayElements[start], length);
  
  if (isCopy == JNI_TRUE) {
    LOG(ESMVR_INFO,"data where copied into a buffer");
#if 0
    (*env)->ReleaseByteArrayElements(env, buffer, byteArrayElements, 0);
#else
    (*env)->ReleasePrimitiveArrayCritical(env, buffer, byteArrayElements, 0);
#endif
  }          
  return(retCode);
}



#if 0
/*
 * Class:     es_mover_TapeInputStream
 * Method:    _write
 * Signature: (I[BI)I
 */
JNIEXPORT jint JNICALL Java_es_mover_TapeInputStream__1write
  (JNIEnv *env, jobject obj, jint fileDescr, jbyteArray buffer, jint start, jint length) {
  
  int retCode = 0;
  /*
  int i = 0;
  jclass clazz1;
  jfieldID fid1;
  jfieldID fid2;
  */
  jboolean isCopy = JNI_FALSE;
  
  jbyte* byteArrayElemens = (*env)->GetByteArrayElements(env, buffer, &isCopy);
  jint arraySize = (*env)->GetArrayLength(env, buffer);
  
  envi = env;
  obji = obj;
  
  retCode = write(fileDescr, &byteArrayElemens[start], length);
  
  /*
  for ( i = 0 ; i < arraySize; i++)
    printf("%d: ", byteArrayElemens[i]);   
  printf("\n");
  */
    
  if (isCopy == JNI_TRUE) {
    /* printf("data where copied into a buffer. isCopy = %d\n", isCopy); */
    (*env)->ReleaseByteArrayElements(env, buffer, byteArrayElemens, 0);
  }
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    writeFilemark
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_es_mover_TapeInputStream__1writeFilemark
  (JNIEnv *env, jobject obj, jint fileDescr, jint nFileMarks) {
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = WriteFilemark( fileDescr, nFileMarks );
  
  return retCode;
}
#endif


/*
 * Class:     es_mover_TapeInputStream
 * Method:    bytesOnMedia
 * Signature: (IJ)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1bytesOnMedia
  (JNIEnv *env, jobject obj, jint fileDescr /*, jlong bytesOnMedia*/) {
  
  unsigned64 mediaBytes = 0;
  jfieldID fid;
  jclass clazz;
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = BytesOnMedia( fileDescr, &mediaBytes );
  
  clazz = (*env)->GetObjectClass(env, obj);
  fid = (*env)->GetFieldID(env, clazz, "bytesOnMedia", "J");
  (*env)->SetLongField(env, obj, fid, mediaBytes);
  
  return retCode;
}



/*
 * Class:     es_mover_TapeInputStream
 * Method:    remainingCapacity
 * Signature: (IJ)I
 */
JNIEXPORT jint JNICALL Java_eurogate_mover_TapeInputStream__1remainingCapacity
  (JNIEnv *env, jobject obj, jint fileDescr ) {
  
  unsigned64 remainCapacity = 0;
  jfieldID fid;
  jclass clazz;
  int retCode = 0;
  
  envi = env;
  obji = obj;
  
  retCode = RemainingCapacity( fileDescr, &remainCapacity );
  
  clazz = (*env)->GetObjectClass(env, obj);
  fid = (*env)->GetFieldID(env, clazz, "remainingCapacity", "J");
  (*env)->SetLongField(env, obj, fid, remainCapacity);
  
  return retCode;
}




/*
 * Class:     es_mover_TapeInputStream
 * Method:    LOG
 * called by eagle c-functions, calls the java method logReport.
 */


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
  int i;
  jclass cls = (*envi)->GetObjectClass(envi, obji);
  jmethodID mid = (*envi)->GetMethodID(envi, cls, "logReport",
				       "(ILjava/lang/String;)V");
  if (mid == 0) {
    /*fprintf(stderr, "mid == 0\n");*/
    return;
  }

#ifndef __STDC__
  va_start(args);
#else
  va_start(args, fmt);
#endif

#if 0
    (void) sprintf(buf, "log %d \"", level);
    ptr = &buf[strlen(buf)];
    (void) vsprintf(ptr, fmt, args);
#else
    (void) vsprintf(buf, fmt, args);
    len = strlen(buf);
    if (buf[len - 1] == '\n')
      buf[(len--) - 1] = '\0';
#endif
#if 0
    buf[len++] = '"'; buf[len] = '\0';
#endif
    
  va_end(args);

  (*envi)->CallVoidMethod(envi, obji, mid, (jint) level,
			  (*envi)->NewStringUTF(envi, buf));
}
