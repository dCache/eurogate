all: test
#
#  makefile for the EuroStore HSM API modules
#
headers = ES_api.h ES_errno.h
sources = ES_api.c
objects = ES_api.o

#CFLAGS = -g -xildoff -erroff=%none
CFLAGS = -g
#LLIBS=-lsocket
INCS = -I.

getline.o: getline.c getline.h
	$(CC) -c $(CFLAGS) $(INCS) getline.c 

libgetline.a: getline.o
	ar rcv libgetline.a getline.o

libesapi.a:	ES_api.o
	ar rcv $@ $?

ES_api.o:	ES_api.c ES_api.h ES_errno.h ES_constants.h
	$(CC) -c $(CFLAGS) $(INCS) ES_api.c

test:	test.o libesapi.a libgetline.a
	$(CC) $(CFLAGS) -o test -g test.o -L. -lesapi -lgetline

test.o:	test.c 
	$(CC) -c $(INCS) $(CFLAGS) test.c

clean:
	rm -f test test1 *.o *.a
