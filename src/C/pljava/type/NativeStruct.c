/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/NativeStruct.h"
#include "pljava/type/NativeStruct_JNI.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"
#include "pljava/Iterator.h"

static jclass    s_NativeStruct_class;
static jfieldID  s_NativeStruct_m_native;
static HashMap   s_nativeCache;

/**
 * Callback that will be called when a context that is associated with
 * NativeStruct objects is deleted.
 */
static void dropCache(void* oldCache, bool isDelete)
{
	JNIEnv* env   = Backend_getMainEnv();
	if(env != 0)
	{
		Iterator itor = HashMap_entries(s_nativeCache);
		while(Iterator_hasNext(itor))
		{
			Entry e = Iterator_next(itor);
			jobject weak = Entry_getValue(e);
			if(weak != 0)
			{
				jobject bound = (*env)->NewLocalRef(env, weak);
				if(bound != 0)
				{
					elog(DEBUG1, "Marking object stale");
					(*env)->SetLongField(env, bound, s_NativeStruct_m_native, 0L);
					(*env)->DeleteLocalRef(env, bound);
				}
				(*env)->DeleteWeakGlobalRef(env, weak);
			}
		}
	}

	if(isDelete)
	{
		PgObject_free((PgObject)s_nativeCache);
		elog(DEBUG1, "NativeStruct cache deleted due to deletion of context");
		s_nativeCache = (HashMap)oldCache;
	}
	else
	{
		HashMap_clear(s_nativeCache);
		elog(DEBUG1, "NativeStruct cache cleared due to context reset");
	}
}

jobject NativeStruct_obtain(JNIEnv* env, void* nativePointer)
{
	jobject weak;
	if(s_nativeCache == 0)
		return 0;

	weak = HashMap_getByOpaque(s_nativeCache, nativePointer);
	if(weak != 0)
		weak = (*env)->NewLocalRef(env, weak);
	return weak;
}

void NativeStruct_setPointer(JNIEnv* env, jobject nativeStruct, void* nativePointer)
{
	if(nativeStruct != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = 0L; /* ensure that the rest is zeroed out */
		p2l.ptrVal = nativePointer;
		(*env)->SetLongField(env, nativeStruct, s_NativeStruct_m_native, p2l.longVal);
	}
}

void NativeStruct_associateCache(MemoryContext ctx)
{
	HashMap oldCache = s_nativeCache;
	s_nativeCache = HashMap_create(13, ctx->parent);
	MemoryContext_addEndOfScopeCB(ctx, dropCache, oldCache);
}

void NativeStruct_init(JNIEnv* env, jobject nativeStruct, void* nativePointer)
{
	jobject oldRef;

	if(nativeStruct == 0)
		return;

	/* Assign the pointer to the 64 bit long attribute m_native.
	 */
	NativeStruct_setPointer(env, nativeStruct, nativePointer);

	/* Store a weak reference to this java object in the s_weakCache using
	 * the nativePointer as the key.
	 */
	oldRef = (jobject)HashMap_putByOpaque(s_nativeCache,
				nativePointer, (*env)->NewWeakGlobalRef(env, nativeStruct));

	if(oldRef != 0)
		/*
		 * An old entry occupied. This is probably due to an old binding
		 * of the native pointer that has been garbage collected. Remove
		 * resources occupied by the VM for the weak reference.
		 */
		(*env)->DeleteWeakGlobalRef(env, oldRef);
}

void* NativeStruct_getStruct(JNIEnv* env, jobject nativeStruct)
{
	if(nativeStruct == 0)
	{
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"Null NativeStruct object");
		return 0;
	}

	Ptr2Long p2l;
	p2l.longVal = (*env)->GetLongField(env, nativeStruct, s_NativeStruct_m_native);
	if(p2l.ptrVal == 0)
	{
		/* Stale handle.
		 */
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"Stale Handle to native structure");
		return 0;
	}
	return p2l.ptrVal;
}

void* NativeStruct_releasePointer(JNIEnv* env, jobject _this)
{
	Ptr2Long p2l;
	p2l.longVal = (*env)->GetLongField(env, _this, s_NativeStruct_m_native);
	if(p2l.ptrVal != 0)
	{
		/* Remove this object from the cache
		 */
		if(s_nativeCache != 0)
		{
			jobject weak = HashMap_removeByOpaque(s_nativeCache, p2l.ptrVal);
			if(weak != 0)
				(*env)->DeleteWeakGlobalRef(env, weak);
		}
		/* Clear the field.
		 */
		(*env)->SetLongField(env, _this, s_NativeStruct_m_native, 0L);	
	}
	return p2l.ptrVal;
}

static Datum _NativeStruct_coerceObject(Type self, JNIEnv* env, jobject nStruct)
{
	return PointerGetDatum(NativeStruct_getStruct(env, nStruct));
}

TypeClass NativeStructClass_alloc(const char* name)
{
	TypeClass self = TypeClass_alloc(name);
	self->coerceObject = _NativeStruct_coerceObject;
	return self;
}

/* Make this datatype available to the postgres system.
 */
extern Datum NativeStruct_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(NativeStruct_initialize);
Datum NativeStruct_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_releasePointer",
	  	"()V",
	  	Java_org_postgresql_pljava_internal_NativeStruct__1releasePointer
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_NativeStruct_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/NativeStruct"));

	PgObject_registerNatives2(env, s_NativeStruct_class, methods);

	s_NativeStruct_m_native = PgObject_getJavaField(
				env, s_NativeStruct_class, "m_native", "J");

	PG_RETURN_VOID();
}

/*
 * Class:     org_postgresql_pljava_internal_NativeStruct
 * Method:    _releasePointer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_NativeStruct__1releasePointer(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE_VOID
	NativeStruct_releasePointer(env, _this);
}
