/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include <postgres.h>
#include <miscadmin.h>
#include <utils/acl.h>
#include <catalog/pg_authid.h>

#include "pljava/type/AclId.h"
#include "pljava/type/Oid.h"
#include "pljava/type/String.h"
#include "pljava/type/Type_priv.h"
#include "org_postgresql_pljava_internal_AclId.h"
#include "pljava/Exception.h"

#if PG_VERSION_NUM >= 160000
#include <catalog/pg_namespace.h>
#define pg_namespace_aclcheck(oid,rid,mode) \
	object_aclcheck(NamespaceRelationId, (oid), (rid), (mode))
#endif

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
		"_getOuterUser",
		"()Lorg/postgresql/pljava/internal/AclId;",
		Java_org_postgresql_pljava_internal_AclId__1getOuterUser
		},
		{
		"_fromName",
		"(Ljava/lang/String;)Lorg/postgresql/pljava/internal/AclId;",
		Java_org_postgresql_pljava_internal_AclId__1fromName
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
 * Method:    _getOuterUser
 * Signature: ()Lorg/postgresql/pljava/internal/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1getOuterUser(JNIEnv* env, jclass clazz)
{
	jobject result = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		result = AclId_create(GetOuterUserId());
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("GetOuterUserId");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_AclId
 * Method:    _fromName
 * Signature: (Ljava/lang/String;)Lorg/postgresql/pljava/internal/AclId;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_AclId__1fromName(JNIEnv* env, jclass clazz, jstring jname)
{
	jobject result = 0;
	if(jname != 0)
	{
		BEGIN_NATIVE
		PG_TRY();
		{
			char* roleName = String_createNTS(jname);
			HeapTuple roleTup = SearchSysCache(AUTHNAME, PointerGetDatum(roleName), 0, 0, 0);
			if(!HeapTupleIsValid(roleTup))
				ereport(ERROR,
						(errcode(ERRCODE_UNDEFINED_OBJECT),
						 errmsg("role \"%s\" does not exist", roleName)));

			result = AclId_create(
#if PG_VERSION_NUM >= 120000
				((Form_pg_authid) GETSTRUCT(roleTup))->oid
#else
				HeapTupleGetOid(roleTup)
#endif
			);
			ReleaseSysCache(roleTup);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SearchSysCache");
		}
		PG_END_TRY();
		END_NATIVE
	}
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
		result = String_createJavaStringFromNTS(
			GetUserNameFromId(
				AclId_getAclId(aclId), /* noerr= */ false
			)
		);
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
