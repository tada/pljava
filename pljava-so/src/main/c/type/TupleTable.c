/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleTable.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"

#if PG_VERSION_NUM < 120000
#define ExecCopySlotHeapTuple(tts) ExecCopySlotTuple((tts))
#endif

static jclass    s_TupleTable_class;
static jmethodID s_TupleTable_init;

jobject TupleTable_createFromSlot(TupleTableSlot* tts)
{
	HeapTuple tuple;
	jobject tupdesc;
	jobjectArray tuples;
	MemoryContext curr;

	if(tts == 0)
		return 0;

	curr = MemoryContextSwitchTo(JavaMemoryContext);

	tupdesc = pljava_TupleDesc_internalCreate(tts->tts_tupleDescriptor);
	tuple   = ExecCopySlotHeapTuple(tts);
	tuples  = pljava_Tuple_createArray(&tuple, 1, false);

	MemoryContextSwitchTo(curr);

	return JNI_newObject(s_TupleTable_class, s_TupleTable_init, tupdesc, tuples);
}

jobject TupleTable_create(SPITupleTable* tts, jobject knownTD)
{
	jobjectArray tuples;
	uint64 tupcount;
	MemoryContext curr;

	if(tts == 0)
		return 0;

#if PG_VERSION_NUM < 130000
	tupcount = tts->alloced - tts->free;
#else
	tupcount = tts->numvals;
#endif
	if ( tupcount > PG_INT32_MAX )
		ereport(ERROR,
				(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
				 errmsg("a PL/Java TupleTable cannot represent more than "
					"INT32_MAX rows")));

	curr = MemoryContextSwitchTo(JavaMemoryContext);

	if(knownTD == 0)
		knownTD = pljava_TupleDesc_internalCreate(tts->tupdesc);

	tuples = pljava_Tuple_createArray(tts->vals, (jint)tupcount, true);
	MemoryContextSwitchTo(curr);

	return JNI_newObject(s_TupleTable_class, s_TupleTable_init, knownTD, tuples);
}

/* Make this datatype available to the postgres system.
 */
extern void TupleTable_initialize(void);
void TupleTable_initialize(void)
{
	s_TupleTable_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/TupleTable"));
	s_TupleTable_init = PgObject_getJavaMethod(
				s_TupleTable_class, "<init>",
				"(Lorg/postgresql/pljava/internal/TupleDesc;[Lorg/postgresql/pljava/internal/Tuple;)V");
}
