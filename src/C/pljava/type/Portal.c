/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Portal.h"
#include "pljava/type/Portal_JNI.h"
#include "pljava/type/String.h"

static Type      s_Portal;
static TypeClass s_PortalClass;
static jclass    s_Portal_class;
static jmethodID s_Portal_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject Portal_create(JNIEnv* env, Portal tts)
{
	if(tts == 0)
		return 0;

	jobject jtts = NativeStruct_obtain(env, tts);
	if(jtts == 0)
	{
		jtts = (*env)->NewObject(env, s_Portal_class, s_Portal_init);
		NativeStruct_init(env, jtts, tts);
	}
	return jtts;
}

static jvalue _Portal_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = Portal_create(env, (Portal)DatumGetPointer(arg));
	return result;
}

static Type Portal_obtain(Oid typeId)
{
	return s_Portal;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Portal_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Portal_initialize);
Datum Portal_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Portal_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Portal"));

	s_Portal_init = PgObject_getJavaMethod(
				env, s_Portal_class, "<init>", "()V");

	s_PortalClass = NativeStructClass_alloc("type.Tuple");
	s_PortalClass->JNISignature   = "Lorg/postgresql/pljava/internal/Portal;";
	s_PortalClass->javaTypeName   = "org.postgresql.pljava.internal.Portal";
	s_PortalClass->coerceDatum    = _Portal_coerceDatum;
	s_Portal = TypeClass_allocInstance(s_PortalClass);

	Type_registerJavaType("org.postgresql.pljava.internal.Portal", Portal_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    getPortalPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal_getPortalPos(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(0)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	return (jint)portal->portalPos;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    fetch
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal_fetch(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	THREAD_FENCE(0)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	SPI_cursor_fetch(portal, forward == JNI_TRUE, (int)count);
	return (jint)SPI_processed;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Portal_getName(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(0)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	const char* name = portal->name;
	if(name == 0)
		return 0;
	return String_createJavaStringFromNTS(env, name);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    getTupleDesc
 * Signature: ()Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Portal_getTupleDesc(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(0)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	return TupleDesc_create(env, portal->tupDesc);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    invalidate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Portal_invalidate(JNIEnv* env, jobject _this)
{
	THREAD_FENCE_VOID
	Portal portal = (Portal)NativeStruct_releasePointer(env, _this);
	if(portal != 0)
		SPI_cursor_close(portal);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    isAtStart
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal_isAtStart(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(false)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atStart ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    isAtEnd
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal_isAtEnd(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(false)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atEnd ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    isPosOverflow
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal_isPosOverflow(JNIEnv* env, jobject _this)
{
	THREAD_FENCE(false)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->posOverflow ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    move
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal_move(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	THREAD_FENCE(0)
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	SPI_cursor_move(portal, forward == JNI_TRUE, (int)count);
	return (jint)SPI_result;
}
