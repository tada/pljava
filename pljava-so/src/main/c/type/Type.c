/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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

#if PG_VERSION_NUM < 110000
static Oid BOOLARRAYOID;
static Oid CHARARRAYOID;
static Oid FLOAT8ARRAYOID;
static Oid INT8ARRAYOID;
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
	Function      fn;
	jobject       rowProducer;
	jobject       rowCollector;
} CallContextData;

/*
 * Called either at normal completion of a set-returning function, or by the
 * _endOfSetCB if PostgreSQL doesn't want all the results.
 */
static void _closeIteration(CallContextData* ctxData)
{
	jobject dummy;

	/*
	 * Why pass 1 as the call_cntr? We won't always have the actual call_cntr
	 * value at _closeIteration time (the _endOfSetCB isn't passed it), and the
	 * Java interfaces being used don't need it (close() isn't passed a row
	 * number), but at least 1 is different from zero, in case vpcInvoke has
	 * a reason to distinguish the first call (in the same invocation as the
	 * overall setup) from subsequent ones.
	 */
	pljava_Function_vpcInvoke(
		ctxData->fn, ctxData->rowProducer, NULL, 1, JNI_TRUE, &dummy);

	JNI_deleteGlobalRef(ctxData->rowProducer);
	if(ctxData->rowCollector != 0)
		JNI_deleteGlobalRef(ctxData->rowCollector);
}

/*
 * Called by PostgreSQL if abandoning the collection of set-returning-function
 * results early.
 */
static void _endOfSetCB(Datum arg)
{
	Invocation ctx;
	CallContextData* ctxData = (CallContextData*)DatumGetPointer(arg);

	/*
	 * Even if there is an invocation already on the stack, there is no
	 * convincing reason to think this callback belongs to it; PostgreSQL
	 * will make this callback when the expression context we did belong to
	 * is being torn down. This is not a hot operation; it only happens in
	 * rare cases when an SRF has been called and not completely consumed.
	 * So just unconditionally set up a context for this call, and clean up
	 * our own mess.
	 */
	PG_TRY();
	{
		Invocation_pushInvocation(&ctx);
		currentInvocation->inExprContextCB = true;
		_closeIteration(ctxData);
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
		Invocation_popInvocation(true);
		PG_RE_THROW();
	}
	PG_END_TRY();
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

		/*
		 * If the set-up function called above did not connect SPI, we are
		 * (unless the function changed it in some other arbitrary way) still
		 * in the multi_call_memory_ctx. We will return to currCtx (the executor
		 * per-row context) at the end of this set-up block, in preparation for
		 * producing the first row, if any.
		 *
		 * If the set-up function did connect SPI, we are now in the SPI Proc
		 * memory context (which will go away in SPI_finish when this call
		 * returns). That's not very much different from currCtx, the one the
		 * executor supplied us, which will be reset by the executor after the
		 * return of this call and before the next invocation. Here, we will
		 * switch back to the multi_call_memory_ctx for the remainder of this
		 * set-up block. As always, this block will end with a switch to currCtx
		 * and be ready to produce the first row.
		 *
		 * Two choices are possible here: 1) leave currCtx unchanged, so we
		 * end up in the executor's per-row context; 2) assign the SPI Proc
		 * context to it, so we end up in that. Because the contexts have very
		 * similar lifecycles, the choice does not seem critical. Of note,
		 * though, is that any SPI function that operates in the SPI Exec
		 * context will unconditionally leave the SPI Proc context as
		 * the current context when it returns; it will not save and restore
		 * its context on entry. Given that behavior, the choice here of (2)
		 * reassigning currCtx to mean the SPI Proc context would seem to create
		 * the situation with the least potential for surprises.
		 */
		if ( currentInvocation->hasConnected )
			currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);

		/*
		 * This palloc depends on being made in the multi_call_memory_ctx.
		 */
		ctxData = (CallContextData*)palloc0(sizeof(CallContextData));
		context->user_fctx = ctxData;

		ctxData->elemType = self;
		ctxData->fn = fn;
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
	 * memory context is one that gets reset between calls: either the per-row
	 * context supplied by the executor, or (if this is the first call and the
	 * setup code used SPI) the "SPI Proc" context.
	 */

	context = SRF_PERCALL_SETUP();
	ctxData = (CallContextData*)context->user_fctx;
	currCtx = CurrentMemoryContext; /* save the supplied per-row context */

	if(JNI_TRUE == pljava_Function_vpcInvoke(ctxData->fn,
		ctxData->rowProducer, ctxData->rowCollector, (jlong)context->call_cntr,
		JNI_FALSE, &row))
	{
		Datum result = Type_datumFromSRF(self, row, ctxData->rowCollector);
		JNI_deleteLocalRef(row);
		SRF_RETURN_NEXT(context, result);
	}

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

