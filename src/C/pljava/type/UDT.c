/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <catalog/pg_namespace.h>
#include <utils/builtins.h>
#include <libpq/pqformat.h>

#include "pljava/type/UDT_priv.h"
#include "pljava/type/String.h"
#include "pljava/SQLInputFromChunk.h"
#include "pljava/SQLOutputToChunk.h"

jvalue _UDT_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	UDT    udt = (UDT)self;
	int32  dataLen = udt->length;

	if(dataLen == -2)
	{
		/* Data is a zero terminated string
		 */
		jstring jstr = String_createJavaStringFromNTS(DatumGetCString(arg));
		result.l = JNI_callStaticObjectMethod(udt->clazz, udt->parse, jstr);
		JNI_deleteLocalRef(jstr);
	}
	else
	{
		char* data;
		jobject inputStream;
		if(dataLen == -1)
		{
			/* Data is a varlena struct
			*/
			bytea* bytes = DatumGetByteaP(arg);
			dataLen = VARSIZE(bytes) - VARHDRSZ;
			data    = VARDATA(bytes);
		}
		else
		{
			/* Data is a binary chunk of size dataLen
			 */
			data = DatumGetPointer(arg);
		}
		result.l = JNI_newObject(udt->clazz, udt->init);

		inputStream = SQLInputFromChunk_create(data, dataLen);
		JNI_callVoidMethod(result.l, udt->readSQL, inputStream, udt->sqlTypeName);
		SQLInputFromChunk_close(inputStream);
	}
	return result;
}

Datum _UDT_coerceObject(Type self, jobject value)
{
	Datum result;
	UDT udt = (UDT)self;
	int32 dataLen = udt->length;
	if(dataLen == -2)
	{
		jstring jstr = (jstring)JNI_callObjectMethod(value, udt->toString);
		char* tmp = String_createNTS(jstr);
		result = CStringGetDatum(tmp);
		JNI_deleteLocalRef(jstr);
	}
	else
	{
		jobject outputStream;
		StringInfoData buffer;
		initStringInfo(&buffer);

		if(dataLen < 0)
			/*
			 * Reserve space for an int32 at the beginning. We are building
			 * a varlena
			 */
			appendBinaryStringInfo(&buffer, (char*)&dataLen, sizeof(int32));

		outputStream = SQLOutputToChunk_create(&buffer);
		JNI_callVoidMethod(value, udt->writeSQL, outputStream);
		SQLOutputToChunk_close(outputStream);

		if(dataLen < 0)
		{
			/* Assign the correct length.
			 */
			*((int32*)buffer.data) = buffer.len;
		}
		else if(dataLen != buffer.len)
		{
			ereport(ERROR, (
				errcode(ERRCODE_CANNOT_COERCE),
				errmsg("UDT for Oid %d produced image with incorrect size. Expected %d, was %d",
					Type_getOid(self), dataLen, buffer.len)));
		}
		result = PointerGetDatum(buffer.data);
	}
	return result;
}

static Type UDT_obtain(Oid typeId)
{
	Type type = Type_fromOidCache(typeId);
	if(type == 0)
	{
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("No type mapping installed for UDT with Oid %d", typeId)));
	}
	return type;
}

Datum UDT_input(UDT udt, PG_FUNCTION_ARGS)
{
	jstring jstr;
	jobject obj;
	char* txt = PG_GETARG_CSTRING(0);

	if(udt->length == -2)
	{
		if(txt != 0)
			txt = pstrdup(txt);
		PG_RETURN_CSTRING(txt);
	}
	jstr = String_createJavaStringFromNTS(txt);
	obj  = JNI_callStaticObjectMethod(udt->clazz, udt->parse, jstr);
	JNI_deleteLocalRef(jstr);

	return _UDT_coerceObject((Type)udt, obj);
}

Datum UDT_output(UDT udt, PG_FUNCTION_ARGS)
{
	char* txt;
	if(udt->length == -2)
	{
		txt = PG_GETARG_CSTRING(0);
		if(txt != 0)
			txt = pstrdup(txt);
	}
	else
	{
		jobject value = _UDT_coerceDatum((Type)udt, PG_GETARG_DATUM(0)).l;
		jstring jstr  = (jstring)JNI_callObjectMethod(value, udt->toString);
		txt = String_createNTS(jstr);
		JNI_deleteLocalRef(value);
		JNI_deleteLocalRef(jstr);
	}
	PG_RETURN_CSTRING(txt);
}

