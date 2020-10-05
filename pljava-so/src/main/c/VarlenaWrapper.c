/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
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

#include <postgres.h>

#if PG_VERSION_NUM < 130000
#include <access/tuptoaster.h>
#define detoast_external_attr heap_tuple_fetch_attr
#else
#include <access/detoast.h>
#endif

#include <utils/datum.h>

#if PG_VERSION_NUM < 80400
#define RegisterSnapshotOnOwner(s,o) NULL
#else
#include <utils/snapshot.h>
#include <utils/snapmgr.h>
#endif

#include "org_postgresql_pljava_internal_VarlenaWrapper_Input_State.h"
#include "org_postgresql_pljava_internal_VarlenaWrapper_Output_State.h"
#include "pljava/VarlenaWrapper.h"
#include "pljava/DualState.h"

#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"

#if PG_VERSION_NUM < 90600
#define GetOldestSnapshot() NULL
#endif

#if PG_VERSION_NUM < 90400
/*
 * There aren't 'indirect' varlenas yet, IS_EXTERNAL_ONDISK is just IS_EXTERNAL,
 * and VARATT_EXTERNAL_GET_POINTER is private inside tuptoaster.c; copy it here.
 */
#define VARATT_IS_EXTERNAL_ONDISK VARATT_IS_EXTERNAL
#define VARATT_EXTERNAL_GET_POINTER(toast_pointer, attr) \
do { \
	varattrib_1b_e *attre = (varattrib_1b_e *) (attr); \
	memcpy(&(toast_pointer), VARDATA_EXTERNAL(attre), sizeof(toast_pointer)); \
} while (0)
#endif

#if PG_VERSION_NUM < 80300
#define VARSIZE_ANY(PTR) VARSIZE(PTR)
#define VARSIZE_ANY_EXHDR(PTR) (VARSIZE(PTR) - VARHDRSZ)
#define SET_VARSIZE(PTR, len) VARATT_SIZEP(PTR) = len & VARATT_MASK_SIZE
struct varatt_external
{
	int32 va_extsize; /* the only piece used here */
};
#undef VARATT_EXTERNAL_GET_POINTER
#define VARATT_EXTERNAL_GET_POINTER(toast_pointer, attr) \
do { \
	(toast_pointer).va_extsize = \
		((varattrib *)(attr))->va_content.va_external.va_extsize; \
} while (0)
#define _VL_TYPE varattrib *
#else
#define _VL_TYPE struct varlena *
#endif

#define INITIALSIZE 1024

static jclass s_VarlenaWrapper_class;
static jmethodID s_VarlenaWrapper_adopt;

static jclass s_VarlenaWrapper_Input_class;
static jclass s_VarlenaWrapper_Output_class;

static jmethodID s_VarlenaWrapper_Input_init;

static jmethodID s_VarlenaWrapper_Output_init;

static jfieldID  s_VarlenaWrapper_Input_State_varlena;

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
#define VARATT_IS_EXTERNAL_EXPANDED(attr) false
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
	_VL_TYPE vl;
	Ptr2Long p2lro;
	Ptr2Long p2lcxt;
	Ptr2Long p2lpin;
	Ptr2Long p2ldatum;
	Size parked;
	Size actual;
	Snapshot pin = NULL;

	vl = (_VL_TYPE) DatumGetPointer(d);

#if PG_VERSION_NUM >= 90400
	if ( VARATT_IS_EXTERNAL_INDIRECT(vl) ) /* at most once; can't be nested */
	{
		struct varatt_indirect redirect;
		VARATT_EXTERNAL_GET_POINTER(redirect, vl);
		vl = (_VL_TYPE)redirect.pointer;
		d = PointerGetDatum(vl);
	}
