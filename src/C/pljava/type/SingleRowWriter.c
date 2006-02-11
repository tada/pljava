/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <funcapi.h>
#include <utils/typcache.h>
#include <access/heapam.h>

#include "pljava/backports.h"
#include "pljava/Invocation.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/ComplexType.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/SingleRowWriter.h"

/*
 * void primitive type.
 */
static jclass s_SingleRowWriter_class;
static jmethodID s_SingleRowWriter_init;
static jmethodID s_SingleRowWriter_getTupleAndClear;
static TypeClass s_SingleRowWriterClass;
static HashMap s_idCache;
static HashMap s_modCache;

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
static Datum _SingleRowWriter_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool hasRow;
	Datum result = 0;
	TupleDesc tupleDesc = Type_getTupleDesc(self, fcinfo);
	jobject jtd = TupleDesc_create(tupleDesc);
	jobject singleRowWriter = SingleRowWriter_create(jtd);
	int numArgs = fcinfo->nargs;
	JNI_deleteLocalRef(jtd);

	/* It's guaranteed that the args array has room for one more
	 * argument.
	 */
	args[numArgs].l = singleRowWriter;

	hasRow = (JNI_callStaticBooleanMethodA(cls, method, args) == JNI_TRUE);

	if(hasRow)
	{
		/* Obtain tuple and return it as a Datum. Must be done using a more
		 * durable context.
		 */
		MemoryContext currCtx = Invocation_switchToUpperContext();
		HeapTuple tuple = SingleRowWriter_getTupleAndClear(singleRowWriter);
	    result = HeapTupleGetDatum(tuple);
		MemoryContextSwitchTo(currCtx);
	}
	else
		fcinfo->isnull = true;

	JNI_deleteLocalRef(singleRowWriter);
	return result;
}

jobject SingleRowWriter_create(jobject tupleDesc)
{
	if(tupleDesc == 0)
		return 0;
	return JNI_newObject(s_SingleRowWriter_class, s_SingleRowWriter_init, tupleDesc);
}

HeapTuple SingleRowWriter_getTupleAndClear(jobject jrps)
{
	jobject tuple;
	Ptr2Long p2l;
	HeapTuple result;

	if(jrps == 0)
		return 0;

	tuple = JNI_callObjectMethod(jrps, s_SingleRowWriter_getTupleAndClear);
	if(tuple == 0)
		return 0;

	p2l.longVal = JavaWrapper_getPointer(tuple);
	result = heap_copytuple((HeapTuple)p2l.ptrVal);
	JNI_deleteLocalRef(tuple);
	return result;
}

static jvalue _SingleRowWriter_coerceDatum(Type self, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _SingleRowWriter_coerceObject(Type self, jobject nothing)
{
	return 0;
}

static Type SingleRowWriter_obtain(Oid typeId)
{
	return (Type)ComplexType_createType(
		s_SingleRowWriterClass, s_idCache, s_modCache, lookup_rowtype_tupdesc(typeId, -1));
}

Type SingleRowWriter_createType(Oid typid, TupleDesc tupleDesc)
{
	return (Type)ComplexType_createType(
		s_SingleRowWriterClass, s_idCache, s_modCache, tupleDesc);
}

/* Make this datatype available to the postgres system.
 */
extern void SingleRowWriter_initialize(void);
void SingleRowWriter_initialize(void)
{
	s_SingleRowWriter_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SingleRowWriter"));
	s_SingleRowWriter_init = PgObject_getJavaMethod(s_SingleRowWriter_class, "<init>", "(Lorg/postgresql/pljava/internal/TupleDesc;)V");
	s_SingleRowWriter_getTupleAndClear = PgObject_getJavaMethod(s_SingleRowWriter_class, "getTupleAndClear", "()Lorg/postgresql/pljava/internal/Tuple;");

	s_idCache = HashMap_create(13, TopMemoryContext);
	s_modCache = HashMap_create(13, TopMemoryContext);

	s_SingleRowWriterClass = ComplexTypeClass_alloc("type.SingleRowWriter");
	s_SingleRowWriterClass->JNISignature = "Ljava/sql/ResultSet;";
	s_SingleRowWriterClass->javaTypeName = "java.lang.ResultSet";
	s_SingleRowWriterClass->coerceDatum  = _SingleRowWriter_coerceDatum;
	s_SingleRowWriterClass->coerceObject = _SingleRowWriter_coerceObject;
	s_SingleRowWriterClass->invoke       = _SingleRowWriter_invoke;

	Type_registerType(InvalidOid, "org.postgresql.pljava.jdbc.SingleRowWriter", SingleRowWriter_obtain);
}
