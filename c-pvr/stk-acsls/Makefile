#
#  makefile for the EuroStore HSM PVR module
#
headers =
sources = es-pvr.c es-pvr-stk.c
objects = es-pvr.o es-pvr-stk.o

INCS = -I. -I../../../../es/pvr-new/csc-2.1/src/h
STKLIB=-L../../../../es/pvr-new/csc-2.1/lib -lapi -lutl -lipc -lcl -lapi
CFLAGS = -g $(INCS) -DSOLARIS

es-pvr:	$(objects)
	cc -g -o es-pvr $(objects) $(STKLIB) \
        -L/usr/ucblib -R/usr/ucblib -lrpcsoc -lsocket -lnsl
