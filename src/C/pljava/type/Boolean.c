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

static Type s_boolean;	/* Primitive (scalar) type */
static TypeClass s_booleanClass;
static Type s_booleanArray;	/* Primitive (scalar) array type */
static TypeClass s_booleanArrayClass;

static Type s_Boolean;	/* Object type */
static TypeClass s_BooleanClass;
static Type s_BooleanArray;	/* Object array type */
static TypeClass s_BooleanArrayClass;

static jclass    s_Boolean_class;
static jmethodID s_Boolean_init;
static jmethodID s_Boolean_booleanValue;

/*
 * boolean primitive type.
 */
static Datum _boolean_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool bv = JNI_callStaticBooleanMethodA(cls, method, args) == JNI_TRUE;
	return BoolGetDatum(bv);
}

static jvalue _boolean_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.z = DatumGetBool(arg) ? JNI_TRUE : JNI_FALSE;
	return result;
}

static Type boolean_obtain(Oid typeId)
{
	return s_boolean;
}

static jvalue _booleanArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v        = DatumGetArrayTypeP(arg);
	jsize      nElems   = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jbooleanArray booleanArray = JNI_newBooleanArray(nElems);

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	JNI_setBooleanArrayRegion(booleanArray, 0, nElems, (jboolean*)ARR_DATA_PTR(v));
#else
	if(ARR_HASNULL(v))
	{
		jsize idx;
		jboolean isCopy = JNI_FALSE;
		bits8* nullBitMap = ARR_NULLBITMAP(v);
		jboolean* values = (jboolean*)ARR_DATA_PTR(v);
		jboolean* elems  = JNI_getBooleanArrayElements(booleanArray, &isCopy);
		for(idx = 0; idx < nElems; ++idx)
		{
			if(arrayIsNull(nullBitMap, idx))
				elems[idx] = 0;
			else
				elems[idx] = *values++;
		}
		JNI_releaseBooleanArrayElements(booleanArray, elems, JNI_COMMIT);
	}
	else
		JNI_setBooleanArrayRegion(booleanArray, 0, nElems, (jboolean*)ARR_DATA_PTR(v));
#endif
	result.l = (jobject)booleanArray;
	return result;
}

static Datum _booleanArray_coerceObject(Type self, jobject booleanArray)
{
	ArrayType* v;
	jsize nElems;

	if(booleanArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)booleanArray);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	v = createArrayType(nElems, sizeof(jboolean), BOOLOID);
#else
	v = createArrayType(nElems, sizeof(jboolean), BOOLOID, false);
#endif
	JNI_getBooleanArrayRegion((jbooleanArray)booleanArray, 0, nElems, (jboolean*)ARR_DATA_PTR(v));	

	PG_RETURN_ARRAYTYPE_P(v);
}

static Type booleanArray_obtain(Oid typeId)
{
	return s_booleanArray;
}

/*
 * java.lang.Boolean type.
 */
static jobject _create(jboolean value)
{
	return JNI_newObject(s_Boolean_class, s_Boolean_init, value);
}

static jboolean _booleanValue(jobject booleanObj)
{
	return booleanObj == 0 ? JNI_FALSE : JNI_callBooleanMethod(booleanObj, s_Boolean_booleanValue);
}

static bool _Boolean_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_booleanClass;
}

static jvalue _Boolean_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = _create(DatumGetBool(arg));
	return result;
}

static Datum _Boolean_coerceObject(Type self, jobject booleanObj)
{
	bool bv = _booleanValue(booleanObj) == JNI_TRUE;
	return BoolGetDatum(bv);
}

static Type Boolean_obtain(Oid typeId)
{
	return s_Boolean;
}

/*
 * java.lang.Boolean[] type.
 */
static bool _BooleanArray_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_booleanArrayClass;
}

static jvalue _BooleanArray_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray booleanArray = JNI_newObjectArray(nElems, s_Boolean_class, 0);
	jboolean* values = (jboolean*)ARR_DATA_PTR(v);
#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	bits8* nullBitMap = ARR_NULLBITMAP(v);
#endif

	for(idx = 0; idx < nElems; ++idx)
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		jobject booleanObj = _create(*values++);
		JNI_setObjectArrayElement(booleanArray, idx, booleanObj);
		JNI_deleteLocalRef(booleanObj);
#else
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(booleanArray, idx, 0);
		else
		{
			jobject booleanObj = _create(*values++);
			JNI_setObjectArrayElement(booleanArray, idx, booleanObj);
			JNI_deleteLocalRef(booleanObj);
		}
#endif
	}
	result.l = (jobject)booleanArray;
	return result;
}