/*
 * Return NULL unless typeId represents a MappedUDT as found in the typeMap,
 * in which case return a freshly-registered UDT Type.
 *
 * A MappedUDT's supporting functions don't have SQL declarations, from which
 * an ordinary function's PLPrincipal and initiating class loader would be
 * determined, so when obtaining the support function handles below, NULL will
 * be passed as the language name, indicating that information isn't available,
 * and won't be baked into the handles.
 *
 * A MappedUDT only has the two support functions readSQL and writeSQL.
 * The I/O support functions parse and toString are only for a BaseUDT, so
 * they do not need to be looked up here.
 *
 * The typeStruct argument supplies the type's name and namespace to
 * UDT_registerUDT, as well as the by-value, length, and alignment common to
 * any registered Type.
 *
 * A complication, though: in principle, this is a function on two variables,
 * typeId and typeMap. (The typeStruct is functionally dependent on typeId.)
 * But registration of the first one to be encountered will enter it in caches
 * that depend only on the typeId (or Java class name, for the other direction)
 * from that point on. This is longstanding PL/Java behavior, but XXX.
 */
static inline Type
checkTypeMappedUDT(Oid typeId, jobject typeMap, Form_pg_type typeStruct)
{
	jobject joid;
	jclass  typeClass;
	Type    type;
	jobject readMH;
	jobject writeMH;
	TupleDesc tupleDesc;
	bool    hasTupleDesc;

	if ( NULL == typeMap )
		return NULL;

	joid      = Oid_create(typeId);
	typeClass = (jclass)JNI_callObjectMethod(typeMap, s_Map_get, joid);
	JNI_deleteLocalRef(joid);

	if ( NULL == typeClass )
		return NULL;

	if ( -2 == typeStruct->typlen )
	{
		JNI_deleteLocalRef(typeClass);
		ereport(ERROR, (
			errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
			errmsg(
				"type mapping in PL/Java for %s with NUL-terminated(-2) "
				"storage not supported",
				format_type_be_qualified(typeId))
		));
	}

	readMH  = pljava_Function_udtReadHandle( typeClass, NULL, true);
	writeMH = pljava_Function_udtWriteHandle(typeClass, NULL, true);

	tupleDesc = lookup_rowtype_tupdesc_noerror(typeId, -1, true);
	hasTupleDesc = NULL != tupleDesc;
	if ( hasTupleDesc )
		ReleaseTupleDesc(tupleDesc);

	type = (Type)UDT_registerUDT(
		typeClass, typeId, typeStruct, hasTupleDesc, false,
		NULL, readMH, writeMH, NULL);
	/*
	 * UDT_registerUDT calls JNI_deleteLocalRef on readMH and writeMH.
	 */

	JNI_deleteLocalRef(typeClass);
	return type;
}

