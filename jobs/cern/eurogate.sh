#!/bin/sh
#
JAVA=/usr/java1.2/bin/java
LOGFILE=/tmp/eugate.log
#
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
SSH=${bins}/ssh
##@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
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

eugateStart() {
   #
   # we need some variables
   #
   checkVar cellBatch || exit 5
   #
   #
   #  run the spy/telnet server if we can find the spyPort/telnetPort
   #
   if [ ! -z "$spyPort" ] ; then
      SPY_IF_REQUESTED="-spy $spyPort"
   fi
   if [ ! -z "$telnetPort" ] ; then
      TELNET_IF_REQUESTED="-telnet $telnetPort"
   fi
   #
   BATCHFILE=${jobs}/$cellBatch
   if [ ! -f "$BATCHFILE" ] ; then
       echo "Cell Batchfile not found : $BATCHFILE" 1>&2
       exit 4
   fi
   #
   # check for java and ssh
   #
   checkJava || exit $?
   checkSsh  || exit $?
   #
   #   some more checks
   #
   rm -rf $LOGFILE >/dev/null 2>/dev/null
   if [ $? -ne 0 ] ; then
      echo "Not allowed to write logfile : ${LOGFILE}. Using dumpster" 1>&2
      LOGFILE=/dev/null
   fi
   #
   if [ \( ! -f "${jobs}/server_key" \) -o \
        \( ! -f "${jobs}/host_key"   \)     ] ; then
        echo "Server or Host RSA Key not found" 1>&2
        echo "ssh-keygen -b  768 -f ./server_key -N \"\""
        echo "ssh-keygen -b 1024 -f ./host_key   -N \"\""
        exit 4
   fi
   #
   echo "Trying to start Eurogate"
   printf "Please wait ... "
   #
   eugateCheck
   if [ $? -eq 0 ] ; then
      echo "System already running"
      exit 4
   fi
   #
   #
   nohup $JAVA  dmg.cells.services.Domain euroGate \
            -param setupFile=$setupFilePath \
                   keyBase=${jobs} \
            -batch $BATCHFILE  \
            $SPY_IF_REQUESTED  $TELNET_IF_REQUESTED >$LOGFILE 2>&1 &
   sleep 7
   printf ". "
   $SSH -p $sshPort -o "FallBackToRsh no" localhost <<!  2>/dev/null | grep Active
      ps -f
      exit
      exit
!
   if [ $? -ne 0 ] ; then
      echo "Eurogate did not startup"
      echo ""
      echo " ------ Infos from Logfile ($LOGFILE) ---------"
      echo ""
      tail -3 $LOGFILE
      exit 4
   else
      exit 0
   fi
}

 
