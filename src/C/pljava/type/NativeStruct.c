/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/NativeStruct.h"
#include "pljava/type/NativeStruct_JNI.h"
#include "pljava/Exception.h"
#include "pljava/Iterator.h"

static jclass    s_NativeStruct_class;
static jfieldID  s_NativeStruct_m_native;

static HashMap s_weakCache;

HashMap NativeStruct_switchTopCache(HashMap newTop)
{
	HashMap current = s_weakCache;
	s_weakCache = newTop;
	return current;
}

HashMap NativeStruct_pushCache()
{
	HashMap current = s_weakCache;
	s_weakCache = 0;
	return current;
}

void NativeStruct_popCache(JNIEnv* env, HashMap previousCache)
{
	HashMap current = s_weakCache;
	s_weakCache = previousCache;

	if(current == 0)
		return;

	Iterator itor = HashMap_entries(current);
	while(Iterator_hasNext(itor))
	{
		Entry e = Iterator_next(itor);
		jobject weak = Entry_getValue(e);
		if(weak != 0)
		{
			jobject bound = (*env)->NewLocalRef(env, weak);
			if(bound != 0)
			{
				(*env)->SetLongField(env, bound, s_NativeStruct_m_native, 0L);
				(*env)->DeleteLocalRef(env, bound);
			}
			(*env)->DeleteWeakGlobalRef(env, weak);
		}
	}
	PgObject_free((PgObject)current);
}

jobject NativeStruct_obtain(JNIEnv* env, void* nativePointer)
{
	if(s_weakCache == 0)
		return 0;

	jobject weak = HashMap_getByOpaque(s_weakCache, nativePointer);
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

void NativeStruct_init(JNIEnv* env, jobject nativeStruct, void* nativePointer)
{
	if(nativeStruct == 0)
		return;

	if(s_weakCache == 0)
		s_weakCache = HashMap_create(13, 0);

	/* Assign the pointer to the 64 bit long attribute m_native.
	 */
	NativeStruct_setPointer(env, nativeStruct, nativePointer);

	/* Store a weak reference to this java object in the s_weakCache using
	 * the nativePointer as the key.
	 */
	jobject oldRef = (jobject)HashMap_putByOpaque(s_weakCache,
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
	Ptr2Long p2l;
	p2l.longVal = (*env)->GetLongField(env, nativeStruct, s_NativeStruct_m_native);
	void* ptr = p2l.ptrVal;
	if(ptr == 0)
	{
		/* Stale handle.
		 */
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"Stale Handle to native structure");
		return 0;
	}
	return ptr;
}

void* NativeStruct_releasePointer(JNIEnv* env, jobject _this)
{
	Ptr2Long p2l;
	p2l.longVal = (*env)->GetLongField(env, _this, s_NativeStruct_m_native);
	void* ptr = p2l.ptrVal;
	if(ptr != 0)
	{
		/* Clear the field.
		 */
		(*env)->SetLongField(env, _this, s_NativeStruct_m_native, 0L);
	
		/* Remove this object from the cache
		 */
		jobject weak = HashMap_removeByOpaque(s_weakCache, ptr);
		if(weak != 0)
			(*env)->DeleteWeakGlobalRef(env, weak);
	}
	return ptr;
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
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_NativeStruct_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/NativeStruct"));

	s_NativeStruct_m_native = PgObject_getJavaField(
				env, s_NativeStruct_class, "m_native", "J");

	s_weakCache = 0;

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
