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
#include <funcapi.h>

#include "org_postgresql_pljava_internal_TupleDesc.h"
#include "pljava/Exception.h"
#include "pljava/PLJavaMemoryContext.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Oid.h"

static Type      s_TupleDesc;
static TypeClass s_TupleDescClass;
static jclass    s_TupleDesc_class;
static jmethodID s_TupleDesc_init;

/*
 * org.postgresql.pljava.TupleDesc type.
 */
jobject TupleDesc_create(JNIEnv* env, TupleDesc td)
{
	jobject jtd;
	if(td == 0)
		return 0;

	jtd = PLJavaMemoryContext_getJavaObject(env, td);
	if(jtd == 0)
	{
		MemoryContext curr = MemoryContextSwitchTo(JavaMemoryContext);
		jtd = TupleDesc_internalCreate(env, td);
		MemoryContextSwitchTo(curr);
	}
	return jtd;
}

jobject TupleDesc_internalCreate(JNIEnv* env, TupleDesc td)
{
	jobject jtd;
	Ptr2Long tdH;

	td = CreateTupleDescCopyConstr(td);
	tdH.ptrVal = td;
	jtd = PgObject_newJavaObject(env, s_TupleDesc_class, s_TupleDesc_init, tdH.longVal);
	PLJavaMemoryContext_setJavaObject(env, td, jtd);
	return jtd;
}

Type TupleDesc_getColumnType(JNIEnv* env, TupleDesc tupleDesc, int index)
{
	Type type;
	Oid typeId = SPI_gettypeid(tupleDesc, index);
	if(!OidIsValid(typeId))
	{
		Exception_throw(env, ERRCODE_INVALID_DESCRIPTOR_INDEX,
			"Invalid attribute index \"%d\"", (int)index);
		type = 0;
	}
	else
		type = Type_objectTypeFromOid(typeId);
	return type;
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
		"_size",
		"(J)I",
		Java_org_postgresql_pljava_internal_TupleDesc__1size
		},
		{
		"_getOid",
		"(JI)Lorg/postgresql/pljava/internal/Oid;",
		Java_org_postgresql_pljava_internal_TupleDesc__1getOid
		},
		{
		"_free",
		"(J)V",
		Java_org_postgresql_pljava_internal_TupleDesc__1free
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TupleDesc_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TupleDesc"));

	PgObject_registerNatives2(env, s_TupleDesc_class, methods);

	s_TupleDesc_init = PgObject_getJavaMethod(
				env, s_TupleDesc_class, "<init>", "(J)V");

	s_TupleDescClass = MemoryContextManagedClass_alloc("type.TupleDesc");
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
 * Signature: (JI)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnName(JNIEnv* env, jclass cls, jlong _this, jint index)
{
	Ptr2Long p2l;
	TupleDesc self;
	jstring result = 0;

	PLJAVA_ENTRY_FENCE(0)
	p2l.longVal = _this;
	self = (TupleDesc)p2l.ptrVal;
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
 * Signature: (JLjava/lang/String;)I;
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getColumnIndex(JNIEnv* env, jclass cls, jlong _this, jstring colName)
{
	Ptr2Long p2l;
	TupleDesc self;
	char* name;
	jint index = 0;

	PLJAVA_ENTRY_FENCE(0)
	p2l.longVal = _this;
	self = (TupleDesc)p2l.ptrVal;
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
 * Signature: (J[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1formTuple(JNIEnv* env, jclass cls, jlong _this, jobjectArray jvalues)
{
	Ptr2Long p2l;
	TupleDesc self;
	jobject result = 0;

	PLJAVA_ENTRY_FENCE(0)
	p2l.longVal = _this;
	self = (TupleDesc)p2l.ptrVal;
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
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1size(JNIEnv* env, jclass cls, jlong _this)
{
	Ptr2Long p2l;
	TupleDesc self;
	p2l.longVal = _this;
	self = (TupleDesc)p2l.ptrVal;
	if(self == 0)
		return 0;
	return (jint)self->natts;
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1free(JNIEnv* env, jobject _this, jlong pointer)
{
	if(pointer != 0)
	{
		/* Avoid callback when explicitly freed from Java code
		 */
		Ptr2Long p2l;
		p2l.longVal = pointer;
		PLJavaMemoryContext_setJavaObject(env, p2l.ptrVal, 0);
		FreeTupleDesc(p2l.ptrVal);
	}
}

/*
 * Class:     org_postgresql_pljava_internal_TupleDesc
 * Method:    _getOid
 * Signature: (JI)Lorg/postgresql/pljava/internal/Oid;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TupleDesc__1getOid(JNIEnv* env, jclass cls, jlong _this, jint index)
{
	Ptr2Long p2l;
	TupleDesc self;
	jobject result = 0;
	PLJAVA_ENTRY_FENCE(0)

	p2l.longVal = _this;
	self = (TupleDesc)p2l.ptrVal;
	if(self != 0)
	{
		PG_TRY();
		{
			Oid typeId = SPI_gettypeid(self, (int)index);
			if(!OidIsValid(typeId))
			{
				Exception_throw(env,
					ERRCODE_INVALID_DESCRIPTOR_INDEX,
					"Invalid attribute index \"%d\"", (int)index);
			}
			else
			{
				result = Oid_create(env, typeId);
			}
		}
		PG_CATCH();
		{
			Exception_throw_ERROR(env, "SPI_gettypeid");
		}
		PG_END_TRY();
	}
	return result;
}
