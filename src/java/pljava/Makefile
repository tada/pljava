#-------------------------------------------------------------------------
# Copyright (c) 2004, 2005 TADA AB - Taby Sweden
# Distributed under the terms shown in the file COPYRIGHT
# found in the root folder of this project or at
# http://eng.tada.se/osprojects/COPYRIGHT.html
#
# @author Thomas Hallgren
#-------------------------------------------------------------------------
NAME			:= pljava
JAVADOCTITLE	:= 'PL/Java v$(PLJAVA_MAJOR_VER).$(PLJAVA_MINOR_VER) API Specification'

include $(MODULEROOT)/Makefile.global

SRCDIR_USC	:= $(subst .,^,$(SRCDIR))
mkclsrc		= $(subst ^,.,$(subst .,/,$(1:%=$(SRCDIR_USC)/%^java)))
JAVAH		:= javah

JNI_CLASSES := \
	org.postgresql.pljava.internal.Backend \
	org.postgresql.pljava.internal.SPI \
	org.postgresql.pljava.internal.AclId \
	org.postgresql.pljava.internal.ErrorData \
	org.postgresql.pljava.internal.Oid \
	org.postgresql.pljava.internal.ExecutionPlan \
	org.postgresql.pljava.internal.JavaWrapper \
	org.postgresql.pljava.internal.LargeObject \
	org.postgresql.pljava.internal.PgSavepoint \
	org.postgresql.pljava.internal.Portal \
	org.postgresql.pljava.internal.Relation \
	org.postgresql.pljava.internal.Session \
	org.postgresql.pljava.internal.SubXactListener \
	org.postgresql.pljava.internal.TriggerData \
	org.postgresql.pljava.internal.Tuple \
	org.postgresql.pljava.internal.TupleDesc \
	org.postgresql.pljava.internal.XactListener \
	org.postgresql.pljava.jdbc.Invocation \
	org.postgresql.pljava.jdbc.SingleRowReader \
	org.postgresql.pljava.jdbc.SQLInputFromChunk \
	org.postgresql.pljava.jdbc.SQLOutputToChunk \
	org.postgresql.pljava.jdbc.SQLInputFromTuple \
	org.postgresql.pljava.jdbc.SQLOutputToTuple

JNISRCS := $(call mkclsrc,$(JNI_CLASSES))

ifdef USE_GCJ
# We include both the pljava_jar.o and the pjlava.jar here although
# pljava doesn't need the latter. Most java compilers will need it
# in order to compile triggers and functions later.
#
JNIHDRS = $(patsubst %,$(JNIDIR)/%.h,$(subst .,_,$(JNI_CLASSES)))
mkcname = $(subst _,.,$(1:$(JNIDIR)/%.h=%))

all: $(OBJDIR)/$(NAME)_jar.o $(JARFILE) $(JNIDIR)/.timestamp

$(OBJDIR)/$(NAME)_jar.o: .timestamp
	@-mkdir -p $(@D)
	@echo $(GCJ) -c -fpic -fjni -o $@ '<java sources>'
	@$(GCJ) -c -fpic -fjni -o $@ $(SRCS)

# gcjh fails to clear its function name cache between files (see
# gcc bugzilla #17575) so we must do each file separately to
# avoid name ambiguities that causes names to be generated with
# parameter type info.
#
$(JNIHDRS): $(JNISRCS)
	@-mkdir -p $(@D)
	@gcjh -jni --classpath=. -d $(@D) $(call mkcname,$@)

# gcjh generated headers don't define final constants (see gcc
# bugzilla #16843) so we must copy our own version of java.sql.Types. 
#
$(JNIDIR)/.timestamp: $(JNIHDRS)
	@-mkdir -p $(@D)
	@cp $(PROJDIR)/fixes/gcj/java_sql_Types.h $(@D)
	@touch $@
else
all: $(JARFILE) $(JNIDIR)/.timestamp

$(JNIDIR)/.timestamp: $(JNISRCS)
	@-mkdir -p $(@D)
	@echo javah -classpath . -d $(@D) '<jni classes>'
	@javah -classpath . -d $(@D) $(JNI_CLASSES) java.sql.Types
	@touch $@

endif

$(JARFILE): .timestamp
	$(JAR) cf $@ .
