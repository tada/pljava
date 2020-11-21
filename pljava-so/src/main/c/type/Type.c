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
 */
#include <postgres.h>
#include <fmgr.h>
#include <funcapi.h>
#include <parser/parse_coerce.h>
#include <utils/builtins.h>
#include <utils/typcache.h>
#include <utils/lsyscache.h>

#include "pljava/type/String_priv.h"
#include "pljava/type/Array.h"
#include "pljava/type/Coerce.h"
#include "pljava/type/Composite.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Oid.h"
#include "pljava/type/UDT.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/HashMap.h"
#include "pljava/SPI.h"

#if PG_VERSION_NUM < 80300
typedef enum CoercionPathType
{
	COERCION_PATH_NONE, 		/* failed to find any coercion pathway */
	COERCION_PATH_FUNC, 		/* apply the specified coercion function */
	COERCION_PATH_RELABELTYPE,  /* binary-compatible cast, no function */
	COERCION_PATH_ARRAYCOERCE,  /* need an ArrayCoerceExpr node */
	COERCION_PATH_COERCEVIAIO	/* need a CoerceViaIO node */
} CoercionPathType;

static CoercionPathType fcp(Oid targetTypeId, Oid sourceTypeId,
							CoercionContext ccontext, Oid *funcid);
static CoercionPathType fcp(Oid targetTypeId, Oid sourceTypeId,
							CoercionContext ccontext, Oid *funcid)
{
	if ( find_coercion_pathway(targetTypeId, sourceTypeId, ccontext, funcid) )
		return *funcid != InvalidOid ?
			COERCION_PATH_FUNC : COERCION_PATH_RELABELTYPE;
	else
		return COERCION_PATH_NONE;
}
#define find_coercion_pathway fcp
#endif

#if PG_VERSION_NUM < 90500
#define DomainHasConstraints(x) true
#endif

#if PG_VERSION_NUM < 110000
static Oid BOOLARRAYOID;
static Oid CHARARRAYOID;
static Oid FLOAT8ARRAYOID;
static Oid INT8ARRAYOID;
#if PG_VERSION_NUM < 80400
static Oid INT2ARRAYOID;
#endif
#endif

static HashMap s_typeByOid;
static HashMap s_obtainerByOid;
static HashMap s_obtainerByJavaName;

static jclass s_Map_class;
static jmethodID s_Map_get;

typedef struct CacheEntryData
{
	Type			type;
	TypeObtainer	obtainer;
	Oid				typeId;
} CacheEntryData;

typedef CacheEntryData* CacheEntry;

static jclass s_Iterator_class;
static jmethodID s_Iterator_hasNext;
static jmethodID s_Iterator_next;

static jclass s_TypeBridge_Holder_class;
static jmethodID s_TypeBridge_Holder_className;
static jmethodID s_TypeBridge_Holder_defaultOid;
static jmethodID s_TypeBridge_Holder_payload;

/*
 * Structure used to retain state of set-returning functions using the
 * SFRM_ValuePerCall protocol (the only one PL/Java currently supports). In that
 * protocol, PostgreSQL will make repeated calls arriving at Type_invokeSRF
 * below, which returns one result row on each call (and then a no-more-results
 * result). This struct holds necessary context through the sequence of calls.
 *
 * If PostgreSQL is satisfied before the whole set has been returned, the
 * _endOfSetCB below will be invoked to clean up the work in progress, and also
 * needs this stashed information.
 */
typedef struct
{
	Type          elemType;
	jobject       rowProducer;
	jobject       rowCollector;
	/*
	 * Invocation instance, if any, the Java counterpart to currentInvocation
	 * the C struct. There isn't one unless it gets asked for, then if it is,
	 * it's saved here, so even though the C currentInvocation really is new on
	 * each entry from PG, Java will see one Invocation instance throughout the
	 * sequence of calls.
	 */
	jobject       invocation;
	/*
	 * Two pieces of state from Invocation.c's management of SPI connection,
	 * effectively keeping one such connection alive through the sequence of
	 * calls. I could easily be led to question the advisability of even doing
	 * that, but it has a long history in PL/Java, so changing it might call for
	 * some careful analysis.
	 */
	MemoryContext spiContext;
	bool          hasConnected;
} CallContextData;

/*
 * Called during evaluation of a set-returning function, at various points after
 * calls into Java code could have instantiated an Invocation, or connected SPI.
 * Does not stash elemType, rowProducer, or rowCollector; those are all
 * unconditionally set in the first-call initialization, and spiContext to zero.
 */
