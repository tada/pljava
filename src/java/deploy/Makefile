#-------------------------------------------------------------------------
# Copyright (c) 2003, 2004 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT.
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
NAME			:= deploy
JAVADOCTITLE	:= 'PL/Java deployer API Specification'

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cmf $(SRCDIR)/META-INF/manifest.txt $@ $(CLASSES)
