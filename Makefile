#-------------------------------------------------------------------------
# Copyright (c) 2003, 2004 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT.
#
# @author Thomas Hallgren
#
# Top level Makefile for PL/Java
#
# The following options are recognized (aside from normal options like
# CFLAGS etc.)
#
#   PGSQLDIR=<pgsql source>  Override the default $(PROJDIR)/../pgsql
#   USE_GCJ=1                Builds a shared object file containing both
#                            C and Java code. Requires GCJ 3.4 or later.
#
#-------------------------------------------------------------------------
export PROJDIR			:= $(shell pwd -P)
export PGSQLDIR			:= $(PROJDIR)/../pgsql

export TARGETDIR		:= $(PROJDIR)/build
export OBJDIR			:= $(TARGETDIR)/objs
export JNIDIR			:= $(TARGETDIR)/jni
export CLASSDIR			:= $(TARGETDIR)/classes
export PLJAVA_MAJOR_VER	:= 1
export PLJAVA_MINOR_VER	:= 0
export PLJAVA_PATCH_VER	:= 0b4
export PLJAVA_VERSION	:= $(PLJAVA_MAJOR_VER)_$(PLJAVA_MINOR_VER)_$(PLJAVA_PATCH_VER)
export TAR				:= /bin/tar

OS := $(shell uname -s)
MACHINE := $(shell uname -m)

.PHONY: all clean docs javadoc source_tarball install uninstall depend release \
	c_all c_install c_uninstall c_depend \
	pljava_all pljava_javadoc \
	deploy_all deploy_javadoc \
	examples_all examples_javadoc

all: pljava_all deploy_all c_all examples_all

install: c_install

uninstall: c_uninstall

depend: c_depend

docs:
	@-mkdir -p $(TARGETDIR)
	@find docs \( \
		   -name CVS \
		-o -name .cvsignore \
		\) -prune -o \( -type f -print \) | cpio -pmdu $(TARGETDIR)
	@cp COPYRIGHT $(TARGETDIR)/docs

javadoc: pljava_javadoc deploy_javadoc examples_javadoc

clean:
	@-rm -rf $(TARGETDIR)

pljava_all pljava_javadoc: pljava_%:
	@-mkdir -p $(CLASSDIR)/pljava
	@$(MAKE) -r -C $(CLASSDIR)/pljava -f $(PROJDIR)/src/java/pljava/Makefile \
	MODULEROOT=$(PROJDIR)/src/java $*

deploy_all deploy_javadoc: deploy_%:
	@-mkdir -p $(CLASSDIR)/deploy
	@$(MAKE) -r -C $(CLASSDIR)/deploy -f $(PROJDIR)/src/java/deploy/Makefile \
	MODULEROOT=$(PROJDIR)/src/java $*

examples_all: examples_%: pljava_all
	@-mkdir -p $(CLASSDIR)/examples
	@$(MAKE) -r -C $(CLASSDIR)/examples -f $(PROJDIR)/src/java/examples/Makefile \
	MODULEROOT=$(PROJDIR)/src/java $*

c_all c_install c_uninstall c_depend: c_%:
	@-mkdir -p $(OBJDIR)
	@$(MAKE) -r -C $(OBJDIR) -f $(PROJDIR)/src/C/pljava/Makefile \
	MODULEROOT=$(PROJDIR)/src/C $*

source_tarball:
	@-mkdir -p $(TARGETDIR)/distrib
	@$(MAKE) -r -f $(PROJDIR)/packaging/Makefile $@

release: all docs javadoc
	@-mkdir -p $(TARGETDIR)/distrib
	@$(MAKE) -r -f $(PROJDIR)/packaging/Makefile $@
