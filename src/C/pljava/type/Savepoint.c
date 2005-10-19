/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_Savepoint.h"
#include "pljava/Exception.h"

#include "pljava/type/Type_priv.h"
#include "pljava/type/JavaHandle.h"
#include "pljava/type/String.h"
#include "pljava/SPI.h"

static Type      s_Savepoint;
static TypeClass s_SavepointClass;
static jclass    s_Savepoint_class;
static jmethodID s_Savepoint_init;

/*
 * org.postgresql.pljava.internal.Savepoint type.
 */
static jobject Savepoint_create(Savepoint* sp)
{
	jobject jsp;
	if(sp == 0)
		return 0;

	jsp = MemoryContext_lookupNative(sp);
	if(jsp == 0)
	{
		jsp = JNI_newObject(s_Savepoint_class, s_Savepoint_init);
		JavaHandle_init(jsp, sp);
	}
	return jsp;
}

static jvalue _Savepoint_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = Savepoint_create((Savepoint*)DatumGetPointer(arg));
	return result;
}

static Type Savepoint_obtain(Oid typeId)
{
	return s_Savepoint;
}

extern void Savepoint_initialize(void);
void Savepoint_initialize(void)
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

	s_Savepoint_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Savepoint"));
	PgObject_registerNatives2(s_Savepoint_class, methods);
	s_Savepoint_init = PgObject_getJavaMethod(s_Savepoint_class, "<init>", "()V");

	s_SavepointClass = JavaHandleClass_alloc("type.Savepoint");
	s_SavepointClass->JNISignature   = "Lorg/postgresql/pljava/internal/Savepoint;";
	s_SavepointClass->javaTypeName   = "org.postgresql.pljava.internal.Savepoint";
	s_SavepointClass->coerceDatum    = _Savepoint_coerceDatum;
	s_Savepoint = TypeClass_allocInstance(s_SavepointClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Savepoint", Savepoint_obtain);
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
	BEGIN_NATIVE
	PG_TRY();
	{
		char* name = String_createNTS(jname);
		jsp = Savepoint_create(SPI_setSavepoint(name));
		pfree(name);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_setSavepoint");
	}
	PG_END_TRY();
	END_NATIVE
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
	jstring result = 0;
	BEGIN_NATIVE
	Savepoint* self = (Savepoint*)JavaHandle_getStruct(_this);
	if(self != 0)
		result = String_createJavaStringFromNTS(self->name);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _release
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1release(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE
	Savepoint* self = (Savepoint*)JavaHandle_releasePointer(_this);
	if(self != 0)
	{
		PG_TRY();
		{
			SPI_releaseSavepoint(self);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_releaseSavepoint");
		}
		PG_END_TRY();
	}
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Savepoint
 * Method:    _rollback
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Savepoint__1rollback(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE
	Savepoint* self = (Savepoint*)JavaHandle_releasePointer(_this);
	if(self != 0)
	{
		PG_TRY();
		{
			SPI_rollbackSavepoint(self);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_rollbackSavepoint");
		}
		PG_END_TRY();
	}
	END_NATIVE
}