Type Type_fromOid(Oid typeId, jobject typeMap)
{
	CacheEntry   ce;
	HeapTuple    typeTup;
	Form_pg_type typeStruct;
	Type         type = Type_fromOidCache(typeId);

	if ( NULL != type )
		return type;

	typeTup    = PgObject_getValidTuple(TYPEOID, typeId, "type");
	typeStruct = (Form_pg_type)GETSTRUCT(typeTup);

	if(typeStruct->typelem != 0 && typeStruct->typlen == -1)
	{
		type = Type_getArrayType(
			Type_fromOid(typeStruct->typelem, typeMap), typeId);
		goto finally;
	}

	/* For some reason, the anyarray is *not* an array with anyelement as the
	 * element type. We'd like to see it that way though.
	 * XXX this is a longstanding PL/Java misconception about the polymorphic
	 * types in PostgreSQL. When a function is declared with types like
	 * ANYARRAY and ANYELEMENT, there is supposed to be a step involving
	 * funcapi.c routines like get_fn_expr_argtype to resolve them to specific
	 * types for the current call site. Another thing to be sure to handle
	 * correctly in the API revamp.
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

	/*
	 * Perhaps we have found a MappedUDT. If so, this check will register and
	 * return it.
	 */
	type = checkTypeMappedUDT(typeId, typeMap, typeStruct);
	if ( NULL != type )
		goto finally;

	/* Composite and record types will not have a TypeObtainer registered
	 */
	if(typeStruct->typtype == 'c'
		|| (typeStruct->typtype == 'p' && typeId == RECORDOID))
	{
		type = Composite_obtain(typeId);
		goto finally;
	}

	ce = (CacheEntry)HashMap_getByOid(s_obtainerByOid, typeId);
	if ( NULL == ce )
	{
		/*
		 * Perhaps we have found a BaseUDT. If so, this check will register and
		 * return it.
		 */
		type = Function_checkTypeBaseUDT(typeId, typeStruct);
		if ( NULL != type )
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

/*
 * The Type_invoke implementation that is 'inherited' by all type classes
 * except Coerce, Composite, and those corresponding to Java primitives.
 * This implementation unconditionally switches to the "upper memory context"
 * recorded in the Invocation before coercing the Java result to a Datum,
 * in case SPI has been connected (which would have switched to a context that
 * is reset too soon for the caller to use the result).
 */
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

/*
 * The Type_datumFromSRF implementation that is 'inherited' by all type classes
 * except Composite. This implementation makes no use of the rowCollector
 * parameter, and unconditionally switches to the "upper memory context"
 * recorded in the Invocation before coercing the Java result to a Datum, in
 * case SPI has been connected (which would have switched to a context that is
 * reset too soon for the caller to use the result).
 */
static Datum _Type_datumFromSRF(Type self, jobject row, jobject rowCollector)
{
	MemoryContext currCtx;
	Datum ret;

	currCtx = Invocation_switchToUpperContext();
	ret = Type_coerceObject(self, row);
	MemoryContextSwitchTo(currCtx);

	return ret;
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

	addTypeBridge(cls, ofInterface,
		"org.postgresql.pljava.model.CatalogObject", OIDOID);

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
	s_Map_get = PgObject_getJavaMethod(
		s_Map_class, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

	s_Iterator_class = JNI_newGlobalRef(
		PgObject_getJavaClass("java/util/Iterator"));
	s_Iterator_hasNext = PgObject_getJavaMethod(
		s_Iterator_class, "hasNext", "()Z");
	s_Iterator_next = PgObject_getJavaMethod(
		s_Iterator_class, "next", "()Ljava/lang/Object;");

#if PG_VERSION_NUM < 110000
	BOOLARRAYOID   = get_array_type(BOOLOID);
	CHARARRAYOID   = get_array_type(CHAROID);
	FLOAT8ARRAYOID = get_array_type(FLOAT8OID);
	INT8ARRAYOID   = get_array_type(INT8OID);
#endif

	initializeTypeBridges();
}

static Type unimplementedTypeObtainer(Oid typeId);
static jvalue unimplementedDatumCoercer(Type, Datum);
static Datum unimplementedObjectCoercer(Type, jobject);

static Type unimplementedTypeObtainer(Oid typeId)
{
	ereport(ERROR,
		(errmsg("no type obtainer registered for type oid %ud", typeId)));
	pg_unreachable();
}

static jvalue unimplementedDatumCoercer(Type t, Datum d)
{
	ereport(ERROR,
		(errmsg("no datum coercer registered for type oid %ud", t->typeId)));
	pg_unreachable();
}

static Datum unimplementedObjectCoercer(Type t, jobject o)
{
	ereport(ERROR,
		(errmsg("no object coercer registered for type oid %ud", t->typeId)));
	pg_unreachable();
}

/*
 * Abstract Type constructor
 */
TypeClass TypeClass_alloc(const char* typeName)
{
	return TypeClass_alloc2(
		typeName, sizeof(struct TypeClass_), sizeof(struct Type_));
}

TypeClass TypeClass_alloc2(
	const char* typeName, Size classSize, Size instanceSize)
{
	TypeClass self = (TypeClass)MemoryContextAlloc(TopMemoryContext, classSize);
	PgObjectClass_init((PgObjectClass)self, typeName, instanceSize, 0);
	self->JNISignature    = "";
	self->javaTypeName    = "";
	self->javaClass       = 0;
	self->canReplaceType  = _Type_canReplaceType;
	self->coerceDatum     = unimplementedDatumCoercer;
	self->coerceObject    = unimplementedObjectCoercer;
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
	Type t = (Type)
		PgObjectClass_allocInstance((PgObjectClass)(cls), TopMemoryContext);
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
static void _registerType(
	Oid typeId, const char* javaTypeName, Type type, TypeObtainer obtainer)
{
	CacheEntry ce = (CacheEntry)
		MemoryContextAlloc(TopMemoryContext, sizeof(CacheEntryData));
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
	_registerType(type->typeId, javaTypeName, type, unimplementedTypeObtainer);
}

void Type_registerType2(
	Oid typeId, const char* javaTypeName, TypeObtainer obtainer)
{
	_registerType(typeId, javaTypeName, 0, obtainer);
}
