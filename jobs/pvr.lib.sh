pvrHelp() {
  echo "... start"
  echo "... stop"
  exit 4
}
startPvr() {
   pid=`getpids es-pvr`
   if [ ! -z "$pid" ] ; then
     echo "Already running $pid" 1>&2
     exit 4
   fi
   #
   checkVar pvrProxyHost pvrProxyPort
   echo "starting EuroGate STK-PVR"
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
           startPvr
           return $?
      ;;
      *stop)   
           stopPvr 
           return $?
     ;;
      *) pvrHelp ;;
   esac
   return $?
}


