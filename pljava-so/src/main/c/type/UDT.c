/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <catalog/pg_namespace.h>
#include <utils/builtins.h>
#include <utils/typcache.h>
#include <libpq/pqformat.h>
#include <funcapi.h>

#include "pljava/type/UDT_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/SQLInputFromChunk.h"
#include "pljava/SQLOutputToChunk.h"
#include "pljava/SQLInputFromTuple.h"
#include "pljava/SQLOutputToTuple.h"

#if PG_VERSION_NUM >= 90000
#include <utils/bytea.h>
#endif

/*
 * This code, as currently constituted, makes these assumptions that limit how
 * Java can implement a (scalar) UDT:
 *
 * ASSUMPTION 1: If a Java UDT is declared with INTERNALLENGTH -2 (indicating
 *               that its internal representation is a variable-length sequence
 *               of nonzero bytes terminated by a zero byte), this code ASSUMES
 *               that the internal representation and the human-readable one
 *               (defined by typinput/typoutput) have to be identical ... an
 *               assumption apparently made because typinput/typoutput consume
 *               and produce the type cstring, whose internallength is also -2.
 *
 * ASSUMPTION 2: Whatever the UDT's internal representation is, its binary
 *               exchange representation (defined by typreceive/typsend) has to
 *               be identical to that.
 *
 * This list of assumptions could grow with further review of the code.
 *
 * Comments will be added below to tag code that embodies these assumptions.
 *
 * The current pattern for a scalar UDT has another difficulty: it relies on
 * toString for producing the external representation, which is a general
 * Object method declared to have nothing to throw. And the general expectation
 * for toString is to produce some nice representation, but not necessarily
 * always the literally re-parsable representation of something. And the scalar
 * readSQL/writeSQL implementations impose a 16-bit limit on lengths of things.
 *
 * Idea for future: add another scalar UDT pattern using different methods, and
 * without the current readSQL/writeSQL limitations. Continue to recognize the
 * parse/toString pattern and provide the old behavior for compatibility.
 */

static jobject coerceScalarDatum(UDT self, Datum arg)
{
	jobject result;
	int32 dataLen = Type_getLength((Type)self);
	bool isJavaBasedScalar = 0 != self->parse;

	if(dataLen == -2)
	{
		/* Data is a zero terminated string
		 */
		jstring jstr = String_createJavaStringFromNTS(DatumGetCString(arg));
		/*
		 * ASSUMPTION 1 is in play here. 'arg' here is a Datum holding this
		 * UDT's internal representation, and will now be passed to 'parse', the
		 * same method that is specified to parse a value from the human-used
		 * external representation.
		 */
		result = pljava_Function_udtParseInvoke(
			self->parse, jstr, self->sqlTypeName);
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
			bool passByValue = Type_isByValue((Type)self);
			/* Data is a binary chunk of size dataLen
			 */
			if (passByValue)
			{
				/* pass by value data is stored in the least
				 * significant bits of a Datum. */
#ifdef WORDS_BIGENDIAN
				data = ((char *)(&arg)) + SIZEOF_DATUM - dataLen;
#else
				data = ((char *)(&arg));
#endif
			}
			else
			{
				data = DatumGetPointer(arg);
			}
		}

		inputStream = SQLInputFromChunk_create(data, dataLen,
			isJavaBasedScalar);
		result = pljava_Function_udtReadInvoke(
			self->readSQL, inputStream, self->sqlTypeName);
		SQLInputFromChunk_close(inputStream);
	}
	return result;
}

static jobject coerceTupleDatum(UDT udt, Datum arg)
{
	jobject result;
	jobject inputStream =
		pljava_SQLInputFromTuple_create(DatumGetHeapTupleHeader(arg));
	result = pljava_Function_udtReadInvoke(
		udt->readSQL, inputStream, udt->sqlTypeName);
	JNI_deleteLocalRef(inputStream);
	return result;
}

