/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
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
	jobject jtts;
	if(tts == 0)
		return 0;

	jtts = NativeStruct_obtain(env, tts);
	if(jtts == 0)
	{
		jtts = PgObject_newJavaObject(env, s_Portal_class, s_Portal_init);
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
	JNINativeMethod methods[] = {
		{
		"_getName",
		"()Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Portal__1getName
		},
		{
		"_getPortalPos",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_Portal__1getPortalPos
		},
		{
		"_getTupleDesc",
		"()Lorg/postgresql/pljava/internal/TupleDesc;",
		Java_org_postgresql_pljava_internal_Portal__1getTupleDesc
		},
		{
		"_fetch",
	  	"(ZI)I",
	  	Java_org_postgresql_pljava_internal_Portal__1fetch
		},
		{
		"_invalidate",
	  	"()V",
	  	Java_org_postgresql_pljava_internal_Portal__1invalidate
		},
		{
		"_isAtEnd",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isAtEnd
		},
		{
		"_isAtStart",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isAtStart
		},
		{
		"_isPosOverflow",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isPosOverflow
		},
		{
		"_move",
	  	"(ZI)I",
	  	Java_org_postgresql_pljava_internal_Portal__1move
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Portal_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Portal"));

	PgObject_registerNatives2(env, s_Portal_class, methods);

	s_Portal_init = PgObject_getJavaMethod(
				env, s_Portal_class, "<init>", "()V");

	s_PortalClass = NativeStructClass_alloc("type.Tuple");
	s_PortalClass->JNISignature   = "Lorg/postgresql/pljava/internal/Portal;";
	s_PortalClass->javaTypeName   = "org.postgresql.pljava.internal.Portal";
	s_PortalClass->coerceDatum    = _Portal_coerceDatum;
	s_Portal = TypeClass_allocInstance(s_PortalClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Portal", Portal_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getPortalPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1getPortalPos(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(0)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	return (jint)portal->portalPos;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _fetch
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1fetch(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(0)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;

	PLJAVA_TRY
	{
		SPI_cursor_fetch(portal, forward == JNI_TRUE, (int)count);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_cursor_fetch");
	}
	PLJAVA_TCEND
	return (jint)SPI_processed;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Portal__1getName(JNIEnv* env, jobject _this)
{
	Portal portal;
	const char* name;
	PLJAVA_ENTRY_FENCE(0)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	name = portal->name;
	if(name == 0)
		return 0;
	return String_createJavaStringFromNTS(env, name);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getTupleDesc
 * Signature: ()Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Portal__1getTupleDesc(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(0)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;
	return TupleDesc_create(env, portal->tupDesc);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _invalidate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Portal__1invalidate(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE_VOID
	portal = (Portal)NativeStruct_releasePointer(env, _this);
	if(portal != 0)
		SPI_cursor_close(portal);
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _isAtStart
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isAtStart(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(false)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atStart ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _isAtEnd
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isAtEnd(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(false)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->atEnd ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _isPosOverflow
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isPosOverflow(JNIEnv* env, jobject _this)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(false)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return false;
	
	return portal->posOverflow ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _move
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1move(JNIEnv* env, jobject _this, jboolean forward, jint count)
{
	Portal portal;
	PLJAVA_ENTRY_FENCE(0)
	portal = (Portal)NativeStruct_getStruct(env, _this);
	if(portal == 0)
		return 0;

	PLJAVA_TRY
	{
		SPI_cursor_move(portal, forward == JNI_TRUE, (int)count);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_cursor_move");
	}
	PLJAVA_TCEND
	return (jint)SPI_processed;
}
