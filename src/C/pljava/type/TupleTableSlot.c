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
#include <executor/tuptable.h>

#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleTableSlot.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/TupleTableSlot_JNI.h"

static Type      s_TupleTableSlot;
static TypeClass s_TupleTableSlotClass;
static jclass    s_TupleTableSlot_class;
static jmethodID s_TupleTableSlot_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject TupleTableSlot_create(JNIEnv* env, TupleTableSlot* tts)
{
	if(tts == 0)
		return 0;

	jobject jtts = NativeStruct_obtain(env, tts);
	if(jtts == 0)
	{
		jtts = PgObject_newJavaObject(env, s_TupleTableSlot_class, s_TupleTableSlot_init);
		NativeStruct_init(env, jtts, tts);
	}
	return jtts;
}

static jvalue _TupleTableSlot_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = TupleTableSlot_create(env, (TupleTableSlot*)DatumGetPointer(arg));
	return result;
}

static Type TupleTableSlot_obtain(Oid typeId)
{
	return s_TupleTableSlot;
}

/* Make this datatype available to the postgres system.
 */
extern Datum TupleTableSlot_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(TupleTableSlot_initialize);
Datum TupleTableSlot_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TupleTableSlot_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TupleTableSlot"));

	s_TupleTableSlot_init = PgObject_getJavaMethod(
				env, s_TupleTableSlot_class, "<init>", "()V");

	s_TupleTableSlotClass = NativeStructClass_alloc("type.TupleTableSlot");
	s_TupleTableSlotClass->JNISignature   = "Lorg/postgresql/pljava/internal/TupleTableSlot;";
	s_TupleTableSlotClass->javaTypeName   = "org.postgresql.pljava.internal.TupleTableSlot";
	s_TupleTableSlotClass->coerceDatum    = _TupleTableSlot_coerceDatum;
	s_TupleTableSlot = TypeClass_allocInstance(s_TupleTableSlotClass);

	Type_registerJavaType("org.postgresql.pljava.internal.TupleTableSlot", TupleTableSlot_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_TupleTableSlot
 * Method:    _getTuple
 * Signature: ()Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleTableSlot__1getTuple(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
	TupleTableSlot* slot = (TupleTableSlot*)NativeStruct_getStruct(env, _this);
	if(slot == 0)
		return 0;
	return Tuple_create(env, slot->val);
}

/*
 * Class:     org_postgresql_pljava_internal_TupleTableSlot
 * Method:    _getTupleDesc
 * Signature: ()Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleTableSlot__1getTupleDesc(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
	TupleTableSlot* slot = (TupleTableSlot*)NativeStruct_getStruct(env, _this);
	if(slot == 0)
		return 0;
	return TupleDesc_create(env, slot->ttc_tupleDescriptor);
}
