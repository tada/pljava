/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/memutils.h>
#include <utils/numeric.h>
#include <nodes/execnodes.h>
#include <funcapi.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/SingleRowWriter.h"
#include "pljava/HashMap.h"
#include "pljava/MemoryContext.h"

/*
 * void primitive type.
 */
static jclass s_ResultSetProvider_class;

#ifndef GCJ /* Bug libgcj/15001 */
static jmethodID s_ResultSetProvider_assignRowValues;
#endif

static TypeClass s_ResultSetProviderClass;
static HashMap s_cache;

typedef struct
{
	jobject singleRowWriter;
	jobject resultSetProvider;
#ifdef GCJ /* Bug libgcj/15001 */
	jmethodID assignRowValues;
#endif
} CallContextData;

static Datum _ResultSetProvider_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool hasRow;
	CallContextData* ctxData;
	FuncCallContext* context;
	bool saveicj = isCallingJava;

	/* stuff done only on the first call of the function
	 */
	if(SRF_IS_FIRSTCALL())
	{
#ifdef GCJ /* Bug libgcj/15001 */
		jclass rsClass;
#endif
		MemoryContext currCtx;
		jobject tmp;
		TupleDesc tupleDesc;

		/* Call the declared Java function. It returns the ResultSetProvider
		 * that later is used once for each row that should be obtained.
		 */
		isCallingJava = true;
		tmp = (*env)->CallStaticObjectMethodA(env, cls, method, args);
		isCallingJava = saveicj;

		/* create a function context for cross-call persistence
		 */
		context = SRF_FIRSTCALL_INIT();

		if(tmp == 0)
		{
			fcinfo->isnull = true;
			SRF_RETURN_DONE(context);
		}

		/* Build a tuple description for the tuples (will be cached
		 * in TopMemoryContext)
		 */
		tupleDesc = TupleDesc_forOid(Type_getOid(self));

		/* switch to memory context appropriate for multiple function calls
		 */
		currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);

#if (PGSQL_MAJOR_VER < 8)
		/* allocate a slot for a tuple with this tupdesc and assign it to
		 * the function context
		 */
		context->slot = TupleDescGetSlot(tupleDesc);
#endif

		/* Create the context used by Pl/Java
		 */
		ctxData = (CallContextData*)palloc(sizeof(CallContextData));
		MemoryContextSwitchTo(currCtx);

		context->user_fctx = ctxData;
		ctxData->resultSetProvider = (*env)->NewGlobalRef(env, tmp);
#ifdef GCJ /* Bug libgcj/15001 */
		rsClass = (*env)->GetObjectClass(env, tmp);
		ctxData->assignRowValues = PgObject_getJavaMethod(
				env, rsClass, "assignRowValues", "(Ljava/sql/ResultSet;I)Z");
		(*env)->DeleteLocalRef(env, rsClass);
#endif
		(*env)->DeleteLocalRef(env, tmp);
		tmp = SingleRowWriter_create(env, tupleDesc);		
		ctxData->singleRowWriter = (*env)->NewGlobalRef(env, tmp);
		(*env)->DeleteLocalRef(env, tmp);
	}

	context = SRF_PERCALL_SETUP();
	ctxData = (CallContextData*)context->user_fctx;

	/* Obtain next row using the RowProvider as a parameter to the
	 * ResultSetProvider.assignRowValues method.
	 */
	isCallingJava = true;
	hasRow = ((*env)->CallBooleanMethod(
			env, ctxData->resultSetProvider,
#ifdef GCJ /* Bug libgcj/15001 */
			ctxData->assignRowValues,
#else
			s_ResultSetProvider_assignRowValues,
#endif
			ctxData->singleRowWriter, (jint)context->call_cntr) == JNI_TRUE);
	isCallingJava = saveicj;

	if(hasRow)
	{
		/* Obtain tuple and return it as a Datum. Must be done using a more
		 * durable context.
		 */
		MemoryContext currCtx = MemoryContext_switchToReturnValueContext();
		HeapTuple tuple = SingleRowWriter_getTupleAndClear(env, ctxData->singleRowWriter);

#if (PGSQL_MAJOR_VER >= 8)
		Datum result = HeapTupleGetDatum(tuple);
#else
		Datum result = TupleGetDatum(context->slot, tuple);
#endif
		MemoryContextSwitchTo(currCtx);
		SRF_RETURN_NEXT(context, result);
	}

	/* This is the end of the set.
	 */
	(*env)->DeleteGlobalRef(env, ctxData->singleRowWriter);
	(*env)->DeleteGlobalRef(env, ctxData->resultSetProvider);
	pfree(ctxData);
	SRF_RETURN_DONE(context);
}

static jvalue _ResultSetProvider_coerceDatum(Type self, JNIEnv* env, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _ResultSetProvider_coerceObject(Type self, JNIEnv* env, jobject nothing)
{
	return 0;
}

static Type ResultSetProvider_obtain(Oid typeId)
{
	/* Check to see if we have a cached version for this
	 * postgres type
	 */
	Type infant = (Type)HashMap_getByOid(s_cache, typeId);
	if(infant == 0)
	{
		infant = TypeClass_allocInstance(s_ResultSetProviderClass, typeId);
		HashMap_putByOid(s_cache, typeId, infant);
	}
	return infant;
}

/* Make this datatype available to the postgres system.
 */
extern Datum ResultSetProvider_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(ResultSetProvider_initialize);
Datum ResultSetProvider_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_ResultSetProvider_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/ResultSetProvider"));

#ifndef GCJ /* Bug libgcj/15001 */
	s_ResultSetProvider_assignRowValues = PgObject_getJavaMethod(
				env, s_ResultSetProvider_class, "assignRowValues", "(Ljava/sql/ResultSet;I)Z");
#endif

	s_cache = HashMap_create(13, TopMemoryContext);

	s_ResultSetProviderClass = TypeClass_alloc("type.ResultSetProvider");
	s_ResultSetProviderClass->JNISignature = "Lorg/postgresql/pljava/ResultSetProvider;";
	s_ResultSetProviderClass->javaTypeName = "org.postgresql.pljava.ResultSetProvider";
	s_ResultSetProviderClass->invoke	   = _ResultSetProvider_invoke;
	s_ResultSetProviderClass->coerceDatum  = _ResultSetProvider_coerceDatum;
	s_ResultSetProviderClass->coerceObject = _ResultSetProvider_coerceObject;

	Type_registerJavaType("org.postgresql.pljava.ResultSetProvider", ResultSetProvider_obtain);
	PG_RETURN_VOID();
}