static Datum coerceScalarObject(UDT self, jobject value)
{
	Datum result;
	int32 dataLen = Type_getLength((Type)self);
	bool isJavaBasedScalar = 0 != self->parse;
	if(dataLen == -2)
	{
		/*
		 * ASSUMPTION 1 is in play here: the toString method, specified to
		 * produce the human-used external representation, is being called here
		 * to produce this UDT's internal representation.
		 */
		jstring jstr = pljava_Function_udtToStringInvoke(self->toString, value);
		char* tmp = String_createNTS(jstr);
		result = CStringGetDatum(tmp);
		JNI_deleteLocalRef(jstr);
	}
	else
	{
		jobject outputStream;
		StringInfoData buffer;
		bool passByValue = Type_isByValue((Type)self);

		MemoryContext currCtx = Invocation_switchToUpperContext();
		initStringInfo(&buffer);
		MemoryContextSwitchTo(currCtx); /* buffer remembers its context */

		if(dataLen < 0)
			/*
			 * Reserve space for an int32 at the beginning. We are building
			 * a varlena
			 */
			appendBinaryStringInfo(&buffer, (char*)&dataLen, sizeof(int32));
		else
			enlargeStringInfo(&buffer, dataLen);

		outputStream = SQLOutputToChunk_create(&buffer, isJavaBasedScalar);
		pljava_Function_udtWriteInvoke(self->writeSQL, value, outputStream);
		SQLOutputToChunk_close(outputStream);

		if(dataLen < 0)
		{
			/* Assign the correct length.
			 */
#if PG_VERSION_NUM < 80300
			VARATT_SIZEP(buffer.data) = buffer.len;
#else
			SET_VARSIZE(buffer.data, buffer.len);
#endif
		}
		else if(dataLen != buffer.len)
		{
			ereport(ERROR, (
				errcode(ERRCODE_CANNOT_COERCE),
				errmsg("UDT for Oid %d produced image with incorrect size. Expected %d, was %d",
					Type_getOid((Type)self), dataLen, buffer.len)));
		}
		if (passByValue) {
			memset(&result, 0, SIZEOF_DATUM);
			/* pass by value data is stored in the least
			 * significant bits of a Datum. */
#ifdef WORDS_BIGENDIAN
			memcpy(&result + SIZEOF_DATUM - dataLen, buffer.data, dataLen);
#else
			memcpy(&result, buffer.data, dataLen);
#endif
		} else {
			result = PointerGetDatum(buffer.data);
		}

	}
	return result;
}

static Datum coerceTupleObject(UDT self, jobject value)
{
	Datum result = 0;
	if(value != 0)
	{
		HeapTuple tuple;
		Oid typeId = ((Type)self)->typeId;
		TupleDesc tupleDesc = lookup_rowtype_tupdesc_noerror(typeId, -1, true);
		jobject sqlOutput = SQLOutputToTuple_create(tupleDesc);
		ReleaseTupleDesc(tupleDesc);
		pljava_Function_udtWriteInvoke(self->writeSQL, value, sqlOutput);
		tuple = SQLOutputToTuple_getTuple(sqlOutput);
		if(tuple != 0)
			result = HeapTupleGetDatum(tuple);
	}
	return result;
}

jvalue _UDT_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	UDT    udt = (UDT)self;
	if(UDT_isScalar(udt))
		result.l = coerceScalarDatum(udt, arg);
	else
		result.l = coerceTupleDatum(udt, arg);
	return result;
}

Datum _UDT_coerceObject(Type self, jobject value)
{
	Datum result;
	UDT udt = (UDT)self;
	if(UDT_isScalar(udt))
		result = coerceScalarObject(udt, value);
	else
		result = coerceTupleObject(udt, value);
	return result;
}

/*
 * Fail openly rather than mysteriously if an INPUT or RECEIVE function is
 * called with a non-default typmod. It seems possible that, aside from COPY
 * operations, that doesn't happen much, and values are usually produced as if
 * with no typmod, then fed through a typmod application cast. So even
 * without this implemented, there may be usable typmod capability except for
 * COPY.
 */
