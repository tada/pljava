#-------------------------------------------------------------------------
# Copyright (c) 2003, 2004 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT.
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
top_builddir := $(PGSQLDIR)
include $(PROJDIR)/src/C/Makefile.global

ifdef USE_GCJ
	GCJEXTRA=-gcj-
else
	GCJEXTRA=-
endif

RELNAME = $(TARGETDIR)/pljava-$(host_tuple)$(GCJEXTRA)pg$(PGSQL_MAJOR_VER)_$(PGSQL_MINOR_VER)-$(PLJAVA_VERSION)

release:
	@-mkdir -p $(RELNAME)
	@-rm -f $(RELNAME)/*
	@find $(TARGETDIR)		\( \
		   -name '*.jar'	\
		-o -name '*.so'		\
		-o -name '*.a'		\
		-o -name '*.dll'	\
		\) -a -not -path $(RELNAME)'/*' -exec cp {} $(RELNAME) \;
	@(cd $(TARGETDIR); $(TAR) cf $(RELNAME)/docs.tar docs)
	@(cd $(RELNAME); $(TAR) zcf $(RELNAME).tar.gz .)

source_tarball:
	@-mkdir -p $(TARGETDIR)
	@find . \( \
		   -name CVS \
		-o -name build \
		-o -name .cvsignore \
		\) -prune -o \( -type f -print \) > $(TARGETDIR)/sourcelist.txt
	@$(TAR) zcf $(TARGETDIR)/pljava_source_$(PLJAVA_VERSION).tar.gz -T $(TARGETDIR)/sourcelist.txt
	@-rm -f $(TARGETDIR)/sourcelist.txt

