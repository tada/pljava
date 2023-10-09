/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

#include "org_postgresql_pljava_internal_SPI.h"
#include "org_postgresql_pljava_internal_SPI_EarlyNatives.h"

#include "org_postgresql_pljava_pg_CatalogObjectImpl_Addressed.h"
#include "org_postgresql_pljava_pg_CatalogObjectImpl_Factory.h"
#include "org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_DatumUtils.h"
#include "org_postgresql_pljava_pg_MemoryContextImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_ResourceOwnerImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_TupleDescImpl.h"
#include "org_postgresql_pljava_pg_TupleTableSlotImpl.h"

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

static jclass s_CatalogObjectImpl_Factory_class;
static jmethodID s_CatalogObjectImpl_Factory_invalidateRelation;
static jmethodID s_CatalogObjectImpl_Factory_syscacheInvalidate;

static jclass s_MemoryContextImpl_class;
static jmethodID s_MemoryContextImpl_callback;
static void memoryContextCallback(void *arg);

static jclass s_ResourceOwnerImpl_class;
static jmethodID s_ResourceOwnerImpl_callback;
static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg);

static jclass s_TupleDescImpl_class;
static jmethodID s_TupleDescImpl_fromByteBuffer;

static jclass s_TupleTableSlotImpl_class;
static jmethodID s_TupleTableSlotImpl_newDeformed;

static void relCacheCB(Datum arg, Oid relid);
static void sysCacheCB(Datum arg, int cacheid, uint32 hash);

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

/*
 * If NULL is passed for jtd, a Java TupleDescriptor will be created here from
 * tupdesc. Otherwise, the passed jtd must be a JNI local reference to an
 * existing Java TupleDescriptor corresponding to tupdesc, and on return, the
 * JNI local reference will have been deleted.
 */
