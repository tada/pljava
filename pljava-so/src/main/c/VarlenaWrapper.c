/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */

#include "org_postgresql_pljava_internal_VarlenaWrapper_Output_State.h"
#include "pljava/VarlenaWrapper.h"
#include "pljava/DualState.h"

#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"

#if PG_VERSION_NUM < 80300
#define VARSIZE_ANY_EXHDR(PTR) (VARSIZE(PTR) - VARHDRSZ)
#define SET_VARSIZE(PTR, len) VARATT_SIZEP(PTR) = len & VARATT_MASK_SIZE
#endif

#define INITIALSIZE 1024

static jclass s_VarlenaWrapper_class;
static jmethodID s_VarlenaWrapper_adopt;

static jclass s_VarlenaWrapper_Input_class;
static jclass s_VarlenaWrapper_Output_class;

static jmethodID s_VarlenaWrapper_Input_init;

static jmethodID s_VarlenaWrapper_Output_init;

/*
 * For VarlenaWrapper.Output, define a dead-simple "expanded object" format
 * consisting of linked allocated blocks, so if a long value is being written,
 * it does not have to get repeatedly reallocated and copied. The "expanded
 * object" form is a valid sort of PostgreSQL Datum, and can be passed around
 * in that form, and reparented between memory contexts with different
 * lifetimes; when a time comes that PostgreSQL needs it in a 'flattened'
 * form, it will use these 'methods' to flatten it, and that's when the one
 * final reallocation and copy will happen.
 */

#if PG_VERSION_NUM < 90500
/*
 * There aren't 'expanded' varlenas yet. Copy some defs (in simplified form)
 * and pretend there are.
 */
typedef struct ExpandedObjectHeader ExpandedObjectHeader;

typedef Size (*EOM_get_flat_size_method) (ExpandedObjectHeader *eohptr);
typedef void (*EOM_flatten_into_method) (ExpandedObjectHeader *eohptr,
										  void *result, Size allocated_size);

typedef struct ExpandedObjectMethods
{
	EOM_get_flat_size_method get_flat_size;
	EOM_flatten_into_method flatten_into;
} ExpandedObjectMethods;

struct ExpandedObjectHeader
{
	int32 magic;
	MemoryContext eoh_context;
};

#define EOH_init_header(eohptr, methods, obj_context) \
	do {(eohptr)->magic = -1; (eohptr)->eoh_context = (obj_context);} while (0)

#define EOHPGetRWDatum(eohptr) (eohptr)
#define DatumGetEOHP(d) (d)
#endif

static Size VOS_get_flat_size(ExpandedObjectHeader *eohptr);
static void VOS_flatten_into(ExpandedObjectHeader *eohptr,
		void *result, Size allocated_size);

static const ExpandedObjectMethods VOS_methods =
{
	VOS_get_flat_size,
	VOS_flatten_into
};

typedef struct ExpandedVarlenaOutputStreamNode ExpandedVarlenaOutputStreamNode;

struct ExpandedVarlenaOutputStreamNode
{
	ExpandedVarlenaOutputStreamNode *next;
	Size size;
};

typedef struct ExpandedVarlenaOutputStreamHeader
{
	ExpandedObjectHeader hdr;
	ExpandedVarlenaOutputStreamNode *tail;
	Size total_size;
} ExpandedVarlenaOutputStreamHeader;



/*
 * Create and return a VarlenaWrapper.Input allowing Java to read the content
 * of an existing Datum d, which must be a varlena type (assumed, not checked
 * here).
 *
 * The datum will be copied (detoasting if need be) into a memory context with
 * parent as its parent, so it can be efficiently reparented later if adopted,
 * and the VarlenaWrapper will be associated with the ResourceOwner ro, which
 * determines its lifespan (if not adopted). The ResourceOwner needs to be one
 * that will be released no later than the memory context itself.
 */
jobject pljava_VarlenaWrapper_Input(
	Datum d, MemoryContext parent, ResourceOwner ro)
{
	jobject vr;
	jobject dbb;
	MemoryContext mc;
	MemoryContext prevcxt;
	struct varlena *copy;
	Ptr2Long p2lro;
	Ptr2Long p2lcxt;
	Ptr2Long p2ldatum;

	mc = AllocSetContextCreate(parent, "PL/Java VarlenaWrapper.Input",
		 ALLOCSET_START_SMALL_SIZES);

	prevcxt = MemoryContextSwitchTo(mc);
	copy = PG_DETOAST_DATUM_COPY(d);
	MemoryContextSwitchTo(prevcxt);

	p2lro.longVal = 0L;
	p2lcxt.longVal = 0L;
	p2ldatum.longVal = 0L;

	p2lro.ptrVal = ro;
	p2lcxt.ptrVal = mc;
	p2ldatum.ptrVal = copy;

	dbb = JNI_newDirectByteBuffer(VARDATA(copy), VARSIZE_ANY_EXHDR(copy));

	vr = JNI_newObjectLocked(s_VarlenaWrapper_Input_class,
		s_VarlenaWrapper_Input_init, pljava_DualState_key(),
		p2lro.longVal, p2lcxt.longVal, p2ldatum.longVal, dbb);
	JNI_deleteLocalRef(dbb);

	return vr;
}

