/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Tuple_JNI.h"

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

	jht = NativeStruct_obtain(env, ht);
	if(jht == 0)
	{
		jht = PgObject_newJavaObject(env, s_Tuple_class, s_Tuple_init);
		NativeStruct_init(env, jht, ht);
	}
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
	  	"(Lorg/postgresql/pljava/internal/TupleDesc;I)Ljava/lang/Object;",
	  	Java_org_postgresql_pljava_internal_Tuple__1getObject
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Tuple_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Tuple"));

	PgObject_registerNatives2(env, s_Tuple_class, methods);

	s_Tuple_init = PgObject_getJavaMethod(
				env, s_Tuple_class, "<init>", "()V");

	s_TupleClass = NativeStructClass_alloc("type.Tuple");
	s_TupleClass->JNISignature   = "Lorg/postgresql/pljava/internal/Tuple;";
	s_TupleClass->javaTypeName   = "org.postgresql.pljava.internal.Tuple";
	s_TupleClass->coerceDatum    = _Tuple_coerceDatum;
	s_Tuple = TypeClass_allocInstance(s_TupleClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Tuple", Tuple_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
 
/*
 * Class:     org_postgresql_pljava_internal_Tuple
 * Method:    _getObject
 * Signature: (Lorg/postgresql/pljava/internal/TupleDesc;I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Tuple__1getObject(JNIEnv* env, jobject _this, jobject _tupleDesc, jint index)
{
	HeapTuple self;
	TupleDesc tupleDesc;
	jobject result = 0;

	PLJAVA_ENTRY_FENCE(0)
	self = (HeapTuple)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	tupleDesc = (TupleDesc)NativeStruct_getStruct(env, _tupleDesc);
	if(tupleDesc == 0)
		return 0;

	PLJAVA_TRY
	{
		Oid typeId = SPI_gettypeid(tupleDesc, (int)index);
		if(!OidIsValid(typeId))
		{
			Exception_throw(env,
				ERRCODE_INVALID_DESCRIPTOR_INDEX,
				"Invalid attribute index \"%d\"", (int)index);
		}
		else
		{
			Datum binVal;
			bool wasNull = false;
			Type type = Type_fromOid(typeId);
			if(Type_isPrimitive(type))
				/*
				 * This is a primitive type
				 */
				type = type->m_class->objectType;
		
			binVal = SPI_getbinval(self, tupleDesc, (int)index, &wasNull);
			if(!wasNull)
				result = Type_coerceDatum(type, env, binVal).l;
		}
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_gettypeid or SPI_getbinval");
	}
	PLJAVA_TCEND
	return result;
}