jobject pljava_TupleTableSlot_create(
	TupleDesc tupdesc, jobject jtd, const TupleTableSlotOps *tts_ops, Oid reloid)
{
	int natts = tupdesc->natts;
	TupleTableSlot *tts = MakeSingleTupleTableSlot(tupdesc, tts_ops);
	jobject tts_b = JNI_newDirectByteBuffer(tts, (jlong)sizeof *tts);
	jobject vals_b = JNI_newDirectByteBuffer(tts->tts_values,
		(jlong)(natts * sizeof *tts->tts_values));
	jobject nuls_b = JNI_newDirectByteBuffer(tts->tts_isnull, (jlong)natts);
	jobject jtts;

	if ( NULL == jtd )
		jtd = pljava_TupleDescriptor_create(tupdesc, reloid);

	jtts = JNI_callStaticObjectMethodLocked(s_TupleTableSlotImpl_class,
		s_TupleTableSlotImpl_newDeformed, tts_b, jtd, vals_b, nuls_b);

	JNI_deleteLocalRef(nuls_b);
	JNI_deleteLocalRef(vals_b);
	JNI_deleteLocalRef(jtd);
	JNI_deleteLocalRef(tts_b);

	return jtts;
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

static void relCacheCB(Datum arg, Oid relid)
{
	JNI_callStaticObjectMethodLocked(s_CatalogObjectImpl_Factory_class,
		s_CatalogObjectImpl_Factory_invalidateRelation, (jint)relid);
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

static void sysCacheCB(Datum arg, int cacheid, uint32 hash)
{
	switch ( cacheid )
	{
	case LANGOID:
	case PROCOID:
	case TYPEOID:
		JNI_callStaticObjectMethodLocked(s_CatalogObjectImpl_Factory_class,
			s_CatalogObjectImpl_Factory_syscacheInvalidate,
			(jint)cacheid, (jint)hash);
		break;
	default:
#ifdef USE_ASSERT_CHECKING
		elog(ERROR, "unhandled invalidation callback for cache id %d", cacheid);
#endif
		break;
	}
}

void pljava_ResourceOwner_unregister(void)
{
	UnregisterResourceReleaseCallback(resourceReleaseCB, NULL);
}

void pljava_ModelUtils_initialize(void)
{
	jclass cls;

	JNINativeMethod catalogObjectAddressedMethods[] =
	{
		{
		"_lookupRowtypeTupdesc",
		"(II)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1lookupRowtypeTupdesc
		},
		{
		"_searchSysCacheCopy1",
		"(II)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy1
		},
		{
		"_searchSysCacheCopy2",
		"(III)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy2
		},
		{
		"_sysTableGetByOid",
		"(IIIIJ)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1sysTableGetByOid
		},
		{
		"_tupDescBootstrap",
		"()Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1tupDescBootstrap
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod catalogObjectFactoryMethods[] =
	{
		{
		"_currentDatabase",
		"()I",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Factory__1currentDatabase
		},
		{ 0, 0, 0 }
	};

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

	JNINativeMethod spiMethods[] =
	{
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_SPI_00024EarlyNatives__1window
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
		{
		"_synthesizeDescriptor",
		"(ILjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_TupleDescImpl__1synthesizeDescriptor
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod ttsiMethods[] =
	{
		{
		"_getsomeattrs",
		"(Ljava/nio/ByteBuffer;I)V",
		Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1getsomeattrs
		},
		{
		"_store_heaptuple",
		"(Ljava/nio/ByteBuffer;JZ)V",
		Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1store_1heaptuple
		},
		{ 0, 0, 0 }
	};

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CatalogObjectImpl$Addressed");
	PgObject_registerNatives2(cls, catalogObjectAddressedMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CatalogObjectImpl$Factory");
	s_CatalogObjectImpl_Factory_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, catalogObjectFactoryMethods);
	JNI_deleteLocalRef(cls);
	s_CatalogObjectImpl_Factory_invalidateRelation =
		PgObject_getStaticJavaMethod(
		s_CatalogObjectImpl_Factory_class, "invalidateRelation", "(I)V");
	s_CatalogObjectImpl_Factory_syscacheInvalidate =
		PgObject_getStaticJavaMethod(
		s_CatalogObjectImpl_Factory_class, "syscacheInvalidate", "(II)V");

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

	cls = PgObject_getJavaClass("org/postgresql/pljava/internal/SPI$EarlyNatives");
	PgObject_registerNatives2(cls, spiMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/TupleDescImpl");
	s_TupleDescImpl_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, tdiMethods);
	JNI_deleteLocalRef(cls);

	s_TupleDescImpl_fromByteBuffer = PgObject_getStaticJavaMethod(
		s_TupleDescImpl_class,
		"fromByteBuffer",
		"(Ljava/nio/ByteBuffer;IIII)"
		"Lorg/postgresql/pljava/model/TupleDescriptor;");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/TupleTableSlotImpl");
	s_TupleTableSlotImpl_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, ttsiMethods);
	JNI_deleteLocalRef(cls);

	s_TupleTableSlotImpl_newDeformed = PgObject_getStaticJavaMethod(
		s_TupleTableSlotImpl_class,
		"newDeformed",
		"(Ljava/nio/ByteBuffer;Lorg/postgresql/pljava/model/TupleDescriptor;"
		"Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)"
		"Lorg/postgresql/pljava/pg/TupleTableSlotImpl$Deformed;");

	RegisterResourceReleaseCallback(resourceReleaseCB, NULL);

	CacheRegisterRelcacheCallback(relCacheCB, 0);

	CacheRegisterSyscacheCallback(LANGOID, sysCacheCB, 0);
	CacheRegisterSyscacheCallback(PROCOID, sysCacheCB, 0);
	CacheRegisterSyscacheCallback(TYPEOID, sysCacheCB, 0);
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _lookupRowtypeTupdesc
 * Signature: (II)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1lookupRowtypeTupdesc(JNIEnv* env, jobject _cls, jint typeid, jint typmod)
{
	TupleDesc td;
	jlong length;
	jobject result = NULL;
	BEGIN_NATIVE_AND_TRY
	td = lookup_rowtype_tupdesc_noerror(typeid, typmod, true);
	if ( NULL != td )
	{
		/*
		 * Per contract, we return the tuple descriptor with its reference count
		 * incremented, but not registered with a resource owner for descriptor
		 * leak warnings. l_r_t_n() will have incremented already, but also
		 * registered for warnings. The proper dance is a second pure increment
		 * here, followed by a DecrTupleDescRefCount to undo what l_r_t_n() did.
		 * And none of that, of course, if the descriptor is not refcounted.
		 */
		if ( td->tdrefcount >= 0 )
		{
			++ td->tdrefcount;
			DecrTupleDescRefCount(td);
		}
		length = (jlong)TupleDescSize(td);
		result = JNI_newDirectByteBuffer((void *)td, length);
	}
	END_NATIVE_AND_CATCH("_lookupRowtypeTupdesc")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _searchSysCacheCopy1
 * Signature: (II)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy1(JNIEnv *env, jclass cls, jint cacheId, jint key1)
{
	jobject result = NULL;
	HeapTuple ht;
	BEGIN_NATIVE_AND_TRY
	ht = SearchSysCacheCopy1(cacheId, Int32GetDatum(key1));
	if ( HeapTupleIsValid(ht) )
	{
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}
	END_NATIVE_AND_CATCH("_searchSysCacheCopy1")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _searchSysCacheCopy2
 * Signature: (III)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy2(JNIEnv *env, jclass cls, jint cacheId, jint key1, jint key2)
{
	jobject result = NULL;
	HeapTuple ht;
	BEGIN_NATIVE_AND_TRY
	ht = SearchSysCacheCopy2(cacheId, Int32GetDatum(key1), Int32GetDatum(key2));
	if ( HeapTupleIsValid(ht) )
	{
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}
	END_NATIVE_AND_CATCH("_searchSysCacheCopy2")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _sysTableGetByOid
 * Signature: (IIIIJ)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1sysTableGetByOid(JNIEnv *env, jclass cls, jint relOid, jint objOid, jint oidCol, jint indexOid, jlong tupleDesc)
{
	jobject result = NULL;
	HeapTuple ht;
	Relation rel;
	SysScanDesc scandesc;
	ScanKeyData entry[1];
	Ptr2Long p2l;

	p2l.longVal = tupleDesc;

	BEGIN_NATIVE_AND_TRY
	rel = relation_open((Oid)relOid, AccessShareLock);

	ScanKeyInit(&entry[0], (AttrNumber)oidCol, BTEqualStrategyNumber, F_OIDEQ,
		ObjectIdGetDatum((Oid)objOid));

	scandesc = systable_beginscan(
		rel, (Oid)indexOid, InvalidOid != indexOid, NULL, 1, entry);

	ht = systable_getnext(scandesc);

	/*
	 * As in the extension.c code from which this is brazenly copied, we assume
	 * there can be at most one matching tuple. (Oid ought to be the primary key
	 * of a catalog table we care about, so it's not a daring assumption.)
	 */
	if ( HeapTupleIsValid(ht) )
	{
		/*
		 * We wish to return a tuple satisfying the same conditions as if it had
		 * been obtained from the syscache, including that it has no external
		 * TOAST pointers. (Inline-compressed values, it could still have.)
		 */
		if ( HeapTupleHasExternal(ht) )
			ht = toast_flatten_tuple(ht, p2l.ptrVal);
		else
			ht = heap_copytuple(ht);
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}

	systable_endscan(scandesc);
	relation_close(rel, AccessShareLock);
	END_NATIVE_AND_CATCH("_sysTableGetByOid")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _tupDescBootstrap
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1tupDescBootstrap(JNIEnv* env, jobject _cls)
{
	Relation rel;
	TupleDesc td;
	jlong length;
	jobject result = NULL;
	BEGIN_NATIVE_AND_TRY
	rel = relation_open(RelationRelationId, AccessShareLock);
	td = RelationGetDescr(rel);
	/*
	 * Per contract, we return the tuple descriptor with its reference count
	 * incremented, without registering it with a resource owner for descriptor
	 * leak warnings.
	 */
	++ td->tdrefcount;
	/*
	 * Can close the relation now that the td reference count is bumped.
	 */
	relation_close(rel, AccessShareLock);
	length = (jlong)TupleDescSize(td);
	result = JNI_newDirectByteBuffer((void *)td, length);
	END_NATIVE_AND_CATCH("_tupDescBootstrap")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Factory
 * Method:    _currentDatabase
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Factory__1currentDatabase(JNIEnv *env, jclass cls)
{
	return MyDatabaseId;
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
 * Class:     org_postgresql_pljava_internal_SPI_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * SPI_result, SPI_processed, and SPI_tuptable. The indices into the array are
 * assigned arbitrarily in the internal class SPI, from which the native .h
 * makes them visible here.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_SPI_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)3, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, &tag, sizeof tag);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_internal_SPI_##tag, \
	b);\
} while (0)

	POPULATE(SPI_result);
	POPULATE(SPI_processed);
	POPULATE(SPI_tuptable);

#undef POPULATE

	return r;
}


/*
 * Class:     org_postgresql_pljava_pg_TupleDescImpl
 * Method:    _assign_record_type_typmod
 * Signature: (Ljava/nio/ByteBuffer;)I
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

/*
 * Class:     org_postgresql_pljava_pg_TupleDescImpl
 * Method:    _synthesizeDescriptor
 * Signature: (ILjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
 *
 * When synthesizing a TupleDescriptor from only a list of types and names, it
 * is tempting to make an ephemeral descriptor all in Java and avoid any JNI
 * call. On the other hand, TupleDescInitEntry is more likely to know what to
 * store in fields of the struct we don't care about, or added in new versions.
 *
 * The Java caller passes n (the number of attributes wanted) and one ByteBuffer
 * in which the sequence (int32 typoid, int32 typmod, bool array, encodedname\0)
 * occurs n times, INTALIGN'd between.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_TupleDescImpl__1synthesizeDescriptor(JNIEnv* env, jobject _cls, jint n, jobject in_b)
{
	jobject result = NULL;
	jlong tupdesc_size;
	int i;
	Oid typoid;
	int32 typmod;
	bool isArray;
	TupleDesc td;
	int32 *in_i;
	char *in_c = (*env)->GetDirectBufferAddress(env, in_b);
	if ( NULL == in_c )
		return NULL;

	BEGIN_NATIVE_AND_TRY

	td = CreateTemplateTupleDesc(n);

	for ( i = 0 ; i < n ; ++ i )
	{
		in_i = (int32 *)INTALIGN((uintptr_t)in_c);
		typoid = *(in_i++);
		typmod = *(in_i++);
		in_c = (char *)(uintptr_t)in_i;
		isArray = *(in_c++);

		TupleDescInitEntry(td, 1 + i, in_c, typoid, typmod, isArray ? 1 : 0);

		in_c += strlen(in_c) + 1;
	}

	tupdesc_size = (jlong)TupleDescSize(td);
	result = JNI_newDirectByteBuffer(td, tupdesc_size);

	END_NATIVE_AND_CATCH("_synthesizeDescriptor")
	return result;
}


/*
 * Class:     org_postgresql_pljava_pg_TupleTableSlotImpl
 * Method:    _getsomeattrs
 * Signature: (Ljava/nio/ByteBuffer;I)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1getsomeattrs(JNIEnv* env, jobject _cls, jobject tts_b, jint attnum)
{
	TupleTableSlot *tts = (*env)->GetDirectBufferAddress(env, tts_b);
	if ( NULL == tts )
		return;

	BEGIN_NATIVE_AND_TRY
	slot_getsomeattrs_int(tts, attnum);
	END_NATIVE_AND_CATCH("_getsomeattrs")
}

/*
 * Class:     org_postgresql_pljava_pg_TupleTableSlotImpl
 * Method:    _store_heaptuple
 * Signature: (Ljava/nio/ByteBuffer;JZ)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1store_1heaptuple(JNIEnv* env, jobject _cls, jobject tts_b, jlong ht, jboolean shouldFree)
{
	Ptr2Long p2l;
	HeapTuple htp;
	TupleTableSlot *tts = (*env)->GetDirectBufferAddress(env, tts_b);
	if ( NULL == tts )
		return;

	BEGIN_NATIVE_AND_TRY
	p2l.longVal = ht;
	htp = p2l.ptrVal;
	ExecStoreHeapTuple(htp, tts, JNI_TRUE == shouldFree);
	END_NATIVE_AND_CATCH("_store_heaptuple")
}
