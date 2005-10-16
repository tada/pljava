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

#include "org_postgresql_pljava_internal_Tuple.h"
#include "pljava/Exception.h"
#include "pljava/PLJavaMemoryContext.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"

static Type      s_Tuple;
static TypeClass s_TupleClass;
static jclass    s_Tuple_class;
static jmethodID s_Tuple_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject Tuple_create(JNIEnv* env, HeapTuple ht)
{
	jobject jht;
	if(ht == 0)
		return 0;

	jht = PLJavaMemoryContext_getJavaObject(env, ht);
	if(jht == 0)
	{
		MemoryContext curr = MemoryContextSwitchTo(JavaMemoryContext);
		jht = Tuple_internalCreate(env, ht, true);
		MemoryContextSwitchTo(curr);
	}
	return jht;
}

jobjectArray Tuple_createArray(JNIEnv* env, HeapTuple* vals, jint size, bool mustCopy)
{
	jobjectArray tuples = (*env)->NewObjectArray(env, size, s_Tuple_class, 0);
	while(--size >= 0)
	{
		jobject heapTuple = Tuple_internalCreate(env, vals[size], mustCopy);
		(*env)->SetObjectArrayElement(env, tuples, size, heapTuple);
		(*env)->DeleteLocalRef(env, heapTuple);
	}
	return tuples;
}

jobject Tuple_internalCreate(JNIEnv* env, HeapTuple ht, bool mustCopy)
{
	jobject jht;
	Ptr2Long htH;

	if(mustCopy)
		ht = heap_copytuple(ht);

	htH.ptrVal = ht;
	jht = PgObject_newJavaObject(env, s_Tuple_class, s_Tuple_init, htH.longVal);
	PLJavaMemoryContext_setJavaObject(env, ht, jht);
	return jht;
}

static jvalue _Tuple_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = Tuple_create(env, (HeapTuple)DatumGetPointer(arg));
	return result;
}

static Type Tuple_obtain(Oid typeId)
{
	return s_Tuple;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Tuple_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Tuple_initialize);
Datum Tuple_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_getObject",
	  	"(JJI)Ljava/lang/Object;",
	  	Java_org_postgresql_pljava_internal_Tuple__1getObject
		},
		{
		"_free",
	  	"(J)V",
	  	Java_org_postgresql_pljava_internal_Tuple__1free
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Tuple_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Tuple"));

	PgObject_registerNatives2(env, s_Tuple_class, methods);

	s_Tuple_init = PgObject_getJavaMethod(
				env, s_Tuple_class, "<init>", "(J)V");

	s_TupleClass = MemoryContextManagedClass_alloc("type.Tuple");
	s_TupleClass->JNISignature   = "Lorg/postgresql/pljava/internal/Tuple;";
	s_TupleClass->javaTypeName   = "org.postgresql.pljava.internal.Tuple";
	s_TupleClass->coerceDatum    = _Tuple_coerceDatum;
	s_Tuple = TypeClass_allocInstance(s_TupleClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Tuple", Tuple_obtain);
	PG_RETURN_VOID();
}

jobject
Tuple_getObject(JNIEnv* env, TupleDesc tupleDesc, HeapTuple tuple, int index)
{
	jobject result = 0;
	PG_TRY();
	{
		Type type = TupleDesc_getColumnType(env, tupleDesc, index);
		if(type != 0)
		{
			bool wasNull = false;
			Datum binVal = SPI_getbinval(tuple, tupleDesc, (int)index, &wasNull);
			if(!wasNull)
				result = Type_coerceDatum(type, env, binVal).l;
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_getbinval");
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
 * Signature: (JJI)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Tuple__1getObject(JNIEnv* env, jclass cls, jlong _this, jlong _tupleDesc, jint index)
{
	Ptr2Long p2l;
	HeapTuple self;
	TupleDesc tupleDesc;

	PLJAVA_ENTRY_FENCE(0)
	p2l.longVal = _this;
	self = (HeapTuple)p2l.ptrVal;
	if(self == 0)
		return 0;

	p2l.longVal = _tupleDesc;
	tupleDesc = (TupleDesc)p2l.ptrVal;
	if(tupleDesc == 0)
		return 0;
	
	return Tuple_getObject(env, tupleDesc, self, (int)index);
}

/*
 * Class:     org_postgresql_pljava_internal_Tuple
 * Method:    _free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Tuple__1free(JNIEnv* env, jobject _this, jlong pointer)
{
	if(pointer != 0)
	{
		/* Avoid callback when explicitly freed from Java code
		 */
		Ptr2Long p2l;
		p2l.longVal = pointer;
		PLJavaMemoryContext_setJavaObject(env, p2l.ptrVal, 0);
		heap_freetuple(p2l.ptrVal);
	}
}
