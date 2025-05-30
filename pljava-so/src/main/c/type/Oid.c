/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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

#define Type PGType
#include <parser/parse_type.h>
#undef Type

#include "org_postgresql_pljava_internal_Oid.h"
#include "java_sql_Types.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Oid.h"
#include "pljava/type/String.h"
#include "pljava/Exception.h"
#include "pljava/Function.h"

static jclass    s_Oid_class;
static jmethodID s_Oid_init;
static jmethodID s_Oid_registerType;
static jfieldID  s_Oid_m_native;
static jobject   s_OidOid;

static jclass    s_CatalogObject_class;
static jclass    s_CatalogObjectImpl_class;
static jmethodID s_CatalogObjectImpl_of;
static jmethodID s_CatalogObject_oid;

static bool _CatalogObject_canReplaceType(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls  ||  Type_getOid(other) == OIDOID;
}

static jvalue _CatalogObject_coerceDatum(Type self, Datum arg)
{
	Oid oid = DatumGetObjectId(arg);
	jvalue result;
	result.l = JNI_callStaticObjectMethodLocked(
		s_CatalogObjectImpl_class, s_CatalogObjectImpl_of, (jint)oid);
	return result;
}

static Datum _CatalogObject_coerceObject(Type self, jobject obj)
{
	jint o = JNI_callIntMethod(obj, s_CatalogObject_oid);
	return ObjectIdGetDatum((Oid)o);
}

/*
 * org.postgresql.pljava.type.Oid type.
 */
jobject Oid_create(Oid oid)
{
	jobject joid;
	/*
	 * This is a natural place to have a StaticAssertStmt making sure the
	 * ubiquitous PG type 'Oid' fits in a jint. If it is ever removed from here
	 * or this code goes away, it should go someplace else. If it ever produces
	 * an error, don't assume the only things that need fixing will be in this
	 * file or nearby....
	 */
	StaticAssertStmt(sizeof(Oid) <= sizeof(jint), "Oid wider than jint?!");
	if(OidIsValid(oid))
		joid = JNI_newObject(s_Oid_class, s_Oid_init, oid);
	else
		joid = 0;
	return joid;
}

Oid Oid_getOid(jobject joid)
{
	if(joid == 0)
		return InvalidOid;
	return ObjectIdGetDatum(JNI_getIntField(joid, s_Oid_m_native));
}

Oid Oid_forSqlType(int sqlType)
{
	Oid typeId;

	switch(sqlType)
	{
		case java_sql_Types_BIT:
			typeId = BITOID;
			break;
		case java_sql_Types_TINYINT:
			typeId = CHAROID;
			break;
		case java_sql_Types_SMALLINT:
			typeId = INT2OID;
			break;
		case java_sql_Types_INTEGER:
			typeId = INT4OID;
			break;
		case java_sql_Types_BIGINT:
			typeId = INT8OID;
			break;
		case java_sql_Types_FLOAT:
		case java_sql_Types_REAL:
			typeId = FLOAT4OID;
			break;
		case java_sql_Types_DOUBLE:
			typeId = FLOAT8OID;
			break;
		case java_sql_Types_NUMERIC:
		case java_sql_Types_DECIMAL:
			typeId = NUMERICOID;
			break;
		case java_sql_Types_DATE:
			typeId = DATEOID;
			break;
		case java_sql_Types_TIME:
			typeId = TIMEOID;
			break;
		case java_sql_Types_TIMESTAMP:
			typeId = TIMESTAMPOID;
			break;
		case java_sql_Types_BOOLEAN:
			typeId = BOOLOID;
			break;
		case java_sql_Types_BINARY:
		case java_sql_Types_VARBINARY:
		case java_sql_Types_LONGVARBINARY:
		case java_sql_Types_BLOB:
			typeId = BYTEAOID;
			break;
		case java_sql_Types_CHAR:
		case java_sql_Types_VARCHAR:
		case java_sql_Types_LONGVARCHAR:
		case java_sql_Types_CLOB:
		case java_sql_Types_DATALINK:
			typeId = TEXTOID;
			break;
		case java_sql_Types_NULL:
		case java_sql_Types_OTHER:
		case java_sql_Types_JAVA_OBJECT:
		case java_sql_Types_DISTINCT:
		case java_sql_Types_STRUCT:
		case java_sql_Types_ARRAY:
		case java_sql_Types_REF:
			typeId = InvalidOid;	/* Not yet mapped */
			break;

		/* JDBC 4.0 - present in Java 6 and later, no need to conditionalize */
		case java_sql_Types_SQLXML:
#ifdef	XMLOID					/* but PG can have been built without libxml */
			typeId = XMLOID;
#else
			typeId = InvalidOid;
#endif
			break;
		case java_sql_Types_ROWID:
		case java_sql_Types_NCHAR:
		case java_sql_Types_NVARCHAR:
		case java_sql_Types_LONGNVARCHAR:
		case java_sql_Types_NCLOB:
			typeId = InvalidOid;	/* Not yet mapped */
			break;

		case java_sql_Types_TIME_WITH_TIMEZONE:
			typeId = TIMETZOID;
			break;
		case java_sql_Types_TIMESTAMP_WITH_TIMEZONE:
			typeId = TIMESTAMPTZOID;
			break;
		case java_sql_Types_REF_CURSOR:
		default:
			typeId = InvalidOid;	/* Not yet mapped */
			break;
	}
	return typeId;
}

static jvalue _Oid_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = Oid_create(DatumGetObjectId(arg));
	return result;
}

static Datum _Oid_coerceObject(Type self, jobject oidObj)
{
	return Oid_getOid(oidObj);
}

/* Make this datatype available to the postgres system.
 */
