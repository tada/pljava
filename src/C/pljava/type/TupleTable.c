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

#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleTable.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"

static jclass    s_TupleTable_class;
static jmethodID s_TupleTable_init;

jobject TupleTable_createFromSlot(JNIEnv* env, TupleTableSlot* tts)
{
	HeapTuple tuple;
	jobject tupdesc;
	jobjectArray tuples;
	MemoryContext curr;

	if(tts == 0)
		return 0;

	curr = MemoryContextSwitchTo(JavaMemoryContext);

	tupdesc = TupleDesc_internalCreate(env, tts->tts_tupleDescriptor);
	tuple   = ExecCopySlotTuple(tts);
	tuples  = Tuple_createArray(env, &tuple, 1, false);

	MemoryContextSwitchTo(curr);

	return PgObject_newJavaObject(env, s_TupleTable_class, s_TupleTable_init, tupdesc, tuples);
}

jobject TupleTable_create(JNIEnv* env, SPITupleTable* tts)
{
	jobject tupdesc;
	jobjectArray tuples;
	MemoryContext curr;

	if(tts == 0)
		return 0;

	curr = MemoryContextSwitchTo(JavaMemoryContext);

	tupdesc = TupleDesc_internalCreate(env, tts->tupdesc);
	tuples = Tuple_createArray(env, tts->vals, (jint)(tts->alloced - tts->free), true);

	MemoryContextSwitchTo(curr);
	return PgObject_newJavaObject(env, s_TupleTable_class, s_TupleTable_init, tupdesc, tuples);
}

/* Make this datatype available to the postgres system.
 */
extern Datum TupleTable_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(TupleTable_initialize);
Datum TupleTable_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TupleTable_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TupleTable"));

	s_TupleTable_init = PgObject_getJavaMethod(
				env, s_TupleTable_class, "<init>",
				"(Lorg/postgresql/pljava/internal/TupleDesc;[Lorg/postgresql/pljava/internal/Tuple;)V");

	PG_RETURN_VOID();
}