Datum UDT_receive(UDT udt, PG_FUNCTION_ARGS)
{
	StringInfo buf;
	char* tmp;
	int32 dataLen = udt->length;

	if(dataLen == -1)
		return bytearecv(fcinfo);

	if(dataLen == -2)
		return unknownrecv(fcinfo);

	buf = (StringInfo)PG_GETARG_POINTER(0);
	tmp = palloc(dataLen);
	pq_copymsgbytes(buf, tmp, dataLen);
	PG_RETURN_POINTER(tmp);
}

Datum UDT_send(UDT udt, PG_FUNCTION_ARGS)
{
	StringInfoData buf;
	int32 dataLen = udt->length;

	if(dataLen == -1)
		return byteasend(fcinfo);

	if(dataLen == -2)
		return unknownsend(fcinfo);

	pq_begintypsend(&buf);
	appendBinaryStringInfo(&buf, PG_GETARG_POINTER(0), dataLen);
    PG_RETURN_BYTEA_P(pq_endtypsend(&buf));
}

/* Make this datatype available to the postgres system.
 */
UDT UDT_registerUDT(const char* className, jclass clazz, Oid typeId, Form_pg_type pgType)
{
	HeapTuple nspTup;
	Form_pg_namespace nspStruct;
	TypeClass udtClass;
	UDT udt;
	int signatureLen;
	jstring sqlTypeName;
	char* classSignature;
	char* sp;
	const char* cp;
	const char* tp;
	char c;

	Type existing = Type_fromOidCache(typeId);
	if(existing != 0)
	{
		if(existing->m_class->coerceDatum != _UDT_coerceDatum)
		{
			ereport(ERROR, (
				errcode(ERRCODE_CANNOT_COERCE),
				errmsg("Attempt to register UDT with Oid %d failed. Oid appoints a non UDT type", typeId)));
		}
		return (UDT)existing;
	}

	nspTup = PgObject_getValidTuple(NAMESPACEOID, pgType->typnamespace, "namespace");
	nspStruct = (Form_pg_namespace)GETSTRUCT(nspTup);

	/* Concatenate namespace + '.' + typename
	 */
	cp = NameStr(nspStruct->nspname);
	tp = NameStr(pgType->typname);
	sp = palloc(strlen(cp) + strlen(tp) + 2);
	sprintf(sp, "%s.%s", cp, tp);
	sqlTypeName = String_createJavaStringFromNTS(sp);
	pfree(sp);

	ReleaseSysCache(nspTup);

	/* Create a Java Signature String from the class name
	 */
	signatureLen = strlen(className) + 2;
	classSignature = MemoryContextAlloc(TopMemoryContext, signatureLen + 1);

	sp = classSignature;
	cp = className;
	*sp++ = 'L';
	while((c = *cp++) != 0)
	{
		if(c == '.')
			c = '/';
		*sp++ = c;
	}
	*sp++ = ';';
	*sp = 0;

	udtClass = TypeClass_alloc2("type.UDT", sizeof(struct TypeClass_), sizeof(struct UDT_));

	udtClass->JNISignature   = classSignature;
	udtClass->javaTypeName   = className;
	udtClass->canReplaceType = _Type_canReplaceType;
	udtClass->coerceDatum    = _UDT_coerceDatum;
	udtClass->coerceObject   = _UDT_coerceObject;

	udt = (UDT)TypeClass_allocInstance(udtClass, typeId);
	udt->length   = pgType->typlen;
	udt->clazz    = clazz;

	udt->sqlTypeName = JNI_newGlobalRef(sqlTypeName);
	JNI_deleteLocalRef(sqlTypeName);

	udt->init     = PgObject_getJavaMethod(clazz, "<init>", "()V");
	udt->toString = PgObject_getJavaMethod(clazz, "toString", "()Ljava/lang/String;");

	/* The parse method is a static method on the class with the signature
	 * (Ljava/lang/String;)<classSignature>
	 */
	sp = palloc(signatureLen + 21);
	strcpy(sp, "(Ljava/lang/String;)");
	strcpy(sp + 20, classSignature);
	udt->parse = PgObject_getStaticJavaMethod(clazz, "parse", sp);
	pfree(sp);

	udt->readSQL = PgObject_getJavaMethod(clazz, "readSQL", "(Ljava/sql/SQLInput;Ljava/lang/String;)V");
	udt->writeSQL = PgObject_getJavaMethod(clazz, "writeSQL", "(Ljava/sql/SQLOutput;)V");

	Type_cacheByOid(typeId, (Type)udt);
	Type_registerType(typeId, className, UDT_obtain);
	return udt;
}
