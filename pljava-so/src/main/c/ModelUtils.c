/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

#include <postgres.h>
#include <miscadmin.h>
#include <access/genam.h>
#include <access/heaptoast.h>
#include <access/relation.h>
#include <access/tupdesc.h>
#include <executor/spi.h>
#include <executor/tuptable.h>
#include <mb/pg_wchar.h>
#include <utils/fmgroids.h>
#include <utils/inval.h>
#include <utils/rel.h>
#include <utils/resowner.h>
#include <utils/typcache.h>

#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/PgObject.h"
#include "pljava/ModelUtils.h"
#include "pljava/VarlenaWrapper.h"

#include "org_postgresql_pljava_pg_CatalogObjectImpl_Factory.h"
#include "org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_DatumUtils.h"
#include "org_postgresql_pljava_pg_MemoryContextImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_ResourceOwnerImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_TupleDescImpl.h"

/*
 * A compilation unit collecting various native methods used in the pg model
 * implementation classes. This is something of a break with past PL/Java
 * practice of having a correspondingly-named C file for a Java class, made on
 * the belief that there won't be that many new methods here, and they will make
 * more sense collected together.
 *
 * Some of the native methods here may *not* include the elaborate fencing seen
 * in other PL/Java native methods, if they involve trivially simple functions
 * that do not require calling into PostgreSQL or other non-thread-safe code.
 * This is, of course, a careful exception made to the general rule. The calling
 * Java code is expected to have good reason to believe any state to be examined
 * by these methods won't be shifting underneath them.
 */

static jclass s_MemoryContextImpl_class;
static jmethodID s_MemoryContextImpl_callback;
static void memoryContextCallback(void *arg);

static jclass s_ResourceOwnerImpl_class;
static jmethodID s_ResourceOwnerImpl_callback;
static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg);

static jclass s_TupleDescImpl_class;
static jmethodID s_TupleDescImpl_fromByteBuffer;

jobject pljava_TupleDescriptor_create(TupleDesc tupdesc, Oid reloid)
{
	jlong tupdesc_size = (jlong)TupleDescSize(tupdesc);
	jobject td_b = JNI_newDirectByteBuffer(tupdesc, tupdesc_size);

	jobject result = JNI_callStaticObjectMethodLocked(s_TupleDescImpl_class,
		s_TupleDescImpl_fromByteBuffer,
		td_b,
		(jint)tupdesc->tdtypeid, (jint)tupdesc->tdtypmod,
		(jint)reloid, (jint)tupdesc->tdrefcount);

	JNI_deleteLocalRef(td_b);
	return result;
}

static void memoryContextCallback(void *arg)
{
	Ptr2Long p2l;

	p2l.longVal = 0L;
	p2l.ptrVal = arg;
	JNI_callStaticVoidMethodLocked(s_MemoryContextImpl_class,
								   s_MemoryContextImpl_callback,
								   p2l.longVal);
}

static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg)
{
	Ptr2Long p2l;

	/*
	 * This static assertion does not need to be in every file
	 * that uses Ptr2Long, but it should be somewhere once, so here it is.
	 */
	StaticAssertStmt(sizeof p2l.ptrVal <= sizeof p2l.longVal,
					 "Pointer will not fit in long on this platform");

	/*
	 * The way ResourceOwnerRelease is implemented, callbacks to loadable
	 * modules (like us!) happen /after/ all of the built-in releasey actions
	 * for a particular phase. So, by looking for RESOURCE_RELEASE_LOCKS here,
	 * we actually end up executing after all the built-in lock-related stuff
	 * has been released, but before any of the built-in stuff released in the
	 * RESOURCE_RELEASE_AFTER_LOCKS phase. Which, at least for the currently
	 * implemented DualState subclasses, is about the right time.
	 */
	if ( RESOURCE_RELEASE_LOCKS != phase )
		return;

	/*
	 * The void *arg is the NULL we supplied at registration time. The resource
	 * manager arranges for CurrentResourceOwner to be the one that is being
	 * released.
	 */
	p2l.longVal = 0L;
	p2l.ptrVal = CurrentResourceOwner;
	JNI_callStaticVoidMethodLocked(s_ResourceOwnerImpl_class,
								   s_ResourceOwnerImpl_callback,
								   p2l.longVal);

	if ( isTopLevel )
		Backend_warnJEP411(isCommit);
}

void pljava_ResourceOwner_unregister(void)
{
	UnregisterResourceReleaseCallback(resourceReleaseCB, NULL);
}

