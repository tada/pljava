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
#include <funcapi.h>

#include "org_postgresql_pljava_internal_TupleDesc.h"
#include "pljava/Backend.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Oid.h"

static jclass    s_TupleDesc_class;
static jmethodID s_TupleDesc_init;

/*
 * org.postgresql.pljava.TupleDesc type.
 * This makes a non-reference-counted copy in JavaMemoryContext of the supplied
 * TupleDesc, which will be freed later when Java code calls the native method
 * _free(). Therefore the caller is done with its TupleDesc when this returns.
 */
jobject pljava_TupleDesc_create(TupleDesc td)
{
	jobject jtd = 0;
	if(td != 0)
	{
		MemoryContext curr = MemoryContextSwitchTo(JavaMemoryContext);
		jtd = pljava_TupleDesc_internalCreate(td);
		MemoryContextSwitchTo(curr);
	}
	return jtd;
}

jobject pljava_TupleDesc_internalCreate(TupleDesc td)
{
	jobject jtd;
	Ptr2Long tdH;

	td = CreateTupleDescCopyConstr(td);
	tdH.longVal = 0L; /* ensure that the rest is zeroed out */
	tdH.ptrVal = td;
	/*
	 * Passing (jlong)0 as the ResourceOwner means this will never be matched by a
	 * nativeRelease call; that's appropriate (for now) as the TupleDesc copy is
	 * being made into JavaMemoryContext, which never gets reset, so only
	 * unreachability from the Java side will free it.
	 * XXX what about invalidating if DDL alters the column layout?
	 */
	jtd = JNI_newObjectLocked(s_TupleDesc_class, s_TupleDesc_init,
		pljava_DualState_key(), (jlong)0, tdH.longVal, (jint)td->natts);
	return jtd;
}

/*
 * Returns NULL if an exception has been thrown for an invalid attribute index
 * (caller should expeditiously return), otherwise the Type for the column data
 * (the one representing the boxing Object type, in the primitive case).
 */
Type pljava_TupleDesc_getColumnType(TupleDesc tupleDesc, int index)
{
	Type type;
	Oid typeId = SPI_gettypeid(tupleDesc, index);
	if(!OidIsValid(typeId))
	{
		Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
			"Invalid attribute index \"%d\"", (int)index);
		type = 0;
	}
	else /* Type_objectTypeFromOid returns boxed types, when that matters */
		type = Type_objectTypeFromOid(typeId, Invocation_getTypeMap());
	return type;
}

static jvalue _TupleDesc_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = pljava_TupleDesc_create((TupleDesc)DatumGetPointer(arg));
	return result;
}

/* Make this datatype available to the postgres system.
 */
extern void pljava_TupleDesc_initialize(void);
void pljava_TupleDesc_initialize(void)
{
	TypeClass cls;
	JNINativeMethod methods[] = {
		{
		"_getColumnName",
	  	"(JI)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName
		},
		{
		"_getColumnIndex",
		"(JLjava/lang/String;)I",
		Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex
		},
		{
		"_formTuple",
		"(J[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_TupleDesc__1formTuple
		},
		{
		"_getOid",
		"(JI)Lorg/postgresql/pljava/internal/Oid;",
		Java_org_postgresql_pljava_internal_TupleDesc__1getOid
		},
		{ 0, 0, 0 }};

	s_TupleDesc_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/TupleDesc"));
	PgObject_registerNatives2(s_TupleDesc_class, methods);
	s_TupleDesc_init = PgObject_getJavaMethod(s_TupleDesc_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJI)V");

	cls = TypeClass_alloc("type.TupleDesc");
	cls->JNISignature = "Lorg/postgresql/pljava/internal/TupleDesc;";
	cls->javaTypeName = "org.postgresql.pljava.internal.TupleDesc";
	cls->coerceDatum  = _TupleDesc_coerceDatum;
	Type_registerType("org.postgresql.pljava.internal.TupleDesc", TypeClass_allocInstance(cls, InvalidOid));
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnName
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName(JNIEnv* env, jclass cls, jlong _this, jint index)
{
	jstring result = 0;

	BEGIN_NATIVE
	PG_TRY();
	{
		char* name;
		Ptr2Long p2l;
		p2l.longVal = _this;
		name = SPI_fname((TupleDesc)p2l.ptrVal, (int)index);
		if(name == 0)
		{
			Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
				"Invalid attribute index \"%d\"", (int)index);
		}
		else
		{
			result = String_createJavaStringFromNTS(name);
			pfree(name);
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_fname");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getColumnIndex
 * Signature: (JLjava/lang/String;)I;
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex(JNIEnv* env, jclass cls, jlong _this, jstring colName)
{
	jint result = 0;

	BEGIN_NATIVE
	char* name = String_createNTS(colName);
	if(name != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		PG_TRY();
		{
			result = SPI_fnumber((TupleDesc)p2l.ptrVal, name);
			if(result == SPI_ERROR_NOATTRIBUTE)
			{
				Exception_throw(ERRCODE_UNDEFINED_COLUMN,
					"Tuple has no attribute \"%s\"", name);
			}
			pfree(name);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_fnumber");
		}
		PG_END_TRY();
	}
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _formTuple
 * Signature: (J[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1formTuple(JNIEnv* env, jclass cls, jlong _this, jobjectArray jvalues)
{
	jobject result = 0;

	BEGIN_NATIVE
	Ptr2Long p2l;
	p2l.longVal = _this;
	PG_TRY();
	{
		jint   idx;
		HeapTuple tuple;
		MemoryContext curr;
		TupleDesc self = (TupleDesc)p2l.ptrVal;
		int    count   = self->natts;
		Datum* values  = (Datum*)palloc(count * sizeof(Datum));
		bool*  nulls   = palloc(count * sizeof(bool));
		jobject typeMap = Invocation_getTypeMap(); /* a global ref */

		memset(values, 0,  count * sizeof(Datum));
		memset(nulls, true, count * sizeof(bool));/*all values null initially*/
	
		for(idx = 0; idx < count; ++idx)
		{
			jobject value = JNI_getObjectArrayElement(jvalues, idx);
			if(value != 0)
			{
				/* Obtain boxed types here too, when that matters. */
				Type type = Type_objectTypeFromOid(SPI_gettypeid(self, idx + 1), typeMap);
				values[idx] = Type_coerceObjectBridged(type, value);
				nulls[idx] = false;
				JNI_deleteLocalRef(value);
			}
		}

		curr = MemoryContextSwitchTo(JavaMemoryContext);
		tuple = heap_form_tuple(self, values, nulls);
		result = pljava_Tuple_internalCreate(tuple, false);
		MemoryContextSwitchTo(curr);
		pfree(values);
		pfree(nulls);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("heap_formtuple");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getOid
 * Signature: (JI)Lorg/postgresql/pljava/internal/Oid;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getOid(JNIEnv* env, jclass cls, jlong _this, jint index)
{
	jobject result = 0;
	
	BEGIN_NATIVE
	Ptr2Long p2l;
	p2l.longVal = _this;
	PG_TRY();
	{
		Oid typeId = SPI_gettypeid((TupleDesc)p2l.ptrVal, (int)index);
		if(!OidIsValid(typeId))
		{
			Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
				"Invalid attribute index \"%d\"", (int)index);
		}
		else
		{
			result = Oid_create(typeId);
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_gettypeid");
	}
	PG_END_TRY();
	END_NATIVE

	return result;
}