static void stashCallContext(CallContextData *ctxData)
{
	bool wasConnected = ctxData->hasConnected;

	ctxData->hasConnected  = currentInvocation->hasConnected;

	ctxData->invocation    = currentInvocation->invocation;

	if ( wasConnected )
		return;

	/*
	 * If SPI has been connected for the first time, capture the memory context
	 * it imposed. Curiously, this is not used again except in _closeIteration.
	 */
	if(ctxData->hasConnected)
		ctxData->spiContext = CurrentMemoryContext;
}

/*
 * Called either at normal completion of a set-returning function, or by the
 * _endOfSetCB if PostgreSQL doesn't want all the results.
 */
static void _closeIteration(CallContextData* ctxData)
{
	jobject dummy;
	currentInvocation->hasConnected = ctxData->hasConnected;
	currentInvocation->invocation   = ctxData->invocation;

	pljava_Function_vpcInvoke(ctxData->rowProducer, NULL, 0, JNI_TRUE, &dummy);
	JNI_deleteGlobalRef(ctxData->rowProducer);
	if(ctxData->rowCollector != 0)
		JNI_deleteGlobalRef(ctxData->rowCollector);

	if(ctxData->hasConnected && ctxData->spiContext != 0)
	{
		/*
		 * SPI was connected. We will (1) switch back to the memory context that
		 * was imposed by SPI_connect, then (2) disconnect. SPI_finish will have
		 * switched back to whatever memory context was current when SPI_connect
		 * was called, and that context had better still be valid. It might be
		 * the executor's multi_call_memory_ctx, if the SPI_connect happened
		 * during initialization of the rowProducer or rowCollector, or the
		 * executor's per-row context, if it happened later. Both of those are
		 * still valid at this point. The final step (3) is to switch back to
		 * the context we had before (1) and (2) happened.
		 */
		MemoryContext currCtx = MemoryContextSwitchTo(ctxData->spiContext);
		Invocation_assertDisconnect();
		MemoryContextSwitchTo(currCtx);
	}
}

/*
 * Called by PostgreSQL if abandoning the collection of set-returning-function
 * results early.
 */
static void _endOfSetCB(Datum arg)
{
	Invocation topCall;
	bool saveInExprCtxCB;
	CallContextData* ctxData = (CallContextData*)DatumGetPointer(arg);
	if(currentInvocation == 0)
		Invocation_pushInvocation(&topCall);

	saveInExprCtxCB = currentInvocation->inExprContextCB;
	currentInvocation->inExprContextCB = true;
	_closeIteration(ctxData);
	currentInvocation->inExprContextCB = saveInExprCtxCB;
}

static Type _getCoerce(Type self, Type other, Oid fromOid, Oid toOid,
	HashMap *map, Type builder(Type, Type, Oid));

Type Type_getCoerceIn(Type self, Type other)
{
	elog(DEBUG2, "Type_getCoerceIn(%d,%d)", self->typeId, other->typeId);
	return _getCoerce(self, other, other->typeId, self->typeId,
		&(self->inCoercions), Coerce_createIn);
}


Type Type_getCoerceOut(Type self, Type other)
{
	elog(DEBUG2, "Type_getCoerceOut(%d,%d)", self->typeId, other->typeId);
	return _getCoerce(self, other, self->typeId, other->typeId,
		&(self->outCoercions), Coerce_createOut);
}

static Type _getCoerce(Type self, Type other, Oid fromOid, Oid toOid,
	HashMap *map, Type builder(Type, Type, Oid))
{
	Oid  funcId;
	Type coercer;
	CoercionPathType cpt;

	if(*map != 0)
	{
		coercer = HashMap_getByOid(*map, other->typeId);
		if(coercer != 0)
			return coercer;
	}

	cpt = find_coercion_pathway(toOid, fromOid, COERCION_EXPLICIT, &funcId);
	switch ( cpt )
	{
	case COERCION_PATH_NONE:
		elog(ERROR, "no conversion function from (regtype) %d to %d",
			 fromOid, toOid);
		pg_unreachable(); /*elog(ERROR is already so marked; what's with gcc?*/
	case COERCION_PATH_RELABELTYPE:
		/*
		 * Binary compatible type. No need for a special coercer.
		 * Unless ... it's a domain ....
		 */
		if ( ! IsBinaryCoercible(fromOid, toOid) && DomainHasConstraints(toOid))
			elog(WARNING, "disregarding domain constraints of (regtype) %d",
				 toOid);
		return self;
	case COERCION_PATH_COERCEVIAIO:
		elog(ERROR, "COERCEVIAIO not implemented from (regtype) %d to %d",
			 fromOid, toOid);
		pg_unreachable();
	case COERCION_PATH_ARRAYCOERCE:
		elog(ERROR, "ARRAYCOERCE not implemented from (regtype) %d to %d",
			 fromOid, toOid);
		pg_unreachable();
	case COERCION_PATH_FUNC:
		break;
	}

	if(*map == 0)
		*map = HashMap_create(7, GetMemoryChunkContext(self));

	coercer = builder(self, other, funcId);
	HashMap_putByOid(*map, other->typeId, coercer);
	return coercer;
}