/*
 * Create and return a VarlenaWrapper.Output, initially empty, into which Java
 * can write.
 *
 * The datum will be assembled in the memory context mc, and the VarlenaWrapper
 * will be associated with the ResourceOwner ro, which determines its lifespan.
 * The ResourceOwner needs to be one that will be released no later than
 * the memory context itself.
 *
 * After Java has written the content, native code can obtain the Datum by
 * calling pljava_VarlenaWrapper_Output_adopt().
 */
jobject pljava_VarlenaWrapper_Output(MemoryContext parent, ResourceOwner ro)
{
	ExpandedVarlenaOutputStreamHeader *evosh;
	jobject vos;
	jobject dbb;
	MemoryContext mc;
	Ptr2Long p2lro;
	Ptr2Long p2lcxt;
	Ptr2Long p2ldatum;

	mc = AllocSetContextCreate(parent, "PL/Java VarlenaWrapper.Output",
		 ALLOCSET_START_SMALL_SIZES);
	/*
	 * Allocate an initial chunk sized to contain the expanded V.O.S. header,
	 * plus the header and data for one node to hold INITIALSIZE data bytes.
	 */
	evosh = MemoryContextAlloc(mc,
		sizeof *evosh + sizeof *(evosh->tail) + INITIALSIZE);
	/*
	 * Initialize the expanded object header and its pointer to the first node.
	 */
	EOH_init_header(&(evosh->hdr), &VOS_methods, mc);
	evosh->total_size = VARHDRSZ;
	evosh->tail = (ExpandedVarlenaOutputStreamNode *)(evosh + 1);
	/*
	 * Initialize that first node.
	 */
	evosh->tail->next = evosh->tail;
	/* evosh->tail->size will be filled in by _nextBuffer() later */

	p2lro.longVal = 0L;
	p2lcxt.longVal = 0L;
	p2ldatum.longVal = 0L;

	p2lro.ptrVal = ro;
	p2lcxt.ptrVal = mc;
	p2ldatum.ptrVal = DatumGetPointer(EOHPGetRWDatum(&(evosh->hdr)));

	/*
	 * The data bytes begin right after the node header struct.
	 */
	dbb = JNI_newDirectByteBuffer(evosh->tail + 1, INITIALSIZE);

	vos = JNI_newObjectLocked(s_VarlenaWrapper_Output_class,
			s_VarlenaWrapper_Output_init, pljava_DualState_key(),
			p2lro.longVal, p2lcxt.longVal, p2ldatum.longVal, dbb);
	JNI_deleteLocalRef(dbb);

	return vos;
}

/*
 * Adopt a VarlenaWrapper (if Output, after Java code has written and closed it)
 * and leave it no longer accessible from Java. It may be an 'expanded' datum,
 * in PG 9.5+ where there are such things. Otherwise, it will be an ordinary
 * flat one (the ersatz 'expanded' form used internally here then being only an
 * implementation detail, not exposed to the caller); its memory context is
 * unchanged.
 */
Datum pljava_VarlenaWrapper_adopt(jobject vlw)
{
	Ptr2Long p2l;
#if PG_VERSION_NUM < 90500
	ExpandedObjectHeader *eohptr;
	Size final_size;
	void *final_result;
#endif

	p2l.longVal = JNI_callLongMethodLocked(vlw, s_VarlenaWrapper_adopt,
					pljava_DualState_key());
#if PG_VERSION_NUM >= 90500
	return PointerGetDatum(p2l.ptrVal);
#else
	eohptr = p2l.ptrVal;
	if ( -1 != eohptr->magic )
		return PointerGetDatum(eohptr);
	final_size = VOS_get_flat_size(eohptr);
	final_result = MemoryContextAlloc(eohptr->eoh_context, final_size);
	VOS_flatten_into(eohptr, final_result, final_size);
	return PointerGetDatum(final_result);
#endif
}

