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
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_Tuple.h"
#include "pljava/Backend.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"

static jclass    s_Tuple_class;
static jmethodID s_Tuple_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject pljava_Tuple_create(HeapTuple ht)
{
	jobject jht = 0;
	if(ht != 0)
	{
		MemoryContext curr = MemoryContextSwitchTo(JavaMemoryContext);
		jht = pljava_Tuple_internalCreate(ht, true);
		MemoryContextSwitchTo(curr);
	}
	return jht;
}

jobjectArray pljava_Tuple_createArray(HeapTuple* vals, jint size, bool mustCopy)
{
	jobjectArray tuples = JNI_newObjectArray(size, s_Tuple_class, 0);
	while(--size >= 0)
	{
		jobject heapTuple = pljava_Tuple_internalCreate(vals[size], mustCopy);
		JNI_setObjectArrayElement(tuples, size, heapTuple);
		JNI_deleteLocalRef(heapTuple);
	}
	return tuples;
}

jobject pljava_Tuple_internalCreate(HeapTuple ht, bool mustCopy)
{
	jobject jht;

	if(mustCopy)
		ht = heap_copytuple(ht);

	/*
	 * Passing (jlong)0 as the ResourceOwner means this will never be matched by a
	 * nativeRelease call; that's appropriate (for now) as the Tuple copy is
	 * being made into JavaMemoryContext, which never gets reset, so only
	 * unreachability from the Java side will free it.
	 * XXX? this seems like a lot of tuple copying.
	 */
	jht = JNI_newObjectLocked(s_Tuple_class, s_Tuple_init,
		pljava_DualState_key(), (jlong)0, PointerGetJLong(ht));
	return jht;
}

static jvalue _Tuple_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = pljava_Tuple_create((HeapTuple)DatumGetPointer(arg));
	return result;
}

/* Make this datatype available to the postgres system.
 */
extern void pljava_Tuple_initialize(void);
void pljava_Tuple_initialize(void)
{
	TypeClass cls;
	JNINativeMethod methods[] = {
		{
		"_getObject",
		"(JJILjava/lang/Class;)Ljava/lang/Object;",
	  	Java_org_postgresql_pljava_internal_Tuple__1getObject
		},
		{ 0, 0, 0 }};

	s_Tuple_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Tuple"));
	PgObject_registerNatives2(s_Tuple_class, methods);
	s_Tuple_init = PgObject_getJavaMethod(s_Tuple_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJ)V");

	cls = TypeClass_alloc("type.Tuple");
	cls->JNISignature = "Lorg/postgresql/pljava/internal/Tuple;";
	cls->javaTypeName = "org.postgresql.pljava.internal.Tuple";
	cls->coerceDatum  = _Tuple_coerceDatum;
	Type_registerType("org.postgresql.pljava.internal.Tuple", TypeClass_allocInstance(cls, InvalidOid));
}

jobject
pljava_Tuple_getObject(
	TupleDesc tupleDesc, HeapTuple tuple, int index, jclass rqcls)
{
	jobject result = 0;
	PG_TRY();
	{
		Type type = pljava_TupleDesc_getColumnType(tupleDesc, index);
		if(type != 0)
		{
			bool wasNull = false;
			Datum binVal = SPI_getbinval(tuple, tupleDesc, (int)index, &wasNull);
			if(!wasNull)
				result = Type_coerceDatumAs(type, binVal, rqcls).l;
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_getbinval");
	}
	PG_END_TRY();
	return result;
}

/****************************************
 * JNI methods
 ****************************************/
 
/*
 * Class:     org_postgresql_pljava_internal_Tuple
 * Method:    _getObject
 * Signature: (JJILjava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Tuple__1getObject(JNIEnv* env, jclass cls, jlong _this, jlong _tupleDesc, jint index, jclass rqcls)
{
	jobject result = 0;

	BEGIN_NATIVE
	HeapTuple self = JLongGet(HeapTuple, _this);
	result = pljava_Tuple_getObject(JLongGet(TupleDesc, _tupleDesc), self,
		(int)index, rqcls);
	END_NATIVE
	return result;
}