bool Type_canReplaceType(Type self, Type other)
{
	return self->typeClass->canReplaceType(self, other);
}

bool Type_isDynamic(Type self)
{
	return self->typeClass->dynamic;
}

bool Type_isOutParameter(Type self)
{
	return self->typeClass->outParameter;
}

jvalue Type_coerceDatum(Type self, Datum value)
{
	return self->typeClass->coerceDatum(self, value);
}

jvalue Type_coerceDatumAs(Type self, Datum value, jclass rqcls)
{
	jstring rqcname;
	char *rqcname0;
	Type rqtype;

	if ( NULL == rqcls  ||  Type_getJavaClass(self) == rqcls )
		return Type_coerceDatum(self, value);

	rqcname = JNI_callObjectMethod(rqcls, Class_getCanonicalName);
	rqcname0 = String_createNTS(rqcname);
	JNI_deleteLocalRef(rqcname);
	rqtype = Type_fromJavaType(self->typeId, rqcname0);
	pfree(rqcname0);
	if ( Type_canReplaceType(rqtype, self) )
		return Type_coerceDatum(rqtype, value);
	return Type_coerceDatum(self, value);
}

Datum Type_coerceObject(Type self, jobject object)
{
	return self->typeClass->coerceObject(self, object);
}

Datum Type_coerceObjectBridged(Type self, jobject object)
{
	jstring rqcname;
	char *rqcname0;
	Type rqtype;

	if ( JNI_FALSE == JNI_isInstanceOf(object, s_TypeBridge_Holder_class) )
		return Type_coerceObject(self, object);

	rqcname = JNI_callObjectMethod(object, s_TypeBridge_Holder_className);
	rqcname0 = String_createNTS(rqcname);
	JNI_deleteLocalRef(rqcname);
	rqtype = Type_fromJavaType(self->typeId, rqcname0);
	pfree(rqcname0);
	if ( ! Type_canReplaceType(rqtype, self) )
	{
		/*
		 * Ignore the TypeBridge in this one oddball case that results from the
		 * existence of two Types both mapping Java's byte[].
		 */
		if ( BYTEAOID == self->typeId  &&  CHARARRAYOID == rqtype->typeId )
			rqtype = self;
		else
			elog(ERROR, "type bridge failure");
	}
	object = JNI_callObjectMethod(object, s_TypeBridge_Holder_payload);
	return Type_coerceObject(rqtype, object);
}

char Type_getAlign(Type self)
{
	return self->align;
}

TypeClass Type_getClass(Type self)
{
	return self->typeClass;
}

int16 Type_getLength(Type self)
{
	return self->length;
}

bool Type_isByValue(Type self)
{
	return self->byValue;
}

jclass Type_getJavaClass(Type self)
{
	TypeClass typeClass = self->typeClass;
	if(typeClass->javaClass == 0)
	{
		jclass cls;
		const char* cp = typeClass->JNISignature;
		if(cp == 0 || *cp == 0)
			ereport(ERROR, (
				errmsg("Type '%s' has no corresponding java class",
					PgObjectClass_getName((PgObjectClass)typeClass))));

		if(*cp == 'L')
		{
			/* L<object name>; should be just <object name>. Strange
			 * since the L and ; are retained if its an array.
			 */
			size_t len = strlen(cp) - 2;
			char* bp = palloc(len + 1);
			memcpy(bp, cp + 1, len);
			bp[len] = 0;
			cls = PgObject_getJavaClass(bp);
			pfree(bp);
		}
		else
			cls = PgObject_getJavaClass(cp);

		typeClass->javaClass = JNI_newGlobalRef(cls);
		JNI_deleteLocalRef(cls);
	}
	return typeClass->javaClass;
}

const char* Type_getJavaTypeName(Type self)
{
	return self->typeClass->javaTypeName;
}

const char* Type_getJNISignature(Type self)
{
	return self->typeClass->getJNISignature(self);
}

