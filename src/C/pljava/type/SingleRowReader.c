/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleTableSlot.h"

/*
 * void primitive type.
 */
static jclass s_SingleRowReader_class;
static jmethodID s_SingleRowReader_init;
static TypeClass s_SingleRowReaderClass;
static Type s_SingleRowReader;

static jvalue _SingleRowReader_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	jobject ttSlot = TupleTableSlot_create(env, (TupleTableSlot*)DatumGetPointer(arg));
	result.l = PgObject_newJavaObject(env, s_SingleRowReader_class, s_SingleRowReader_init, ttSlot);
	(*env)->DeleteLocalRef(env, ttSlot);
	return result;
}

static Datum _SingleRowReader_coerceObject(Type self, JNIEnv* env, jobject nothing)
{
	/* Should never be used here.
	 */
	return 0;
}

static Type SingleRowReader_obtain(Oid typeId)
{
	return s_SingleRowReader;
}

/* Make this datatype available to the postgres system.
 */
extern Datum SingleRowReader_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(SingleRowReader_initialize);
Datum SingleRowReader_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_SingleRowReader_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/jdbc/SingleRowReader"));

	s_SingleRowReader_init = PgObject_getJavaMethod(
				env, s_SingleRowReader_class, "<init>", "(Lorg/postgresql/pljava/internal/TupleTableSlot;)V");

	s_SingleRowReaderClass = TypeClass_alloc("type.SingleRowReader");
	s_SingleRowReaderClass->JNISignature = "Ljava/sql/ResultSet;";
	s_SingleRowReaderClass->javaTypeName = "java.lang.ResultSet";
	s_SingleRowReaderClass->coerceDatum  = _SingleRowReader_coerceDatum;
	s_SingleRowReaderClass->coerceObject = _SingleRowReader_coerceObject;
	s_SingleRowReader = TypeClass_allocInstance(s_SingleRowReaderClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.jdbc.SingleRowReader", SingleRowReader_obtain);
	PG_RETURN_VOID();
}
