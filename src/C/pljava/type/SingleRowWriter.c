/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <funcapi.h>

#include "pljava/MemoryContext.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/SingleRowWriter.h"

/*
 * void primitive type.
 */
static jclass s_SingleRowWriter_class;
static jmethodID s_SingleRowWriter_init;
static jmethodID s_SingleRowWriter_getTupleAndClear;
static TypeClass s_SingleRowWriterClass;
static HashMap s_cache;

/*
 * This function is a bit special in that it adds an additional parameter
 * to the parameter list (a java.sql.ResultSet implemented as a
 * SingleRowWriter) and calls a boolean method. It's assumed that the
 * SingleRowWriter has been initialized with values if the method returns
 * true. If so, the values are obtained in the form of a HeapTuple which in
 * turn is returned (as a Datum) from this method.
 * 
 * NOTE! It's an absolute prerequisite that the args argument has room for
 * one extra parameter.
 */
static Datum _SingleRowWriter_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool saveIcj = isCallingJava;
	bool hasRow;
	Datum result = 0;
	TupleDesc tupleDesc = TupleDesc_forOid(Type_getOid(self));
	jobject singleRowWriter = SingleRowWriter_create(env, tupleDesc);
	int numArgs = fcinfo->nargs;

	/* It's guaranteed that the args array has room for one more
	 * argument.
	 */
	args[numArgs].l = singleRowWriter;

	isCallingJava = true;
	hasRow = ((*env)->CallStaticBooleanMethodA(env, cls, method, args) == JNI_TRUE);
	isCallingJava = saveIcj;

	if(hasRow)
	{
		/* Obtain tuple and return it as a Datum. Must be done using a more
		 * durable context.
		 */
		MemoryContext currCtx = MemoryContext_switchToUpperContext();
		HeapTuple tuple = SingleRowWriter_getTupleAndClear(env, singleRowWriter);
#if (PGSQL_MAJOR_VER == 7 && PGSQL_MINOR_VER < 5)
	    result = TupleGetDatum(TupleDescGetSlot(tupleDesc), tuple);
#else
	    result = HeapTupleGetDatum(tuple);
#endif
		MemoryContextSwitchTo(currCtx);
	}
	else
		fcinfo->isnull = true;

	(*env)->DeleteLocalRef(env, singleRowWriter);
	return result;
}

jobject SingleRowWriter_create(JNIEnv* env, TupleDesc tupleDesc)
{
	jobject jtd;
	jobject result;
	if(tupleDesc == 0)
		return 0;

	jtd = TupleDesc_create(env, tupleDesc);
	result = PgObject_newJavaObject(env, s_SingleRowWriter_class, s_SingleRowWriter_init, jtd);
	(*env)->DeleteLocalRef(env, jtd);
	return result;
}

HeapTuple SingleRowWriter_getTupleAndClear(JNIEnv* env, jobject jrps)
{
	jobject tuple;
	HeapTuple result;
	bool saveIcj = isCallingJava;

	if(jrps == 0)
		return 0;

	isCallingJava = true;
	tuple = (*env)->CallObjectMethod(env, jrps, s_SingleRowWriter_getTupleAndClear);
	isCallingJava = saveIcj;
	if(tuple == 0)
		return 0;

	result = (HeapTuple)NativeStruct_getStruct(env, tuple);
	(*env)->DeleteLocalRef(env, tuple);
	return result;
}

static jvalue _SingleRowWriter_coerceDatum(Type self, JNIEnv* env, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _SingleRowWriter_coerceObject(Type self, JNIEnv* env, jobject nothing)
{
	return 0;
}

static Type SingleRowWriter_obtain(Oid typeId)
{
	/* Check to see if we have a cached version for this
	 * postgres type
	 */
	Type infant = (Type)HashMap_getByOid(s_cache, typeId);
	if(infant == 0)
	{
		infant = TypeClass_allocInstance(s_SingleRowWriterClass, typeId);
		HashMap_putByOid(s_cache, typeId, infant);
	}
	return infant;
}

/* Make this datatype available to the postgres system.
 */
extern Datum SingleRowWriter_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(SingleRowWriter_initialize);
Datum SingleRowWriter_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_SingleRowWriter_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/jdbc/SingleRowWriter"));

	s_SingleRowWriter_init = PgObject_getJavaMethod(
				env, s_SingleRowWriter_class, "<init>", "(Lorg/postgresql/pljava/internal/TupleDesc;)V");

	s_SingleRowWriter_getTupleAndClear = PgObject_getJavaMethod(
				env, s_SingleRowWriter_class, "getTupleAndClear", "()Lorg/postgresql/pljava/internal/Tuple;");

	s_cache = HashMap_create(13, TopMemoryContext);

	s_SingleRowWriterClass = TypeClass_alloc("type.SingleRowWriter");
	s_SingleRowWriterClass->JNISignature = "Ljava/sql/ResultSet;";
	s_SingleRowWriterClass->javaTypeName = "java.lang.ResultSet";
	s_SingleRowWriterClass->coerceDatum  = _SingleRowWriter_coerceDatum;
	s_SingleRowWriterClass->coerceObject = _SingleRowWriter_coerceObject;
	s_SingleRowWriterClass->invoke       = _SingleRowWriter_invoke;

	Type_registerJavaType("org.postgresql.pljava.jdbc.SingleRowWriter", SingleRowWriter_obtain);
	PG_RETURN_VOID();
}
