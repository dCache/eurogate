#!/bin/sh
#
#   some constants
#
setupFile=eurogateSetup
SSH=ssh
JAVA=java
LOGFILE=/tmp/eugate.log
cellBatch=desyEurogate.batch
#
os=`uname -s 2>/dev/null` || \
    ( echo "Can't determine OS Type" 1>&2 ; exit 4 ) || exit $?

#
#--------------------------------------------------------------
#
weAreLinux() {
#--------------
   #
   #         get our location 
   #
   PRG=`type -p $0` >/dev/null 2>&1
   while [ -L "$PRG" ]
   do
       newprg=`expr "\`/bin/ls -l "$PRG"\`" : ".*$PRG -> \(.*\)"`
       expr "$newprg" : / >/dev/null || newprg="`dirname $PRG`/$newprg"
       PRG="$newprg"
   done
   #
   eurogateHome=`dirname $PRG`
   weAre=`basename $0`
   return 0
}
#
#--------------------------------------------------------------
#
weAreSolaris() {
#----------------
   PRG="`/usr/bin/which \"$0\"`" >/dev/null 2>&1
   J_HOME="`/usr/bin/dirname \"$PRG\"`"
   progname=`/usr/bin/basename "$0"`

   while [ -h "$PRG" ]; do
       ls=`/usr/bin/ls -ld "$PRG"`
       link=`/usr/bin/expr "$ls" : '^.*-> \(.*\)$'`
       if /usr/bin/expr "$link" : '^/' > /dev/null; then
          prg="$link"
       else
          prg="`/usr/bin/dirname \"$PRG\"`/$link"
       fi
       PRG=`which "$prg"` >/dev/null 2>&1
       J_HOME=`/usr/bin/dirname "$PRG"`
   done           
   eurogateHome=$J_HOME
   weAre=$progname
   return 0
}
if [ "$os" = "Linux" ] ; then
   weAreLinux
elif [ "$os" = "SunOS" ]  ; then
   weAreSolaris
else
   echo "Sorry, no support for $os" 1>&2
   exit 3
fi
#echo "Our Home : $eurogateHome"
#echo "My Name  : $weAre"
#
#   run the setup file
#
if [ -f /usr/etc/$setupFile ] ; then
   setup=/usr/etc/$setupFile
elif [ -f $eurogateHome/$setupFile ] ; then
   setup=$eurogateHome/$setupFile
else
   echo "Fatal : Configuration file not found : $setupFile" 2>&1 
   exit 4
fi
#
. $setup >/dev/null 2>/dev/null
#
#----------------------------------------------------------------------
#
#          We need the full path for the diskCache domain
#
fullSetup=`echo $setup | \
awk '{ 
    if( substr($1,0,1) == "/" ){ 
        print $1 ;
    }else{ 
        printf "%s/%s\n",this,$1 ;
    } ; }' this=$PWD - 2>/dev/null`
#
#=========================================================================
#
#         needful things
#
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
#
checkJava() {
   $JAVA -version 1>/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
     echo "Fatal : can't find java runtime" 2>&1
     return 7
   fi
   checkVar cellClasses || exit 4
   export CLASSPATH
   CLASSPATH=$cellClasses:$eurogateHome/../eurogate.jar:$eurogateHome/../..
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
#
checkSsh() {
   checkVar sshPort || exit 4
   which $SSH >/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
     echo "Please add 'ssh' to your PATH"
     return 4
   fi
   return 0
}
#-----------------------------------------------------------------
##################################################################
#
eugateStart() {
   # 
   # we need some variables
   #
   #   checkVar poolBase ftpPort vspPort || exit $?
   #
   #
   #  run the spy server if we can find the spyPort
   #
   if [ ! -z "$spyPort" ] ; then
      SPY_IF_REQUESTED="-spy $spyPort"
   fi
   #
   # check for java and ssh
   #
   checkJava || exit $?
   checkSsh  || exit $?
   #
   BATCHFILE=$eurogateHome/$cellBatch
   if [ ! -f "$BATCHFILE" ] ; then
       echo "Cell Batchfile not found : $BATCHFILE" 1>&2
       exit 4
   fi
   #
   #   some more checks
   #
   rm -rf $LOGFILE >/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
      echo "Not allowed to write logfile : ${LOGFILE}. Using dumpster" 1>&2
      LOGFILE=/dev/null
   fi
   #
   if [ \( ! -f "$eurogateHome/server_key" \) -o \
        \( ! -f "$eurogateHome/host_key"   \)     ] ; then
        echo "Server or Host RSA Key not found" 1>&2
        echo "ssh-keygen -b  768 -f ./server_key -N \"\""
        echo "ssh-keygen -b 1024 -f ./host_key   -N \"\""
        exit 4
   fi
   #
   echo "Trying to start Eurogate"
   printf "Please wait ... "
   #
   ( ! eugateCheck ) || ( echo "System already running" ; exit 4 ; ) || exit $?
   #
   #
   nohup $JAVA  dmg.cells.services.Domain euroGate \
            -param setupFile=$fullSetup \
                   keyBase=$eurogateHome \
            -batch $BATCHFILE  \
            $SPY_IF_REQUESTED  >$LOGFILE 2>&1 &
   sleep 5
   printf ". "
   $SSH -p $sshPort -o "FallBackToRsh no" localhost <<!  2>/dev/null | grep Active
      ps -f
      exit
      exit
!
   if [ $? -ne 0 ] ; then 
      echo "Eurogate didn't startup"
      echo ""
      echo " ------ Infos from Logfile ($LOGFILE) ---------"
      echo ""
      tail -3 $LOGFILE
      exit 4
   else
      exit 0
   fi
}
##################################################################
#
#       dcache.login      
#
eugateLogin() {
#............
#
   checkSsh  || exit $?
#
  $SSH -p $sshPort -o "FallBackToRsh no" localhost
  return $?
}
#
##################################################################
#
#       eugate.check      
#
eugateCheck() {
   checkSsh || exit 4
   $SSH -p $sshPort -o "FallBackToRsh no" localhost <<! >/dev/null 2>&1
      exit
      exit
!
   return $?
}
##################################################################
#
#       eugate.stop      
#
eugateStop() {
#
   checkSsh  || exit $?
#
   echo "Trying to halt Eurogate" 
   $SSH -p $sshPort -o "FallBackToRsh no" localhost <<!  1>/dev/null 2>&1
       kill System
!
  if [ $? -ne 0 ] ; then
     echo "System already stopped" 1>&2
     exit 3
  fi
  return 0
}
#-----------------------------------------------------------------
#
theSwitch() {
   case "$weAre" in
      *start)       eugateStart  ;;
      *stop)        eugateStop  ;;
      *login)       eugateLogin  ;;
      *check)       eugateCheck  ;;
      *spy)         eugateSpy $*  ;;
      eurogate.sh) 
         weAre=$1 
         shift 
         theSwitch $*
      ;;
   esac
}
#
# ---------------------------------------------------------------------
#
theSwitch $*



















