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

override CPPFLAGS := -DUSE_THREADS -DPKGLIBDIR=\"$(pkglibdir)\" -I$(incldir) -I$(JAVA_HOME)/include $(CPPFLAGS)
ifeq ($(PORTNAME), cygwin)
#
# Portname will not suffice. The Win32 JDK have no knowledge of cygwin
# GNU compiler doesn't know __int64, instead it uses long long.
#
override CPPFLAGS := -I$(JAVA_HOME)/include/win32 -DCYGWIN -D__int64='long long' $(CPPFLAGS)
#
# No use using -fpic. All code is position independent anyway.
#
override CFLAGS := -g $(CFLAGS)
else
#
# This works for Linux 9 on an 32-bit intel platform.
#
override CPPFLAGS := $(CPPFLAGS) -I$(JAVA_HOME)/include/$(PORTNAME)
override LDFLAGS := -g -shared -L$(JAVA_HOME)/jre/lib/i386/client -ljvm $(LDFLAGS)
override CFLAGS := -g -fpic $(CFLAGS)
endif

OBJS = Backend.o PgObject.o Exception.o HashMap.o Iterator.o Function.o SPI.o

subsys:
	$(MAKE) -C type all

ifeq ($(PORTNAME), cygwin)
all: subsys pljava.dll

pljava.dll: $(OBJS) type/SUBSYS.o
	dlltool --input-def ../../jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
	dlltool --export-all --add-stdcall-alias --output-def pljava.def $(OBJS) type/SUBSYS.o
	dllwrap -o pljava.dll --def pljava.def $(OBJS) type/SUBSYS.o $(top_builddir)/src/utils/dllinit.o -lpostgres -L. -ljvm

else
all: subsys libpljava.so

libpljava.so: $(OBJS) type/SUBSYS.o
	$(CC) $(LDFLAGS) -o libpljava.so $(OBJS) type/SUBSYS.o

endif

depend dep:
	$(MAKE) -C type $@
	$(CC) -MM $(CFLAGS) *.c >depend

clean:
	$(MAKE) -C type $@
	rm -f pljava.so $(OBJS)

ifeq (depend,$(wildcard depend))
include depend
endif
