#-------------------------------------------------------------------------
#
# Makefile for the deploy utility of pljava
#
#-------------------------------------------------------------------------
NAME := deploy

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cmf $(SRCDIR)/META-INF/manifest.txt $@ $(CLASSES)
