##########################################################
#
#   startup of the csc only.
#   Needs File :  cscSetup ( int /etc/ or <here> )  
#   and vars   :  ${roboticHost}  ascls hostname.
#
checkVar  roboticHost bins info  || exit 4
#
ACSSS=${bins}
#
#
# common char. of RPC communication to MVS LS system
#
CSI_UDP_RPCSERVICE=TRUE; export CSI_UDP_RPCSERVICE
CSI_TCP_RPCSERVICE=FALSE; export CSI_TCP_RPCSERVICE
# CSI_RETRY_TIMEOUT=4; export CSI_RETRY_TIMEOUT
CSI_RETRY_TRIES=5; export CSI_RETRY_TRIES
CSI_HOSTNAME=$roboticHost; export CSI_HOSTNAME
CSI_CONNECT_AGETIME=172800; export CSI_CONNECT_AGETIME
ACSAPI_PACKET_VERSION=4;  export ACSAPI_PACKET_VERSION
#
#
SSI=$ACSSS/ssi
ACSEL=$ACSSS/mini_el
T_KILL_FILE=${info}/t_kill_file; export T_KILL_FILE
#
########################################################
#
#   start the eventlogger and the ssi
#
startCscProcs() {
   #
   if [ ! -d ${info}  ] ; then
     $ECHO "Info directory not found  : ${info}"
     exit 5
   fi
   if [ -f $T_KILL_FILE ] ; then
     $ECHO "STK daemons already run - $T_KILL_FILE exists"
     exit 3
   fi
   #
   #	execute the ACSEL
   #
   $ECHO -n "Starting CSC Event-Logger ... "
   # start CSC 2.1 mini_el
   $ACSEL &
   elpid=$!
   $ECHO " done <pid=$elpid>"
   #
   # execute the SSI
   #
   $ECHO -n "Starting SSI ... "
   sleep 30 >/dev/null 2>/dev/null &
   curpid=$!
   $ECHO -n "<sleep=$curpid> ... "
   sleep 2
   # start ssi
   $SSI $curpid 50004 23 >/dev/null 2>/dev/null &
   ssipid=$!
   $ECHO -n "<ssi=$ssipid> ... "
   # wait for ssi to signal proper startup
   wait $curpid >/dev/null 2>/dev/null
   waitrc=$?
   $ECHO " <rc=$waitrc> done"
   #
   if [ $waitrc -eq 0 ] ; then
      echo "Starting ssi failed"
      kill -9 $elpid $ssipid 1>/dev/null 2>/dev/null 
      exit 4
   fi
   #
   rm -rf $T_KILL_FILE >/dev/null 2>&1
   $ECHO $ssipid >>$T_KILL_FILE
   $ECHO $elpid >>$T_KILL_FILE
   #
   exit 0
}
stopCscProcs() {
   if [ ! -f $T_KILL_FILE ]; then
     $ECHO "no PID file (${T_KILL_FILE}) found"
     exit 1
   fi

   #	Read the file and input the pids
   while [ 1 ]
   do
     read pid
     $ECHO -n "stopping PID $pid ."
     if [ $pid ]
     then
       while [ 1 ]
       do
         kill -TERM $pid >/dev/null 2>&1
         if [ $? -ne 0 ]; then
	   $ECHO "done"
	   break
         else
	   for i in [ 1 2 3 4 5 6 7 8 9 10 11 12 13 ]
	   do
	     sleep 1
	     kill -TERM $pid >/dev/null 2>&1
	     if [ $? -ne 0 ]; then
	       break;
	     fi
	     $ECHO -n "."
	   done
         fi
       done
     else
       break
     fi
   done < $T_KILL_FILE

   rm $T_KILL_FILE
}
#-----------------------------------------------------------------
cscHelp() {
   echo "Usage : "
   echo "csc.sh  start"
   echo "csc.sh  stop"
   return 3
}
#
cscSwitch() {
   case "$1" in
      *start)  
           startCscProcs 2>/dev/null 
           return $?
      ;;
      *stop)   
           stopCscProcs 
           return $?
     ;;
      *) cscHelp ;;
   esac
   return $?
}
#
# ---------------------------------------------------------------------
#
