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
#include "pljava/type/Portal.h"
#include "pljava/type/Portal_JNI.h"

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
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/Portal"));

	s_Portal_init = PgObject_getJavaMethod(
				env, s_Portal_class, "<init>", "()V");

	s_PortalClass = NativeStructClass_alloc("type.Tuple");
	s_PortalClass->JNISignature   = "Lorg/postgresql/pljava/Portal;";
	s_PortalClass->javaTypeName   = "org.postgresql.pljava.Portal";
	s_PortalClass->coerceDatum    = _Portal_coerceDatum;
	s_Portal = TypeClass_allocInstance(s_PortalClass);

	Type_registerJavaType("org.postgresql.pljava.Portal", Portal_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_Portal_close(JNIEnv* env, jobject _this)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return;
	
	SPI_cursor_close(portal);
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    getPortalPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_Portal_getPortalPos(JNIEnv* env, jobject _this)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	return (jint)portal->portalPos;
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    fetch
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_Portal_fetch(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	SPI_cursor_fetch(portal, forward == JNI_TRUE, (int)count);
	return (jint)SPI_result;
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    isAtStart
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_Portal_isAtStart(JNIEnv* env, jobject _this)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atStart ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    isAtEnd
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_Portal_isAtEnd(JNIEnv* env, jobject _this)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atEnd ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    isPosOverflow
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_Portal_isPosOverflow(JNIEnv* env, jobject _this)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->posOverflow ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_Portal
 * Method:    move
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_Portal_move(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	Portal portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	SPI_cursor_move(portal, forward == JNI_TRUE, (int)count);
	return (jint)SPI_result;
}

