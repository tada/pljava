/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <miscadmin.h>

#include "pljava/type/String.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/AclId_JNI.h"
#include "pljava/Exception.h"

static Type      s_AclId;
static TypeClass s_AclIdClass;
static jclass    s_AclId_class;
static jmethodID s_AclId_init;
static jfieldID  s_AclId_m_native;

/*
 * org.postgresql.pljava.type.AclId type.
 */
static jobject AclId_create(JNIEnv* env, AclId aclId)
{
	return PgObject_newJavaObject(env, s_AclId_class, s_AclId_init, (jint)aclId);
}

static AclId AclId_getAclId(JNIEnv* env, jobject aclId)
{
	return (AclId)(*env)->GetIntField(env, aclId, s_AclId_m_native);
}

static jvalue _AclId_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = AclId_create(env, (AclId)DatumGetInt32(arg));
	return result;
}

static Datum _AclId_coerceObject(Type self, JNIEnv* env, jobject aclidObj)
{
	return Int32GetDatum(AclId_getAclId(env, aclidObj));
}

static Type AclId_obtain(Oid typeId)
{
	return s_AclId;
}

/* Make this datatype available to the postgres system.
 */
extern Datum AclId_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(AclId_initialize);
Datum AclId_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_AclId_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/AclId"));

	s_AclId_init = PgObject_getJavaMethod(
				env, s_AclId_class, "<init>", "(I)V");

	s_AclId_m_native = PgObject_getJavaField(
				env, s_AclId_class, "m_native", "I");

	s_AclIdClass = TypeClass_alloc("type.AclId");
	s_AclIdClass->JNISignature   = "Lorg/postgresql/pljava/internal/AclId;";
	s_AclIdClass->javaTypeName   = "org.postgresql.pljava.internal.AclId";
	s_AclIdClass->coerceDatum    = _AclId_coerceDatum;
	s_AclIdClass->coerceObject   = _AclId_coerceObject;
	s_AclId = TypeClass_allocInstance(s_AclIdClass);

	Type_registerJavaType("org.postgresql.pljava.internal.AclId", AclId_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_AclId
 * Method:    _getUser
 * Signature: ()Lorg/postgresql/pljava/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1getUser(JNIEnv* env, jclass clazz)
{
	PLJAVA_ENTRY_FENCE(0)
	
	jobject result = 0;
	PLJAVA_TRY
	{
		result = AclId_create(env, GetUserId());
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "GetUserId");
	}
	PLJAVA_TCEND
	return result;
}

/*
 * Class:     org_postgresql_pljava_AclId
 * Method:    _getSessionUser
 * Signature: ()Lorg/postgresql/pljava/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1getSessionUser(JNIEnv* env, jclass clazz)
{
	PLJAVA_ENTRY_FENCE(0)
	
	jobject result = 0;
	PLJAVA_TRY
	{
		result = AclId_create(env, GetSessionUserId());
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "GetSessionUserId");
	}
	PLJAVA_TCEND
	return result;
}

/*
 * Class:     org_postgresql_pljava_AclId
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_AclId__1getName(JNIEnv* env, jobject aclId)
{
	PLJAVA_ENTRY_FENCE(0)
	jstring result = 0;
	PLJAVA_TRY
	{
		result = String_createJavaStringFromNTS(env,
			GetUserNameFromId(AclId_getAclId(env, aclId)));
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "GetUserNameFromId");
	}
	PLJAVA_TCEND
	return result;
}
