#-------------------------------------------------------------------------
#
# Makefile for the tests of PL/Java
#
#-------------------------------------------------------------------------
NAME 			:= test
JAVADOCTITLE	:= 'PL/Java test API Specification'

include $(MODULEROOT)/Makefile.global

all: $(JARFILE)

$(JARFILE): .timestamp
	$(JAR) cf $@ $(CLASSES)
