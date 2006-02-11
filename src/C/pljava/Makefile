#-------------------------------------------------------------------------
# Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT
# found in the root folder of this project or at
# http://eng.tada.se/osprojects/COPYRIGHT.html
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
NAME	:= pljava

include $(MODULEROOT)/Makefile.global

ifeq ($(PORTNAME), win32)
	DLL_BUILD := 1
else
ifeq ($(PORTNAME), darwin)
	#
	# override with JAVAVM_FWX_ROOT=/... on command line
	# if something else is needed
	#
	JAVAVM_FWX_ROOT := /System/Library/Frameworks
	override CPPFLAGS := -I$(JAVAVM_FWX_ROOT)/JavaVM.framework/Headers/ $(CPPFLAGS)
else
ifeq ($(PORTNAME), ibmos2)
	DLL_BUILD := 1
else
	JRE_INCL := $(PORTNAME)
	ifeq ($(host_cpu), i686)
		JRE_CPU := i386
	else
	ifeq ($(host_cpu), x86_64)
		JRE_CPU := amd64
	else
		JRE_CPU := $(host_cpu)
	endif
	endif

endif
endif
endif
ifdef DLL_BUILD
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
		ifdef JAVA_HOME
			ifeq ($(PORTNAME), win32)
				JDK_HOME := $(subst \,/,$(JAVA_HOME))
			else
				JDK_HOME := $(JAVA_HOME)
			endif
		else
			ifeq ($(PORTNAME), ibmos2)
				JAVA_HOME_TEST_RESULT := $(echo "You must set JAVA_HOME")
			else
				JAVA_HOME_TEST_RESULT := $(error You must set JAVA_HOME)
			endif
		endif
		ifdef DLL_BUILD
			ifeq ($(PORTNAME),ibmos2)
				JRE_INCL := os2
			else
				JRE_INCL := win32
			endif
			JVM_LIB  := $(JDK_HOME)/jre/bin/client
		else
			JRE_LIB := $(JDK_HOME)/jre/lib/$(JRE_CPU)
			JVM_LIB := $(firstword $(shell /bin/ls -d \
				$(JRE_LIB)/client \
				$(JRE_LIB)/server \
				$(JRE_LIB)/jrockit \
				2> /dev/null))
		endif

		override CPPFLAGS := -I"$(JDK_HOME)/include" -I"$(JDK_HOME)/include/$(JRE_INCL)" $(CPPFLAGS)
		SHLIB_LINK = $(BE_DLLLIBS) -L. -L"$(JVM_LIB)" -ljvm
	endif
endif

.PHONY: checkjavahome extralibs

ifeq ($(JRE_INCL), win32)
#
# Mingw is a bit special in that we use a "normal" windows
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

checkjavahome:
	$(JAVA_HOME_TEST_RESULT)

extralibs: libjvm.dll.a

libjvm.dll.a: $(OBJS)
	dlltool --input-def $(SRCDIR)/jvm.def --kill-at --dllname jvm.dll --output-lib libjvm.dll.a
endif

endif

include $(PGSQLSRC)/Makefile.shlib

all: checkjavahome extralibs $(shlib)

install: install-lib

uninstall: uninstall-lib
