/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
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
#include "pljava/Backend.h"
#include "pljava/HashMap.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"


#ifndef GCJ /* Bug libgcj/15001 */
static jclass s_ResultSetProvider_class;
static jmethodID s_ResultSetProvider_assignRowValues;
static jmethodID s_ResultSetProvider_close;
#endif

static jclass s_ResultSetHandle_class;
static jclass s_ResultSetPicker_class;
static jmethodID s_ResultSetPicker_init;

static TypeClass s_ResultSetProviderClass;
static TypeClass s_ResultSetHandleClass;
static Type s_ResultSetHandle;
static HashMap s_cache;

typedef struct
{
	jobject singleRowWriter;
	jobject resultSetProvider;
	bool    hasConnected;
#ifdef GCJ /* Bug libgcj/15001 */
	jmethodID assignRowValues;
	jmethodID close;
#endif
} CallContextData;

static Datum _ResultSetProvider_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool hasRow;
	MemoryContext currCtx;
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
		jobject tmp;
		jobject tmp2;
		TupleDesc tupleDesc;

		/* create a function context for cross-call persistence
		 */
		context = SRF_FIRSTCALL_INIT();

		/* switch to memory context appropriate for multiple function calls
		 */
		currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);

		/* Call the declared Java function. It returns a ResultSetProvider
		 * or a ResultSet. A ResultSet must be wrapped in a ResultSetPicker
		 * (implements ResultSetProvider).
		 */
		isCallingJava = true;
		tmp = (*env)->CallStaticObjectMethodA(env, cls, method, args);
		isCallingJava = saveicj;
		Exception_checkException(env);

		if(tmp == 0)
		{
			fcinfo->isnull = true;
			MemoryContextSwitchTo(currCtx);
			SRF_RETURN_DONE(context);
		}

		if((*env)->IsInstanceOf(env, tmp, s_ResultSetHandle_class))
		{
			jobject wrapper;
			isCallingJava = true;
			wrapper = PgObject_newJavaObject(env, s_ResultSetPicker_class, s_ResultSetPicker_init, tmp);
			isCallingJava = saveicj;
			Exception_checkException(env);

			(*env)->DeleteLocalRef(env, tmp);
			tmp = wrapper;
		}
		isCallingJava = saveicj;

		/* Build a tuple description for the tuples (will be cached
		 * in TopMemoryContext)
		 */
		tupleDesc = TupleDesc_forOid(Type_getOid(self));
		if(tupleDesc == 0)
			ereport(ERROR, (errmsg("Unable to find tuple descriptor")));

#if (PGSQL_MAJOR_VER < 8)
		/* allocate a slot for a tuple with this tupdesc and assign it to
		 * the function context
		 */
		context->slot = TupleDescGetSlot(tupleDesc);
#endif

		/* Create the context used by Pl/Java
		 */
		ctxData = (CallContextData*)palloc(sizeof(CallContextData));

		context->user_fctx = ctxData;
		ctxData->hasConnected = false;
		ctxData->resultSetProvider = (*env)->NewGlobalRef(env, tmp);
#ifdef GCJ /* Bug libgcj/15001 */
		rsClass = (*env)->GetObjectClass(env, tmp);
		ctxData->assignRowValues = PgObject_getJavaMethod(
				env, rsClass, "assignRowValues", "(Ljava/sql/ResultSet;I)Z");
		ctxData->close = PgObject_getJavaMethod(
				env, rsClass, "close", "()V");
		(*env)->DeleteLocalRef(env, rsClass);
