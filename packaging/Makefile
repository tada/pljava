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

RELNAME = pljava-$(host_tuple)$(GCJEXTRA)pg$(PGSQL_MAJOR_VER).$(PGSQL_MINOR_VER)-$(PLJAVA_VERSION)

release:
	@-mkdir -p $(RELNAME)
	@-rm -f $(RELNAME)/*
	@find .	\(				\
		   -name '*.jar'	\
		-o -name '*.so'		\
		-o -name '*.a'		\
		-o -name '*.dll'	\
		\) -a -not -path $(RELNAME)'/*' -exec cp {} $(RELNAME) \;
	@$(TAR) cf $(RELNAME)/docs.tar docs
	(cd $(RELNAME); $(TAR) zcf ../distrib/$(RELNAME).tar.gz .)

source_tarball:
	@(cd ..; find . \( \
		   -name CVS \
		-o -name build \
		-o -name .cvsignore \
		\) -prune -o \( -type f -print \) > build/sourcelist.txt)
	$(TAR) zcf distrib/pljava-src-$(PLJAVA_VERSION).tar.gz -T sourcelist.txt)
	@-rm -f sourcelist.txt

JAR				:= jar

PUBLIC_ROOT		:= classes/pljava
PUBLIC_CLASSES	:= $(wildcard $(PUBLIC_ROOT)/org/postgresql/pljava/*.*)
PUBLIC_REL_CLASSES = $(foreach var,$(PUBLIC_CLASSES),$(subst $(PUBLIC_ROOT)/,,$(var)))

PUBLIC_SRC_ROOT	:= ../src/java/pljava
PUBLIC_SRCS		:= $(wildcard $(PUBLIC_SRC_ROOT)/org/postgresql/pljava/*.*)
PUBLIC_REL_SRCS = $(foreach var,$(PUBLIC_SRCS),$(subst $(PUBLIC_SRC_ROOT)/,,$(var)))

PUBLIC_NAME		:= pljava-public-$(PLJAVA_VERSION)
PUBLIC_BUNDLE	:= distrib/$(PUBLIC_NAME)-bundle.zip
JIRA_DESC		:= distrib/jira-desc.txt
BUNDLEDIR		:= maven_bundle
PUBLIC_JARFILE	:= $(PUBLIC_NAME).jar

maven_bundle: $(PUBLIC_BUNDLE) $(JIRA_DESC)

$(PUBLIC_BUNDLE): $(BUNDLEDIR)/LICENSE.txt $(BUNDLEDIR)/project.xml $(BUNDLEDIR)/$(PUBLIC_JARFILE)
	@(cd $(BUNDLEDIR); $(JAR) cMf ../$@ *)

$(BUNDLEDIR)/LICENSE.txt: $(PROJDIR)/COPYRIGHT
	@-mkdir -p $(BUNDLEDIR)
	@cp $< $(BUNDLEDIR)/LICENSE.txt

$(BUNDLEDIR)/project.xml: $(PROJDIR)/packaging/project.xml
	@-mkdir -p $(BUNDLEDIR)
	@sed -e 's/@PLJAVA_VERSION@/'$(PLJAVA_VERSION)'/' $< > $@

$(BUNDLEDIR)/$(PUBLIC_JARFILE): $(PUBLIC_CLASSES)
	@-mkdir -p $(BUNDLEDIR)
	@(cd $(PUBLIC_SRC_ROOT); $(JAR) cMf ../../../build/$(PUBLIC_ROOT)/src.zip $(PUBLIC_REL_SRCS)) 
	@(cd $(PUBLIC_ROOT); $(JAR) cf ../../$@ src.zip $(PUBLIC_REL_CLASSES))

$(JIRA_DESC): $(PROJDIR)/packaging/jira-desc.txt
	@sed -e 's/@PLJAVA_VERSION@/'$(PLJAVA_VERSION)'/' $< > $@
