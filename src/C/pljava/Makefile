#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------

MODULE=pljava
include $(MODULEROOT)/Makefile.global

ifndef USE_GCJ
	ifeq ($JAVA_HOME),)
		USE_GCJ := 1
	endif
endif

ifdef USE_DLL
#
# Cygwin/MinGW style, builds a pljava.dll
#
ifdef USE_GCJ
	override CPPFLAGS := -DGCJ $(CPPFLAGS)
else
	#
	# GNU compiler doesn't know __int64, instead it uses long long.
	#
	override CPPFLAGS := -D__int64='long long' $(CPPFLAGS)
	override CPPFLAGS := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/win32 $(CPPFLAGS)
endif

pljava: $(OBJS) pljava.dll

ifndef USE_GCJ
pljava.dll: $(OBJS)
	dlltool --input-def $(SRCDIR)/jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
	dlltool --export-all --add-stdcall-alias --output-def pljava.def $(OBJS)
	dllwrap -o pljava.dll --def pljava.def $(OBJS) \
		$(PGSQLDIR)/src/utils/dllinit.o \
		-L$(PGSQLDIR)/src/backend -L$(PGSQLDIR)/src/port -lpostgres -L. -ljvm $(LIBS)
else
pljava.dll: $(OBJS)
	dlltool --export-all --add-stdcall-alias --output-def pljava.def $(OBJS)
	dllwrap -o pljava.dll --def pljava.def $(OBJS) \
		$(PGSQLDIR)/src/utils/dllinit.o \
		-L$(PGSQLDIR)/src/backend -L$(PGSQLDIR)/src/port -lpostgres -lgcj -liconv $(LIBS)
	dlltool --input-def pljava.def --kill-at --dllname pljava.dll --output-lib libpljava.dll.a
endif

clean:
	rm -f pljava.dll pljava.def libjvm.dll.a $(OBJS)

else
#
# Unix style, builds a libpljava.so
#
ifeq ($(host_cpu), i686)
	jre_cpu = i386
else
	jre_cpu = $(host_cpu)
endif

ifndef USE_GCJ
	override CPPFLAGS := -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(PORTNAME) $(CPPFLAGS)
else
	override CFLAGS  := -fpic $(CFLAGS)
	override LDFLAGS := -L$(JAVA_HOME)/jre/lib/$(JRE_CPU)/client $(LDFLAGS)
endif

pljava: libpljava.so

libpljava.so: $(OBJS)
	$(CC) $(LDFLAGS) -L$(PGSQLDIR)/src/backend -L$(PGSQLDIR)/src/port $(LIBS) -o libpljava.so $(OBJS)

clean:
	$(MAKE) -C type $@
	rm -f libpljava.so $(OBJS)

endif
