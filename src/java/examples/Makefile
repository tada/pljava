#-------------------------------------------------------------------------
#
# Makefile for the deploy utility of pljava
#
#-------------------------------------------------------------------------
NAME 			:= examples
JAVADOCTITLE	:= 'PL/Java examples API Specification'
JAVAFLAGS		:= -classpath $(TARGETDIR)/classes/pljava

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cmf $(SRCDIR)/deployment/examples.manifest $@ $(CLASSES) \
	-C $(SRCDIR) deployment/examples.ddr
