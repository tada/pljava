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

#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/TupleDesc_JNI.h"

static Type      s_TupleDesc;
static TypeClass s_TupleDescClass;
static jclass    s_TupleDesc_class;
static jmethodID s_TupleDesc_init;

/*
 * org.postgresql.pljava.TupleDesc type.
 */
jobject TupleDesc_create(JNIEnv* env, TupleDesc td)
{
	if(td == 0)
		return 0;

	jobject jtd = NativeStruct_obtain(env, td);
	if(jtd == 0)
	{
		jtd = PgObject_newJavaObject(env, s_TupleDesc_class, s_TupleDesc_init);
		NativeStruct_init(env, jtd, td);
	}
	return jtd;
}

static jvalue _TupleDesc_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = TupleDesc_create(env, (TupleDesc)DatumGetPointer(arg));
	return result;
}

static Type TupleDesc_obtain(Oid typeId)
{
	return s_TupleDesc;
}

/* Make this datatype available to the postgres system.
 */
extern Datum TupleDesc_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(TupleDesc_initialize);
Datum TupleDesc_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TupleDesc_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TupleDesc"));

	s_TupleDesc_init = PgObject_getJavaMethod(
				env, s_TupleDesc_class, "<init>", "()V");

	s_TupleDescClass = NativeStructClass_alloc("type.TupleDesc");
	s_TupleDescClass->JNISignature   = "Lorg/postgresql/pljava/internal/TupleDesc;";
	s_TupleDescClass->javaTypeName   = "org.postgresql.pljava.internal.TupleDesc";
	s_TupleDescClass->coerceDatum    = _TupleDesc_coerceDatum;
	s_TupleDesc = TypeClass_allocInstance(s_TupleDescClass);

	Type_registerJavaType("org.postgresql.pljava.internal.TupleDesc", TupleDesc_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnName
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName(JNIEnv* env, jobject _this, jint index)
{
	PLJAVA_ENTRY_FENCE(0)
	TupleDesc self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	jstring result = 0;
	PLJAVA_TRY
	{
		char* name = SPI_fname(self, (int)index);
		if(name == 0)
		{
			Exception_throw(env,
				ERRCODE_INVALID_DESCRIPTOR_INDEX,
				"Invalid attribute index \"%d\"", (int)index);
		}
		else
		{
			result = String_createJavaStringFromNTS(env, name);
			pfree(name);
		}
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_fname");
	}
	PLJAVA_TCEND
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnIndex
 * Signature: (Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex(JNIEnv* env, jobject _this, jstring colName)
{
	PLJAVA_ENTRY_FENCE(0)
	TupleDesc self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;
	
	char* name = String_createNTS(env, colName);
	if(name == 0)
		return 0;

	jint index = 0;
	PLJAVA_TRY
	{
		index = SPI_fnumber(self, name);
		if(index < 0)
		{
			Exception_throw(env,
				ERRCODE_UNDEFINED_COLUMN,
				"Tuple has no attribute \"%s\"", name);
		}
		pfree(name);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_fnumber");
	}
	PLJAVA_TCEND
	return index;
}