extern void Oid_initialize(void);
void Oid_initialize(void)
{
	TypeClass cls;
	JNINativeMethod methods[] = {
		{
		"_forTypeName",
	  	"(Ljava/lang/String;)I",
	  	Java_org_postgresql_pljava_internal_Oid__1forTypeName
		},
		{
		"_forSqlType",
	  	"(I)I",
	  	Java_org_postgresql_pljava_internal_Oid__1forSqlType
		},
		{
		"_getTypeId",
		"()Lorg/postgresql/pljava/internal/Oid;",
		Java_org_postgresql_pljava_internal_Oid__1getTypeId
		},
		{
		"_getJavaClassName",
		"(I)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_Oid__1getJavaClassName
		},
		{
		"_getCurrentLoader",
		"()Ljava/lang/ClassLoader;",
		Java_org_postgresql_pljava_internal_Oid__1getCurrentLoader
		},
		{ 0, 0, 0 }};

	jobject tmp;

	s_Oid_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Oid"));
	PgObject_registerNatives2(s_Oid_class, methods);
	s_Oid_init = PgObject_getJavaMethod(s_Oid_class, "<init>", "(I)V");
	s_Oid_m_native = PgObject_getJavaField(s_Oid_class, "m_native", "I");

	cls = TypeClass_alloc("type.Oid");
	cls->JNISignature   = "Lorg/postgresql/pljava/internal/Oid;";
	cls->javaTypeName   = "org.postgresql.pljava.internal.Oid";
	cls->coerceDatum    = _Oid_coerceDatum;
	cls->coerceObject   = _Oid_coerceObject;
	Type_registerType("org.postgresql.pljava.internal.Oid", TypeClass_allocInstance(cls, OIDOID));

	tmp = Oid_create(OIDOID);
	s_OidOid = JNI_newGlobalRef(tmp);
	JNI_deleteLocalRef(tmp);

	s_Oid_registerType = PgObject_getStaticJavaMethod(
				s_Oid_class, "registerType",
				"(Ljava/lang/Class;Lorg/postgresql/pljava/internal/Oid;)V");

	JNI_callStaticVoidMethod(s_Oid_class, s_Oid_registerType, s_Oid_class, s_OidOid);

	s_CatalogObject_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/model/CatalogObject"));
	s_CatalogObjectImpl_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/pg/CatalogObjectImpl"));
	s_CatalogObject_oid = PgObject_getJavaMethod(s_CatalogObject_class,
		"oid", "()I");
	s_CatalogObjectImpl_of = PgObject_getStaticJavaMethod(
		s_CatalogObjectImpl_class,
		"of", "(I)Lorg/postgresql/pljava/model/CatalogObject;");

	cls = TypeClass_alloc("type.CatalogObject");
	cls->JNISignature   = "Lorg/postgresql/pljava/model/CatalogObject;";
	cls->javaTypeName   = "org.postgresql.pljava.model.CatalogObject";
	cls->canReplaceType = _CatalogObject_canReplaceType;
	cls->coerceDatum    = _CatalogObject_coerceDatum;
	cls->coerceObject   = _CatalogObject_coerceObject;
	Type_registerType("org.postgresql.pljava.model.CatalogObject", 
		TypeClass_allocInstance(cls, OIDOID));
}

/*
 * Class:     org_postgresql_pljava_internal_Oid
 * Method:    _forSqlType
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Oid__1forSqlType(JNIEnv* env, jclass cls, jint sqlType)
{
	Oid typeId = InvalidOid;
	BEGIN_NATIVE
	typeId = Oid_forSqlType(sqlType);
	if(typeId == InvalidOid)
		Exception_throw(ERRCODE_INTERNAL_ERROR, "No such SQL type: %d", (int)sqlType);
	END_NATIVE
	return typeId;
}

/*
 * Class:     org_postgresql_pljava_internal_Oid
 * Method:    _forTypeName
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Oid__1forTypeName(JNIEnv* env, jclass cls, jstring typeString)
{
	Oid typeId = InvalidOid;
	BEGIN_NATIVE
	char* typeNameOrOid = String_createNTS(typeString);
	if(typeNameOrOid != 0)
	{
		PG_TRY();
		{
			int32 typmod = 0;
			parseTypeString(typeNameOrOid, &typeId, &typmod, 0);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("parseTypeString");
		}
		PG_END_TRY();
		pfree(typeNameOrOid);
	}
	END_NATIVE
	return typeId;
}


/*
 * Class:     org_postgresql_pljava_internal_Oid
 * Method:    _getTypeId
 * Signature: ()Lorg/postgresql/pljava/internal/Oid;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Oid__1getTypeId(JNIEnv* env, jclass cls)
{
	return s_OidOid;
}

/*
 * Class:     org_postgresql_pljava_internal_Oid
 * Method:    _getJavaClassName
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Oid__1getJavaClassName(JNIEnv* env, jclass cls, jint oid)
{
	jstring result = 0;
	BEGIN_NATIVE
	if(!OidIsValid((Oid)oid))
	{
		Exception_throw(ERRCODE_DATA_EXCEPTION, "Invalid OID \"%d\"", (int)oid);
	}
	else
	{
		Type type = Type_objectTypeFromOid((Oid)oid, Function_currentTypeMap());
		result = String_createJavaStringFromNTS(Type_getJavaTypeName(type));
	}
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Oid
 * Method:    _getCurrentLoader
 * Signature: ()Ljava/lang/ClassLoader;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Oid__1getCurrentLoader(JNIEnv *env, jclass cls)
{
	jobject result = NULL;
	BEGIN_NATIVE
	result = Function_currentLoader();
	END_NATIVE
	return result;
}
