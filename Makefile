#-------------------------------------------------------------------------
#
# Top level Makefile for pljava
#
# The following options are recognized (aside from normal options like
# CFLAGS etc.)
#
#   PGSQLDIR=<pgsql source>  Override the default $(PROJDIR)/../pgsql
#   USE_GCJ=1                Builds a shared object file containing both
#                            C and Java code. Requires GCJ 3.4 or later.
#
#-------------------------------------------------------------------------
export PROJDIR		:= $(shell pwd -P)
export PGSQLDIR		:= $(PROJDIR)/../pgsql

export TARGETDIR	:= $(PROJDIR)/build
export OBJDIR		:= $(TARGETDIR)/objs
export JNIDIR		:= $(TARGETDIR)/jni
export CLASSDIR		:= $(TARGETDIR)/classes

.PHONY: all clean javadoc install uninstall depend \
	c_all c_install c_uninstall c_depend \
	pljava_all pljava_javadoc \
	deploy_all deploy_javadoc \
	examples_all examples_javadoc

all: pljava_all deploy_all c_all examples_all

install: c_install

uninstall: c_uninstall

depend: c_depend

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
