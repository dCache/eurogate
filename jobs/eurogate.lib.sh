#!/bin/sh
#
JAVA=${java}
LOGDIR=/home/eurogateDb/log
SSH=${bins}/ssh
keys=${jobs}
LD_LIBRARY_PATH=${libraryPath}
export LD_LIBRARY_PATH
#
#
##################################################################
#
#       eugate.check
#
eurogateCheck() {
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
eurogateStop() {
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
##################################################################
#
#       eugate.initPvl
#
eurogateExecStore() {
#
   checkSsh  || exit $?
   checkVar  sshPort || exit $?
#
   $SSH -p $sshPort -o "FallBackToRsh no" localhost <<!
       exit
       set dest MAIN-store
       $* 
exit
exit
!
  if [ $? -ne 0 ] ; then
     echo "Init Failed" 1>&2
     exit 3
  fi
  return 0
}
eurogateInitPvl() {
#
   checkSsh  || exit $?
   checkVar  pvlDbPort || exit $?
#
   echo "Trying to InitPvl Database"
   $SSH -p $pvlDbPort -o "FallBackToRsh no" localhost <<!  1>/dev/null 2>&1
       exec context initPvlDatabase
       exit
       exit
!
  if [ $? -ne 0 ] ; then
     echo "Init Failed" 1>&2
     exit 3
  fi
  return 0
}
eurogateDeletePvl() {
   checkVar databaseRoot || exit $?
   rm -rf ${databaseRoot}/pvl/* >/dev/null 2>/dev/null
   return 0 
}

##################################################################
#
#       eurogate start
#
eurogateStart() {
   #
   # we need some variables
   #
   checkVar masterBatch moverBatch master mover masterHost masterPort || exit 5
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
   #
   # check for java and ssh
   #
   checkJava || exit $?
   checkSsh  || exit $?
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
   eurogateCheck
   if [ $? -eq 0 ] ; then
      echo "System already running"
      exit 4
   fi
   #
   #
   #
   if [ "${master}" = "yes" ] ; then
   
      domainName=euroGate
      log=$LOGDIR/${domainName}.log
      rm -rf $log >/dev/null 2>/dev/null
      if [ $? -ne 0 ] ; then
         echo "Not allowed to write logfile : ${log}. Using dumpster" 1>&2
         log=/dev/null
      fi
      #
      BATCHFILE=${jobs}/$masterBatch
      if [ ! -f "$BATCHFILE" ] ; then
          echo "Cell Batchfile not found : $BATCHFILE" 1>&2
          exit 4
      fi
      nohup $JAVA  dmg.cells.services.Domain $domainName \
               -param setupFile=$setupFilePath \
                      keyBase=${keys} \
               -batch $BATCHFILE  \
               -tunnel2 ${masterPort} \
               -routed \
               $SPY_IF_REQUESTED  $TELNET_IF_REQUESTED >$log 2>&1 &
#
      for c in 5 4 3 2 1 0 ; do printf "${c} " ; sleep 1 ; done 
      printf "\n" 
#      $SSH -p $sshPort -o "FallBackToRsh no" localhost <<!  2>/dev/null | grep Active
#         ps -f
##         exit
#         exit
#!
#      if [ $? -ne 0 ] ; then
#         echo "Eurogate did not startup"
         echo ""
         echo " ------ Infos from Logfile ($log) ---------"
         echo ""
         tail -3 $log
         exit 4
#      else
#         exit 0
#      fi
   fi
   if [ "${mover}" = "yes" ] ; then
      BATCHFILE=${jobs}/$moverBatch
      if [ ! -f "$BATCHFILE" ] ; then
          echo "Cell Batchfile not found : $BATCHFILE" 1>&2
          exit 4
      fi
      for moverVersion in 0 1  
      do
         domainName=euroMover${moverVersion}
         log=$LOGDIR/${domainName}.log
         rm -rf $log >/dev/null 2>/dev/null
         if [ $? -ne 0 ] ; then
            echo "Not allowed to write logfile : ${log}. Using dumpster" 1>&2
            log=/dev/null
         fi
         #
         nohup $JAVA  dmg.cells.services.Domain euroMover${moverVersion} \
                  -param setupFile=${setupFilePath} \
                         keyBase=${keys} \
                         moverVersion=${moverVersion} \
                  -batch $BATCHFILE  \
                  -connect2 ${masterHost} ${masterPort} \
                  -routed \
                  >$log 2>&1 &   
      done
   fi
}
##################################################################
#
#       eurogate login
#
eurogateLogin() {
#............
#
   checkSsh  || exit $?
#
  $SSH -p $sshPort -o "FallBackToRsh no" localhost
  return $?
}
#

eurogateHelp() {
   echo "Usage : eurogate start"
   echo "        eurogate login"
   echo "        eurogate initPvl"
   echo "        eurogate deletePvl"
   echo "        eurogate stop"
   echo "        eurogate deleteObjy"
   echo "        eurogate createObjy"
   echo "        eurogate initObjy"
   return 0
}
eurogateSwitch() {
   case $1 in
     *start)       eurogateStart $* ;;
     *stop)        eurogateStop  $* ;;
     *login)       eurogateLogin  $* ;;
     *initPvl)     eurogateInitPvl  $* ;;
     *deletePvl)   eurogateDeletePvl  $* ;;
     *createObjy)  createOODB $* ;;
     *deleteObjy)  deleteOODB $* ;;
     *initObjy)    initOODB $* ;;
     *clearObjy)   clearOODB $* ;;
     *execStore)   shift ; eurogateExecStore $* ;;
     *)            eurogateHelp $* ;;
   esac
   return $?
} 
