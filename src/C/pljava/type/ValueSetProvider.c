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
#include <funcapi.h>

#include "pljava/type/Type_priv.h"
#include "pljava/HashMap.h"
#include "pljava/MemoryContext.h"

#ifndef GCJ /* Bug libgcj/15001 */
static jclass s_Iterator_class;
static jmethodID s_Iterator_hasNext;
static jmethodID s_Iterator_next;
#endif

static TypeClass s_ValueSetProviderClass;
static HashMap s_cache;	/* Cached by element type */

typedef struct
{
	jobject iterator;
	Type    elementType;
#ifdef GCJ /* Bug libgcj/15001 */
	jmethodID next;
	jmethodID hasNext;
#endif
} CallContextData;

static Datum _ValueSetProvider_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
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

		/* Call the declared Java function. It returns the Iterator
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

		/* switch to memory context appropriate for multiple function calls
		 */
		currCtx = MemoryContextSwitchTo(context->multi_call_memory_ctx);

		/* Create the context used by Pl/Java
		 */
		ctxData = (CallContextData*)palloc(sizeof(CallContextData));
		MemoryContextSwitchTo(currCtx);

		context->user_fctx = ctxData;
		ctxData->iterator = (*env)->NewGlobalRef(env, tmp);
		ctxData->elementType = Type_fromOid(Type_getOid(self));
#ifdef GCJ /* Bug libgcj/15001 */
		rsClass = (*env)->GetObjectClass(env, tmp);
		ctxData->hasNext = PgObject_getJavaMethod(
				env, rsClass, "hasNext", "()Z");
		ctxData->next = PgObject_getJavaMethod(
				env, rsClass, "next", "()Ljava/lang/Object;");
		(*env)->DeleteLocalRef(env, rsClass);
#endif
		(*env)->DeleteLocalRef(env, tmp);
	}

	context = SRF_PERCALL_SETUP();
	ctxData = (CallContextData*)context->user_fctx;

	/* Obtain next row using the RowProvider as a parameter to the
	 * Iterator.assignRowValues method.
	 */
	isCallingJava = true;
	hasRow = ((*env)->CallBooleanMethod(
			env, ctxData->iterator,
#ifdef GCJ /* Bug libgcj/15001 */
			ctxData->hasNext
#else
			s_Iterator_hasNext
#endif
			) == JNI_TRUE);
	isCallingJava = saveicj;

	if(hasRow)
	{
		MemoryContext currCtx;
		Datum result;
		jobject tmp;
		
		isCallingJava = true;
#ifdef GCJ /* Bug libgcj/15001 */
		tmp = (*env)->CallObjectMethod(env, ctxData->iterator, ctxData->next);
#else
		tmp = (*env)->CallObjectMethod(env, ctxData->iterator, s_Iterator_next);
#endif
		isCallingJava = saveicj;

		/* The element must be coerced using the return value context
		 */
		currCtx = MemoryContext_switchToUpperContext();
		result = Type_coerceObject(ctxData->elementType, env, tmp);
		MemoryContextSwitchTo(currCtx);
		SRF_RETURN_NEXT(context, result);
	}

	/* This is the end of the set.
	 */
	(*env)->DeleteGlobalRef(env, ctxData->iterator);
	pfree(ctxData);
	SRF_RETURN_DONE(context);
}

static jvalue _ValueSetProvider_coerceDatum(Type self, JNIEnv* env, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _ValueSetProvider_coerceObject(Type self, JNIEnv* env, jobject nothing)
{
	return 0;
}

static Type ValueSetProvider_obtain(Oid typeId)
{
	/* Check to see if we have a cached version for this
	 * postgres type
	 */
	Type infant = (Type)HashMap_getByOid(s_cache, typeId);
	if(infant == 0)
	{
		infant = TypeClass_allocInstance(s_ValueSetProviderClass, typeId);
		HashMap_putByOid(s_cache, typeId, infant);
	}
	return infant;
}

/* Make this datatype available to the postgres system.
 */
extern Datum ValueSetProvider_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(ValueSetProvider_initialize);
Datum ValueSetProvider_initialize(PG_FUNCTION_ARGS)
{
#ifndef GCJ /* Bug libgcj/15001 */
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);
	s_Iterator_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/util/Iterator"));
	s_Iterator_hasNext = PgObject_getJavaMethod(
				env, s_Iterator_class, "hasNext", "()Z");
	s_Iterator_next = PgObject_getJavaMethod(
				env, s_Iterator_class, "next", "()Ljava/lang/Object;");
#endif

	s_cache = HashMap_create(13, TopMemoryContext);

	s_ValueSetProviderClass = TypeClass_alloc("type.Iterator");
	s_ValueSetProviderClass->JNISignature = "Ljava/util/Iterator;";
	s_ValueSetProviderClass->javaTypeName = "java.util.Iterator";
	s_ValueSetProviderClass->invoke	      = _ValueSetProvider_invoke;
	s_ValueSetProviderClass->coerceDatum  = _ValueSetProvider_coerceDatum;
	s_ValueSetProviderClass->coerceObject = _ValueSetProvider_coerceObject;

	Type_registerJavaType("java.util.Iterator", ValueSetProvider_obtain);
	PG_RETURN_VOID();
}