#endif

	parked = VARSIZE_ANY(vl);
	actual = toast_raw_datum_size(d) - VARHDRSZ;

	mc = AllocSetContextCreate(parent, "PL/Java VarlenaWrapper.Input",
		 ALLOCSET_START_SMALL_SIZES);

	prevcxt = MemoryContextSwitchTo(mc);

	if ( actual < 4096  ||  (actual >> 1) < parked )
		goto justDetoastEagerly;
	if ( VARATT_IS_EXTERNAL_EXPANDED(vl) )
		goto justDetoastEagerly;
	if ( VARATT_IS_EXTERNAL_ONDISK(vl) )
	{
		pin = GetOldestSnapshot();
		if ( NULL == pin )
		{
			/*
			 * Unable to register a snapshot and just park the tiny pointer.
			 * If it points to compressed data, can still park that rather than
			 * fully detoasting.
			 */
			struct varatt_external toast_pointer;
			VARATT_EXTERNAL_GET_POINTER(toast_pointer, vl);
			parked = toast_pointer.va_extsize + VARHDRSZ;
			if ( (actual >> 1) < parked ) /* not compressed enough to bother */
				goto justDetoastEagerly;
			vl = detoast_external_attr(vl); /* fetch without decompressing */
			d = PointerGetDatum(vl);
			dbb = NULL;
			goto constructResult;
		}
		pin = RegisterSnapshotOnOwner(pin, ro);
	}

/* parkAndDetoastLazily: */
	vl = (_VL_TYPE) DatumGetPointer(datumCopy(d, false, -1));
	dbb = NULL;
	goto constructResult;

justDetoastEagerly:
	vl = (_VL_TYPE) PG_DETOAST_DATUM_COPY(d);
	parked = actual + VARHDRSZ;
	dbb = JNI_newDirectByteBuffer(VARDATA(vl), actual);

constructResult:
	MemoryContextSwitchTo(prevcxt);

	p2lro.longVal = 0L;
	p2lcxt.longVal = 0L;
	p2lpin.longVal = 0L;
	p2ldatum.longVal = 0L;

	p2lro.ptrVal = ro;
	p2lcxt.ptrVal = mc;
	p2lpin.ptrVal = pin;
	p2ldatum.ptrVal = vl;

	vr = JNI_newObjectLocked(s_VarlenaWrapper_Input_class,
		s_VarlenaWrapper_Input_init, pljava_DualState_key(),
		p2lro.longVal, p2lcxt.longVal, p2lpin.longVal, p2ldatum.longVal,
		(jlong)parked, (jlong)actual, dbb);

	if ( NULL != dbb )
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
	JNINativeMethod methodsIn[] =
	{
		{
		"_unregisterSnapshot",
		"(JJ)V",
		Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1unregisterSnapshot
		},
		{
		"_detoast",
		"(JJJJ)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1detoast
		},
		{
		"_fetch",
		"(JJ)J",
		Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1fetch
		},
		{ 0, 0, 0 }
	};
	JNINativeMethod methodsOut[] =
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
		"JJJJJJLjava/nio/ByteBuffer;)V");

	s_VarlenaWrapper_Output_init = PgObject_getJavaMethod(
		s_VarlenaWrapper_Output_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;"
		"JJJLjava/nio/ByteBuffer;)V");

	s_VarlenaWrapper_adopt = PgObject_getJavaMethod(
		s_VarlenaWrapper_class, "adopt",
		"(Lorg/postgresql/pljava/internal/DualState$Key;)J");

	clazz = PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Input$State");

	PgObject_registerNatives2(clazz, methodsIn);

	s_VarlenaWrapper_Input_State_varlena = PgObject_getJavaField(
		clazz, "m_varlena", "J");

	JNI_deleteLocalRef(clazz);

	clazz = PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Output$State");

	PgObject_registerNatives2(clazz, methodsOut);

	JNI_deleteLocalRef(clazz);
}

