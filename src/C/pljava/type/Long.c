/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/Array.h"
#include "pljava/Invocation.h"

static Type s_long;	/* Primitive (scalar) type */
static TypeClass s_longClass;
static Type s_longArray;	/* Primitive (scalar) array type */
static TypeClass s_longArrayClass;

static Type s_Long;	/* Object type */
static TypeClass s_LongClass;
static Type s_LongArray;	/* Object array type */
static TypeClass s_LongArrayClass;

static jclass    s_Long_class;
static jmethodID s_Long_init;
static jmethodID s_Long_longValue;

/*
 * long primitive type.
 */
static Datum _long_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jlong lv = JNI_callStaticLongMethodA(cls, method, args);

	/* Since we don't know if 64 bit quantities are passed by reference or
	 * by value, we have to make sure that the correct context is used if
	 * it's the former.
	 */
	MemoryContext currCtx = Invocation_switchToUpperContext();
	Datum ret = Int64GetDatum(lv);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static jvalue _long_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.j = DatumGetInt64(arg);
	return result;
}

static Type long_obtain(Oid typeId)
{
	return s_long;
}

static jvalue _longArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
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
	JNI_getLongArrayRegion((jlongArray)longArray, 0, nElems, (jlong*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type longArray_obtain(Oid typeId)
{
	return s_longArray;
}

/*
 * java.lang.Long type.
 */
static jobject _create(jlong value)
{
	return JNI_newObject(s_Long_class, s_Long_init, value);
}

static jlong _longValue(jobject longObj)
{
	return longObj == 0 ? 0 : JNI_callLongMethod(longObj, s_Long_longValue);
}

static bool _Long_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_longClass;
}

static jvalue _Long_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetInt64(arg));
	return result;
}

static Datum _Long_coerceObject(Type self, jobject longObj)
{
	jlong lv = _longValue(longObj);
	return Int64GetDatum(lv);
}

static Type Long_obtain(Oid typeId)
{
	return s_Long;
}

/*
 * java.lang.Long[] type.
 */
static bool _LongArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_longArrayClass;
}

static jvalue _LongArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray longArray = JNI_newObjectArray(nElems, s_Long_class, 0);
	jlong* values = (jlong*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject longObj = _create(*values++);
		JNI_setObjectArrayElement(longArray, idx, longObj);
		JNI_deleteLocalRef(longObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(longArray, idx, 0);
		else
		{
			jobject longObj = _create(*values++);
			JNI_setObjectArrayElement(longArray, idx, longObj);
			JNI_deleteLocalRef(longObj);
		}
#endif
	}
	result.l = (jobject)longArray;
	return result;
}

static Datum _LongArray_coerceObject(Type self, jobject longArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jlong* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(longArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)longArray);
	v = createArrayType(nElems, sizeof(jlong), INT8OID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(longArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)longArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)longArray);
	v = createArrayType(nElems, sizeof(jlong), INT8OID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jlong*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject longObj = JNI_getObjectArrayElement(longArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(longObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _longValue(longObj);
			JNI_deleteLocalRef(longObj);
		}
#else
		if(longObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _longValue(longObj);
			JNI_deleteLocalRef(longObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type LongArray_obtain(Oid typeId)
{
	return s_LongArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Long_initialize(void);
void Long_initialize(void)
{
	s_Long_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Long"));
	s_Long_init = PgObject_getJavaMethod(s_Long_class, "<init>", "(J)V");
	s_Long_longValue = PgObject_getJavaMethod(s_Long_class, "longValue", "()J");

	s_LongClass = TypeClass_alloc("type.Long");
	s_LongClass->canReplaceType = _Long_canReplace;
	s_LongClass->JNISignature   = "Ljava/lang/Long;";
	s_LongClass->javaTypeName   = "java.lang.Long";
	s_LongClass->coerceObject   = _Long_coerceObject;
	s_LongClass->coerceDatum    = _Long_coerceDatum;
	s_Long = TypeClass_allocInstance(s_LongClass, INT8OID);

	s_LongArrayClass = TypeClass_alloc("type.Long[]");
	s_LongArrayClass->canReplaceType = _LongArray_canReplace;
	s_LongArrayClass->JNISignature   = "[Ljava/lang/Long;";
	s_LongArrayClass->javaTypeName   = "java.lang.Long[]";
	s_LongArrayClass->coerceDatum    = _LongArray_coerceDatum;
	s_LongArrayClass->coerceObject   = _LongArray_coerceObject;
	s_LongArray = TypeClass_allocInstance(s_LongArrayClass, InvalidOid);

	s_longClass = TypeClass_alloc("type.long");
	s_longClass->JNISignature   = "J";
	s_longClass->javaTypeName   = "long";
	s_longClass->objectType     = s_Long;
	s_longClass->invoke         = _long_invoke;
	s_longClass->coerceDatum    = _long_coerceDatum;
	s_longClass->coerceObject   = _Long_coerceObject;
	s_long = TypeClass_allocInstance(s_longClass, INT8OID);

	s_longArrayClass = TypeClass_alloc("type.long[]");
	s_longArrayClass->JNISignature       = "[J";
	s_longArrayClass->javaTypeName       = "long[]";
	s_longArrayClass->objectType         = s_LongArray;
	s_longArrayClass->coerceDatum        = _longArray_coerceDatum;
	s_longArrayClass->coerceObject       = _longArray_coerceObject;
	s_longArray = TypeClass_allocInstance(s_longArrayClass, InvalidOid);

	s_longClass->arrayType = s_longArray;
	s_LongClass->arrayType = s_LongArray;

	Type_registerType(INT8OID, "long", long_obtain);
	Type_registerType(InvalidOid, "java.lang.Long", Long_obtain);
	Type_registerType(InvalidOid, "long[]", longArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Long[]", LongArray_obtain);
}
