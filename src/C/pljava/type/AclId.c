/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <miscadmin.h>
#include <utils/acl.h>

#include "pljava/type/AclId.h"
#include "pljava/type/Oid.h"
#include "pljava/type/String.h"
#include "pljava/type/Type_priv.h"
#include "org_postgresql_pljava_internal_AclId.h"
#include "pljava/Exception.h"

static Type      s_AclId;
static TypeClass s_AclIdClass;
static jclass    s_AclId_class;
static jmethodID s_AclId_init;
static jfieldID  s_AclId_m_native;

/*
 * org.postgresql.pljava.type.AclId type.
 */
jobject AclId_create(AclId aclId)
{
	return JNI_newObject(s_AclId_class, s_AclId_init, (jint)aclId);
}

AclId AclId_getAclId(jobject aclId)
{
	return (AclId)JNI_getIntField(aclId, s_AclId_m_native);
}

static jvalue _AclId_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = AclId_create((AclId)DatumGetInt32(arg));
	return result;
}

static Datum _AclId_coerceObject(Type self, jobject aclidObj)
{
	return Int32GetDatum(AclId_getAclId(aclidObj));
}

static Type AclId_obtain(Oid typeId)
{
	return s_AclId;
}

extern void AclId_initialize(void);
void AclId_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_getUser",
	  	"()Lorg/postgresql/pljava/internal/AclId;",
	  	Java_org_postgresql_pljava_internal_AclId__1getUser
		},
		{
		"_getSessionUser",
		"()Lorg/postgresql/pljava/internal/AclId;",
		Java_org_postgresql_pljava_internal_AclId__1getSessionUser
		},
		{
		"_getName",
		"()Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_AclId__1getName
		},
		{
		"_hasSchemaCreatePermission",
		"(Lorg/postgresql/pljava/internal/Oid;)Z",
		Java_org_postgresql_pljava_internal_AclId__1hasSchemaCreatePermission
		},
		{
		"_isSuperuser",
		"()Z",
		Java_org_postgresql_pljava_internal_AclId__1isSuperuser
		},
		{ 0, 0, 0 }};

	s_AclId_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/AclId"));
	PgObject_registerNatives2(s_AclId_class, methods);
	s_AclId_init = PgObject_getJavaMethod(s_AclId_class, "<init>", "(I)V");
	s_AclId_m_native = PgObject_getJavaField(s_AclId_class, "m_native", "I");

	s_AclIdClass = TypeClass_alloc("type.AclId");
	s_AclIdClass->JNISignature   = "Lorg/postgresql/pljava/internal/AclId;";
	s_AclIdClass->javaTypeName   = "org.postgresql.pljava.internal.AclId";
	s_AclIdClass->coerceDatum    = _AclId_coerceDatum;
	s_AclIdClass->coerceObject   = _AclId_coerceObject;
	s_AclId = TypeClass_allocInstance(s_AclIdClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.AclId", AclId_obtain);
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _getUser
 * Signature: ()Lorg/postgresql/pljava/internal/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1getUser(JNIEnv* env, jclass clazz)
{
	jobject result = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		result = AclId_create(GetUserId());
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("GetUserId");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _getSessionUser
 * Signature: ()Lorg/postgresql/pljava/internal/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1getSessionUser(JNIEnv* env, jclass clazz)
{
	jobject result = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		result = AclId_create(GetSessionUserId());
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("GetSessionUserId");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_AclId__1getName(JNIEnv* env, jobject aclId)
{
	jstring result = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		result = String_createJavaStringFromNTS(GetUserNameFromId(AclId_getAclId(aclId)));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("GetUserNameFromId");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _hasSchemaCreatePermission
 * Signature: (Lorg/postgresql/pljava/internal/Oid)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_AclId__1hasSchemaCreatePermission(JNIEnv* env, jobject aclId, jobject oid)
{
	jboolean result = JNI_FALSE;
	BEGIN_NATIVE
	result = (jboolean)(pg_namespace_aclcheck(Oid_getOid(oid), AclId_getAclId(aclId), ACL_CREATE) == ACLCHECK_OK);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _isSuperuser
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_AclId__1isSuperuser(JNIEnv* env, jobject aclId)
{
	jboolean result = JNI_FALSE;
	BEGIN_NATIVE
	result = superuser_arg(AclId_getAclId(aclId)) ? JNI_TRUE : JNI_FALSE;
	END_NATIVE
	return result;
}