static void noTypmodYet(UDT udt, PG_FUNCTION_ARGS)
{
	Oid toid;
	int mod;

	if ( 3 > PG_NARGS() )
		return;

	toid = PG_GETARG_OID(1);
	mod  = PG_GETARG_INT32(2);

	if ( -1 != mod )
		ereport(ERROR, (
			errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
			errmsg(
				"PL/Java UDT with non-default type modifier not yet supported")
			));

	if ( Type_getOid((Type)udt) != toid )
		ereport(ERROR, (
			errcode(ERRCODE_INTERNAL_ERROR),
			errmsg("Unexpected type Oid %d passed to PL/Java UDT", toid)));
}

Datum UDT_input(UDT udt, PG_FUNCTION_ARGS)
{
	jstring jstr;
	jobject obj;
	char* txt;

	if(!UDT_isScalar(udt))
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("UDT with Oid %d is not scalar", Type_getOid((Type)udt))));

	noTypmodYet(udt, fcinfo);

	txt = PG_GETARG_CSTRING(0);

	if(Type_getLength((Type)udt) == -2)
	{
		/*
		 * ASSUMPTION 1 is in play here. UDT_input is passed a cstring holding
		 * the human-used external representation, and, just because this UDT is
		 * also declared with length -2, that external representation is being
		 * copied directly here as the internal representation, without even
		 * invoking any of the UDT's code.
		 */
		if(txt != 0)
			txt = pstrdup(txt);
		PG_RETURN_CSTRING(txt);
	}
	/*
	 * Length != -2 so we do the expected: call parse to construct a Java object
	 * from the external representation, then _UDT_coerceObject to get the
	 * internal representation from the object.
	 */
	jstr = String_createJavaStringFromNTS(txt);
	obj  = pljava_Function_udtParseInvoke(udt->parse, jstr, udt->sqlTypeName);
	JNI_deleteLocalRef(jstr);

	return _UDT_coerceObject((Type)udt, obj);
}

Datum UDT_output(UDT udt, PG_FUNCTION_ARGS)
{
	char* txt;

	if(!UDT_isScalar(udt))
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("UDT with Oid %d is not scalar", Type_getOid((Type)udt))));

	if(Type_getLength((Type)udt) == -2)
	{
		txt = PG_GETARG_CSTRING(0);
		if(txt != 0)
		/*
		 * ASSUMPTION 1 is in play here. UDT_output returns a cstring to contain
		 * the human-used external representation, and, just because this UDT's
		 * internal representation is also declared with length -2, the internal
		 * is being copied directly as the external representation, without even
		 * invoking any of the UDT's code.
		 */
			txt = pstrdup(txt);
	}
	else
	{
		/*
		 * Length != -2 so we do the expected: call _UDT_coerceDatum to
		 * construct a Java object from the internal representation, then
		 * toString to get the external representation from the object.
		 */
		jobject value = _UDT_coerceDatum((Type)udt, PG_GETARG_DATUM(0)).l;
		jstring jstr  = pljava_Function_udtToStringInvoke(udt->toString, value);

		MemoryContext currCtx = Invocation_switchToUpperContext();
		txt = String_createNTS(jstr);
		MemoryContextSwitchTo(currCtx);

		JNI_deleteLocalRef(value);
		JNI_deleteLocalRef(jstr);
	}
	PG_RETURN_CSTRING(txt);
}

Datum UDT_receive(UDT udt, PG_FUNCTION_ARGS)
{
	StringInfo buf;
	char* tmp;
	int32 dataLen = Type_getLength((Type)udt);

	if(!UDT_isScalar(udt))
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("UDT with Oid %d is not scalar", Type_getOid((Type)udt))));

	noTypmodYet(udt, fcinfo);

	/*
	 * ASSUMPTION 2 is in play here. The external byte stream is being received
	 * and directly stored as the internal representation of the type.
	 */
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
	int32 dataLen = Type_getLength((Type)udt);

	if(!UDT_isScalar(udt))
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("UDT with Oid %d is not scalar", Type_getOid((Type)udt))));

	/*
	 * ASSUMPTION 2 is in play here. The internal representation of the type
	 * is being transmitted directly as the external byte stream.
	 */
	if(dataLen == -1)
		return byteasend(fcinfo);

	if(dataLen == -2)
		return unknownsend(fcinfo);

	pq_begintypsend(&buf);
	appendBinaryStringInfo(&buf, PG_GETARG_POINTER(0), dataLen);
    PG_RETURN_BYTEA_P(pq_endtypsend(&buf));
}

