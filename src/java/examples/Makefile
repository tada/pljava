#-------------------------------------------------------------------------
# Copyright (c) 2003, 2004 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT.
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
NAME 			:= examples
JAVADOCTITLE	:= 'PL/Java examples API Specification'
JAVAFLAGS		:= -classpath $(TARGETDIR)/classes/pljava

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cmf $(SRCDIR)/deployment/examples.manifest $@ $(CLASSES) \
	-C $(SRCDIR) deployment/examples.ddr