static Datum _BooleanArray_coerceObject(Type self, jobject booleanArray)
{
	ArrayType* v;
	jsize idx;
	jsize nElems;
	jboolean* values;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
	if(booleanArray == 0)
		return 0;

	nElems = JNI_getArrayLength((jarray)booleanArray);
	v = createArrayType(nElems, sizeof(jboolean), BOOLOID);
#else
	bool hasNull;
	bits8* nullBitMap;

	if(booleanArray == 0)
		return 0;

	hasNull = JNI_hasNullArrayElement((jobjectArray)booleanArray) == JNI_TRUE;
	nElems = JNI_getArrayLength((jarray)booleanArray);
	v = createArrayType(nElems, sizeof(jboolean), BOOLOID, hasNull);
	nullBitMap = ARR_NULLBITMAP(v);
#endif

	values = (jboolean*)ARR_DATA_PTR(v);
	for(idx = 0; idx < nElems; ++idx)
	{
		/* TODO: Check for NULL values
		 */
		jobject booleanObj = JNI_getObjectArrayElement(booleanArray, idx);
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
		if(booleanObj == 0)
			*values++ = 0;
		else
		{
			*values++ = _booleanValue(booleanObj);
			JNI_deleteLocalRef(booleanObj);
		}
#else
		if(booleanObj == 0)
			arraySetNull(nullBitMap, idx, true);
		else
		{
			arraySetNull(nullBitMap, idx, false);
			*values++ = _booleanValue(booleanObj);
			JNI_deleteLocalRef(booleanObj);
		}
#endif
	}
	PG_RETURN_ARRAYTYPE_P(v);
}

static Type BooleanArray_obtain(Oid typeId)
{
	return s_BooleanArray;
}

/* Make this datatype available to the postgres system.
 */
extern void Boolean_initialize(void);
void Boolean_initialize(void)
{
	s_Boolean_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Boolean"));
	s_Boolean_init = PgObject_getJavaMethod(s_Boolean_class, "<init>", "(Z)V");
	s_Boolean_booleanValue = PgObject_getJavaMethod(s_Boolean_class, "booleanValue", "()Z");

	s_BooleanClass = TypeClass_alloc("type.Boolean");
	s_BooleanClass->canReplaceType = _Boolean_canReplace;
	s_BooleanClass->JNISignature   = "Ljava/lang/Boolean;";
	s_BooleanClass->javaTypeName   = "java.lang.Boolean";
	s_BooleanClass->coerceObject   = _Boolean_coerceObject;
	s_BooleanClass->coerceDatum    = _Boolean_coerceDatum;
	s_Boolean = TypeClass_allocInstance(s_BooleanClass, BOOLOID);

	s_BooleanArrayClass = TypeClass_alloc("type.Boolean[]");
	s_BooleanArrayClass->canReplaceType = _BooleanArray_canReplace;
	s_BooleanArrayClass->JNISignature   = "[Ljava/lang/Boolean;";
	s_BooleanArrayClass->javaTypeName   = "java.lang.Boolean[]";
	s_BooleanArrayClass->coerceDatum    = _BooleanArray_coerceDatum;
	s_BooleanArrayClass->coerceObject   = _BooleanArray_coerceObject;
	s_BooleanArray = TypeClass_allocInstance(s_BooleanArrayClass, InvalidOid);

	s_booleanClass = TypeClass_alloc("type.boolean");
	s_booleanClass->JNISignature   = "Z";
	s_booleanClass->javaTypeName   = "boolean";
	s_booleanClass->objectType     = s_Boolean;
	s_booleanClass->invoke         = _boolean_invoke;
	s_booleanClass->coerceDatum    = _boolean_coerceDatum;
	s_booleanClass->coerceObject   = _Boolean_coerceObject;
	s_boolean = TypeClass_allocInstance(s_booleanClass, BOOLOID);

	s_booleanArrayClass = TypeClass_alloc("type.boolean[]");
	s_booleanArrayClass->JNISignature       = "[Z";
	s_booleanArrayClass->javaTypeName       = "boolean[]";
	s_booleanArrayClass->objectType         = s_BooleanArray;
	s_booleanArrayClass->coerceDatum        = _booleanArray_coerceDatum;
	s_booleanArrayClass->coerceObject       = _booleanArray_coerceObject;
	s_booleanArray = TypeClass_allocInstance(s_booleanArrayClass, InvalidOid);

	s_booleanClass->arrayType = s_booleanArray;
	s_BooleanClass->arrayType = s_BooleanArray;

	Type_registerType(BOOLOID, "boolean", boolean_obtain);
	Type_registerType(InvalidOid, "java.lang.Boolean", Boolean_obtain);
	Type_registerType(InvalidOid, "boolean[]", booleanArray_obtain);
	Type_registerType(InvalidOid, "java.lang.Boolean[]", BooleanArray_obtain);
}
