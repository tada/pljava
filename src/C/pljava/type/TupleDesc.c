/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>
#include <funcapi.h>

#include "org_postgresql_pljava_internal_TupleDesc.h"
#include "pljava/Exception.h"
#include "pljava/HashMap.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"

static Type      s_TupleDesc;
static TypeClass s_TupleDescClass;
static jclass    s_TupleDesc_class;
static jmethodID s_TupleDesc_init;
static HashMap   s_nativeCache;

/*
 * org.postgresql.pljava.TupleDesc type.
 */
jobject TupleDesc_create(JNIEnv* env, TupleDesc td)
{
	jobject jtd;
	if(td == 0)
		return 0;

	jtd = NativeStruct_obtain(env, td);
	if(jtd == 0)
	{
		jtd = PgObject_newJavaObject(env, s_TupleDesc_class, s_TupleDesc_init);
		NativeStruct_init(env, jtd, td);
	}
	return jtd;
}

TupleDesc TupleDesc_forOid(Oid oid)
{
	TupleDesc tupleDesc = (TupleDesc)HashMap_getByOid(s_nativeCache, oid);
	if(tupleDesc == 0)
	{
		MemoryContext oldcxt = MemoryContextSwitchTo(TopMemoryContext);
		tupleDesc = TypeGetTupleDesc(oid, NIL);
		MemoryContextSwitchTo(oldcxt);
		HashMap_putByOid(s_nativeCache, oid, tupleDesc);
	}
	return tupleDesc;
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
	JNINativeMethod methods[] = {
		{
		"_getColumnName",
	  	"(I)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName
		},
		{
		"_getColumnIndex",
		"(Ljava/lang/String;)I",
		Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex
		},
		{
		"_formTuple",
		"([Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_TupleDesc__1formTuple
		},
		{
		"_size",
		"()I",
		Java_org_postgresql_pljava_internal_TupleDesc__1size
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TupleDesc_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TupleDesc"));

	PgObject_registerNatives2(env, s_TupleDesc_class, methods);

	s_TupleDesc_init = PgObject_getJavaMethod(
				env, s_TupleDesc_class, "<init>", "()V");

	s_nativeCache = HashMap_create(13, TopMemoryContext);

	s_TupleDescClass = NativeStructClass_alloc("type.TupleDesc");
	s_TupleDescClass->JNISignature   = "Lorg/postgresql/pljava/internal/TupleDesc;";
	s_TupleDescClass->javaTypeName   = "org.postgresql.pljava.internal.TupleDesc";
	s_TupleDescClass->coerceDatum    = _TupleDesc_coerceDatum;
	s_TupleDesc = TypeClass_allocInstance(s_TupleDescClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.TupleDesc", TupleDesc_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnName
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName(JNIEnv* env, jobject _this, jint index)
{
	TupleDesc self;
	jstring result = 0;

	PLJAVA_ENTRY_FENCE(0)
	self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		char* name = SPI_fname(self, (int)index);
		if(name == 0)
		{
			Exception_throw(env,
				ERRCODE_INVALID_DESCRIPTOR_INDEX,
				"Invalid attribute index \"%d\"", (int)index);
		}
		else
		{
			result = String_createJavaStringFromNTS(env, name);
			pfree(name);
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_fname");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnIndex
 * Signature: (Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex(JNIEnv* env, jobject _this, jstring colName)
{
	TupleDesc self;
	char* name;
	jint index = 0;

	PLJAVA_ENTRY_FENCE(0)
	self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;
	
	name = String_createNTS(env, colName);
	if(name == 0)
		return 0;

	PG_TRY();
	{
		index = SPI_fnumber(self, name);
		if(index < 0)
		{
			Exception_throw(env,
				ERRCODE_UNDEFINED_COLUMN,
				"Tuple has no attribute \"%s\"", name);
		}
		pfree(name);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_fnumber");
	}
	PG_END_TRY();
	return index;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _formTuple
 * Signature: ([Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1formTuple(JNIEnv* env, jobject _this, jobjectArray jvalues)
{
	TupleDesc self;
	jobject result = 0;

	PLJAVA_ENTRY_FENCE(0)
	self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		jint   idx;
		int    count   = self->natts;
		Datum* values  = (Datum*)palloc(count * sizeof(Datum));
		char*  nulls   = palloc(count);

		memset(values, 0,  count * sizeof(Datum));
		memset(nulls, 'n', count);	/* all values null initially */
	
		for(idx = 0; idx < count; ++idx)
		{
			jobject value = (*env)->GetObjectArrayElement(env, jvalues, idx);
			if(value != 0)
			{
				Type type = Type_fromOid(SPI_gettypeid(self, idx + 1));
				values[idx] = Type_coerceObject(type, env, value);
				nulls[idx] = ' ';
			}
		}
		result = Tuple_create(env, heap_formtuple(self, values, nulls));
		pfree(values);
		pfree(nulls);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "heap_formtuple");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _size
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1size(JNIEnv* env, jobject _this)
{
	TupleDesc self;
	PLJAVA_ENTRY_FENCE(0)
	self = (TupleDesc)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;
	return (jint)self->natts;
}