Type Type_getArrayType(Type self, Oid arrayTypeId)
{
	Type arrayType = self->arrayType;
	if(arrayType != 0)
	{
		if(arrayType->typeId == arrayTypeId)
			return arrayType;

		if(arrayType->typeId == InvalidOid)
		{
			arrayType->typeId = arrayTypeId;
			return arrayType;
		}
	}
	arrayType = self->typeClass->createArrayType(self, arrayTypeId);
	self->arrayType = arrayType;
	return arrayType;
}

Type Type_getElementType(Type self)
{
	return self->elementType;
}

Type Type_getObjectType(Type self)
{
	return self->objectType;
}

Type Type_getRealType(Type self, Oid realTypeId, jobject typeMap)
{
	return self->typeClass->getRealType(self, realTypeId, typeMap);
}

Oid Type_getOid(Type self)
{
	return self->typeId;
}

TupleDesc Type_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	return self->typeClass->getTupleDesc(self, fcinfo);
}

Datum Type_invoke(Type self, Function fn, PG_FUNCTION_ARGS)
{
	return self->typeClass->invoke(self, fn, fcinfo);
}

Datum Type_invokeSRF(Type self, Function fn, PG_FUNCTION_ARGS)
{
	jobject row;
	CallContextData* ctxData;
	FuncCallContext* context;
	MemoryContext currCtx;

	/* stuff done only on the first call of the function
	 */
	if(SRF_IS_FIRSTCALL())
	{
		jobject tmp;

		/* create a function context for cross-call persistence
		 */
		context = SRF_FIRSTCALL_INIT();

		/*
		 * Before creating the rowProducer (and rowCollector, if applicable),
		 * switch to the SRF_FIRSTCALL_INIT-created multi_call_memory_ctx that
		 * is not reset between calls. The motivation seems clear enough (allow
		 * the first-call initialization to allocate things in a context that
		 * will last through the sequence), though it is not clear whether
		 * anything in existing PL/Java code in fact does so (other than our
		 * allocation of ctxData below, which could perhaps just be a direct
		 * MemoryContextAllocZero).
		 */
		currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);

		/* Call the declared Java function. It returns an instance
		 * that can produce the rows.
		 */
		tmp = pljava_Function_refInvoke(fn);
		if(tmp == 0)
		{
			Invocation_assertDisconnect();
			MemoryContextSwitchTo(currCtx);
			fcinfo->isnull = true;
			SRF_RETURN_DONE(context);
		}

		ctxData = (CallContextData*)palloc0(sizeof(CallContextData));
		context->user_fctx = ctxData;

		ctxData->elemType = self;
		ctxData->rowProducer = JNI_newGlobalRef(tmp);
		JNI_deleteLocalRef(tmp);

		/* Some row producers will need a writable result set in order
		 * to produce the row. If one is needed, it's created here.
		 */
		tmp = Type_getSRFCollector(self, fcinfo);
		if(tmp != 0)
		{
			ctxData->rowCollector = JNI_newGlobalRef(tmp);
			JNI_deleteLocalRef(tmp);
		}		

		stashCallContext(ctxData);

		/* Register callback to be called when the function ends
		 */
		RegisterExprContextCallback(
			((ReturnSetInfo*)fcinfo->resultinfo)->econtext,
			_endOfSetCB, PointerGetDatum(ctxData));

		/*
		 * Switch back to the context on entry, which by caller arrangement is
		 * one that gets reset between calls. Thus here at the conclusion of the
		 * first-call initialization, the context invariant below is satisfied.
		 */
		MemoryContextSwitchTo(currCtx);
	}

	/*
	 * Invariant: whether this is the first call and the SRF_IS_FIRSTCALL block
	 * above just completed, or this is a subsequent call, at this point, the
	 * memory context is the per-row one supplied by the executor (which gets
	 * reset between calls).
	 */

	context = SRF_PERCALL_SETUP();
	ctxData = (CallContextData*)context->user_fctx;
	currCtx = CurrentMemoryContext; /* save executor's per-row context */
	currentInvocation->hasConnected = ctxData->hasConnected;
	currentInvocation->invocation   = ctxData->invocation;

	if(JNI_TRUE == pljava_Function_vpcInvoke(
		ctxData->rowProducer, ctxData->rowCollector, (jlong)context->call_cntr,
		JNI_FALSE, &row))
	{
		Datum result = Type_datumFromSRF(self, row, ctxData->rowCollector);
		JNI_deleteLocalRef(row);
		stashCallContext(ctxData);
		currentInvocation->hasConnected = false;
		currentInvocation->invocation   = 0;
		MemoryContextSwitchTo(currCtx);
		SRF_RETURN_NEXT(context, result);
	}

	stashCallContext(ctxData);
	currentInvocation->hasConnected = false;
	currentInvocation->invocation   = 0;
	MemoryContextSwitchTo(currCtx);

	/* Unregister this callback and call it manually. We do this because
	 * otherwise it will be called when the backend is in progress of
	 * cleaning up Portals. If we close cursors (i.e. drop portals) in
	 * the close, then that mechanism fails since attempts are made to
	 * delete portals more then once.
	 */
	UnregisterExprContextCallback(
		((ReturnSetInfo*)fcinfo->resultinfo)->econtext,
		_endOfSetCB,
		PointerGetDatum(ctxData));

	_closeIteration(ctxData);

	/* This is the end of the set.
	 */
	SRF_RETURN_DONE(context);
}

