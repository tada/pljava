/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

#include <postgres.h>
#include <miscadmin.h>
#include <access/genam.h>
#include <access/heaptoast.h>
#include <access/relation.h>
#include <access/tupdesc.h>
#include <executor/spi.h>
#include <executor/tuptable.h>
#include <mb/pg_wchar.h>
#include <utils/fmgroids.h>
#include <utils/inval.h>
#include <utils/rel.h>
#include <utils/resowner.h>
#include <utils/typcache.h>

#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/PgObject.h"
#include "pljava/ModelUtils.h"
#include "pljava/VarlenaWrapper.h"

#include "org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives.h"

/*
 * A compilation unit collecting various native methods used in the pg model
 * implementation classes. This is something of a break with past PL/Java
 * practice of having a correspondingly-named C file for a Java class, made on
 * the belief that there won't be that many new methods here, and they will make
 * more sense collected together.
 *
 * Some of the native methods here may *not* include the elaborate fencing seen
 * in other PL/Java native methods, if they involve trivially simple functions
 * that do not require calling into PostgreSQL or other non-thread-safe code.
 * This is, of course, a careful exception made to the general rule. The calling
 * Java code is expected to have good reason to believe any state to be examined
 * by these methods won't be shifting underneath them.
 */

void pljava_ModelUtils_initialize(void)
{
	jclass cls;

	JNINativeMethod charsetMethods[] =
	{
		{
		"_serverEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding
		},
		{
		"_clientEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding
		},
		{
		"_nameToOrdinal",
		"(Ljava/nio/ByteBuffer;)I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal
		},
		{
		"_ordinalToName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName
		},
		{
		"_ordinalToIcuName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName
		},
		{ 0, 0, 0 }
	};

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CharsetEncodingImpl$EarlyNatives");
	PgObject_registerNatives2(cls, charsetMethods);
	JNI_deleteLocalRef(cls);
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _serverEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = GetDatabaseEncoding();
	END_NATIVE_AND_CATCH("_serverEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _clientEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = pg_get_client_encoding();
	END_NATIVE_AND_CATCH("_clientEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _nameToOrdinal
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal(JNIEnv *env, jclass cls, jobject bb)
{
	int result = -1;
	char const *name = (*env)->GetDirectBufferAddress(env, bb);
	if ( NULL == name )
		return result;
	BEGIN_NATIVE_AND_TRY
	result = pg_char_to_encoding(name);
	END_NATIVE_AND_CATCH("_nameToOrdinal")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = pg_encoding_to_char(ordinal);
	if ( '\0' != *name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToName")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToIcuName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = get_encoding_name_for_icu(ordinal);
	if ( NULL != name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToIcuName")
	return result;
}
