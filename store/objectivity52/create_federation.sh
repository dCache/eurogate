#!/bin/zsh
#
# create brandnew federated database for eurogate Store
#
/usr/object/bin/oonewfd -fdnumber 666 -pagesize 4096 -lockserverhost `uname -n` -fdfilepath EuroGate.FDB EuroGate
