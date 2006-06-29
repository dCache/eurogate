#
#  defined :
#     ourBaseName  
#     setupFilePath
#     thisDir
#     ourHomeDir
#     config
#
if [ ! -z "${pool}" ] ; then  
     poolFile=${config}/${pool}
     if [ ! -f ${poolFile} ] ; then
        if [ -f ${poolFile}.poollist ] ; then
           poolFile=${poolFile}.poollist
        else
           echo "Pools file not found : ${poolFile}" 1>&2
           exit 4
        fi
     fi
     POOL="pool=${poolFile}"
     x=`echo ${pool} | awk -F. '{print $1}'`
     domainName=${x}Domain
     pidFile=${config}/lastPid.${x}
elif [ -f ${config}/${ourBaseName}.poollist ] ; then
     POOL="pool=${poolFile}"
     domainName=${ourBaseName}Domain
     pidFile=${config}/lastPid.${ourBaseName}
else
     domainName=${ourBaseName}Domain
     pidFile=${config}/lastPid.${ourBaseName}
fi
if [ ! -z "${domain}" ] ; then
    domainName=${domain}
    base=`expr "${domainName}" : "\(.*\)Domain"`
    if [ -z "$base" ] ; then base=${domainName} ; fi
    pidFile=${config}/lastPid.${base}
fi
#
findJavaVM() {
   PATH=/usr/java/bin:/usr/bin:/usr/lib/jvm/jre/bin:$PATH
   javaLocation=`which java 2>/dev/null`
   if [ $? -eq 0 ] ; then
      echo ${javaLocation}
      return 0
   fi
   echo "not found ${javaLocation}" >&2
   find /usr -maxdepth 1 -type d -name "jdk*" | while  read dir ; do
      javaloc=$dir/bin/java
      if [ -f $javaloc ] ; then echo $javaloc ; return 0 ; fi
   done
   find /usr -maxdepth 1 -type d -name "j2sdk*" | while  read dir ; do
      javaloc=$dir/bin/java
      if [ -f $javaloc ] ; then echo $javaloc ; return 0 ; fi
   done
   find /opt -maxdepth 1 -type d -name "jdk*" | while  read dir ; do
      javaloc=$dir/bin/java
      if [ -f $javaloc ] ; then echo $javaloc ; return 0 ; fi
   done
   find /opt -maxdepth 1 -type d -name "j2sdk*" | while  read dir ; do
      javaloc=$dir/bin/java
      if [ -f $javaloc ] ; then echo $javaloc ; return 0 ; fi
   done
   return 1
}
checkJavaVM() {
  if [ -z "${java}" ] ; then
     findJavaVM
     return $?
  else
     ${java} -version >/dev/null 2>&1
     if [ $? -ne 0 ]  ; then
         findJavaVM
         return $?
     fi
     echo ${java}
     return 0
  fi
}
#
java=`checkJavaVM 2>/dev/null`
if [ -z "${java}" ] ; then
  echo ""
  echo " --- Configuration Problem : can't find java VM"
  echo "        Please set the 'java' variable in"
  echo "          config/eurogateSetup correctly"
  echo ""
  exit 5
fi
#
#  host key
#
sshHostKey=/etc/ssh/ssh_host_key
if [ ! -f ${keyBase}/host_key ] ; then
   if [ ! -r ${sshHostKey} ] ; then
      echo " --- Configuration Error : no access to host key"
      echo "      Host key (${sshHostKey}) either doesn't exist"
      echo "      or is not accessible with our uid"
      echo "      Or you may create a faked host key in my config"
      echo "      directory by running :"
      echo "      ( cd ../config ; ssh-keygen -b 1024 -t rsa1  -f ./host_key   -N \"\" )"
      echo ""
      exit 5
   else
      ln -s ${sshHostKey} ${keyBase}/host_key
   fi
fi
#
if [ -z  "${logfile}" ] ; then
   if [ -z "${logArea}" ] ; then
     echo " -- Configuration Error : The log area ${logArea} doesn't exist."
     echo "                          Please fix config/eurogateSetup"
     exit 5
     logfile=/tmp/${domainName}.log 
   else
     logfile=${logArea}/${domainName}.log
   fi
fi
touch ${logfile} 2>/dev/null
if [ $? -ne 0 ] ; then
     echo " -- Configuration Error : The log area ${logArea} is not writable for us."
     echo "" 
     exit 6
  logfile=/dev/null
fi
touch ${pidFile} 2>/dev/null
if [ $? -ne 0 ] ; then
     echo " -- Configuration Error : Can't write into my own config directory."
     echo ""
   exit 4