bool UDT_isScalar(UDT udt)
{
	return ! udt->hasTupleDesc;
}

/* Make this datatype available to the postgres system. The four ...MH arguments
 * are passed to JNI_deleteLocalRef after being saved as global references.
 */
UDT UDT_registerUDT(jclass clazz, Oid typeId, Form_pg_type pgType,
	bool hasTupleDesc, bool isJavaBasedScalar, jobject parseMH, jobject readMH,
	jobject writeMH, jobject toStringMH)
{
	jstring jcn;
	MemoryContext currCtx;
	HeapTuple nspTup;
	Form_pg_namespace nspStruct;
	TypeClass udtClass;
	UDT udt;
	Size signatureLen;
	jstring sqlTypeName;
	char* className;
	char* classSignature;
	char* sp;
	const char* cp;
	const char* tp;
	char c;

	Type existing = Type_fromOidCache(typeId);
	if(existing != 0)
	{
		if(existing->typeClass->coerceDatum != _UDT_coerceDatum)
		{
			ereport(ERROR, (
				errcode(ERRCODE_CANNOT_COERCE),
				errmsg("Attempt to register UDT with Oid %d failed. Oid appoints a non UDT type", typeId)));
		}
		JNI_deleteLocalRef(parseMH);
		JNI_deleteLocalRef(readMH);
		JNI_deleteLocalRef(writeMH);
		JNI_deleteLocalRef(toStringMH);
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
	jcn = JNI_callObjectMethod(clazz, Class_getName);
	currCtx = MemoryContextSwitchTo(TopMemoryContext);
	className = String_createNTS(jcn);
	JNI_deleteLocalRef(jcn);

	signatureLen = strlen(className) + 2;
	classSignature = palloc(signatureLen + 1);
	MemoryContextSwitchTo(currCtx);

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
	udtClass->javaClass      = JNI_newGlobalRef(clazz);
	udtClass->canReplaceType = _Type_canReplaceType;
	udtClass->coerceDatum    = _UDT_coerceDatum;
	udtClass->coerceObject   = _UDT_coerceObject;

	udt = (UDT)TypeClass_allocInstance2(udtClass, typeId, pgType);
	udt->sqlTypeName = JNI_newGlobalRef(sqlTypeName);
	JNI_deleteLocalRef(sqlTypeName);

	if(isJavaBasedScalar)
	{
		/* A scalar mapping that is implemented in Java will have the static method:
		 * 
		 *   T parse(String stringRep, String sqlTypeName);
		 * 
		 * and a matching:
		 * 
		 *   String toString();
		 * 
		 * instance method. A pure mapping (i.e. no Java I/O methods) will not
		 * have this.
		 */
	
		/* The parse method is a static method on the class with the signature
		 * (Ljava/lang/String;Ljava/lang/String;)<classSignature>
		 */
		if ( NULL == parseMH  ||  NULL == toStringMH )
			elog(ERROR,
				"PL/Java UDT with oid %u registered without both i/o handles",
				typeId);
		udt->parse = JNI_newGlobalRef(parseMH);
		udt->toString = JNI_newGlobalRef(toStringMH);
		JNI_deleteLocalRef(parseMH);
		JNI_deleteLocalRef(toStringMH);
	}
	else
	{
		udt->parse = NULL;
		udt->toString = NULL;
	}

	udt->hasTupleDesc = hasTupleDesc;
	if ( NULL == readMH  ||  NULL == writeMH )
		elog(ERROR,
			"PL/Java UDT with oid %u registered without both r/w handles",
			typeId);
	udt->readSQL = JNI_newGlobalRef(readMH);
	udt->writeSQL = JNI_newGlobalRef(writeMH);
	JNI_deleteLocalRef(readMH);
	JNI_deleteLocalRef(writeMH);
	Type_registerType(className, (Type)udt);
	return udt;
}