/*
 * Class:     org_postgresql_pljava_internal_VarlenaWrapper_Input_State
 * Method:    _unregisterSnapshot
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1unregisterSnapshot
  (JNIEnv *env, jobject _this, jlong snapshot, jlong ro)
{
#if PG_VERSION_NUM >= 80400
	BEGIN_NATIVE_NO_ERRCHECK
	Ptr2Long p2lsnap;
	Ptr2Long p2lro;
	p2lsnap.longVal = snapshot;
	p2lro.longVal = ro;
	UnregisterSnapshotFromOwner(p2lsnap.ptrVal, p2lro.ptrVal);
	END_NATIVE
#endif
}

/*
 * Class:     org_postgresql_pljava_internal_VarlenaWrapper_Input_State
 * Method:    _detoast
 * Signature: (JJJJ)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1detoast
  (JNIEnv *env, jobject _this, jlong vl, jlong cxt, jlong snap, jlong resOwner)
{
	Ptr2Long p2lvl;
	Ptr2Long p2lcxt;
#if PG_VERSION_NUM >= 80400
	Ptr2Long p2lsnap;
	Ptr2Long p2lro;
#endif
	Ptr2Long p2ldetoasted;
	_VL_TYPE detoasted;
	MemoryContext prevcxt;
	jobject dbb = NULL;

	BEGIN_NATIVE_NO_ERRCHECK

	p2lvl.longVal = vl;
	p2lcxt.longVal = cxt;
#if PG_VERSION_NUM >= 80400
	p2lsnap.longVal = snap;
	p2lro.longVal = resOwner;
#endif

	prevcxt = MemoryContextSwitchTo((MemoryContext)p2lcxt.ptrVal);

	detoasted = (_VL_TYPE) PG_DETOAST_DATUM_COPY(PointerGetDatum(p2lvl.ptrVal));
	p2ldetoasted.longVal = 0L;
	p2ldetoasted.ptrVal = detoasted;

	MemoryContextSwitchTo(prevcxt);

	JNI_setLongField(_this,
		s_VarlenaWrapper_Input_State_varlena, p2ldetoasted.longVal);
	pfree(p2lvl.ptrVal);

#if PG_VERSION_NUM >= 80400
	if ( 0 != snap )
		UnregisterSnapshotFromOwner(p2lsnap.ptrVal, p2lro.ptrVal);
#endif

	dbb = JNI_newDirectByteBuffer(
		VARDATA(detoasted), VARSIZE_ANY_EXHDR(detoasted));

	END_NATIVE

	return dbb;
}

/*
 * Class:     org_postgresql_pljava_internal_VarlenaWrapper_Input_State
 * Method:    _fetch
 * Signature: (JJ)J
 *
 * Assumption: this is only called when a snapshot has been registered (meaning
 * the varlena is EXTERNAL_ONDISK) and the snapshot is soon to be unregistered.
 * All that's needed is to 'fetch' the representation from disk, in case the
 * toast rows could be subject to vacuuming after the snapshot is unregistered.
 * A fetch is not a full detoast; if what's fetched is compressed, it stays
 * compressed. This method does not need to unregister the snapshot, as that
 * will happen soon anyway. It does pfree the toast pointer.
 */
JNIEXPORT jlong JNICALL Java_org_postgresql_pljava_internal_VarlenaWrapper_00024Input_00024State__1fetch
  (JNIEnv *env, jobject _this, jlong varlena, jlong memContext)
{
	Ptr2Long p2lvl;
	Ptr2Long p2lcxt;
	MemoryContext prevcxt;
	_VL_TYPE fetched;

	p2lvl.longVal = varlena;
	p2lcxt.longVal = memContext;

	BEGIN_NATIVE_NO_ERRCHECK;
	prevcxt = MemoryContextSwitchTo((MemoryContext) p2lcxt.ptrVal);
	fetched = detoast_external_attr((_VL_TYPE) p2lvl.ptrVal);
	pfree(p2lvl.ptrVal);
	p2lvl.longVal = 0L;
	p2lvl.ptrVal = fetched;
	MemoryContextSwitchTo(prevcxt);
	END_NATIVE;

	return p2lvl.longVal;
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
	jobject dbb = NULL;

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