bool Type_isPrimitive(Type self)
{
	return self->objectType != 0;
}

Type Type_fromJavaType(Oid typeId, const char* javaTypeName)
{
	/*
	 * Do an initial lookup with InvalidOid as the oid part of the key. Multiple
	 * entries for the same Java name and distinct oids are not anticipated
	 * except for arrays.
	 */
	CacheEntry ce = (CacheEntry)HashMap_getByStringOid(
		s_obtainerByJavaName, javaTypeName, InvalidOid);

	/*
	 * If no entry was found using InvalidOid and a valid typeId is provided
	 * and the wanted Java type is an array, repeat the lookup using the typeId.
	 */
	if ( NULL == ce  &&  InvalidOid != typeId
			&&  NULL != strchr(javaTypeName, ']') )
		ce = (CacheEntry)HashMap_getByStringOid(
			s_obtainerByJavaName, javaTypeName, typeId);

	if(ce == 0)
	{
		size_t jtlen = strlen(javaTypeName) - 2;
		if(jtlen > 0 && strcmp("[]", javaTypeName + jtlen) == 0)
		{
			Type type;
			char* elemName = palloc(jtlen+1);
			memcpy(elemName, javaTypeName, jtlen);
			elemName[jtlen] = 0;
			type = Type_getArrayType(Type_fromJavaType(InvalidOid, elemName), typeId);
			pfree(elemName);
			return type;
		}
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("No java type mapping installed for \"%s\"", javaTypeName)));
	}

	return ce->type == 0
		? ce->obtainer(typeId == InvalidOid ? ce->typeId : typeId)
		: ce->type;
}

void Type_cacheByOid(Oid typeId, Type type)
{
	HashMap_putByOid(s_typeByOid, typeId, type);
}

Type Type_fromOidCache(Oid typeId)
{
	return (Type)HashMap_getByOid(s_typeByOid, typeId);
}

Type Type_fromOid(Oid typeId, jobject typeMap)
{
	CacheEntry   ce;
	HeapTuple    typeTup;
	Form_pg_type typeStruct;
	Type         type = Type_fromOidCache(typeId);

	if(type != 0)
		return type;

	typeTup    = PgObject_getValidTuple(TYPEOID, typeId, "type");
	typeStruct = (Form_pg_type)GETSTRUCT(typeTup);

	if(typeStruct->typelem != 0 && typeStruct->typlen == -1)
	{
		type = Type_getArrayType(Type_fromOid(typeStruct->typelem, typeMap), typeId);
		goto finally;
	}

	/* For some reason, the anyarray is *not* an array with anyelement as the
	 * element type. We'd like to see it that way though.
	 */
	if(typeId == ANYARRAYOID)
	{
		type = Type_getArrayType(Type_fromOid(ANYELEMENTOID, typeMap), typeId);
		goto finally;
	}

	if(typeStruct->typbasetype != 0)
	{
		/* Domain type, recurse using the base type (which in turn may
		 * also be a domain)
		 */
		type = Type_fromOid(typeStruct->typbasetype, typeMap);
		goto finally;
	}

	if(typeMap != 0)
	{
		jobject joid      = Oid_create(typeId);
		jclass  typeClass =
			(jclass)JNI_callObjectMethod(typeMap, s_Map_get, joid);

		JNI_deleteLocalRef(joid);
		if(typeClass != 0)
		{
			/*
			 * We have found a MappedUDT. It doesn't have SQL-declared I/O
			 * functions, so we need to look up only the read and write handles,
			 * and there will be no PLPrincipal to associate them with,
			 * indicated by passing NULL as the language name.
			 */
			jobject readMH =
				pljava_Function_udtReadHandle(typeClass, NULL, true);
			jobject writeMH =
				pljava_Function_udtWriteHandle(typeClass, NULL, true);
			TupleDesc tupleDesc =
				lookup_rowtype_tupdesc_noerror(typeId, -1, true);
			bool hasTupleDesc = NULL != tupleDesc;
			if ( hasTupleDesc )
				ReleaseTupleDesc(tupleDesc);
			type = (Type)UDT_registerUDT(
				typeClass, typeId, typeStruct, hasTupleDesc, false,
				NULL, readMH, writeMH, NULL);
			/*
			 * UDT_registerUDT calls JNI_deleteLocalRef on readMH and writeMH.
			 */
			JNI_deleteLocalRef(typeClass);
			goto finally;
		}
	}

	/* Composite and record types will not have a TypeObtainer registered
	 */
	if(typeStruct->typtype == 'c'
		|| (typeStruct->typtype == 'p' && typeId == RECORDOID))
	{
		type = Composite_obtain(typeId);
		goto finally;
	}

	ce = (CacheEntry)HashMap_getByOid(s_obtainerByOid, typeId);
	if(ce == 0)
	{
		/*
		 * Perhaps we have found a BaseUDT. If so, this check will register and
		 * return it.
		 */
		type = Function_checkTypeBaseUDT(typeId, typeStruct);
		if ( 0 != type )
			goto finally;
		/*
		 * Default to String and standard textin/textout coercion.
		 * Note: if the AS spec includes a Java signature, and the corresponding
		 * Java type is not String, that will trigger a call to
		 * Type_fromJavaType to see if a mapping is registered that way. If not,
		 * *that* function reports 'No java type mapping installed for "%s"'.
		 */
		type = String_obtain(typeId);
	}
	else
	{
		type = ce->type;
		if(type == 0)
			type = ce->obtainer(typeId);
	}

finally:
	ReleaseSysCache(typeTup);
	Type_cacheByOid(typeId, type);
	return type;
}