void pljava_ModelUtils_initialize(void)
{
	jclass cls;

	JNINativeMethod charsetMethods[] =
	{
		{
		"_serverEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding
		},
		{
		"_clientEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding
		},
		{
		"_nameToOrdinal",
		"(Ljava/nio/ByteBuffer;)I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal
		},
		{
		"_ordinalToName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName
		},
		{
		"_ordinalToIcuName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod datumMethods[] =
	{
		{
		"_addressOf",
		"(Ljava/nio/ByteBuffer;)J",
		Java_org_postgresql_pljava_pg_DatumUtils__1addressOf
		},
		{
		"_map",
		"(JI)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_DatumUtils__1map
		},
		{
		"_mapCString",
		"(J)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_DatumUtils__1mapCString
		},
		{
		"_mapVarlena",
		"(Ljava/nio/ByteBuffer;JJJ)Lorg/postgresql/pljava/adt/spi/Datum$Input;",
		Java_org_postgresql_pljava_pg_DatumUtils__1mapVarlena
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod memoryContextMethods[] =
	{
		{
		"_registerCallback",
		"(J)V",
		Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1registerCallback
		},
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod resourceOwnerMethods[] =
	{
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_ResourceOwnerImpl_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod tdiMethods[] =
	{
		{
		"_assign_record_type_typmod",
		"(Ljava/nio/ByteBuffer;)I",
		Java_org_postgresql_pljava_pg_TupleDescImpl__1assign_1record_1type_1typmod
		},
		{ 0, 0, 0 }
	};

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CharsetEncodingImpl$EarlyNatives");
	PgObject_registerNatives2(cls, charsetMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/DatumUtils");
	PgObject_registerNatives2(cls, datumMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/MemoryContextImpl$EarlyNatives");
	PgObject_registerNatives2(cls, memoryContextMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/MemoryContextImpl");
	s_MemoryContextImpl_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
	s_MemoryContextImpl_callback = PgObject_getStaticJavaMethod(
		s_MemoryContextImpl_class, "callback", "(J)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/ResourceOwnerImpl$EarlyNatives");
	PgObject_registerNatives2(cls, resourceOwnerMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/ResourceOwnerImpl");
	s_ResourceOwnerImpl_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
	s_ResourceOwnerImpl_callback = PgObject_getStaticJavaMethod(
		s_ResourceOwnerImpl_class, "callback", "(J)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/TupleDescImpl");
	s_TupleDescImpl_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, tdiMethods);
	JNI_deleteLocalRef(cls);

	s_TupleDescImpl_fromByteBuffer = PgObject_getStaticJavaMethod(
		s_TupleDescImpl_class,
		"fromByteBuffer",
		"(Ljava/nio/ByteBuffer;IIII)"
		"Lorg/postgresql/pljava/model/TupleDescriptor;");

	RegisterResourceReleaseCallback(resourceReleaseCB, NULL);
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _serverEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = GetDatabaseEncoding();
	END_NATIVE_AND_CATCH("_serverEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _clientEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = pg_get_client_encoding();
	END_NATIVE_AND_CATCH("_clientEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _nameToOrdinal
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal(JNIEnv *env, jclass cls, jobject bb)
{
	int result = -1;
	char const *name = (*env)->GetDirectBufferAddress(env, bb);
	if ( NULL == name )
		return result;
	BEGIN_NATIVE_AND_TRY
	result = pg_char_to_encoding(name);
	END_NATIVE_AND_CATCH("_nameToOrdinal")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = pg_encoding_to_char(ordinal);
	if ( '\0' != *name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToName")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToIcuName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = get_encoding_name_for_icu(ordinal);
	if ( NULL != name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToIcuName")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _addressOf
 * Signature: (Ljava/nio/ByteBuffer;)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1addressOf(JNIEnv* env, jobject _cls, jobject bb)
{
	Ptr2Long p2l;
	p2l.longVal = 0;
	p2l.ptrVal = (*env)->GetDirectBufferAddress(env, bb);
	return p2l.longVal;
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _map
 * Signature: (JI)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1map(JNIEnv* env, jobject _cls, jlong nativeAddress, jint length)
{
	Ptr2Long p2l;
	p2l.longVal = nativeAddress;
	return (*env)->NewDirectByteBuffer(env, p2l.ptrVal, length);
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _mapCString
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1mapCString(JNIEnv* env, jobject _cls, jlong nativeAddress)
{
	jlong length;
	void *base;
	Ptr2Long p2l;

	p2l.longVal = nativeAddress;
	base = p2l.ptrVal;
	length = (jlong)strlen(base);
	return (*env)->NewDirectByteBuffer(env, base, length);
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _mapVarlena
 * Signature: (Ljava/nio/ByteBuffer;JJJ)Lorg/postgresql/pljava/adt/spi/Datum$Input;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1mapVarlena(JNIEnv* env, jobject _cls, jobject bb, jlong offset, jlong resowner, jlong memcontext)
{
	Ptr2Long p2lvl;
	Ptr2Long p2lro;
	Ptr2Long p2lmc;
	jobject result = NULL;

	p2lvl.longVal = 0;
	if ( NULL != bb )
	{
		p2lvl.ptrVal = (*env)->GetDirectBufferAddress(env, bb);
		if ( NULL == p2lvl.ptrVal )
			return NULL;
	}
	p2lvl.longVal += offset;

	p2lro.longVal = resowner;
	p2lmc.longVal = memcontext;

	BEGIN_NATIVE_AND_TRY
	result =  pljava_VarlenaWrapper_Input(PointerGetDatum(p2lvl.ptrVal),
		(MemoryContext)p2lmc.ptrVal, (ResourceOwner)p2lro.ptrVal);
	END_NATIVE_AND_CATCH("_mapVarlena")
	return result;
}


/*
 * Class:     org_postgresql_pljava_pg_MemoryContext_EarlyNatives
 * Method:    _registerCallback
 * Signature: (J)V;
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1registerCallback(JNIEnv* env, jobject _cls, jlong nativeAddress)
{
	Ptr2Long p2l;
	MemoryContext cxt;
	MemoryContextCallback *cb;

	p2l.longVal = nativeAddress;
	cxt = p2l.ptrVal;
	BEGIN_NATIVE_AND_TRY
	/*
	 * Optimization? Use MemoryContextAllocExtended with NO_OOM, and do without
	 * the AND_TRY/AND_CATCH to catch a PostgreSQL ereport.
	 */
	cb = MemoryContextAlloc(cxt, sizeof *cb);
	cb->func = memoryContextCallback;
	cb->arg = cxt;
	MemoryContextRegisterResetCallback(cxt, cb);
	END_NATIVE_AND_CATCH("_registerCallback")
}

/*
 * Class:     org_postgresql_pljava_pg_MemoryContext_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * holding the well-known memory contexts. The indices into the array are
 * assigned arbitrarily in the API class CatalogObject.Factory and inherited
 * from it in CatalogObjectImpl.Factory, from which the native .h makes them
 * visible here. A peculiar consequence is that the code in MemoryContextImpl
 * can be ignorant of them, and just fetch the array element at the index passed
 * from the API class.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)10, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, \
	&tag##Context, sizeof tag##Context);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_pg_CatalogObjectImpl_Factory_MCX_##tag, \
	b);\
} while (0)

	POPULATE(CurrentMemory);
	POPULATE(TopMemory);
	POPULATE(Error);
	POPULATE(Postmaster);
	POPULATE(CacheMemory);
	POPULATE(Message);
	POPULATE(TopTransaction);
	POPULATE(CurTransaction);
	POPULATE(Portal);
	POPULATE(JavaMemory);

#undef POPULATE

	return r;
}

/*
 * Class:     org_postgresql_pljava_pg_ResourceOwnerImpl_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * holding the well-known resource owners. The indices into the array are
 * assigned arbitrarily in the API class CatalogObject.Factory and inherited
 * from it in CatalogObjectImpl.Factory, from which the native .h makes them
 * visible here. A peculiar consequence is that the code in ResourceOwnerImpl
 * can be ignorant of them, and just fetch the array element at the index passed
 * from the API class.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_ResourceOwnerImpl_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)4, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, \
	&tag##ResourceOwner, sizeof tag##ResourceOwner);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_pg_CatalogObjectImpl_Factory_RSO_##tag, \
	b);\
} while (0)

	POPULATE(Current);
	POPULATE(CurTransaction);
	POPULATE(TopTransaction);
	POPULATE(AuxProcess);

#undef POPULATE

	return r;
}

/*
 * Class:     org_postgresql_pljava_pg_TupleDescImpl
 * Method:    _assign_record_type_typmod
 * Signature: (Ljava/nio/ByteBuffer)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_TupleDescImpl__1assign_1record_1type_1typmod(JNIEnv* env, jobject _cls, jobject td_b)
{
	TupleDesc td = (*env)->GetDirectBufferAddress(env, td_b);
	if ( NULL == td )
		return -1;

	BEGIN_NATIVE_AND_TRY
	assign_record_type_typmod(td);
	END_NATIVE_AND_CATCH("_assign_record_type_typmod")
	return td->tdtypmod;
}
