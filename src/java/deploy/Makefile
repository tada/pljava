#-------------------------------------------------------------------------
#
# Makefile for the deploy utility of pljava
#
#-------------------------------------------------------------------------
NAME			:= deploy
JAVADOCTITLE	:= 'PL/Java deployer API Specification'

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cmf $(SRCDIR)/META-INF/manifest.txt $@ $(CLASSES)
