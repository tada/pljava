/*
 * Copyright (c) 2004-2026 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"

static jclass s_byteArray_class;
static jclass s_Blob_class;
static jmethodID s_Blob_length;
static jmethodID s_Blob_getBytes;

/*
 * byte[] type. Copies data to/from a bytea struct.
 */
static jvalue _byte_array_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	bytea* bytes  = DatumGetByteaP(arg);
	jsize  length = VARSIZE(bytes) - VARHDRSZ;
	jbyteArray ba = JNI_newByteArray(length);
	JNI_setByteArrayRegion(ba, 0, length, (jbyte*)VARDATA(bytes)); 
	result.l = ba;
	return result;
}

static Datum _byte_array_coerceObject(Type self, jobject byteArray)
{
	bytea* bytes = 0;
	jlong length;
	int32 byteaSize;

	if ( byteArray == 0 )
		return 0;

	if ( JNI_isInstanceOf(byteArray, s_byteArray_class) )
	{
		length = JNI_getArrayLength((jarray)byteArray);
	}
	else if ( JNI_isInstanceOf(byteArray, s_Blob_class))
	{
		length = JNI_callLongMethod(byteArray, s_Blob_length);

		if ( 0 > length  ||  length > PG_INT32_MAX - VARHDRSZ )
		{
			ereport(ERROR, (
				errcode(ERRCODE_PROGRAM_LIMIT_EXCEEDED),
				errmsg("cannot accommodate reported Blob length %" PRId64,
					length)
			));
		}
		byteArray =
			JNI_callObjectMethod(byteArray, s_Blob_getBytes, (jlong)1, length);
	}
	else
	{
		elog(ERROR, "cannot coerce this Java class to bytea");
	}

	byteaSize = length + VARHDRSZ;

	bytes = (bytea*)palloc(byteaSize);
	SET_VARSIZE(bytes, byteaSize);

	JNI_getByteArrayRegion(
		(jbyteArray)byteArray, 0, length, (jbyte*)VARDATA(bytes));

	PG_RETURN_BYTEA_P(bytes);
}

/* Make this datatype available to the postgres system.
 */
extern void byte_array_initialize(void);
void byte_array_initialize(void)
{
	TypeClass cls = TypeClass_alloc("type.byte[]");
	cls->JNISignature = "[B";
	cls->javaTypeName = "byte[]";
	cls->coerceDatum  = _byte_array_coerceDatum;
	cls->coerceObject = _byte_array_coerceObject;
	Type_registerType("byte[]", TypeClass_allocInstance(cls, BYTEAOID));

	s_byteArray_class = JNI_newGlobalRef(PgObject_getJavaClass("[B"));
	s_Blob_class =
		JNI_newGlobalRef(PgObject_getJavaClass("java/sql/Blob"));
	s_Blob_length = PgObject_getJavaMethod(s_Blob_class, "length", "()J");
	s_Blob_getBytes =
		PgObject_getJavaMethod(s_Blob_class, "getBytes", "(JI)[B");
}

