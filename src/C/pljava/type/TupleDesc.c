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
		jtd = (*env)->NewObject(env, s_TupleDesc_class, s_TupleDesc_init);
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
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/TupleDesc"));

	s_TupleDesc_init = PgObject_getJavaMethod(
				env, s_TupleDesc_class, "<init>", "()V");

	s_TupleDescClass = NativeStructClass_alloc("type.TupleDesc");
	s_TupleDescClass->JNISignature   = "Lorg/postgresql/pljava/TupleDesc;";
	s_TupleDescClass->javaTypeName   = "org.postgresql.pljava.TupleDesc";
	s_TupleDescClass->coerceDatum    = _TupleDesc_coerceDatum;
	s_TupleDesc = TypeClass_allocInstance(s_TupleDescClass);

	Type_registerJavaType("org.postgresql.pljava.TupleDesc", TupleDesc_obtain);
	PG_RETURN_VOID();
}

/*
 * Class:     org_postgresql_pljava_TupleDesc
 * Method:    getColumnName
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_TupleDesc_getColumnName(JNIEnv* env, jobject _this, jint index)
{
	TupleDesc self = (TupleDesc)NativeStruct_getStruct(env, _this);

	char* name = SPI_fname(self, (int)index);
	if(name == 0)
	{
		Exception_throw(env,
			ERRCODE_INVALID_DESCRIPTOR_INDEX,
			"Invalid attribute index \"%d\"", (int)index);
		return 0;
	}
	jstring ret = String_createJavaStringFromNTS(env, name);
	pfree(name);
	return ret;
}

/*
 * Class:     org_postgresql_pljava_TupleDesc
 * Method:    getColumnIndex
 * Signature: (Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_TupleDesc_getColumnIndex(JNIEnv* env, jobject _this, jstring colName)
{
	TupleDesc self = (TupleDesc)NativeStruct_getStruct(env, _this);
	char* name = String_createNTS(env, colName);
	jint index = SPI_fnumber(self, name);
	if(index < 0)
	{
		Exception_throw(env,
			ERRCODE_UNDEFINED_COLUMN,
			"Tuple has no attribute \"%s\"", name);
		return 0;
	}
	pfree(name);
	return index;
}

