#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------

incldir = ../include
subdir = ../org.postgresql.pljava/src/C/pljava
top_builddir = ../../../../postgresql-7.4
include $(top_builddir)/src/Makefile.global

override CPPFLAGS := -DUSE_THREADS -DPKGLIBDIR=\"$(pkglibdir)\" -I$(incldir) -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(PORTNAME) $(CPPFLAGS)
override CFLAGS := -g -fpic $(CFLAGS)
override LDFLAGS := -g -shared -L$(JAVA_HOME)/jre/lib/i386/client -ljvm $(LDFLAGS)

OBJS = Server.o PgObject.o Exception.o HashMap.o Iterator.o Function.o SPI.o

all: subsys libpljava.so

subsys:
	$(MAKE) -C type all

libpljava.so: $(OBJS) type/SUBSYS.o
	$(CC) $(LDFLAGS) -o libpljava.so $(OBJS) type/SUBSYS.o

depend dep:
	$(MAKE) -C type $@
	$(CC) -MM $(CFLAGS) *.c >depend

clean:
	$(MAKE) -C type $@
	rm -f pljava.so $(OBJS)

ifeq (depend,$(wildcard depend))
include depend
endif
