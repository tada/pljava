/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_JavaHandle.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/JavaHandle.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"
#include "pljava/Iterator.h"

static jclass    s_JavaHandle_class;
static jfieldID  s_JavaHandle_m_native;

void JavaHandle_releaseCache(HashMap cache)
{
	Iterator itor = HashMap_entries(cache);
	while(Iterator_hasNext(itor))
	{
		Entry e = Iterator_next(itor);
		jobject weak = Entry_getValue(e);
		if(weak != 0)
		{
			jobject bound = JNI_newLocalRef(weak);
			if(bound != 0)
			{
				JNI_setLongField(bound, s_JavaHandle_m_native, 0L);
				JNI_deleteLocalRef(bound);
			}
			JNI_deleteWeakGlobalRef(weak);
		}
	}
}

void JavaHandle_setPointer(jobject nativeStruct, void* nativePointer)
{
	if(nativeStruct != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = 0L; /* ensure that the rest is zeroed out */
		p2l.ptrVal = nativePointer;
		JNI_setLongField(nativeStruct, s_JavaHandle_m_native, p2l.longVal);
	}
}

void JavaHandle_init(jobject nativeStruct, void* nativePointer)
{
	jobject oldRef;

	if(nativeStruct == 0)
		return;

	/* Assign the pointer to the 64 bit long attribute m_native.
	 */
	JavaHandle_setPointer(nativeStruct, nativePointer);

	/* Store a weak reference to this java object in the s_weakCache using
	 * the nativePointer as the key.
	 */
	oldRef = (jobject)HashMap_putByOpaque(MemoryContext_getCurrentNativeCache(),
				nativePointer, JNI_newWeakGlobalRef(nativeStruct));

	if(oldRef != 0)
		/*
		 * An old entry occupied. This is probably due to an old binding
		 * of the native pointer that has been garbage collected. Remove
		 * resources occupied by the VM for the weak reference.
		 */
		JNI_deleteWeakGlobalRef(oldRef);
}

void* JavaHandle_getStruct(jobject nativeStruct)
{
	Ptr2Long p2l;
	if(nativeStruct == 0)
	{
		Exception_throw(ERRCODE_INTERNAL_ERROR, "Null JavaHandle object");
		return 0;
	}

	p2l.longVal = JNI_getLongField(nativeStruct, s_JavaHandle_m_native);
	if(p2l.ptrVal == 0)
	{
		/* Stale handle.
		 */
		Exception_throw(ERRCODE_INTERNAL_ERROR, "Stale Handle to native structure");
		return 0;
	}
	return p2l.ptrVal;
}

void* JavaHandle_releasePointer(jobject _this)
{
	Ptr2Long p2l;
	p2l.longVal = JNI_getLongField(_this, s_JavaHandle_m_native);
	if(p2l.ptrVal != 0)
	{
		/* Remove this object from the cache
		 */
		MemoryContext_dropNative(p2l.ptrVal);

		/* Clear the field.
		 */
		JNI_setLongField(_this, s_JavaHandle_m_native, 0L);	
	}
	return p2l.ptrVal;
}

static Datum _JavaHandle_coerceObject(Type self, jobject nStruct)
{
	return PointerGetDatum(JavaHandle_getStruct(nStruct));
}

TypeClass JavaHandleClass_alloc(const char* name)
{
	TypeClass self = TypeClass_alloc(name);
	self->coerceObject = _JavaHandle_coerceObject;
	return self;
}

extern void JavaHandle_initialize(void);
void JavaHandle_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_releasePointer",
	  	"()V",
	  	Java_org_postgresql_pljava_internal_JavaHandle__1releasePointer
		},
		{ 0, 0, 0 }
	};
	s_JavaHandle_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/JavaHandle"));
	PgObject_registerNatives2(s_JavaHandle_class, methods);
	s_JavaHandle_m_native = PgObject_getJavaField(s_JavaHandle_class, "m_native", "J");
}

/*
 * Class:     org_postgresql_pljava_internal_JavaHandle
 * Method:    _releasePointer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_JavaHandle__1releasePointer(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE
	JavaHandle_releasePointer(_this);
	END_NATIVE
}
