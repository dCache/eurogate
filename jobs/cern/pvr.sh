##@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
#
#     were are we ? who are we ?
#
#
if [ -z "$initDone" ] ; then 
#echo "DEBUG : running <init>"
initDone=true
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
      ourHome=$J_HOME/..
      weAre=$progname
      return 0
   }
   os=`uname -s 2>/dev/null` || \
       ( echo "Can\'t determine OS Type" 1>&2 ; exit 4 ) || exit $?


   if [ "$os" = "SunOS" ]  ; then
      weAreSolaris
   else
      echo "Sorry, no support for $os" 1>&2
      exit 3
   fi
   #
   bins=$ourHome/bin
   jobs=$ourHome/jobs
   info=$ourHome/info
   ECHO=/usr/ucb/echo
   #
   #  run some needful things
   #
   if [ -z "$needFulThings" ]  ; then
      needFulThings=loaded
      export needFulThings
      if [ ! -f "$jobs/needFulThings.sh" ] ; then
         $ECHO "Panic, not found : $jobs/needFulThings.sh" 
         exit 4
      fi    
      . $jobs/needFulThings.sh 
   fi
   #
   #   try to find a setupfile
   #
   setupFileName=`echo ${weAre} | awk -F. '{ printf "%sSetup",$1 }' 2>/dev/null`
   if [ -z "${setupFileName}" ] ; then
      echo "Cannot determine setupFileName" 1>&2
      exit 4
   fi 
   if [ -f ${jobs}/${setupFileName} ] ; then
      setupFilePath=${jobs}/${setupFileName}
   elif [ -f /etc/${setupFileName} ] ; then
      setupFilePath=/etc/${setupFileName}
   else
     echo "Setupfile <${setupFileName}> not found in (/etc/.. ${jobs}/.. " 1>&2
     exit 4
   fi
   setupFilePath=`getFull $setupFilePath`
   echo "Using setupfile : $setupFilePath"
   . ${setupFilePath}
#
#     end of init
#
fi
#
startPvr() {
   echo "starting EuroGate STK-PVR"
   #
   checkVar pvrProxyHost pvrProxyPort
   #
   pvrLog=${pvrLog:=/dev/null}
   #
   ${bins}/es-pvr -n stk -a $pvrProxyHost -p $pvrProxyPort -l $pvrLog
   if [ $? -ne 0 ] ; then
      echo "Could not start PVR"
      exit 4
   else
      echo "STK PVR started"
   fi
}
stopPvr() {

    killproc es-pvr
    
}
pvrSwitch() {
   case "$1" in
      *start)  
           startPvr 2>/dev/null 
           return $?
      ;;
      *stop)   
           stopPvr 
           return $?
     ;;
      *) cscHelp ;;
   esac
   return $?
}
#
# ---------------------------------------------------------------------
#
pvrSwitch $*

exit $?
