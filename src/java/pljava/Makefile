#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava
#
#-------------------------------------------------------------------------
NAME		:= pljava

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
all: $(OBJDIR)/$(NAME)_jar.o $(JNIDIR)/.timestamp

$(OBJDIR)/$(NAME)_jar.o: .timestamp
	@-mkdir -p $(@D)
	$(GCJ) -g -c -fjni -o $@ $(SRCS)
else
all: $(JARFILE) $(JNIDIR)/.timestamp

$(JARFILE): .timestamp
	$(JAR) cf $@ $(CLASSES)
endif

$(JNIDIR)/.timestamp: $(JNISRCS)
	@-mkdir -p $(@D)
	$(JAVAH) -classpath . -d $(@D) $(JNI_CLASSES) java.sql.Types
	touch $@
