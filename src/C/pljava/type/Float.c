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

static Type s_float;	/* Primitive (scalar) type */
static TypeClass s_floatClass;
static Type s_floatArray;	/* Primitive (scalar) array type */
static TypeClass s_floatArrayClass;

static Type s_Float;	/* Object type */
static TypeClass s_FloatClass;
static Type s_FloatArray;	/* Object array type */
static TypeClass s_FloatArrayClass;

static jclass    s_Float_class;
static jmethodID s_Float_init;
static jmethodID s_Float_floatValue;

/*
 * float primitive type.
 */
static Datum _float_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jfloat sv = JNI_callStaticFloatMethodA(cls, method, args);
	return Float4GetDatum(sv);
}

static jvalue _float_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.f = DatumGetFloat4(arg);
	return result;
}

static Type float_obtain(Oid typeId)
{
	return s_float;
}

static jvalue _floatArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jfloatArray floatArray = JNI_newFloatArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setFloatArrayRegion(floatArray, 0, nElems, (jfloat*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jfloat* values = (jfloat*)ARR_DATA_PTR(v);
		jfloat* elems  = JNI_getFloatArrayElements(floatArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseFloatArrayElements(floatArray, elems, JNI_COMMIT);
	}
	else
		JNI_setFloatArrayRegion(floatArray, 0, nElems, (jfloat*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)floatArray;
	return result;
}

static Datum _floatArray_coerceObject(Type self, jobject floatArray)
{
	ArrayType* v;
	jsize nElems;

	if(floatArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)floatArray);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jfloat), FLOAT4OID);
#else
	v = createArrayType(nElems, sizeof(jfloat), FLOAT4OID, false);
#endif
	JNI_getFloatArrayRegion((jfloatArray)floatArray, 0, nElems, (jfloat*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type floatArray_obtain(Oid typeId)
{
	return s_floatArray;
}

/*
 * java.lang.Float type.
 */
static jobject _create(jfloat value)
{
	return JNI_newObject(s_Float_class, s_Float_init, value);
}

static jfloat _floatValue(jobject floatObj)
{
	return floatObj == 0 ? 0 : JNI_callFloatMethod(floatObj, s_Float_floatValue);
}

static bool _Float_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_floatClass;
}

static jvalue _Float_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetFloat4(arg));
	return result;
}

static Datum _Float_coerceObject(Type self, jobject floatObj)
{
	jfloat sv = _floatValue(floatObj);
	return Float4GetDatum(sv);
}

static Type Float_obtain(Oid typeId)
{
	return s_Float;
}

/*
 * java.lang.Float[] type.
 */
static bool _FloatArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_floatArrayClass;
}

static jvalue _FloatArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray floatArray = JNI_newObjectArray(nElems, s_Float_class, 0);
	jfloat* values = (jfloat*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject floatObj = _create(*values++);
		JNI_setObjectArrayElement(floatArray, idx, floatObj);
		JNI_deleteLocalRef(floatObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(floatArray, idx, 0);
		else
		{
			jobject floatObj = _create(*values++);
			JNI_setObjectArrayElement(floatArray, idx, floatObj);
			JNI_deleteLocalRef(floatObj);
		}
#endif
	}
	result.l = (jobject)floatArray;
	return result;
}

static Datum _FloatArray_coerceObject(Type self, jobject floatArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jfloat* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(floatArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)floatArray);
	v = createArrayType(nElems, sizeof(jfloat), FLOAT4OID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(floatArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)floatArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)floatArray);
	v = createArrayType(nElems, sizeof(jfloat), FLOAT4OID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jfloat*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject floatObj = JNI_getObjectArrayElement(floatArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(floatObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _floatValue(floatObj);
			JNI_deleteLocalRef(floatObj);
		}
#else
		if(floatObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _floatValue(floatObj);
			JNI_deleteLocalRef(floatObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type FloatArray_obtain(Oid typeId)
{
	return s_FloatArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Float_initialize(void);
void Float_initialize(void)
{
	s_Float_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Float"));
	s_Float_init = PgObject_getJavaMethod(s_Float_class, "<init>", "(F)V");
	s_Float_floatValue = PgObject_getJavaMethod(s_Float_class, "floatValue", "()F");

	s_FloatClass = TypeClass_alloc("type.Float");
	s_FloatClass->canReplaceType = _Float_canReplace;
	s_FloatClass->JNISignature   = "Ljava/lang/Float;";
	s_FloatClass->javaTypeName   = "java.lang.Float";
	s_FloatClass->coerceObject   = _Float_coerceObject;
	s_FloatClass->coerceDatum    = _Float_coerceDatum;
	s_Float = TypeClass_allocInstance(s_FloatClass, FLOAT4OID);

	s_FloatArrayClass = TypeClass_alloc("type.Float[]");
	s_FloatArrayClass->canReplaceType = _FloatArray_canReplace;
	s_FloatArrayClass->JNISignature   = "[Ljava/lang/Float;";
	s_FloatArrayClass->javaTypeName   = "java.lang.Float[]";
	s_FloatArrayClass->coerceDatum    = _FloatArray_coerceDatum;
	s_FloatArrayClass->coerceObject   = _FloatArray_coerceObject;
	s_FloatArray = TypeClass_allocInstance(s_FloatArrayClass, InvalidOid);

	s_floatClass = TypeClass_alloc("type.float");
	s_floatClass->JNISignature   = "F";
	s_floatClass->javaTypeName   = "float";
	s_floatClass->objectType     = s_Float;
	s_floatClass->invoke         = _float_invoke;
	s_floatClass->coerceDatum    = _float_coerceDatum;
	s_floatClass->coerceObject   = _Float_coerceObject;
	s_float = TypeClass_allocInstance(s_floatClass, INT2OID);

	s_floatArrayClass = TypeClass_alloc("type.float[]");
	s_floatArrayClass->JNISignature       = "[F";
	s_floatArrayClass->javaTypeName       = "float[]";
	s_floatArrayClass->objectType         = s_FloatArray;
	s_floatArrayClass->coerceDatum        = _floatArray_coerceDatum;
	s_floatArrayClass->coerceObject       = _floatArray_coerceObject;
	s_floatArray = TypeClass_allocInstance(s_floatArrayClass, InvalidOid);

	s_floatClass->arrayType = s_floatArray;
	s_FloatClass->arrayType = s_FloatArray;

	Type_registerType(FLOAT4OID, "float", float_obtain);
	Type_registerType(InvalidOid, "java.lang.Float", Float_obtain);
	Type_registerType(InvalidOid, "float[]", floatArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Float[]", FloatArray_obtain);
}
