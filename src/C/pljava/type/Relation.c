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
jobject Relation_create(Relation td)
{
	jobject jtd;
	if(td == 0)
		return 0;

	jtd = MemoryContext_lookupNative(td);
	if(jtd == 0)
	{
		jtd = JNI_newObject(s_Relation_class, s_Relation_init);
		JavaHandle_init(jtd, td);
	}
	return jtd;
}

static jvalue _Relation_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = Relation_create((Relation)DatumGetPointer(arg));
	return result;
}

static Type Relation_obtain(Oid typeId)
{
	return s_Relation;
}

extern void Relation_initialize(void);
void Relation_initialize()
{
	JNINativeMethod methods[] = {
		{
		"_getName",
		"(J)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Relation__1getName
		},
		{
		"_getTupleDesc",
	  	"(J)Lorg/postgresql/pljava/internal/TupleDesc;",
	  	Java_org_postgresql_pljava_internal_Relation__1getTupleDesc
		},
		{
		"_modifyTuple",
		"(JJ[I[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_Relation__1modifyTuple
		},
		{ 0, 0, 0 }};

	s_Relation_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Relation"));
	PgObject_registerNatives2(s_Relation_class, methods);
	s_Relation_init = PgObject_getJavaMethod(s_Relation_class, "<init>", "()V");

	s_RelationClass = JavaHandleClass_alloc("type.Relation");
	s_RelationClass->JNISignature   = "Lorg/postgresql/pljava/internal/Relation;";
	s_RelationClass->javaTypeName   = "org.postgresql.pljava.internal.Relation";
	s_RelationClass->coerceDatum    = _Relation_coerceDatum;
	s_Relation = TypeClass_allocInstance(s_RelationClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Relation", Relation_obtain);
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _getName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Relation__1getName(JNIEnv* env, jclass clazz, jlong _this)
{
	jstring result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		PG_TRY();
		{
			char* relName = SPI_getrelname((Relation)p2l.ptrVal);
			result = String_createJavaStringFromNTS(relName);
			pfree(relName);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_getrelname");
		}
		PG_END_TRY();
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _getTupleDesc
 * Signature: (J)Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Relation__1getTupleDesc(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = TupleDesc_create(((Relation)p2l.ptrVal)->rd_att);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _modifyTuple
 * Signature: (JJ[I[Ljava/lang/Object;)Lorg/postgresql/internal/pljava/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Relation__1modifyTuple(JNIEnv* env, jclass clazz, jlong _this, jlong _tuple, jintArray _indexes, jobjectArray _values)
{
	jobject result = 0;
	if(_this != 0 && _tuple != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;

		BEGIN_NATIVE
	
		HeapTuple tuple;
		Relation self = (Relation)p2l.ptrVal;

		p2l.longVal = _tuple;
		tuple = (HeapTuple)p2l.ptrVal;
		PG_TRY();
		{
			jint idx;
			TupleDesc tupleDesc = self->rd_att;
		
			jint   count  = JNI_getArrayLength(_indexes);
			Datum* values = (Datum*)palloc(count * sizeof(Datum));
			char*  nulls  = 0;
		
			jint* javaIdxs = JNI_getIntArrayElements(_indexes, 0);
		
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
					Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
						"Invalid attribute index \"%d\"", attIndex);
					return 0L;	/* Exception */
				}
		
				type = Type_fromOid(typeId);
				value = JNI_getObjectArrayElement(_values, idx);
				if(value != 0)
					values[idx] = Type_coerceObject(type, value);
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
				Exception_throwSPI("modifytuple", SPI_result);
	
			JNI_releaseIntArrayElements(_indexes, javaIdxs, JNI_ABORT);
		
			if(sizeof(int) != sizeof(jint))	/* compiler will optimize this */
				pfree(indexes);
		
			pfree(values);
			if(nulls != 0)
				pfree(nulls);	
		}
		PG_CATCH();
		{
			tuple = 0;
			Exception_throw_ERROR("SPI_gettypeid");
		}
		PG_END_TRY();
		if(tuple != 0)
			result = Tuple_create(tuple);
		END_NATIVE
	}
	return result;
}
