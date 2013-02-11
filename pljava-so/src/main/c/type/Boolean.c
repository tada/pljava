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

static TypeClass s_booleanClass;
static jclass    s_Boolean_class;
static jclass    s_BooleanArray_class;
static jmethodID s_Boolean_init;
static jmethodID s_Boolean_booleanValue;

/*
 * boolean primitive type.
 */
static Datum _boolean_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jboolean v = JNI_callStaticBooleanMethodA(cls, method, args);
	return BoolGetDatum(v);
}

static jvalue _boolean_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.z = DatumGetBool(arg);
	return result;
}

static jvalue _booleanArray_coerceDatum(Type self, Datum arg)
{
	jvalue     result;
	ArrayType* v      = DatumGetArrayTypeP(arg);
	jsize      nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
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

	if(!JNI_isInstanceOf( booleanArray, s_BooleanArray_class))
		JNI_getBooleanArrayRegion((jbooleanArray)booleanArray, 0,
					  nElems, (jboolean*)ARR_DATA_PTR(v));
	else
	{
		int idx = 0;
		jboolean *array = (jboolean*)ARR_DATA_PTR(v);

		for(idx = 0; idx < nElems; ++idx)
		{
			array[idx] = JNI_callBooleanMethod(JNI_getObjectArrayElement(booleanArray, idx),
							   s_Boolean_booleanValue);
		}
	}

	PG_RETURN_ARRAYTYPE_P(v);
}

/*
 * java.lang.Boolean type.
 */
static bool _Boolean_canReplace(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls || cls == s_booleanClass;
}

static jvalue _Boolean_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Boolean_class, s_Boolean_init, DatumGetBool(arg));
	return result;
}

static Datum _Boolean_coerceObject(Type self, jobject booleanObj)
{
	return BoolGetDatum(booleanObj == 0 ? false : JNI_callBooleanMethod(booleanObj, s_Boolean_booleanValue) == JNI_TRUE);
}

static Type _boolean_createArrayType(Type self, Oid arrayTypeId)
{
	return Array_fromOid2(arrayTypeId, self, _booleanArray_coerceDatum, _booleanArray_coerceObject);
}

/* Make this datatype available to the postgres system.
 */
extern void Boolean_initialize(void);
void Boolean_initialize(void)
{
	Type t_boolean;
	Type t_Boolean;
	TypeClass cls;

	s_Boolean_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Boolean"));
	s_BooleanArray_class = JNI_newGlobalRef(PgObject_getJavaClass("[Ljava/lang/Boolean;"));
	s_Boolean_init = PgObject_getJavaMethod(s_Boolean_class, "<init>", "(Z)V");
	s_Boolean_booleanValue = PgObject_getJavaMethod(s_Boolean_class, "booleanValue", "()Z");

	cls = TypeClass_alloc("type.Boolean");
	cls->canReplaceType = _Boolean_canReplace;
	cls->JNISignature = "Ljava/lang/Boolean;";
	cls->javaTypeName = "java.lang.Boolean";
	cls->coerceDatum  = _Boolean_coerceDatum;
	cls->coerceObject = _Boolean_coerceObject;
	t_Boolean = TypeClass_allocInstance(cls, BOOLOID);

	cls = TypeClass_alloc("type.boolean");
	cls->JNISignature = "Z";
	cls->javaTypeName = "boolean";
	cls->invoke       = _boolean_invoke;
	cls->coerceDatum  = _boolean_coerceDatum;
	cls->coerceObject = _Boolean_coerceObject;
	cls->createArrayType = _boolean_createArrayType;
	s_booleanClass = cls;

	t_boolean = TypeClass_allocInstance(cls, BOOLOID);
	t_boolean->objectType = t_Boolean;

	Type_registerType("boolean", t_boolean);
	Type_registerType("java.lang.Boolean", t_Boolean);
}
