#-------------------------------------------------------------------------
#
# Makefile--
#    Makefile for pljava/type
#
#-------------------------------------------------------------------------

incldir = ../../include
subdir = ../org.postgresql.pljava/src/C/pljava/type
top_builddir = ../../../../../postgresql-7.4
include $(top_builddir)/src/Makefile.global

override CPPFLAGS := -DUSE_THREADS -DPKGLIBDIR=\"$(pkglibdir)\" -I$(incldir) -I$(JAVA_HOME)/include $(CPPFLAGS)
ifeq ($(PORTNAME), cygwin)
#
# Portname will not suffice. The Win32 JDK have no knowledge of cygwin
# GNU compiler doesn't know __int64, instead it uses long long.
#
override CPPFLAGS := -I$(JAVA_HOME)/include/win32 -DCYGWIN -D__int64='long long' $(CPPFLAGS)
#
# No use using -fpic. All code is position independent anyway.
#
override CFLAGS := -g $(CFLAGS)
else
#
# This works for Linux 9 on an 32-bit intel platform.
#
override CPPFLAGS := $(CPPFLAGS) -I$(JAVA_HOME)/include/$(PORTNAME)
override CFLAGS := -g -fpic $(CFLAGS)
endif

OBJS = Type.o \
	Void.o \
	Byte.o \
	Boolean.o \
	Short.o \
	Integer.o \
	Long.o \
	Float.o \
	Double.o \
	BigDecimal.o \
	Date.o \
	Time.o \
	Timestamp.o \
	String.o \
	Oid.o \
	AclId.o \
	byte_array.o \
	NativeStruct.o \
	ExecutionPlan.o \
	Portal.o \
	Relation.o \
	SPITupleTable.o \
	TriggerData.o \
	TupleDesc.o \
	Tuple.o \
	TupleTable.o \
	TupleTableSlot.o

all: SUBSYS.o

SUBSYS.o: $(OBJS)
	$(LD) $(LDREL) $(LDOUT) SUBSYS.o $(OBJS)

depend dep:
	$(CC) -MM $(CFLAGS) *.c >depend

clean: 
	rm -f SUBSYS.o $(OBJS)

ifeq (depend,$(wildcard depend))
include depend
endif
