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

static Type s_short;	/* Primitive (scalar) type */
static TypeClass s_shortClass;
static Type s_shortArray;	/* Primitive (scalar) array type */
static TypeClass s_shortArrayClass;

static Type s_Short;	/* Object type */
static TypeClass s_ShortClass;
static Type s_ShortArray;	/* Object array type */
static TypeClass s_ShortArrayClass;

static jclass    s_Short_class;
static jmethodID s_Short_init;
static jmethodID s_Short_shortValue;

/*
 * short primitive type.
 */
static Datum _short_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jshort sv = JNI_callStaticShortMethodA(cls, method, args);
	return Int16GetDatum(sv);
}

static jvalue _short_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.s = DatumGetInt16(arg);
	return result;
}

static Type short_obtain(Oid typeId)
{
	return s_short;
}

static jvalue _shortArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jshortArray shortArray = JNI_newShortArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setShortArrayRegion(shortArray, 0, nElems, (jshort*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jshort* values = (jshort*)ARR_DATA_PTR(v);
		jshort* elems  = JNI_getShortArrayElements(shortArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseShortArrayElements(shortArray, elems, JNI_COMMIT);
	}
	else
		JNI_setShortArrayRegion(shortArray, 0, nElems, (jshort*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)shortArray;
	return result;
}

static Datum _shortArray_coerceObject(Type self, jobject shortArray)
{
	ArrayType* v;
	jsize nElems;

	if(shortArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)shortArray);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jshort), INT2OID);
#else
	v = createArrayType(nElems, sizeof(jshort), INT2OID, false);
#endif
	JNI_getShortArrayRegion((jshortArray)shortArray, 0, nElems, (jshort*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type shortArray_obtain(Oid typeId)
{
	return s_shortArray;
}

/*
 * java.lang.Short type.
 */
static jobject _create(jshort value)
{
	return JNI_newObject(s_Short_class, s_Short_init, value);
}

static jshort _shortValue(jobject shortObj)
{
	return shortObj == 0 ? 0 : JNI_callShortMethod(shortObj, s_Short_shortValue);
}

static bool _Short_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_shortClass;
}

static jvalue _Short_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetInt16(arg));
	return result;
}

static Datum _Short_coerceObject(Type self, jobject shortObj)
{
	jshort sv = _shortValue(shortObj);
	return Int16GetDatum(sv);
}

static Type Short_obtain(Oid typeId)
{
	return s_Short;
}

/*
 * java.lang.Short[] type.
 */
static bool _ShortArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_shortArrayClass;
}

static jvalue _ShortArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray shortArray = JNI_newObjectArray(nElems, s_Short_class, 0);
	jshort* values = (jshort*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject shortObj = _create(*values++);
		JNI_setObjectArrayElement(shortArray, idx, shortObj);
		JNI_deleteLocalRef(shortObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(shortArray, idx, 0);
		else
		{
			jobject shortObj = _create(*values++);
			JNI_setObjectArrayElement(shortArray, idx, shortObj);
			JNI_deleteLocalRef(shortObj);
		}
#endif
	}
	result.l = (jobject)shortArray;
	return result;
}

static Datum _ShortArray_coerceObject(Type self, jobject shortArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jshort* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(shortArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)shortArray);
	v = createArrayType(nElems, sizeof(jshort), INT2OID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(shortArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)shortArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)shortArray);
	v = createArrayType(nElems, sizeof(jshort), INT2OID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jshort*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject shortObj = JNI_getObjectArrayElement(shortArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(shortObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _shortValue(shortObj);
			JNI_deleteLocalRef(shortObj);
		}
#else
		if(shortObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _shortValue(shortObj);
			JNI_deleteLocalRef(shortObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type ShortArray_obtain(Oid typeId)
{
	return s_ShortArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Short_initialize(void);
void Short_initialize(void)
{
	s_Short_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Short"));
	s_Short_init = PgObject_getJavaMethod(s_Short_class, "<init>", "(S)V");
	s_Short_shortValue = PgObject_getJavaMethod(s_Short_class, "shortValue", "()S");

	s_ShortClass = TypeClass_alloc("type.Short");
	s_ShortClass->canReplaceType = _Short_canReplace;
	s_ShortClass->JNISignature   = "Ljava/lang/Short;";
	s_ShortClass->javaTypeName   = "java.lang.Short";
	s_ShortClass->coerceObject   = _Short_coerceObject;
	s_ShortClass->coerceDatum    = _Short_coerceDatum;
	s_Short = TypeClass_allocInstance(s_ShortClass, INT2OID);

	s_ShortArrayClass = TypeClass_alloc("type.Short[]");
	s_ShortArrayClass->canReplaceType = _ShortArray_canReplace;
	s_ShortArrayClass->JNISignature   = "[Ljava/lang/Short;";
	s_ShortArrayClass->javaTypeName   = "java.lang.Short[]";
	s_ShortArrayClass->coerceDatum    = _ShortArray_coerceDatum;
	s_ShortArrayClass->coerceObject   = _ShortArray_coerceObject;
	s_ShortArray = TypeClass_allocInstance(s_ShortArrayClass, InvalidOid);

	s_shortClass = TypeClass_alloc("type.short");
	s_shortClass->JNISignature   = "S";
	s_shortClass->javaTypeName   = "short";
	s_shortClass->objectType     = s_Short;
	s_shortClass->invoke         = _short_invoke;
	s_shortClass->coerceDatum    = _short_coerceDatum;
	s_shortClass->coerceObject   = _Short_coerceObject;
	s_short = TypeClass_allocInstance(s_shortClass, INT2OID);

	s_shortArrayClass = TypeClass_alloc("type.short[]");
	s_shortArrayClass->JNISignature       = "[S";
	s_shortArrayClass->javaTypeName       = "short[]";
	s_shortArrayClass->objectType         = s_ShortArray;
	s_shortArrayClass->coerceDatum        = _shortArray_coerceDatum;
	s_shortArrayClass->coerceObject       = _shortArray_coerceObject;
	s_shortArray = TypeClass_allocInstance(s_shortArrayClass, InvalidOid);

	s_shortClass->arrayType = s_shortArray;
	s_ShortClass->arrayType = s_ShortArray;

	Type_registerType(INT2OID, "short", short_obtain);
	Type_registerType(InvalidOid, "java.lang.Short", Short_obtain);
	Type_registerType(InvalidOid, "short[]", shortArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Short[]", ShortArray_obtain);
}
