#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------
NAME	:= pljava

include $(MODULEROOT)/Makefile.global

ifeq ($(PORTNAME), win32)
	DLL_BUILD := 1
else
ifeq ($(PORTNAME), cygwin)
	DLL_BUILD := 1
	override CPPFLAGS := -DCYGWIN $(CPPFLAGS)
else
ifeq ($(PORTNAME), darwin)
	#
	# override with JAVAVM_FWX_ROOT=/... on command line
	# if something else is needed
	#
	JAVAVM_FWX_ROOT := /System/Library/Frameworks
	override CPPFLAGS := -I$(JAVAVM_FWX_ROOT)/JavaVM.framework/Headers/ $(CPPFLAGS)
else
	JRE_INCL := $(PORTNAME)
	ifeq ($(host_cpu), i686)
		JRE_LIB := lib/i386/client
	else
		JRE_LIB := lib/$(host_cpu)/client
	endif
endif
endif
endif

ifdef DLL_BUILD
	JRE_INCL := win32
	JRE_LIB  := bin/client

	# This works but is not very nice since it affects all
	# DLLTOOL invocations in Makefile.shlib. A custom flag
	# is requested from postgres.
	#
	override DLLTOOL := $(DLLTOOL) --add-stdcall-alias
endif

ifdef USE_GCJ
	override CPPFLAGS := -DGCJ $(CPPFLAGS)
	SHLIB_LINK = $(BE_DLLLIBS) -lgcj
ifdef DLL_BUILD
	SHLIB_LINK += -lws2_32
endif
	OBJS += pljava_jar.o

else
ifeq ($(PORTNAME), darwin)
	SHLIB_LINK = $(BE_DLLLIBS) -L. -framework JavaVM
else
	override CPPFLAGS := -I$(JDK_HOME)/include -I$(JDK_HOME)/include/$(JRE_INCL) $(CPPFLAGS)
	SHLIB_LINK = $(BE_DLLLIBS) -L. -L$(JDK_HOME)/jre/$(JRE_LIB) -ljvm
endif
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
EXTRAS := libjvm.dll.a

extralibs: libjvm.dll.a

libjvm.dll.a: $(OBJS)
	dlltool --input-def $(SRCDIR)/jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
endif

endif

all: extralibs all-lib

install: install-lib

uninstall: uninstall-lib

include $(PGSQLDIR)/src/Makefile.shlib
