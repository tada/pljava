/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"

/*
 * byte[] type. Copies data to/from a bytea struct.
 */
static jvalue _byte_array_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	bytea* bytes  = DatumGetByteaP(arg);
	jsize  length = VARSIZE(bytes) - VARHDRSZ;
	jbyteArray ba = (*env)->NewByteArray(env, length);
	(*env)->SetByteArrayRegion(env, ba, 0, length, (jbyte*)VARDATA(bytes)); 
	result.l = ba;
	return result;
}

static Datum _byte_array_coerceObject(Type self, JNIEnv* env, jobject byteArray)
{
	jsize  length    = (*env)->GetArrayLength(env, (jbyteArray)byteArray);
	int32  byteaSize = length + VARHDRSZ;
	bytea* bytes     = (bytea*)palloc(byteaSize);

	VARATT_SIZEP(bytes) = byteaSize;
	(*env)->GetByteArrayRegion(env, (jbyteArray)byteArray, 0, length, (jbyte*)VARDATA(bytes));
	PG_RETURN_BYTEA_P(bytes);
}

static Type s_byte_array;
static TypeClass s_byte_arrayClass;

static Type byte_array_obtain(Oid typeId)
{
	return s_byte_array;
}

/* Make this datatype available to the postgres system.
 */
extern Datum byte_array_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(byte_array_initialize);
Datum byte_array_initialize(PG_FUNCTION_ARGS)
{
	s_byte_arrayClass = TypeClass_alloc("type.byte[]");
	s_byte_arrayClass->JNISignature = "[B";
	s_byte_arrayClass->javaTypeName = "byte[]";
	s_byte_arrayClass->coerceDatum  = _byte_array_coerceDatum;
	s_byte_arrayClass->coerceObject = _byte_array_coerceObject;
	s_byte_array = TypeClass_allocInstance(s_byte_arrayClass, BYTEAOID);

	Type_registerPgType(BYTEAOID, byte_array_obtain);
	Type_registerJavaType("byte[]", byte_array_obtain);
	PG_RETURN_VOID();
}

