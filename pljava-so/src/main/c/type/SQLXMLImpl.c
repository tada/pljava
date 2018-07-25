/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
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

#include "org_postgresql_pljava_jdbc_SQLXMLImpl.h"

#include "pljava/type/Type_priv.h"
#include "pljava/VarlenaWrapper.h"

static jclass    s_SQLXML_class;
static jmethodID s_SQLXML_adopt;
static jclass    s_SQLXML_Readable_class;
static jmethodID s_SQLXML_Readable_init;
static jclass    s_SQLXML_Writable_class;
static jmethodID s_SQLXML_Writable_init;

static jvalue _SQLXML_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jobject vwi = pljava_VarlenaWrapper_Input(
		arg, TopTransactionContext, TopTransactionResourceOwner);
	result.l = JNI_newObject(
		s_SQLXML_Readable_class, s_SQLXML_Readable_init, vwi);
	JNI_deleteLocalRef(vwi);
	return result;
}

static Datum _SQLXML_coerceObject(Type self, jobject sqlxml)
{
	jobject vw = JNI_callObjectMethodLocked(sqlxml, s_SQLXML_adopt);
	Datum d = pljava_VarlenaWrapper_adopt(vw);
	JNI_deleteLocalRef(vw);
#if PG_VERSION_NUM >= 90500
	if ( VARATT_IS_EXTERNAL_EXPANDED_RW(DatumGetPointer(d)) )
		return TransferExpandedObject(d, CurrentMemoryContext);
#endif
#if PG_VERSION_NUM >= 90200
	MemoryContextSetParent(
		GetMemoryChunkContext(DatumGetPointer(d)), CurrentMemoryContext);
#else
	if ( CurrentMemoryContext != GetMemoryChunkContext(DatumGetPointer(d)) )
		d = PointerGetDatum(PG_DETOAST_DATUM_COPY(d));
#endif
	return d;
}

/* Make this datatype available to the postgres system.
 */
extern void pljava_SQLXMLImpl_initialize(void);
void pljava_SQLXMLImpl_initialize(void)
{
	jclass clazz;
	JNINativeMethod methods[] =
	{
		{
		"_newWritable",
		"()Ljava/sql/SQLXML;",
		Java_org_postgresql_pljava_jdbc_SQLXMLImpl__1newWritable
		},
		{ 0, 0, 0 }
	};

	TypeClass cls = TypeClass_alloc("type.SQLXML");
	cls->JNISignature = "Ljava/sql/SQLXML;";
	cls->javaTypeName = "java.sql.SQLXML";
	cls->coerceDatum  = _SQLXML_coerceDatum;
	cls->coerceObject = _SQLXML_coerceObject;
	Type_registerType("java.sql.SQLXML", TypeClass_allocInstance(cls, XMLOID));

	s_SQLXML_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl"));
	s_SQLXML_adopt = PgObject_getJavaMethod(s_SQLXML_class,
		"adopt", "()Lorg/postgresql/pljava/internal/VarlenaWrapper;");

	s_SQLXML_Readable_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl$Readable"));
	s_SQLXML_Readable_init = PgObject_getJavaMethod(s_SQLXML_Readable_class,
		"<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Input;)V");

	s_SQLXML_Writable_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl$Writable"));
	s_SQLXML_Writable_init = PgObject_getJavaMethod(s_SQLXML_Writable_class,
		"<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Output;)V");

	clazz = PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLXMLImpl");
	PgObject_registerNatives2(clazz, methods);
	JNI_deleteLocalRef(clazz);
}

/*
 * Class:     org_postgresql_pljava_jdbc_SQLXMLImpl
 * Method:    _newWritable
 * Signature: ()Ljava/sql/SQLXML;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_jdbc_SQLXMLImpl__1newWritable
	(JNIEnv *env, jclass sqlxml_class)
{
	jobject sqlxml;
	jobject vwo;
	BEGIN_NATIVE
	vwo = pljava_VarlenaWrapper_Output(
			TopTransactionContext, TopTransactionResourceOwner);
	sqlxml = JNI_newObjectLocked(
			s_SQLXML_Writable_class, s_SQLXML_Writable_init, vwo);
	END_NATIVE
	return sqlxml;
}