#endif
		(*env)->DeleteLocalRef(env, tmp);
		tmp = TupleDesc_create(env, tupleDesc);
		tmp2 = SingleRowWriter_create(env, tmp);
		(*env)->DeleteLocalRef(env, tmp);		
		ctxData->singleRowWriter = (*env)->NewGlobalRef(env, tmp2);
		(*env)->DeleteLocalRef(env, tmp2);

		MemoryContextSwitchTo(currCtx);
	}

	context = SRF_PERCALL_SETUP();
	ctxData = (CallContextData*)context->user_fctx;

	/* Obtain next row using the RowProvider as a parameter to the
	 * ResultSetProvider.assignRowValues method.
	 */
	currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);
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
	MemoryContextSwitchTo(currCtx);
	Exception_checkException(env);

	if(hasRow)
	{
		/* Obtain tuple and return it as a Datum. Must be done using a more
		 * durable context.
		 */
		HeapTuple tuple;
		Datum result = 0;
		currCtx = MemoryContext_switchToUpperContext();
		tuple = SingleRowWriter_getTupleAndClear(env, ctxData->singleRowWriter);
		if(tuple != 0)
		{
#if (PGSQL_MAJOR_VER >= 8)
			result = HeapTupleGetDatum(tuple);
#else
			result = TupleGetDatum(context->slot, tuple);
#endif
		}
		MemoryContextSwitchTo(currCtx);

		/* Neat trick that prevents SPI_finish
		 */
		if(currentCallContext->hasConnected)
		{
			ctxData->hasConnected = true;
			currentCallContext->hasConnected = false;
		}
		SRF_RETURN_NEXT(context, result);
	}

	/* This is the end of the set.
	 */
	currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);
	isCallingJava = true;
#ifdef GCJ /* Bug libgcj/15001 */
	(*env)->CallVoidMethod(env, ctxData->resultSetProvider, ctxData->close);
#else
	(*env)->CallVoidMethod(env, ctxData->resultSetProvider, s_ResultSetProvider_close);
#endif
	isCallingJava = saveicj;
	MemoryContextSwitchTo(currCtx);

	(*env)->DeleteGlobalRef(env, ctxData->singleRowWriter);
	(*env)->DeleteGlobalRef(env, ctxData->resultSetProvider);
	if(ctxData->hasConnected)
		currentCallContext->hasConnected = true;
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

static Type ResultSetHandle_obtain(Oid typeId)
{
	return s_ResultSetHandle;
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
#ifndef GCJ /* Bug libgcj/15001 */
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);
	s_ResultSetProvider_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/ResultSetProvider"));

	s_ResultSetProvider_assignRowValues = PgObject_getJavaMethod(
				env, s_ResultSetProvider_class, "assignRowValues", "(Ljava/sql/ResultSet;I)Z");
	s_ResultSetProvider_close = PgObject_getJavaMethod(
				env, s_ResultSetProvider_class, "close", "()V");
#endif
	s_ResultSetHandle_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/ResultSetHandle"));
	s_ResultSetPicker_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/ResultSetPicker"));
	s_ResultSetPicker_init = PgObject_getJavaMethod(
				env, s_ResultSetPicker_class, "<init>", "(Lorg/postgresql/pljava/ResultSetHandle;)V");

	s_cache = HashMap_create(13, TopMemoryContext);

	s_ResultSetProviderClass = TypeClass_alloc("type.ResultSetProvider");
	s_ResultSetProviderClass->JNISignature = "Lorg/postgresql/pljava/ResultSetProvider;";
	s_ResultSetProviderClass->javaTypeName = "org.postgresql.pljava.ResultSetProvider";
	s_ResultSetProviderClass->invoke	   = _ResultSetProvider_invoke;
	s_ResultSetProviderClass->coerceDatum  = _ResultSetProvider_coerceDatum;
	s_ResultSetProviderClass->coerceObject = _ResultSetProvider_coerceObject;
	Type_registerJavaType("org.postgresql.pljava.ResultSetProvider", ResultSetProvider_obtain);

	s_ResultSetHandleClass = TypeClass_alloc("type.ResultSetHandle");
	s_ResultSetHandleClass->JNISignature = "Lorg/postgresql/pljava/ResultSetHandle;";
	s_ResultSetHandleClass->javaTypeName = "org.postgresql.pljava.ResultSetHandle";
	s_ResultSetHandle = TypeClass_allocInstance(s_ResultSetHandleClass, InvalidOid);
	Type_registerJavaType("org.postgresql.pljava.ResultSetHandle", ResultSetHandle_obtain);
	PG_RETURN_VOID();
}
