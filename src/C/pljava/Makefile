#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------

incldir = ../include
subdir = ../org.postgresql.pljava/src/C/pljava
top_builddir = ../../../../postgresql-7.4
bin_dir = ../../../bin
include ../Makefile.global

OBJS = Backend.o PgObject.o Exception.o HashMap.o Iterator.o Function.o SPI.o

subsys:
	$(MAKE) -C type all

ifeq ($(PORTNAME), cygwin)
#
# Cygwin style, builds a pljava.dll
#
all: subsys $(bin_dir)/pljava.dll

$(bin_dir)/pljava.dll: $(OBJS) type/SUBSYS.o
	dlltool --input-def ../../jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
	dlltool --export-all --add-stdcall-alias --output-def pljava.def $(OBJS) type/SUBSYS.o
	dllwrap -o $(bin_dir)/pljava.dll --def pljava.def $(OBJS) type/SUBSYS.o $(top_builddir)/src/utils/dllinit.o -lpostgres -L. -ljvm

clean:
	$(MAKE) -C type $@
	rm -f $(bin_dir)/pljava.dll pljava.def libjvm.dll.a $(OBJS)

else
#
# Unix style, builds a libpljava.so
#
all: subsys $(bin_dir)/libpljava.so

libpljava.so: $(OBJS) type/SUBSYS.o
	$(CC) $(LDFLAGS) -o $(bin_dir)/libpljava.so $(OBJS) type/SUBSYS.o

clean:
	$(MAKE) -C type $@
	rm -f $(bin_dir)/libpljava.so $(OBJS)

endif

depend dep:
	$(MAKE) -C type $@
	$(CC) -MM $(CFLAGS) *.c >depend

ifeq (depend,$(wildcard depend))
include depend
endif