Type Type_objectTypeFromOid(Oid typeId, jobject typeMap)
{
	Type type = Type_fromOid(typeId, typeMap);
	Type objectType = type->objectType;
	return (objectType == 0) ? type : objectType;
}

bool _Type_canReplaceType(Type self, Type other)
{
	return self->typeClass == other->typeClass;
}

Datum _Type_invoke(Type self, Function fn, PG_FUNCTION_ARGS)
{
	MemoryContext currCtx;
	Datum ret;
	jobject value = pljava_Function_refInvoke(fn);
	if(value == 0)
	{
		fcinfo->isnull = true;
		return 0;
	}

	/* The return value cannot be created in the current context since it
	 * goes out of scope when SPI_finish is called.
	 */
	currCtx = Invocation_switchToUpperContext();
	ret = self->typeClass->coerceObject(self, value);
	MemoryContextSwitchTo(currCtx);
	JNI_deleteLocalRef(value);
	return ret;
}

static Type _Type_createArrayType(Type self, Oid arrayTypeId)
{
	return Array_fromOid(arrayTypeId, self);
}

static jobject _Type_getSRFCollector(Type self, PG_FUNCTION_ARGS)
{
	return 0;
}

static Datum _Type_datumFromSRF(Type self, jobject row, jobject rowCollector)
{
	return Type_coerceObject(self, row);
}

jobject Type_getSRFCollector(Type self, PG_FUNCTION_ARGS)
{
	return self->typeClass->getSRFCollector(self, fcinfo);
}

Datum Type_datumFromSRF(Type self, jobject row, jobject rowCollector)
{
	return self->typeClass->datumFromSRF(self, row, rowCollector);
}

static Type _Type_getRealType(Type self, Oid realId, jobject typeMap)
{
	return self;
}

static const char* _Type_getJNISignature(Type self)
{
	return self->typeClass->JNISignature;
}

TupleDesc _Type_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	ereport(ERROR,
		(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
		 errmsg("Type is not associated with a record")));
	return 0;	/* Keep compiler happy */
}

static void addTypeBridge(jclass c, jmethodID m, char const *cName, Oid oid)
{
	jstring jcn = String_createJavaStringFromNTS(cName);
	JNI_callStaticObjectMethodLocked(c, m, jcn, oid);
	JNI_deleteLocalRef(jcn);
}

