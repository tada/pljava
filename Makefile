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
export PROJDIR   := $(shell pwd -P)
export TARGETDIR := $(PROJDIR)/bin/build
export PGSQLDIR  := $(PROJDIR)/../pgsql

.PHONY: all clean install uninstall depend

all clean install uninstall depend:
	@mkdir -p $(TARGETDIR)/pljava
	@$(MAKE) -r -C $(TARGETDIR)/pljava \
	-f $(PROJDIR)/src/C/pljava/Makefile \
	MODULEROOT=$(PROJDIR)/src/C $@
