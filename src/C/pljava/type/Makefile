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

override CPPFLAGS := -I$(incldir) -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/$(PORTNAME) $(CPPFLAGS)
override CFLAGS := -g -fpic $(CFLAGS)

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
	Relation.o \
	TriggerData.o \
	TupleDesc.o \
	Tuple.o

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