static Size VOS_get_flat_size(ExpandedObjectHeader *eohptr)
{
	ExpandedVarlenaOutputStreamHeader *evosh =
		(ExpandedVarlenaOutputStreamHeader *)eohptr;
	return evosh->total_size;
}

static void VOS_flatten_into(ExpandedObjectHeader *eohptr,
		void *result, Size allocated_size)
{
	ExpandedVarlenaOutputStreamHeader *evosh =
		(ExpandedVarlenaOutputStreamHeader *)eohptr;
	ExpandedVarlenaOutputStreamNode *node = evosh->tail;
#if PG_VERSION_NUM < 90500
	ExpandedVarlenaOutputStreamNode *next;
#endif

	Assert(allocated_size == evosh->total_size);
	SET_VARSIZE(result, allocated_size);
	result = VARDATA(result);

	do
	{
		node = node->next;
		memcpy(result, node + 1, node->size);
		result = (char *)result + node->size;
	}
	while ( node != evosh->tail );

#if PG_VERSION_NUM < 90500
	/*
	 * It's been flattened into the same context; the original nodes can be
	 * freed so the 2x memory usage doesn't last longer than necessary. Freeing
	 * them retail isn't ideal, but this is back-compatibility code. Remember
	 * the first one wasn't a separate allocation.
	 */
	 node = node->next; /* this is the head, the one that can't be pfreed */
	 evosh->tail = node; /* tail is now head, the non-pfreeable node */
	 node = node->next;
	 while ( node != evosh->tail )
	 {
		next = node->next;
		pfree(node);
		node = next;
	 }
	 pfree(evosh);
#endif
}

void pljava_VarlenaWrapper_initialize(void)
{
	jclass clazz;
	JNINativeMethod methods[] =
	{
		{
		"_nextBuffer",
		"(JII)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Output_00024State__1nextBuffer
		},
		{ 0, 0, 0 }
	};

	s_VarlenaWrapper_class =
		(jclass)JNI_newGlobalRef(PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper"));
	s_VarlenaWrapper_Input_class =
		(jclass)JNI_newGlobalRef(PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Input"));
	s_VarlenaWrapper_Output_class =
		(jclass)JNI_newGlobalRef(PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Output"));

	s_VarlenaWrapper_Input_init = PgObject_getJavaMethod(
		s_VarlenaWrapper_Input_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;"
		"JJJLjava/nio/ByteBuffer;)V");

	s_VarlenaWrapper_Output_init = PgObject_getJavaMethod(
		s_VarlenaWrapper_Output_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;"
		"JJJLjava/nio/ByteBuffer;)V");

	s_VarlenaWrapper_adopt = PgObject_getJavaMethod(
		s_VarlenaWrapper_class, "adopt",
		"(Lorg/postgresql/pljava/internal/DualState$Key;)J");

	clazz = (jclass)JNI_newGlobalRef(PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Output$State"));
	PgObject_registerNatives2(clazz, methods);
	JNI_deleteLocalRef(clazz);
}

/*
 * Class:     org_postgresql_pljava_internal_VarlenaWrapper_Output_State
 * Method:    _nextBuffer
 * Signature: (JII)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Output_00024State__1nextBuffer
  (JNIEnv *env, jobject _this,
   jlong varlenaPtr, jint currentBufPosition, jint desiredCapacity)
{
	ExpandedVarlenaOutputStreamHeader *evosh;
	ExpandedVarlenaOutputStreamNode *node;
	Ptr2Long p2l;
	Datum d;
	jobject dbb;

	p2l.longVal = varlenaPtr;
	d = PointerGetDatum(p2l.ptrVal);

	evosh = (ExpandedVarlenaOutputStreamHeader *)DatumGetEOHP(d);
	evosh->tail->size  = currentBufPosition;
	evosh->total_size += currentBufPosition;

	if ( 0 == desiredCapacity )
		return NULL;

	BEGIN_NATIVE
	/*
	 * This adjustment of desiredCapacity is arbitrary and amenable to
	 * performance experimentation. For initial signs of life, ignore the
	 * desiredCapacity hint completely and use a hardwired size.
	 */
	desiredCapacity = 8180;

	node = (ExpandedVarlenaOutputStreamNode *)
			MemoryContextAlloc(evosh->hdr.eoh_context, desiredCapacity);
	node->next = evosh->tail->next;
	evosh->tail->next = node;
	evosh->tail = node;

	dbb = JNI_newDirectByteBuffer(node + 1, desiredCapacity - sizeof *node);
	END_NATIVE

	return dbb;
}
