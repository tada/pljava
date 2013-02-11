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

static TypeClass s_intClass;
static jclass    s_Integer_class;
static jclass    s_IntegerArray_class;
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

	if(!JNI_isInstanceOf( intArray, s_IntegerArray_class))
	  JNI_getIntArrayRegion((jintArray)intArray, 0, nElems, (jint*)ARR_DATA_PTR(v));
	else
	  {
	    int idx = 0;
	    jint *array = (jint*)ARR_DATA_PTR(v);

	    for(idx = 0; idx < nElems; ++idx)
	      {
		array[idx] = JNI_callIntMethod(JNI_getObjectArrayElement(intArray, idx),
					       s_Integer_intValue);
	      }
	  }


	PG_RETURN_ARRAYTYPE_P(v);
}

/*
 * java.lang.Integer type.
 */
static bool _Integer_canReplace(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls || cls == s_intClass;
}

static jvalue _Integer_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Integer_class, s_Integer_init, DatumGetInt32(arg));
	return result;
}

static Datum _Integer_coerceObject(Type self, jobject intObj)
{
	return Int32GetDatum(intObj == 0 ? 0 : JNI_callIntMethod(intObj, s_Integer_intValue));
}

static Type _int_createArrayType(Type self, Oid arrayTypeId)
{
	return Array_fromOid2(arrayTypeId, self, _intArray_coerceDatum, _intArray_coerceObject);
}

/* Make this datatype available to the postgres system.
 */
extern void Integer_initialize(void);
void Integer_initialize(void)
{
	Type t_int;
	Type t_Integer;
	TypeClass cls;

	s_Integer_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Integer"));
	s_IntegerArray_class = JNI_newGlobalRef(PgObject_getJavaClass("[Ljava/lang/Integer;"));
	s_Integer_init = PgObject_getJavaMethod(s_Integer_class, "<init>", "(I)V");
	s_Integer_intValue = PgObject_getJavaMethod(s_Integer_class, "intValue", "()I");

	cls = TypeClass_alloc("type.Integer");
	cls->canReplaceType = _Integer_canReplace;
	cls->JNISignature = "Ljava/lang/Integer;";
	cls->javaTypeName = "java.lang.Integer";
	cls->coerceDatum  = _Integer_coerceDatum;
	cls->coerceObject = _Integer_coerceObject;
	t_Integer = TypeClass_allocInstance(cls, INT4OID);

	cls = TypeClass_alloc("type.int");
	cls->JNISignature = "I";
	cls->javaTypeName = "int";
	cls->invoke       = _int_invoke;
	cls->coerceDatum  = _int_coerceDatum;
	cls->coerceObject = _Integer_coerceObject;
	cls->createArrayType = _int_createArrayType;
	s_intClass = cls;

	t_int = TypeClass_allocInstance(cls, INT4OID);
	t_int->objectType = t_Integer;
	Type_registerType("int", t_int);
	Type_registerType("java.lang.Integer", t_Integer);
}