#   pidFile=/dev/null
fi
#
#############################################################
#
#    the database root
#
prepareDbSimMode() {

   if [ -z "${databaseRoot}" ] ; then
      echo " -- Configuration Error : Database Root not specifed" 1>&2
      echo "        Please fix eurogateSetup (${databaseRoot})"
      exit 2
   fi
   if [ ! -w "${databaseRoot}" ] ; then
      echo " -- Configuration Error : Database Root not writable or doesn't exist" 1>&2
      echo "        Please fix this first (${databaseRoot})"
      exit 2
   fi
   if [ -z "`ls ${databaseRoot}`" ] ; then
      echo " * Creating new Database"
      mkdir -p ${databaseRoot}/store
      mkdir -p ${databaseRoot}/pvr/stk
#      mkdir -p ${databaseRoot}/pvr/copan-0
#      mkdir -p ${databaseRoot}/pvr/copan-1
      mkdir -p ${databaseRoot}/pvr/stacker
      mkdir -p ${databaseRoot}/pvl
      mkdir -p ${databaseRoot}/mover
      mkdir -p ${databaseRoot}/users/acls
      mkdir -p ${databaseRoot}/users/relations
      mkdir -p ${databaseRoot}/users/meta
   fi
   return 0
}
#
procStop() {
   if [ ! -f ${pidFile} ] ; then
      echo "Can't find appropiate pidFile(${pidFile})" 1>&2
      exit 0 
   fi
   x=`cat ${pidFile}`
   if [ -z "$x" ] ; then
      echo "Pid File (${pidFile}) doesn't contain valid PID" 1>&2
      exit 1
   fi
   touch ${config}/realStop.${domainName} 2>/dev/null
   kill -TERM $x 1>/dev/null 2>/dev/null
   printf "Stopping ${domainName} (pid=`cat ${pidFile}`) "
   for c in  0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do 
     sleep 1
     kill -0 $x 1>/dev/null 2>/dev/null
     if [ $? -ne 0 ] ; then
        echo "Done" 
        exit 0
     fi
     printf "$c "
     if [ $c -eq 8 ] ; then
         kill -9 $x
     fi
   done
   echo "Giving up : ${domainName} might still be running" 1>&2
   exit 4

}
procStart() {

  if [ -f ${pidFile} ] ; then
     x=`cat ${pidFile}`
     kill -0 $x 1>/dev/null 2>/dev/null
     if [ $? -eq 0 ] ; then
        echo "${domainName} might still be running" 1>&2
        exit 4
     fi
  fi
#
  if [ ! -z "${telnetPort}" ] ; then
     TELNET_PORT="-telnet ${telnetPort}" 
  elif [ ! -z "${telnet}" ] ; then
     TELNET_PORT="-telnet ${telnet}"
  fi
  if [ ! -z "${batch}" ] ; then
     batchFile=${batch}
  else
     batchFile=${config}/${ourBaseName}.batch
  fi
  if [ -f "$batchFile" ] ; then 
      BATCH_FILE="-batch ${batchFile}" 
  else
      echo "Batch file doesn't exist : ${batchFile}, can't continue ..." 1>&2
      exit 5
  fi
  if [ ! -z "${debug}" ] ; then DEBUG="-debug" ; fi
  export CLASSPATH
  CLASSPATH=${classpath}:${thisDir}/../..:${thisDir}/../classes/cells.jar:${thisDir}/../classes/dcache.jar
  export LD_LIBRARY_PATH
  LD_LIBRARY_PATH=${librarypath}
#
#  echo "(Using classpath : $CLASSPATH )"
#  echo "(Using librarypath : $LD_LIBRARY_PATH )"
#  echo "${java} ${java_options}"
  rm -fr ${config}/realStop.${domainName} 2>/dev/null
  [ "${logMode}" = "new" ] && mv ${logfile} ${logfile}.old 2>/dev/null
     ( while [ ! -f "${config}/realStop.${domainName}" ] ; do
        ${java} ${java_options} dmg.cells.services.Domain ${domainName} \
                $TELNET_PORT \
                -param setupFile=${setupFilePath} \
                       ourHomeDir=${ourHomeDir} \
                       ourName=${ourBaseName} \
                       ${POOL} \
                $BATCH_FILE $DEBUG  1>>${logfile}  2>&1 &
        latestPid=$!
        echo ${latestPid} >${pidFile} 
        wait
        if [ -f "${config}/realStop.${domainName}" ] ; then break ; fi
        [ "${logMode}" = "new" ] && mv ${logfile} ${logfile}.old 2>/dev/null
        if [ -f "${config}/delay" ] ; then
           delay=`cat ${config}/delay 2>/dev/null`
           sleep ${delay} 2>/dev/null
        fi
     done ) &
     printf "Starting ${domainName}  "
     for c in 6 5 4 3 2 1 0 ; do 
        sleep 1
        printf "$c "
     done
     latestPid=`cat ${pidFile} 2>/dev/null`
     kill -0 ${latestPid} 1>/dev/null 2>/dev/null
     if [ $? -ne 0 ] ; then
         echo " failed"
         grep PANIC ${logfile}.old
         exit 4
     else
         echo "Done (pid=${latestPid})"
     fi
  exit 0
}
procHelp() {
   echo "Usage : ${ourBaseName} start|stop"
   exit 4
}

procSwitch() {
   case "$1" in
      *start)       prepareDbSimMode
                    shift 1 
                    procStart $* ;;
      *stop)        procStop  ;;
      *) procHelp $*
      ;;
   esac
}