static void initializeTypeBridges()
{
	jclass cls;
	jmethodID ofClass;
	jmethodID ofInterface;

	cls = PgObject_getJavaClass("org/postgresql/pljava/jdbc/TypeBridge");
	ofClass = PgObject_getStaticJavaMethod(cls, "ofClass",
		"(Ljava/lang/String;I)Lorg/postgresql/pljava/jdbc/TypeBridge;");
	ofInterface = PgObject_getStaticJavaMethod(cls, "ofInterface",
		"(Ljava/lang/String;I)Lorg/postgresql/pljava/jdbc/TypeBridge;");

	addTypeBridge(cls, ofClass, "java.time.LocalDate", DATEOID);
	addTypeBridge(cls, ofClass, "java.time.LocalDateTime", TIMESTAMPOID);
	addTypeBridge(cls, ofClass, "java.time.LocalTime", TIMEOID);
	addTypeBridge(cls, ofClass, "java.time.OffsetDateTime", TIMESTAMPTZOID);
	addTypeBridge(cls, ofClass, "java.time.OffsetTime", TIMETZOID);

	/*
	 * TypeBridges that allow Java primitive array types to be passed to things
	 * expecting their boxed counterparts. An oddball case is byte[], given the
	 * default oid BYTEAOID here instead of CHARARRAYOID following the pattern,
	 * because there is a whole 'nother (see byte_array.c) Type that also maps
	 * byte[] on the Java side, but bytea for PostgreSQL (I am not at all sure
	 * what I think of that), and bridging it to a different Oid here would
	 * break it as a parameter to prepared statements that were working. So
	 * cater to that use, while possibly complicating the new use that was not
	 * formerly possible.
	 *
	 * There is no bridge for char[], because PL/Java has no Type that maps it
	 * to anything in PostgreSQL.
	 */
	addTypeBridge(cls, ofClass, "boolean[]", BOOLARRAYOID);
	addTypeBridge(cls, ofClass,    "byte[]", BYTEAOID);
	addTypeBridge(cls, ofClass,   "short[]", INT2ARRAYOID);
	addTypeBridge(cls, ofClass,     "int[]", INT4ARRAYOID);
	addTypeBridge(cls, ofClass,    "long[]", INT8ARRAYOID);
	addTypeBridge(cls, ofClass,   "float[]", FLOAT4ARRAYOID);
	addTypeBridge(cls, ofClass,  "double[]", FLOAT8ARRAYOID);

	addTypeBridge(cls, ofInterface, "java.sql.SQLXML",
#if defined(XMLOID)
		XMLOID
#else
		TEXTOID
#endif
	);

	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/jdbc/TypeBridge$Holder");
	s_TypeBridge_Holder_class = JNI_newGlobalRef(cls);
	s_TypeBridge_Holder_className = PgObject_getJavaMethod(cls, "className",
		"()Ljava/lang/String;");
	s_TypeBridge_Holder_defaultOid = PgObject_getJavaMethod(cls, "defaultOid",
		"()I");
	s_TypeBridge_Holder_payload = PgObject_getJavaMethod(cls, "payload",
		"()Ljava/lang/Object;");
}

/*
 * Shortcuts to initializers of known types
 */
extern void Any_initialize(void);
extern void Coerce_initialize(void);
extern void Void_initialize(void);
extern void Boolean_initialize(void);
extern void Byte_initialize(void);
extern void Short_initialize(void);
extern void Integer_initialize(void);
extern void Long_initialize(void);
extern void Float_initialize(void);
extern void Double_initialize(void);
extern void BigDecimal_initialize(void);

extern void Date_initialize(void);
extern void Time_initialize(void);
extern void Timestamp_initialize(void);

extern void Oid_initialize(void);
extern void AclId_initialize(void);

extern void String_initialize(void);
extern void byte_array_initialize(void);

extern void TupleTable_initialize(void);

extern void Composite_initialize(void);

extern void pljava_SQLXMLImpl_initialize(void);

