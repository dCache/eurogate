#!/bin/sh
#############################################################
#
#   try to find  java
#
checkForJava() {

   java=`findBinary java /usr/java/bin /usr/lib/java/bin`
   if [ $? -ne 0 ] ; then echo "Couldn't find java" ; exit 4 ; fi
   JAVA=${java}
#
#  find the javaclasses
#
   checkJava || return $?
}
#
#############################################################
#
#   find the logdirectory
#
findLogDir() {
   if [ \( -z "${logdir}"   \) -o \
        \( ! -d "${logdir}" \) -o \
        \( ! -w "${logdir}" \)      ] ; then
      LOGDIR=/dev/null
   else
      LOGDIR=${logdir}
   fi
   echo " * chosing Logdirectory : $LOGDIR"
   return 0
}
#############################################################
#
#    the database root
#
prepareDbSimMode() {

   if [ -z "${databaseRoot}" ] ; then
      echo "Database Root not specifed" 1>&2
      return 2
   fi
   if [ ! -w "${databaseRoot}" ] ; then
      echo "Database Root not writable" 1>&2
      return 2
   fi
   if [ -z "`ls ${databaseRoot}`" ] ; then
      echo " * Creating new Database"
      mkdir -p ${databaseRoot}/store
      mkdir -p ${databaseRoot}/pvr/stk
      mkdir -p ${databaseRoot}/pvl
      mkdir -p ${databaseRoot}/mover
      mkdir -p ${databaseRoot}/users/acls 
      mkdir -p ${databaseRoot}/users/relations
      mkdir -p ${databaseRoot}/users/meta
   fi 
   return 0   
}
#############################################################
#
#   try to find  ssh
#
checkForSsh() {
   ssh=`findBinary ssh /usr/bin`
   if [ $? -ne 0 ] ; then echo "Couldn't find ssh" 1>&2 ; return 4 ; fi
   SSH=${ssh}
   #
   #  the ssh keys
   #
   if [ -z "${keyBase}"  ]  ; then
      echo " ! The keyBase is not defined" 1>&2
      echo " ! Please read eurogateSetup(.template) for the neccessary infos !" 1>&2 
      return 3
   fi
   if [ \( ! -f "${keyBase}/server_key" \) -o \
        \( ! -f "${keyBase}/host_key"   \)    ] ; then
     echo " * Trying to create new keys (may take awhile)"
     sshkeygen=`findBinary2 ssh-keygen sshkeygen /usr/bin`   
     if [ $? -ne 0 ] ; then echo "Couldn't find ssh-keygen" 1>&2 ; return 4 ; fi
     SSHKEYGEN=${sshkeygen}
     $SSHKEYGEN -b  768 -f ${keyBase}/server_key -N "" 1>/dev/null
     if [ $? -ne 0 ] ; then
        echo "Keygen of server key failed" 1>&2
        return 4
     fi
     $SSHKEYGEN -b 1024 -f ${keyBase}/host_key   -N "" 1>/dev/null
     if [ $? -ne 0 ] ; then
        echo "Keygen of host key failed" 1>&2
        return 4
     fi
   fi
   return 0
}
#
switchSimMode() {
   
   if [ \( -z "${javaOnly}" \) -o \( "${javaOnly}" = "true" \) ] ; then
      echo " * Chosing javaOnly Movers" 1>&2
   elif [ "${javaOnly}" != "false" ] ; then
      echo " ! The javaOnly variable needs to be " 2>&1 
      echo " ! set true of false ( and not ${javaOnly} )" 2>&1
      return 5
   else 
      if [ \( -z "${sharedLibraries}" \) -o \
           \( ! -d "${sharedLibraries}"  \) ] ; then
         echo " ! javaOnly=false  needs a valid sharedLibrary directory" 1>&2
         return 5
      fi
      echo " * Chosing 'real Movers in simulation mode'" 1>&2
      export LD_LIBRARY_PATH
      LD_LIBRARY_PATH=${sharedLibraries}
   fi
   return 0
}
#
#
#
##################################################################
#
#       eugate.check
#
eurogateCheck() {
   checkForSsh || return $?
   $SSH -p $sshPort -c blowfish -o "FallBackToRsh no" localhost <<! >/dev/null 2>&1
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
  if [ \( "$mode" = "real" \) -a \( "$mover" = "yes" \) ] ; then
     pidFile=${info}/moverPids
     if [ ! -f ${pidFile} ] ; then
        echo " ! no movers found"
        exit 5
     fi
     kill `cat ${pidFile}` 2>/dev/null
     rm -rf ${pidFile} 2>/dev/null
     exit 0
  fi
   checkForSsh  || exit $?
#
   echo "Trying to halt Eurogate"
   $SSH -p $sshPort -c blowfish -o "FallBackToRsh no" localhost <<!  1>/dev/null 2>&1
exit
       set dest System@euroMover0
       kill System
exit
       set dest System@euroMover1
       kill System
exit
       set dest local
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
#   checkSsh  || exit $?
   checkVar  pvlDbPort || exit $?
#
   echo "Trying to InitPvl Database"
   $SSH -p $pvlDbPort -c blowfish -o "FallBackToRsh no" localhost <<!  1>/dev/null 2>&1
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
#       make ready to use package ( simulation mode only )
#
makePackage() {
   # make sure we have a valid classpath were we can extract the
   # cells.jar from. 
   # In addition we need the binaries 'ssh' and 'ssh-keygen'
   # althought checkForSsh will generate a host/server keypair.
   #
   echo " * checking for cells.jar and ssh binaries."
   #
   #  we stay with the currently chosen classpath
   #
   java=`findBinary java /usr/java/bin /usr/lib/java/bin`
   if [ $? -ne 0 ] ; then echo "Couldn't find java" ; exit 4 ; fi
   JAVA=${java}
   checkForSsh  || exit $?
   echo " * removing unnessary java directories."
   rm -rf ${thisDir}/../store/bdb \
          ${thisDir}/../store/objectivity52 \
          ${thisDir}/../pvl/regexp            >/dev/null 2>&1
   #
   # adjust location
   #
   cd ${thisDir}/..
   mkdir eurogate/bin 2>/dev/null
   #
   #
   #  check for newer class files.
   #
   unset NEED_TO_COMPILE
   if [ ! -f bin/eurogate.jar ] ; then
      NEED_TO_COMPILE=yes
   else
      n=`find . -name "*.java" -newer bin/eurogate.jar 2>/dev/null`
      if [ ! -z "$n" ] ; then
         NEED_TO_COMPILE=yes
      fi
   fi
   if [ ! -z "$NEED_TO_COMPILE" ] ; then
      echo " * compiling sources ... (may take awhile)"
      javadir=`dirname ${JAVA}`
      if [ "$javadir" = "." ] ; then 
        JAR=jar 
        JAVAC=javac
      else 
        JAR=$javadir/jar 
        JAVAC=$javadir/javac
      fi
      ${JAVAC} `find . -name "*.java"`
      if [ $? -ne 0 ] ; then
         echo " !!!! Compilation failed, can't continue"
         exit 3
      fi
      echo " * creating eurogate.jar"

      cd ..
      ${JAR} c0f eurogate/bin/eurogate.jar `find . -name "*.class"` 1>/dev/null 2>&1
   else
      echo " * eurogate.jar still up to date"
      cd ..
   fi
   #
   #  find the cells
   #
   myCells=`getCellsLocation 2>/dev/null`
   if [ \( ! -z "$myCells" \) -a \( -f "$myCells" \) ] ; then
      cp $myCells eurogate/bin
   else
      echo " !!! can't find the cells" 1>&2
      exit 4
   fi
   #
   # find the ssh
   #
   sshdir=`dirname ${SSH}` 
   if [ "$sshdir" = "." ] ; then
      sshdir=`which ssh`
      sshdir=`dirname $sshdir`
   else
      sshdir=`dirname $SSH`
   fi
   cp $sshdir/ssh $sshdir/ssh-keygen eurogate/bin
   echo " * creating tar file"
   tar cf eurogate.tar eurogate/jobs eurogate/bin eurogate/docs
   echo " * Done -> `pwd`/eurogate.tar "
   exit 0  
}
#
##################################################################
#
#       eurogate start
#
eurogateStart() {
   #
   # we need some variables
   #
   #
   checkVar pvlDbPort sshPort clientPort || exit $?
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
   if [ ! -f "${cellBatch}" ] ; then
      echo " ! Can't find our cell batchfile  ${cellBatch}" 1>&2
      exit 4
   fi
#   set -x
   #
   # check for java and ssh
   #
   checkForJava || exit $?
   checkForSsh  || exit $?
   findLogDir
   switchSimMode || exit $?
   prepareDbSimMode || exit $?
   #
   echo " * Trying to start Eurogate"
   #
   eurogateCheck
   if [ $? -eq 0 ] ; then
      echo "System already running"
      exit 4
   fi
   #
   #
   #
   printf " * Please wait ... "
   #
   #
   BATCHFILE=${cellBatch}
   if [ ! -f "$BATCHFILE" ] ; then
       echo "Cell Batchfile not found : $BATCHFILE" 1>&2
       exit 4
   fi
   if [ "${master}" = "yes" ] ; then
      domainName=euroGate
      log=$LOGDIR/${domainName}.log
      rm -rf $log >/dev/null 2>/dev/null
      touch ${log} >/dev/null 2>/dev/null
      if [ $? -ne 0 ] ; then
         echo "Not allowed to write logfile : ${log}. Using dumpster" 1>&2
         log=/dev/null
      fi
   
      nohup $JAVA  dmg.cells.services.Domain $domainName \
               -param setupFile=$setupFilePath \
                      thisDir=${thisDir}  \
               -tunnel2 ${masterPort} \
               -batch $BATCHFILE  \
               -routed \
               $SPY_IF_REQUESTED  $TELNET_IF_REQUESTED >$log 2>&1 &
#
      for c in 5 4 3 2 1 0 ; do printf "${c} " ; sleep 1 ; done 
      printf "\n" 
      x=`tail -3 $log | grep PANIC`
      if [ ! -z "$x" ] ; then
         echo ""
         echo " ------ Infos from Logfile ($log) ---------"
         echo ""
         tail -5 $log
         exit 1
      else
         echo " * Eurogate started "
      fi
   fi
#   set -x
   if [ "${mover}" = "yes" ] ; then
      pidFile=${info}/moverPids
      if [ -f ${pidFile} ] ; then
         n1=`cat $pidFile | wc | awk '{ print $1 }'`
         pidList=`cat $pidFile`
         n2=`kill -0 $pidList 2>&1 | wc | awk '{ print $1 }'`
         if [ "$n1" -ne "$n2" ] ; then
             echo " ! Kill running mover first !"
             exit 4
         fi
         rm ${pidFile} 2>/dev/null
      fi
      for moverVersion in 0 1  
      do
         domainName=euroMover${moverVersion}
         log=$LOGDIR/${domainName}.log
         rm -rf $log >/dev/null 2>/dev/null
         touch ${log} >/dev/null 2>/dev/null
         if [ $? -ne 0 ] ; then
            echo "Not allowed to write logfile : ${log}. Using dumpster" 1>&2
            log=/dev/null
         fi
         #
         nohup $JAVA -Xmaxjitcodesize0 dmg.cells.services.Domain euroMover${moverVersion} \
                  -param setupFile=${setupFilePath} \
                         thisDir=${thisDir}  \
                         moverVersion=${moverVersion} \
                  -batch $BATCHFILE  \
                  -connect2 ${masterHost} ${masterPort} \
                  -routed \
                  >$log 2>&1 &   
         echo $! >>${info}/moverPids
         printf " ${domainName} "
      done
      echo " done"
   fi
}
##################################################################
#
#       eurogate login
#
eurogateLogin() {
#............
#
   checkForSsh  || exit $?
#
  $SSH -p $sshPort -c blowfish -o "FallBackToRsh no" localhost
  return $?
}
#
eurogateAdmin() {
#............
#
   checkForSsh  || exit $?
#
  shift 1
  $SSH -p $aclPort -c blowfish -o "FallBackToRsh no" $* localhost
  return $?
}
#

eurogateHelp() {
   echo "Usage : eurogate start"
   echo "        eurogate login"
   echo "        eurogate stop"
   echo "        eurogate admin"
   return 0
}
eurogateSwitch() {
   case $1 in
     *start)       eurogateStart $* ;;
     *stop)        eurogateStop  $* ;;
     *login)       eurogateLogin  $* ;;
     *admin)       eurogateAdmin  $* ;;
     *otto)        getCellsLocation $* ;;
     *mkpack)      makePackage $* ;;
     *)            eurogateHelp $* ;;
   esac
   return $?
} 
