#=========================================================================
#
#         needful things
#
#
getFull() {

   echo $1 | \
   awk '{
       if( substr($1,0,1) == "/" ){
           print $1 ;
       }else{
           printf "%s/%s\n",this,$1 ;
       } ; }' this=$PWD - 2>/dev/null
   return $?
}
checkVar() {
  while [ $# -ne 0 ] ; do
    z="echo \${$1}"
    x=`( eval  $z )`
    if [ -z "$x" ] ; then
      echo "Fatal : Variable not defined in $setupFile : $1" 2>1
      return 1
    fi
    shift
  done
}
checkJava() {
   $JAVA -version 1>/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
     echo "Fatal : can't find java runtime" 2>&1
     return 7
   fi
   checkVar cellClasses || exit 4
   export CLASSPATH
   CLASSPATH=$cellClasses:$eurogateHome/../eurogate.jar:$eurogateHome/../..
#   CLASSPATH=$cellClasses:$eurogateHome/../eurogate.jar:$eurogateHome/../..
   #
   # we have to check if we are able to find all classes
   # we need. This is somehow nasty because Linux java
   # returns a $?=0 even if it can't find all the classes.
   #
   err=`$JAVA eurogate.misc.Version 2>&1 1>/dev/null`
   if [ "$err" = "Cells not found" ] ; then
       echo "Cell classes not found in $CLASSPATH" 1>&2
       exit 4
   elif [ ! -z "$err" ] ; then
       echo "Eurogate classes not found in $CLASSPATH" 1>&2
       exit 5
   fi
   return 0 ;
}
javaClasspath() {
   $JAVA -version 1>/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
     echo "Fatal : can't find java runtime" 2>&1
     return 7
   fi
   checkVar cellClasses || exit 4
   export CLASSPATH
   x1=`getFull $cellClasses`
   x2=`getFull $eurogateHome/../eurogate.jar`
   x3=`getFull $eurogateHome/../..`
   CLASSPATH=$x1:$x2:$x3
   #
   # we have to check if we are able to find all classes
   # we need. This is somehow nasty because Linux java
   # returns a $?=0 even if it can't find all the classes.
   #
   err=`$JAVA eurogate.misc.Version 2>&1 1>/dev/null`
   if [ "$err" = "Cells not found" ] ; then
       echo "Cell classes not found in $CLASSPATH" 1>&2
       exit 4
   elif [ ! -z "$err" ] ; then
       echo "Eurogate classes not found in $CLASSPATH" 1>&2
       exit 5
   fi
   echo $CLASSPATH
   return 0 ;
}
#
checkSsh() {
#   checkVar sshPort || exit 4
   which $SSH >/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
     echo "Please add 'ssh (versionI)' to your PATH"
     return 4
   fi
   return 0
}
#
killproc() {            # kill the named process(es)
        pid=`/usr/bin/ps -e |
             /usr/bin/grep $1 |
             /usr/bin/sed -e 's/^  *//' -e 's/ .*//'`
        [ "$pid" != "" ] && kill $pid
}
getpids() {            # kill the named process(es)
        pid=`/usr/bin/ps -e |
             /usr/bin/grep $1 |
             /usr/bin/sed -e 's/^  *//' -e 's/ .*//'`
        echo  "$pid"
        return 0
}