extern void Type_initialize(void);
void Type_initialize(void)
{
	s_typeByOid          = HashMap_create(59, TopMemoryContext);
	s_obtainerByOid      = HashMap_create(59, TopMemoryContext);
	s_obtainerByJavaName = HashMap_create(59, TopMemoryContext);

	String_initialize();

	Any_initialize();
	Coerce_initialize();
	Void_initialize();
	Boolean_initialize();
	Byte_initialize();
	Short_initialize();
	Integer_initialize();
	Long_initialize();
	Float_initialize();
	Double_initialize();

	BigDecimal_initialize();

	Date_initialize();
	Time_initialize();
	Timestamp_initialize();

	Oid_initialize();
	AclId_initialize();

	byte_array_initialize();

	TupleTable_initialize();

	Composite_initialize();
	pljava_SQLXMLImpl_initialize();

	s_Map_class = JNI_newGlobalRef(PgObject_getJavaClass("java/util/Map"));
	s_Map_get = PgObject_getJavaMethod(s_Map_class, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

	s_Iterator_class = JNI_newGlobalRef(PgObject_getJavaClass("java/util/Iterator"));
	s_Iterator_hasNext = PgObject_getJavaMethod(s_Iterator_class, "hasNext", "()Z");
	s_Iterator_next = PgObject_getJavaMethod(s_Iterator_class, "next", "()Ljava/lang/Object;");

#if PG_VERSION_NUM < 110000
	BOOLARRAYOID   = get_array_type(BOOLOID);
	CHARARRAYOID   = get_array_type(CHAROID);
	FLOAT8ARRAYOID = get_array_type(FLOAT8OID);
	INT8ARRAYOID   = get_array_type(INT8OID);
#if PG_VERSION_NUM < 80400
	INT2ARRAYOID   = get_array_type(INT2OID);
#endif
#endif

	initializeTypeBridges();
}

/*
 * Abstract Type constructor
 */
TypeClass TypeClass_alloc(const char* typeName)
{
	return TypeClass_alloc2(typeName, sizeof(struct TypeClass_), sizeof(struct Type_));
}

TypeClass TypeClass_alloc2(const char* typeName, Size classSize, Size instanceSize)
{
	TypeClass self = (TypeClass)MemoryContextAlloc(TopMemoryContext, classSize);
	PgObjectClass_init((PgObjectClass)self, typeName, instanceSize, 0);
	self->JNISignature    = "";
	self->javaTypeName    = "";
	self->javaClass       = 0;
	self->canReplaceType  = _Type_canReplaceType;
	self->coerceDatum     = (DatumCoercer)_PgObject_pureVirtualCalled;
	self->coerceObject    = (ObjectCoercer)_PgObject_pureVirtualCalled;
	self->createArrayType = _Type_createArrayType;
	self->invoke          = _Type_invoke;
	self->getSRFCollector = _Type_getSRFCollector;
	self->datumFromSRF    = _Type_datumFromSRF;
	self->getTupleDesc    = _Type_getTupleDesc;
	self->getJNISignature = _Type_getJNISignature;
	self->dynamic         = false;
	self->outParameter    = false;
	self->getRealType     = _Type_getRealType;
	return self;
}

/*
 * Types are always allocated in global context.
 */
Type TypeClass_allocInstance(TypeClass cls, Oid typeId)
{
	return TypeClass_allocInstance2(cls, typeId, 0);
}

/*
 * Types are always allocated in global context.
 */
Type TypeClass_allocInstance2(TypeClass cls, Oid typeId, Form_pg_type pgType)
{
	Type t = (Type)PgObjectClass_allocInstance((PgObjectClass)(cls), TopMemoryContext);
	t->typeId       = typeId;
	t->arrayType    = 0;
	t->elementType  = 0;
	t->objectType   = 0;
	t->inCoercions  = 0;
	t->outCoercions = 0;
	if(pgType != 0)
	{
		t->length  = pgType->typlen;
		t->byValue = pgType->typbyval;
		t->align   = pgType->typalign;
	}
	else if(typeId != InvalidOid)
	{
		get_typlenbyvalalign(typeId,
						 &t->length,
						 &t->byValue,
						 &t->align);
	}
	else
	{
		t->length = 0;
		t->byValue = true;
		t->align = 'i';
	}
	return t;
}

/*
 * Register this type.
 */
static void _registerType(Oid typeId, const char* javaTypeName, Type type, TypeObtainer obtainer)
{
	CacheEntry ce = (CacheEntry)MemoryContextAlloc(TopMemoryContext, sizeof(CacheEntryData));
	ce->typeId   = typeId;
	ce->type     = type;
	ce->obtainer = obtainer;

	if(javaTypeName != 0)
	{
		/*
		 * The s_obtainerByJavaName cache is now keyed by Java name and an oid,
		 * rather than Java name alone, to address an issue affecting arrays.
		 * To avoid changing other behavior, the oid used in the hash key will
		 * be InvalidOid always, unless the Java name being registered is
		 * an array type and the caller has passed a valid oid.
		 */
		Oid keyOid = (NULL == strchr(javaTypeName, ']'))
			? InvalidOid
			: typeId;
		HashMap_putByStringOid(s_obtainerByJavaName, javaTypeName, keyOid, ce);
	}

	if(typeId != InvalidOid && HashMap_getByOid(s_obtainerByOid, typeId) == 0)
		HashMap_putByOid(s_obtainerByOid, typeId, ce);
}

void Type_registerType(const char* javaTypeName, Type type)
{
	_registerType(type->typeId, javaTypeName, type, (TypeObtainer)_PgObject_pureVirtualCalled);
}

void Type_registerType2(Oid typeId, const char* javaTypeName, TypeObtainer obtainer)
{
	_registerType(typeId, javaTypeName, 0, obtainer);
}
