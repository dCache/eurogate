#!/bin/sh
#
goUp() {
#
# on linux we could use 'realpath'
#
  m=`pwd`
  m=`basename $m`
  cd ..
  x=`ls -ld $m 2>/dev/null | awk '/ -> /{ print $NF }' 2>/dev/null`
  if [ ! -z "$x" ]  ; then
     m=$x
     m=`echo $m | awk -F/ '{ for(i=1;i<NF;i++)printf "%s/",$i }'`
     cd $m
  fi
}
printf " Checking JVM .......... "
which java 1>/dev/null 2>/dev/null
if [ $? -ne 0 ] ; then
  echo "Failed : Java VM not found on PATH" 
  exit 4
fi
version=`java -version 2>&1 | grep version | awk '{print $3}'`
x=`expr $version : "\"\(.*\)\"" | awk -F. '{ print $2 }'`
if [ "$x" -lt 4 ] ; then
   echo "Failed : Insufficient Java Version : $version"
   exit 4
fi
echo "Ok"
#
printf " Checking Cells ........ "
if [ ! -f "../classes/cells.jar" ] ; then
  echo "Failed : cells.jar not found in ../classes"
  exit 4
fi
echo "Ok"
#
printf " Compiling Eurogate .... "
#
rm -rf ../store/objectivity52
rm -rf ../store/bdb
rm -rf ../spy
#
export CLASSPATH
CLASSPATH=../classes/cells.jar:../..
javac `find .. -name "*.java"`
if [ $? -ne 0 ] ; then
  echo ""
  echo "  !!! Error in compiling dCache " 1>&2
  exit 5
fi
echo "Ok"
#
printf " Making eurogate.jar ... "
(  goUp ;
   goUp ; 
  rm -rf eurogate/classes/eurogate.jar
  jar cf eurogate/classes/eurogate.jar `find . -name "*.class"`
 )
echo "Done" 
#
printf " Compiling client ...... "
(
cd ../capi
make -f Makefile.linux >/dev/null 2>&1
if [ $? -ne 0 ] ; then
   echo " Failed"
   echo "    Please run the folling commands for deatils "
   echo "    cd capi"
   echo "    make -f Makefile.linux"
   exit 5
fi
echo "Done" 
)
#
printf " Coping stuff to dest .. "
goUp
mkdir -p dist/eurogate 2>/dev/null
cd dist/eurogate
mkdir classes jobs eurogatedocs config bin 2>/dev/null
#
cd ../..
#
if [ ! -d classes ] ; then
  echo "Lost my way ..... `pwd`"
  exit 4
fi
#
printf "classes "
cp classes/cells.jar classes/eurogate.jar dist/eurogate/classes
printf "jobs "
cp jobs/eurogate.lib.sh jobs/wrapper2.sh jobs/needFulThings.sh  dist/eurogate/jobs
printf "config "
cp config/eurogateSetup config/eurogate.batch  dist/eurogate/config
cd dist/eurogate/jobs
[ ! -f eurogate ] && ln -s wrapper2.sh eurogate
cd ../../..
printf "docs "
( tar cf - docs ) | ( cd dist/eurogate/eurogatedocs ; tar xf - )
printf "bin "
cp capi/test capi/eucp dist/eurogate/bin
( cd dist/eurogate/bin ; [ ! -f eurm ] && ln -s eucp eurm )
#
echo "Done"
exit 0
