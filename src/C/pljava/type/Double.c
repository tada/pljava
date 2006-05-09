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

static Type s_double;	/* Primitive (scalar) type */
static TypeClass s_doubleClass;
static Type s_doubleArray;	/* Primitive (scalar) array type */
static TypeClass s_doubleArrayClass;

static Type s_Double;	/* Object type */
static TypeClass s_DoubleClass;
static Type s_DoubleArray;	/* Object array type */
static TypeClass s_DoubleArrayClass;

static jclass    s_Double_class;
static jmethodID s_Double_init;
static jmethodID s_Double_doubleValue;

/*
 * double primitive type.
 */
static Datum _double_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jdouble lv = JNI_callStaticDoubleMethodA(cls, method, args);

	/* Since we don't know if 64 bit quantities are passed by reference or
	 * by value, we have to make sure that the correct context is used if
	 * it's the former.
	 */
	MemoryContext currCtx = Invocation_switchToUpperContext();
	Datum ret = Float8GetDatum(lv);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static jvalue _double_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.d = DatumGetFloat8(arg);
	return result;
}

static Type double_obtain(Oid typeId)
{
	return s_double;
}

static jvalue _doubleArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jdoubleArray doubleArray = JNI_newDoubleArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setDoubleArrayRegion(doubleArray, 0, nElems, (jdouble*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jdouble* values = (jdouble*)ARR_DATA_PTR(v);
		jdouble* elems  = JNI_getDoubleArrayElements(doubleArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseDoubleArrayElements(doubleArray, elems, JNI_COMMIT);
	}
	else
		JNI_setDoubleArrayRegion(doubleArray, 0, nElems, (jdouble*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)doubleArray;
	return result;
}

static Datum _doubleArray_coerceObject(Type self, jobject doubleArray)
{
	ArrayType* v;
	jsize nElems;

	if(doubleArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)doubleArray);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jdouble), FLOAT8OID);
#else
	v = createArrayType(nElems, sizeof(jdouble), FLOAT8OID, false);
#endif
	JNI_getDoubleArrayRegion((jdoubleArray)doubleArray, 0, nElems, (jdouble*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type doubleArray_obtain(Oid typeId)
{
	return s_doubleArray;
}

/*
 * java.lang.Double type.
 */
static jobject _create(jdouble value)
{
	return JNI_newObject(s_Double_class, s_Double_init, value);
}

static jdouble _doubleValue(jobject doubleObj)
{
	return doubleObj == 0 ? 0 : JNI_callDoubleMethod(doubleObj, s_Double_doubleValue);
}

static bool _Double_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_doubleClass;
}

static jvalue _Double_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetFloat8(arg));
	return result;
}

static Datum _Double_coerceObject(Type self, jobject doubleObj)
{
	jdouble lv = _doubleValue(doubleObj);
	return Float8GetDatum(lv);
}

static Type Double_obtain(Oid typeId)
{
	return s_Double;
}

/*
 * java.lang.Double[] type.
 */
static bool _DoubleArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_doubleArrayClass;
}

static jvalue _DoubleArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray doubleArray = JNI_newObjectArray(nElems, s_Double_class, 0);
	jdouble* values = (jdouble*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject doubleObj = _create(*values++);
		JNI_setObjectArrayElement(doubleArray, idx, doubleObj);
		JNI_deleteLocalRef(doubleObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(doubleArray, idx, 0);
		else
		{
			jobject doubleObj = _create(*values++);
			JNI_setObjectArrayElement(doubleArray, idx, doubleObj);
			JNI_deleteLocalRef(doubleObj);
		}
#endif
	}
	result.l = (jobject)doubleArray;
	return result;
}

static Datum _DoubleArray_coerceObject(Type self, jobject doubleArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jdouble* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(doubleArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)doubleArray);
	v = createArrayType(nElems, sizeof(jdouble), FLOAT8OID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(doubleArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)doubleArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)doubleArray);
	v = createArrayType(nElems, sizeof(jdouble), FLOAT8OID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jdouble*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject doubleObj = JNI_getObjectArrayElement(doubleArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(doubleObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _doubleValue(doubleObj);
			JNI_deleteLocalRef(doubleObj);
		}
#else
		if(doubleObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _doubleValue(doubleObj);
			JNI_deleteLocalRef(doubleObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type DoubleArray_obtain(Oid typeId)
{
	return s_DoubleArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Double_initialize(void);
void Double_initialize(void)
{
	s_Double_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Double"));
	s_Double_init = PgObject_getJavaMethod(s_Double_class, "<init>", "(D)V");
	s_Double_doubleValue = PgObject_getJavaMethod(s_Double_class, "doubleValue", "()D");

	s_DoubleClass = TypeClass_alloc("type.Double");
	s_DoubleClass->canReplaceType = _Double_canReplace;
	s_DoubleClass->JNISignature   = "Ljava/lang/Double;";
	s_DoubleClass->javaTypeName   = "java.lang.Double";
	s_DoubleClass->coerceObject   = _Double_coerceObject;
	s_DoubleClass->coerceDatum    = _Double_coerceDatum;
	s_Double = TypeClass_allocInstance(s_DoubleClass, FLOAT8OID);

	s_DoubleArrayClass = TypeClass_alloc("type.Double[]");
	s_DoubleArrayClass->canReplaceType = _DoubleArray_canReplace;
	s_DoubleArrayClass->JNISignature   = "[Ljava/lang/Double;";
	s_DoubleArrayClass->javaTypeName   = "java.lang.Double[]";
	s_DoubleArrayClass->coerceDatum    = _DoubleArray_coerceDatum;
	s_DoubleArrayClass->coerceObject   = _DoubleArray_coerceObject;
	s_DoubleArray = TypeClass_allocInstance(s_DoubleArrayClass, InvalidOid);

	s_doubleClass = TypeClass_alloc("type.double");
	s_doubleClass->JNISignature   = "D";
	s_doubleClass->javaTypeName   = "double";
	s_doubleClass->objectType     = s_Double;
	s_doubleClass->invoke         = _double_invoke;
	s_doubleClass->coerceDatum    = _double_coerceDatum;
	s_doubleClass->coerceObject   = _Double_coerceObject;
	s_double = TypeClass_allocInstance(s_doubleClass, FLOAT8OID);

	s_doubleArrayClass = TypeClass_alloc("type.double[]");
	s_doubleArrayClass->JNISignature       = "[D";
	s_doubleArrayClass->javaTypeName       = "double[]";
	s_doubleArrayClass->objectType         = s_DoubleArray;
	s_doubleArrayClass->coerceDatum        = _doubleArray_coerceDatum;
	s_doubleArrayClass->coerceObject       = _doubleArray_coerceObject;
	s_doubleArray = TypeClass_allocInstance(s_doubleArrayClass, InvalidOid);

	s_doubleClass->arrayType = s_doubleArray;
	s_DoubleClass->arrayType = s_DoubleArray;

	Type_registerType(FLOAT8OID, "double", double_obtain);
	Type_registerType(InvalidOid, "java.lang.Double", Double_obtain);
	Type_registerType(InvalidOid, "double[]", doubleArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Double[]", DoubleArray_obtain);
}
