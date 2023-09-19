/*
 * Copyright (c) 2018-2023 Tada AB and other contributors, as listed below.
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

static TypeClass s_SQLXMLClass;
static jclass    s_SQLXML_class;
static jmethodID s_SQLXML_adopt;
static jclass    s_SQLXML_Readable_PgXML_class;
static jmethodID s_SQLXML_Readable_PgXML_init;
static jclass    s_SQLXML_Writable_class;
static jmethodID s_SQLXML_Writable_init;

static TypeClass s_SQLXMLClass_Synthetic;
static jclass    s_SQLXML_Readable_Synthetic_class;
static jmethodID s_SQLXML_Readable_Synthetic_init;

static bool   _SQLXML_canReplaceType(Type self, Type other);
static jvalue _SQLXML_coerceDatum(Type self, Datum arg);
static Datum  _SQLXML_coerceObject(Type self, jobject sqlxml);
static Type   _SQLXML_obtain(Oid typeId);

/*
 * It is possible to install PL/Java in a PostgreSQL instance that was built
 * without libxml and the native XML data type. It could even be useful for
 * SQLXML to be usable in those circumstances, so the canReplaceType method
 * will return true if the native type is text. (An exact match on TEXTOID is
 * required, for now at least, because over in String.c, canReplaceType answers
 * true for any native type that has text in/out conversions, and we do NOT want
 * SQLXML to willy/nilly expose the internals of just any of those.
 */
static bool _SQLXML_canReplaceType(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return
		Type_getClass(self) == cls  ||
#if defined(XMLOID)
		Type_getOid(other) == XMLOID  ||
#endif
		Type_getOid(other) == PG_NODE_TREEOID  ||  /* a synthetic rendering */
		Type_getOid(other) == TEXTOID;
}

static jvalue _SQLXML_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jobject vwi = pljava_VarlenaWrapper_Input(
		arg, TopTransactionContext, TopTransactionResourceOwner);
	result.l = JNI_newObject(
		s_SQLXML_Readable_PgXML_class, s_SQLXML_Readable_PgXML_init,
		vwi, Type_getOid(self));
	JNI_deleteLocalRef(vwi);
	return result;
}

static jvalue _SQLXML_coerceDatum_synthetic(Type self, Datum arg)
{
	jvalue result;
	jobject vwi = pljava_VarlenaWrapper_Input(
		arg, TopTransactionContext, TopTransactionResourceOwner);
	result.l = JNI_newObject(
		s_SQLXML_Readable_Synthetic_class, s_SQLXML_Readable_Synthetic_init,
		vwi, Type_getOid(self));
	JNI_deleteLocalRef(vwi);
	return result;
}

static Datum _SQLXML_coerceObject(Type self, jobject sqlxml)
{
	jobject vw = JNI_callStaticObjectMethodLocked(
		s_SQLXML_class, s_SQLXML_adopt, sqlxml, Type_getOid(self));
	Datum d = pljava_VarlenaWrapper_adopt(vw);
	JNI_deleteLocalRef(vw);
	if ( VARATT_IS_EXTERNAL_EXPANDED_RW(DatumGetPointer(d)) )
		return TransferExpandedObject(d, CurrentMemoryContext);

	MemoryContextSetParent(
		GetMemoryChunkContext(DatumGetPointer(d)), CurrentMemoryContext);

	return d;
}

/*
 * A Type can be 'registered' two ways. In one case, a single instance can be
 * created with TypeClass_allocInstance(2)? and assigned a fixed Oid, and that
 * instance then passed to Type_registerType along with the Java name.
 *
 * The other way is not to allocate any Type instance up front, but instead
 * to call Type_registerType2, passing just the type's canonical Oid, the Java
 * name, and an 'obtainer' function, like this one.
 *
 * The difference appears when this TypeClass has a _canReplaceType function
 * that allows it to serve more than one PostgreSQL type (as, indeed, SQLXML
 * now does and can). With the first registration style, the same Type instance
 * will be used for any of the PostgreSQL types accepted by the _canReplaceType
 * function. With the second style, the obtainer will be called to produce a
 * distinct Type instance (sharing the same TypeClass) for each one, recording
 * its own PostgreSQL Oid.
 *
 * SQLXML has a need to run a content verifier when 'bouncing' a readable
 * instance back to PostgreSQL, and ideally only to do so when the Oids at
 * create and adopt time are different, so it cannot make do with the singleton
 * type instance, and needs to use Type_registerType2 with an obtainer.
 *
 * The obtainer can, however, cache a single instance per supported oid, of
 * which there are, so far, only two (one, in PG instances without XML).
 */
static Type _SQLXML_obtain(Oid typeId)
{
	static Type textInstance;
	Oid allowedId = InvalidOid;
	bool synthetic = false;
	Type *cache;
#if defined(XMLOID)
	static Type xmlInstance;
#endif
	static Type pgNodeTreeInstance;

	switch ( typeId )
	{
	case PG_NODE_TREEOID:
		allowedId = PG_NODE_TREEOID;
		synthetic = true;
		cache = &pgNodeTreeInstance;
		break;
	default:
		if ( TEXTOID == typeId )
		{
			cache = &textInstance;
			allowedId = TEXTOID;
		}
#if defined(XMLOID)
		else
		{
			allowedId = XMLOID;
			cache = &xmlInstance;
		}
#endif
	}
	if ( NULL == *cache )
		*cache = TypeClass_allocInstance(
			synthetic ? s_SQLXMLClass_Synthetic : s_SQLXMLClass, allowedId);
	return *cache;
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
	cls->canReplaceType = _SQLXML_canReplaceType;
	cls->coerceDatum  = _SQLXML_coerceDatum;
	cls->coerceObject = _SQLXML_coerceObject;
	s_SQLXMLClass = cls;

	Type_registerType2(InvalidOid, "java.sql.SQLXML", _SQLXML_obtain);

	cls = TypeClass_alloc("type.SQLXML");
	cls->JNISignature = "Ljava/sql/SQLXML;";
	cls->javaTypeName = "java.sql.SQLXML";
	cls->canReplaceType = _SQLXML_canReplaceType;
	cls->coerceDatum  = _SQLXML_coerceDatum_synthetic;
	/* cls->coerceObject = _SQLXML_coerceObject; */
	s_SQLXMLClass_Synthetic = cls; /* what happens if I don't register it? */

	s_SQLXML_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl"));
	s_SQLXML_adopt = PgObject_getStaticJavaMethod(s_SQLXML_class, "adopt",
		"(Ljava/sql/SQLXML;I)Lorg/postgresql/pljava/internal/VarlenaWrapper;");

	s_SQLXML_Readable_PgXML_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl$Readable$PgXML"));
	s_SQLXML_Readable_PgXML_init = PgObject_getJavaMethod(
		s_SQLXML_Readable_PgXML_class,
		"<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Input;I)V");

	s_SQLXML_Readable_Synthetic_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLXMLImpl$Readable$Synthetic"));
	s_SQLXML_Readable_Synthetic_init = PgObject_getJavaMethod(
		s_SQLXML_Readable_Synthetic_class,
		"<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Input;I)V");

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
	jobject sqlxml = NULL;
	jobject vwo;
	BEGIN_NATIVE
	vwo = pljava_VarlenaWrapper_Output(
			TopTransactionContext, TopTransactionResourceOwner);
	sqlxml = JNI_newObjectLocked(
			s_SQLXML_Writable_class, s_SQLXML_Writable_init, vwo);
	END_NATIVE
	return sqlxml;
}
