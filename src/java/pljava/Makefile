#-------------------------------------------------------------------------
# Copyright (c) 2003, 2004 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT.
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
NAME			:= pljava
JAVADOCTITLE	:= 'PL/Java v1.0.0 API Specification'

include $(MODULEROOT)/Makefile.global

SRCDIR_USC	:= $(subst .,^,$(SRCDIR))
mkclsrc		= $(subst ^,.,$(subst .,/,$(1:%=$(SRCDIR_USC)/%^java)))
JAVAH		:= javah
INTPKG		:= org.postgresql.pljava.internal

JNI_CLASSES := \
	$(INTPKG).Backend \
	$(INTPKG).SPI \
	$(INTPKG).AclId \
	$(INTPKG).ErrorData \
	$(INTPKG).Oid \
	$(INTPKG).NativeStruct \
	$(INTPKG).ExecutionPlan \
	$(INTPKG).HeapTupleHeader \
	$(INTPKG).Portal \
	$(INTPKG).Relation \
	$(INTPKG).SPITupleTable \
	$(INTPKG).TriggerData \
	$(INTPKG).Tuple \
	$(INTPKG).TupleDesc \
	$(INTPKG).TupleTable \
	$(INTPKG).TupleTableSlot

JNISRCS := $(call mkclsrc,$(JNI_CLASSES))

ifdef USE_GCJ
# We include both the pljava_jar.o and the pjlava.jar here although
# pljava doesn't need the latter. Most java compilers will need it
# in order to compile triggers and functions later.
#
all: $(OBJDIR)/$(NAME)_jar.o $(JARFILE) $(JNIDIR)/.timestamp

$(OBJDIR)/$(NAME)_jar.o: .timestamp
	@-mkdir -p $(@D)
	@echo $(GCJ) -g -c -fjni -o $@ '<java sources>'
	@$(GCJ) -g -c -fjni -o $@ $(SRCS)
else
all: $(JARFILE) $(JNIDIR)/.timestamp

endif

$(JARFILE): .timestamp
	$(JAR) cf $@ .

$(JNIDIR)/.timestamp: $(JNISRCS)
	@-mkdir -p $(@D)
	@echo $(JAVAH) -classpath . -d $(@D) '<jni classes>'
	@$(JAVAH) -classpath . -d $(@D) $(JNI_CLASSES) java.sql.Types
	@touch $@
