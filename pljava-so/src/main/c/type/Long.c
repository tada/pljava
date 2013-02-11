/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Copyright (c) 2010, 2011 PostgreSQL Global Development Group
 *
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://wiki.tada.se/index.php?title=PLJava_License
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/Array.h"
#include "pljava/Invocation.h"

static TypeClass s_longClass;
static jclass    s_Long_class;
static jclass    s_LongArray_class;
static jmethodID s_Long_init;
static jmethodID s_Long_longValue;

/*
 * long primitive type.
 */
static Datum _asDatum(jlong v)
{
	MemoryContext currCtx = Invocation_switchToUpperContext();
	Datum ret = Int64GetDatum(v);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static Datum _long_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	return _asDatum(JNI_callStaticLongMethodA(cls, method, args));
}

static jvalue _long_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.j = DatumGetInt64(arg);
	return result;
}

static jvalue _longArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v      = DatumGetArrayTypeP(arg);
	jsize      nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jlongArray longArray = JNI_newLongArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setLongArrayRegion(longArray, 0, nElems, (jlong*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jlong* values = (jlong*)ARR_DATA_PTR(v);
		jlong* elems  = JNI_getLongArrayElements(longArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseLongArrayElements(longArray, elems, JNI_COMMIT);
	}
	else
		JNI_setLongArrayRegion(longArray, 0, nElems, (jlong*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)longArray;
	return result;
}

static Datum _longArray_coerceObject(Type self, jobject longArray)
{
	ArrayType* v;
	jsize nElems;

	if(longArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)longArray);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jlong), INT8OID);
#else
	v = createArrayType(nElems, sizeof(jlong), INT8OID, false);
#endif

	if(!JNI_isInstanceOf( longArray, s_LongArray_class))
		JNI_getLongArrayRegion((jlongArray)longArray, 0, nElems, (jlong*)ARR_DATA_PTR(v));
	else
	{
		int idx = 0;
		jlong *array = (jlong*)ARR_DATA_PTR(v);

		for(idx = 0; idx < nElems; ++idx)
		{
			array[idx] = JNI_callLongMethod(JNI_getObjectArrayElement(longArray, idx),
							s_Long_longValue);
		}
	}

	PG_RETURN_ARRAYTYPE_P(v);
}

/*
 * java.lang.Long type.
 */
static bool _Long_canReplace(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls || cls == s_longClass;
}

static jvalue _Long_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Long_class, s_Long_init, DatumGetInt64(arg));
	return result;
}

static Datum _Long_coerceObject(Type self, jobject longObj)
{
	return _asDatum(longObj == 0 ? 0 : JNI_callLongMethod(longObj, s_Long_longValue));
}

static Type _long_createArrayType(Type self, Oid arrayTypeId)
{
	return Array_fromOid2(arrayTypeId, self, _longArray_coerceDatum, _longArray_coerceObject);
}

/* Make this datatype available to the postgres system.
 */
extern void Long_initialize(void);
void Long_initialize(void)
{
	Type t_long;
	Type t_Long;
	TypeClass cls;

	s_Long_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Long"));
	s_LongArray_class = JNI_newGlobalRef(PgObject_getJavaClass("[Ljava/lang/Long;"));
	s_Long_init = PgObject_getJavaMethod(s_Long_class, "<init>", "(J)V");
	s_Long_longValue = PgObject_getJavaMethod(s_Long_class, "longValue", "()J");

	cls = TypeClass_alloc("type.Long");
	cls->canReplaceType = _Long_canReplace;
	cls->JNISignature = "Ljava/lang/Long;";
	cls->javaTypeName = "java.lang.Long";
	cls->coerceDatum  = _Long_coerceDatum;
	cls->coerceObject = _Long_coerceObject;
	t_Long = TypeClass_allocInstance(cls, INT8OID);

	cls = TypeClass_alloc("type.long");
	cls->JNISignature = "J";
	cls->javaTypeName = "long";
	cls->invoke       = _long_invoke;
	cls->coerceDatum  = _long_coerceDatum;
	cls->coerceObject = _Long_coerceObject;
	cls->createArrayType = _long_createArrayType;
	s_longClass = cls;

	t_long = TypeClass_allocInstance(cls, INT8OID);
	t_long->objectType = t_Long;
	Type_registerType("long", t_long);
	Type_registerType("java.lang.Long", t_Long);
}
