#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------
NAME	:= pljava

include $(MODULEROOT)/Makefile.global

ifeq ($(PORTNAME), win32)
	JRE_INCL := win32
	JRE_LIB  := bin/client
else
ifeq ($(PORTNAME), cygwin)
	JRE_INCL := win32
	JRE_LIB  := bin/client
else
	JRE_INCL := $(PORTNAME)
	ifeq ($(host_cpu), i686)
		JRE_LIB := lib/i386/client
	else
		JRE_LIB := lib/$(host_cpu)/client
	endif
endif
endif

ifdef USE_GCJ
	override CPPFLAGS := -DGCJ $(CPPFLAGS)
	SHLIB_LINK = $(BE_DLLLIBS) -lgcj -lwsock32
else
	override CPPFLAGS := -I$(JDK_HOME)/include -I$(JDK_HOME)/include/$(JRE_INCL) $(CPPFLAGS)
	SHLIB_LINK = $(BE_DLLLIBS) -L. -L$(JDK_HOME)/jre/$(JRE_LIB) -ljvm
endif

SO_MAJOR_VERSION := 1
SO_MINOR_VERSION := 0

.PHONY: extralibs

ifeq ($(JRE_INCL), win32)
#
# Cygwin/Mingw is a bit special in that we use a "normal" windows
# port of the Java Runtime Environment (unless we use gcj
# of course). The headers etc. for the JRE is windows style
# and contains __int64. GNU compiler doesn't know __int64,
# instead it uses long long.
#
ifndef USE_GCJ

override CPPFLAGS := -D__int64='long long' $(CPPFLAGS)

# We need to be able to link with the JRE jvm.dll
#
EXTRAS := libjvm.dll

extralibs: libjvm.dll

libjvm.dll: $(OBJS)
	dlltool --input-def $(SRCDIR)/jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
endif

endif

all: extralibs all-lib

clean: clean-lib
	rm -f $(OBJS) $(EXTRAS)

install: install-lib

uninstall: uninstall-lib

include $(PGSQLDIR)/src/Makefile.shlib
