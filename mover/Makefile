#
#  makefile for the EuroStore HSM Mover (device part) module
#
headers =
sources = eagle.c
objects = eagle.o

INCS = -I. -I/usr/local/include
CFLAGS = -g $(INCS) -DSOLARIS26 -DES

eagle.o:	$(sources)
maint.o:	maint.c

maint:	maint.o eagle.o
	cc -g -o maint maint.o eagle.o -L/usr/local/lib -lgetline
