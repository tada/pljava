/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_Savepoint.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/NativeStruct.h"
#include "pljava/type/String.h"
#include "pljava/SPI.h"

static Type      s_Savepoint;
static TypeClass s_SavepointClass;
static jclass    s_Savepoint_class;
static jmethodID s_Savepoint_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
static jobject Savepoint_create(JNIEnv* env, Savepoint* sp)
{
	jobject jsp;
	if(sp == 0)
		return 0;

	jsp = MemoryContext_lookupNative(env, sp);
	if(jsp == 0)
	{
		jsp = PgObject_newJavaObject(env, s_Savepoint_class, s_Savepoint_init);
		NativeStruct_init(env, jsp, sp);
	}
	return jsp;
}

static jvalue _Savepoint_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = Savepoint_create(env, (Savepoint*)DatumGetPointer(arg));
	return result;
}

static Type Savepoint_obtain(Oid typeId)
{
	return s_Savepoint;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Savepoint_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Savepoint_initialize);
Datum Savepoint_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_set",
	  	"(Ljava/lang/String;)Lorg/postgresql/pljava/internal/Savepoint;",
	  	Java_org_postgresql_pljava_internal_Savepoint__1set
		},
		{
		"_release",
		"()V",
		Java_org_postgresql_pljava_internal_Savepoint__1release
		},
		{
		"_rollback",
		"()V",
		Java_org_postgresql_pljava_internal_Savepoint__1rollback
		},
		{
		"_getName",
		"()Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Savepoint__1getName
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Savepoint_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Savepoint"));

	PgObject_registerNatives2(env, s_Savepoint_class, methods);

	s_Savepoint_init = PgObject_getJavaMethod(
				env, s_Savepoint_class, "<init>", "()V");

	s_SavepointClass = NativeStructClass_alloc("type.Savepoint");
	s_SavepointClass->JNISignature   = "Lorg/postgresql/pljava/internal/Savepoint;";
	s_SavepointClass->javaTypeName   = "org.postgresql.pljava.internal.Savepoint";
	s_SavepointClass->coerceDatum    = _Savepoint_coerceDatum;
	s_Savepoint = TypeClass_allocInstance(s_SavepointClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Savepoint", Savepoint_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _set
 * Signature: (Ljava/lang/String;)Lorg/postgresql/pljava/internal/Savepoint;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1set(JNIEnv* env, jclass cls, jstring jname)
{
	jobject jsp = 0;
	PLJAVA_ENTRY_FENCE(0)
	PG_TRY();
	{
		char* name = String_createNTS(env, jname);
		jsp = Savepoint_create(env, SPI_setSavepoint(name));
		pfree(name);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_setSavepoint");
	}
	PG_END_TRY();
	return jsp;
}

/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1getName(JNIEnv* env, jobject _this)
{
	Savepoint* self;
	PLJAVA_ENTRY_FENCE(0)
	self = (Savepoint*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;
	return String_createJavaStringFromNTS(env, self->name);
}

/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _release
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1release(JNIEnv* env, jobject _this)
{
	Savepoint* self;
	PLJAVA_ENTRY_FENCE_VOID
	self = (Savepoint*)NativeStruct_releasePointer(env, _this);
	if(self == 0)
		return;

	PG_TRY();
	{
		SPI_releaseSavepoint(self);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_releaseSavepoint");
	}
	PG_END_TRY();
}

/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _rollback
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1rollback(JNIEnv* env, jobject _this)
{
	Savepoint* self;
	PLJAVA_ENTRY_FENCE_VOID
	self = (Savepoint*)NativeStruct_releasePointer(env, _this);
	if(self == 0)
		return;

	PG_TRY();
	{
		SPI_rollbackSavepoint(self);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_rollbackSavepoint");
	}
	PG_END_TRY();
}
