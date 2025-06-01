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

#include "org_postgresql_pljava_internal_Relation.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/SPI.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/Relation.h"

/**
 * \addtogroup JNI
 * @{
 */

static jclass    s_Relation_class;
static jmethodID s_Relation_init;

/*
 * org.postgresql.pljava.Relation type.
 */
jobject pljava_Relation_create(Relation r)
{
	if ( NULL == r )
		return NULL;

	return JNI_newObjectLocked(
			s_Relation_class,
			s_Relation_init,
			pljava_DualState_key(),
			PointerGetJLong(currentInvocation),
			PointerGetJLong(r));
}

void pljava_Relation_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_getName",
		"(J)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Relation__1getName
		},
		{
		"_getSchema",
		"(J)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Relation__1getSchema
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
		{ 0, 0, 0 }
	};

	s_Relation_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Relation"));
	PgObject_registerNatives2(s_Relation_class, methods);
	s_Relation_init = PgObject_getJavaMethod(s_Relation_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJ)V");
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
	Relation self = JLongGet(Relation, _this);

	if(self != 0)
	{
		BEGIN_NATIVE
		PG_TRY();
		{
			char* relName = SPI_getrelname(self);
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
 * Method:    _getSchema
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Relation__1getSchema(JNIEnv* env, jclass clazz, jlong _this)
{
	jstring result = 0;
	Relation self = JLongGet(Relation, _this);

	if(self != 0)
	{
		BEGIN_NATIVE
		PG_TRY();
		{
			char* schema = SPI_getnspname(self);
			result = String_createJavaStringFromNTS(schema);
			pfree(schema);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_getnspname");
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
	Relation self = JLongGet(Relation, _this);

	if(self != 0)
	{
		BEGIN_NATIVE
		result = pljava_TupleDesc_create(self->rd_att);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Relation
 * Method:    _modifyTuple
 * Signature: (JJ[I[Ljava/lang/Object;)Lorg/postgresql/internal/pljava/Tuple;
 *
 * Note: starting with PostgreSQL 10, SPI_modifytuple must be run with SPI
 * 'connected'. However, the caller likely wants a result living in a memory
 * context longer-lived than SPI's. (At present, the only calls of this method
 * originate in Function_invokeTrigger, which does switchToUpperContext() just
 * for that reason.) Blindly adding Invocation_assertConnect() here would alter
 * the behavior of subsequent palloc()s (not just in SPI_modifytuple, but also
 * in, e.g., Tuple_create). So, given there's only one caller, let it be the
 * caller's responsibility to ensure SPI is connected AND that a suitable
 * memory context is selected for the result the caller wants.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Relation__1modifyTuple(JNIEnv* env, jclass clazz, jlong _this, jlong _tuple, jintArray _indexes, jobjectArray _values)
{
	jobject result = 0;
	Relation self = JLongGet(Relation, _this);

	if(self != 0 && _tuple != 0)
	{
		BEGIN_NATIVE
		HeapTuple tuple = JLongGet(HeapTuple, _tuple);
		PG_TRY();
		{
			jint idx;
			TupleDesc tupleDesc = self->rd_att;
			jobject typeMap = Invocation_getTypeMap();

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
		
				type = Type_fromOid(typeId, typeMap);
				value = JNI_getObjectArrayElement(_values, idx);
				if(value != 0)
					values[idx] = Type_coerceObjectBridged(type, value);
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
			result = pljava_Tuple_create(tuple);
		END_NATIVE
	}
	return result;
}
/** @} */
