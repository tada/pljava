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

#include "org_postgresql_pljava_internal_Relation.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/Relation.h"

static Type      s_Relation;
static TypeClass s_RelationClass;
static jclass    s_Relation_class;
static jmethodID s_Relation_init;

/*
 * org.postgresql.pljava.Relation type.
 */
jobject Relation_create(JNIEnv* env, Relation td)
{
	jobject jtd;
	if(td == 0)
		return 0;

	jtd = MemoryContext_lookupNative(env, td);
	if(jtd == 0)
	{
		jtd = PgObject_newJavaObject(env, s_Relation_class, s_Relation_init);
		NativeStruct_init(env, jtd, td);
	}
	return jtd;
}

static jvalue _Relation_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = Relation_create(env, (Relation)DatumGetPointer(arg));
	return result;
}

static Type Relation_obtain(Oid typeId)
{
	return s_Relation;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Relation_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Relation_initialize);
Datum Relation_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_getName",
		"()Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Relation__1getName
		},
		{
		"_getTupleDesc",
	  	"()Lorg/postgresql/pljava/internal/TupleDesc;",
	  	Java_org_postgresql_pljava_internal_Relation__1getTupleDesc
		},
		{
		"_modifyTuple",
		"(Lorg/postgresql/pljava/internal/Tuple;[I[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_Relation__1modifyTuple
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Relation_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/Relation"));

	PgObject_registerNatives2(env, s_Relation_class, methods);

	s_Relation_init = PgObject_getJavaMethod(
				env, s_Relation_class, "<init>", "()V");

	s_RelationClass = NativeStructClass_alloc("type.Relation");
	s_RelationClass->JNISignature   = "Lorg/postgresql/pljava/internal/Relation;";
	s_RelationClass->javaTypeName   = "org.postgresql.pljava.internal.Relation";
	s_RelationClass->coerceDatum    = _Relation_coerceDatum;
	s_Relation = TypeClass_allocInstance(s_RelationClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Relation", Relation_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Relation__1getName(JNIEnv* env, jobject _this)
{
	Relation self;
	jstring ret = 0;
	PLJAVA_ENTRY_FENCE(0)
	self = (Relation)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		char* relName = SPI_getrelname(self);
		ret = String_createJavaStringFromNTS(env, relName);
		pfree(relName);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_getrelname");
	}
	PG_END_TRY();
	return ret;
}

/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _getTupleDesc
 * Signature: ()Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Relation__1getTupleDesc(JNIEnv* env, jobject _this)
{
	Relation self;
	PLJAVA_ENTRY_FENCE(0)
	self = (Relation)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	return TupleDesc_create(env, self->rd_att);
}

/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _modifyTuple
 * Signature: (Lorg/postgresql/pljava/internal/Tuple;[I[Ljava/lang/Object;)Lorg/postgresql/internal/pljava/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Relation__1modifyTuple(JNIEnv* env, jobject _this, jobject _tuple, jintArray _indexes, jobjectArray _values)
{
	Relation self;
	HeapTuple tuple;
	PLJAVA_ENTRY_FENCE(0)
	self = (Relation)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	tuple = (HeapTuple)NativeStruct_getStruct(env, _tuple);
	if(tuple == 0)
		return 0;

	PG_TRY();
	{
		jint idx;
		TupleDesc tupleDesc = self->rd_att;
	
		jint   count  = (*env)->GetArrayLength(env, _indexes);
		Datum* values = (Datum*)palloc(count * sizeof(Datum));
		char*  nulls  = 0;
	
		jint* javaIdxs = (*env)->GetIntArrayElements(env, _indexes, 0);
	
		int* indexes;
		if(sizeof(int) == sizeof(jint))	/* compiler will optimize this */
			indexes = (int*)javaIdxs;
		else
			indexes = (int*)palloc(count * sizeof(int));
	
		for(idx = 0; idx < count; ++idx)
		{
			int attIndex;
			Oid typeId;
			Type type;
			jobject value;

			if(sizeof(int) == sizeof(jint))	/* compiler will optimize this */
				attIndex = indexes[idx];
			else
			{
				attIndex = (int)javaIdxs[idx];
				indexes[idx] = attIndex;
			}
	
			typeId = SPI_gettypeid(tupleDesc, attIndex);
			if(!OidIsValid(typeId))
			{
				Exception_throw(env,
					ERRCODE_INVALID_DESCRIPTOR_INDEX,
					"Invalid attribute index \"%d\"", attIndex);
				return 0L;	/* Exception */
			}
	
			type = Type_fromOid(typeId);
			value = (*env)->GetObjectArrayElement(env, _values, idx);
			if(value != 0)
				values[idx] = Type_coerceObject(type, env, value);
			else
			{
				if(nulls == 0)
				{
					nulls = (char*)palloc(count+1);
					memset(nulls, ' ', count);	/* all values non-null initially */
					nulls[count] = 0;
				}
				nulls[idx] = 'n';
				values[idx] = 0;
			}
		}

		tuple = SPI_modifytuple(self, tuple, count, indexes, values, nulls);
		if(tuple == 0)
			Exception_throwSPI(env, "modifytuple", SPI_result);

		(*env)->ReleaseIntArrayElements(env, _indexes, javaIdxs, JNI_ABORT);
	
		if(sizeof(int) != sizeof(jint))	/* compiler will optimize this */
			pfree(indexes);
	
		pfree(values);
		if(nulls != 0)
			pfree(nulls);	
	}
	PG_CATCH();
	{
		tuple = 0;
		Exception_throw_ERROR(env, "SPI_gettypeid");
	}
	PG_END_TRY();

	return (tuple == 0) ? 0 : Tuple_create(env, tuple);
}
