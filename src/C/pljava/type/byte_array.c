/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"

static jclass s_byteArray_class;
static jclass s_BlobValue_class;
static jmethodID s_BlobValue_length;
static jmethodID s_BlobValue_getContent;

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
	bytea* bytes = 0;
	if((*env)->IsInstanceOf(env, byteArray, s_byteArray_class))
	{
		jsize  length    = (*env)->GetArrayLength(env, (jbyteArray)byteArray);
		int32  byteaSize = length + VARHDRSZ;

		bytes = (bytea*)palloc(byteaSize);
		VARATT_SIZEP(bytes) = byteaSize;
		(*env)->GetByteArrayRegion(env, (jbyteArray)byteArray, 0, length, (jbyte*)VARDATA(bytes));
	}
	else if((*env)->IsInstanceOf(env, byteArray, s_BlobValue_class))
	{
		jobject byteBuffer;
		jlong length;
		int32 byteaSize;
		bool saveicj = isCallingJava;
		
		isCallingJava = true;
		length = (*env)->CallLongMethod(env, byteArray, s_BlobValue_length);
		isCallingJava = saveicj;

		byteaSize = (int32)(length + VARHDRSZ);
		bytes = (bytea*)palloc(byteaSize);
		VARATT_SIZEP(bytes) = byteaSize;

		isCallingJava = true;
		byteBuffer = (*env)->NewDirectByteBuffer(env, (void*)VARDATA(bytes), length);
		if(byteBuffer != 0)
			(*env)->CallVoidMethod(env, byteArray, s_BlobValue_getContent, byteBuffer);
		isCallingJava = saveicj;

		if((*env)->ExceptionCheck(env))
		{
			pfree(bytes);
			bytes = 0;
		}
		if(byteBuffer != 0)
			(*env)->DeleteLocalRef(env, byteBuffer);
	}
	else
	{
		Exception_throwIllegalArgument(env, "Not coercable to bytea");
	}

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
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);
	s_byteArray_class = (*env)->NewGlobalRef(env, PgObject_getJavaClass(env, "[B"));
	s_BlobValue_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/jdbc/BlobValue"));

	s_BlobValue_length = PgObject_getJavaMethod(env, s_BlobValue_class, "length", "()Z");
	s_BlobValue_getContent = PgObject_getJavaMethod(env, s_BlobValue_class, "getContent", "([java/nio/ByteBuffer;)V");

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

