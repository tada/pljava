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

static Type s_int;	/* Primitive (scalar) type */
static TypeClass s_intClass;
static Type s_intArray;	/* Primitive (scalar) array type */
static TypeClass s_intArrayClass;

static Type s_Integer;	/* Object type */
static TypeClass s_IntegerClass;
static Type s_IntegerArray;	/* Object array type */
static TypeClass s_IntegerArrayClass;

static jclass    s_Integer_class;
static jmethodID s_Integer_init;
static jmethodID s_Integer_intValue;

/*
 * int primitive type.
 */
static Datum _int_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jint iv = JNI_callStaticIntMethodA(cls, method, args);
	return Int32GetDatum(iv);
}

static jvalue _int_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.i = DatumGetInt32(arg);
	return result;
}

static Type int_obtain(Oid typeId)
{
	return s_int;
}

static jvalue _intArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jintArray  intArray = JNI_newIntArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setIntArrayRegion(intArray, 0, nElems, (jint*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jint* values = (jint*)ARR_DATA_PTR(v);
		jint* elems  = JNI_getIntArrayElements(intArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseIntArrayElements(intArray, elems, JNI_COMMIT);
	}
	else
		JNI_setIntArrayRegion(intArray, 0, nElems, (jint*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)intArray;
	return result;
}

static Datum _intArray_coerceObject(Type self, jobject intArray)
{
	ArrayType* v;
	jsize nElems;

	if(intArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)intArray);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jint), INT4OID);
#else
	v = createArrayType(nElems, sizeof(jint), INT4OID, false);
#endif
	JNI_getIntArrayRegion((jintArray)intArray, 0, nElems, (jint*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type intArray_obtain(Oid typeId)
{
	return s_intArray;
}

/*
 * java.lang.Integer type.
 */
static jobject _create(jint value)
{
	return JNI_newObject(s_Integer_class, s_Integer_init, value);
}

static jint _intValue(jobject intObj)
{
	return intObj == 0 ? 0 : JNI_callIntMethod(intObj, s_Integer_intValue);
}

static bool _Integer_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_intClass;
}

static jvalue _Integer_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetInt32(arg));
	return result;
}

static Datum _Integer_coerceObject(Type self, jobject intObj)
{
	return Int32GetDatum(_intValue(intObj));
}

static Type Integer_obtain(Oid typeId)
{
	return s_Integer;
}

/*
 * java.lang.Integer[] type.
 */
static bool _IntegerArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_intArrayClass;
}

static jvalue _IntegerArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray intArray = JNI_newObjectArray(nElems, s_Integer_class, 0);
	jint* values = (jint*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject intObj = _create(*values++);
		JNI_setObjectArrayElement(intArray, idx, intObj);
		JNI_deleteLocalRef(intObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(intArray, idx, 0);
		else
		{
			jobject intObj = _create(*values++);
			JNI_setObjectArrayElement(intArray, idx, intObj);
			JNI_deleteLocalRef(intObj);
		}
#endif
	}
	result.l = (jobject)intArray;
	return result;
}

static Datum _IntegerArray_coerceObject(Type self, jobject intArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jint* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(intArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)intArray);
	v = createArrayType(nElems, sizeof(jint), INT4OID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(intArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)intArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)intArray);
	v = createArrayType(nElems, sizeof(jint), INT4OID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jint*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject intObj = JNI_getObjectArrayElement(intArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(intObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _intValue(intObj);
			JNI_deleteLocalRef(intObj);
		}
#else			
		if(intObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _intValue(intObj);
			JNI_deleteLocalRef(intObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type IntegerArray_obtain(Oid typeId)
{
	return s_IntegerArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Integer_initialize(void);
void Integer_initialize(void)
{
	s_Integer_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Integer"));
	s_Integer_init = PgObject_getJavaMethod(s_Integer_class, "<init>", "(I)V");
	s_Integer_intValue = PgObject_getJavaMethod(s_Integer_class, "intValue", "()I");

	s_IntegerClass = TypeClass_alloc("type.Integer");
	s_IntegerClass->canReplaceType = _Integer_canReplace;
	s_IntegerClass->JNISignature   = "Ljava/lang/Integer;";
	s_IntegerClass->javaTypeName   = "java.lang.Integer";
	s_IntegerClass->coerceDatum    = _Integer_coerceDatum;
	s_IntegerClass->coerceObject   = _Integer_coerceObject;
	s_Integer = TypeClass_allocInstance(s_IntegerClass, INT4OID);

	s_IntegerArrayClass = TypeClass_alloc("type.Integer[]");
	s_IntegerArrayClass->canReplaceType = _IntegerArray_canReplace;
	s_IntegerArrayClass->JNISignature   = "[Ljava/lang/Integer;";
	s_IntegerArrayClass->javaTypeName   = "java.lang.Integer[]";
	s_IntegerArrayClass->coerceDatum    = _IntegerArray_coerceDatum;
	s_IntegerArrayClass->coerceObject   = _IntegerArray_coerceObject;
	s_IntegerArray = TypeClass_allocInstance(s_IntegerArrayClass, InvalidOid);

	s_intClass = TypeClass_alloc("type.int");
	s_intClass->JNISignature       = "I";
	s_intClass->javaTypeName       = "int";
	s_intClass->objectType         = s_Integer;
	s_intClass->invoke             = _int_invoke;
	s_intClass->coerceDatum        = _int_coerceDatum;
	s_intClass->coerceObject       = _Integer_coerceObject;
	s_int = TypeClass_allocInstance(s_intClass, INT4OID);

	s_intArrayClass = TypeClass_alloc("type.int[]");
	s_intArrayClass->JNISignature       = "[I";
	s_intArrayClass->javaTypeName       = "int[]";
	s_intArrayClass->objectType         = s_IntegerArray;
	s_intArrayClass->coerceDatum        = _intArray_coerceDatum;
	s_intArrayClass->coerceObject       = _intArray_coerceObject;
	s_intArray = TypeClass_allocInstance(s_intArrayClass, InvalidOid);

	s_intClass->arrayType = s_intArray;
	s_IntegerClass->arrayType = s_IntegerArray;

	Type_registerType(INT4OID, "int", int_obtain);
	Type_registerType(InvalidOid, "java.lang.Integer", Integer_obtain);
	Type_registerType(InvalidOid, "int[]", intArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Integer[]", IntegerArray_obtain);
}
