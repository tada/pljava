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
#include <executor/spi.h>

#include "org_postgresql_pljava_jdbc_Invocation.h"
#include "pljava/Invocation.h"
#include "pljava/Function.h"
#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"
#include "pljava/Backend.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"

#define LOCAL_FRAME_SIZE 128

static jclass       s_Invocation_class;
static jmethodID    s_Invocation_onExit;
static jfieldID     s_Invocation_s_unhandled;
static unsigned int s_callLevel = 0;

Invocation* currentInvocation;

/*
 * Two features of the calling convention for PL/Java functions will be handled
 * here in Invocation to keep wrappers in Function simple. A PL/Java function
 * may use static primitive slot 0 to return a primitive value, so that will
 * always be saved in an Invocation struct and restored on both normal and
 * exceptional return paths, when the heavier-weight full pushing of a Java
 * ParameterFrame has not occurred. Likewise, the heavy full push is skipped if
 * either the current or the new frame limits are (0,0), which means for such
 * cases the frame limits themselves must be saved and restored the same way.
 */
static jvalue *s_primSlot0;
static jshort *s_frameLimits;

/*
 * To keep these values somewhat encapsulated, Function.c calls this function
 * during its initialization to share them, rather than simply making them
 * global.
 */
void pljava_Invocation_shareFrame(jvalue *slot0, jshort *limits)
{
	if ( 0 != s_primSlot0  ||  0 != s_frameLimits )
		return;
	s_primSlot0 = slot0;
	s_frameLimits = limits;
}

extern void Invocation_initialize(void);
void Invocation_initialize(void)
{
	jclass cls;
	JNINativeMethod invocationMethods[] =
	{
		{
		"_getCurrent",
		"()Lorg/postgresql/pljava/jdbc/Invocation;",
		Java_org_postgresql_pljava_jdbc_Invocation__1getCurrent
		},
		{
		"_getNestingLevel",
		"()I",
		Java_org_postgresql_pljava_jdbc_Invocation__1getNestingLevel
		},
		{
		"_clearErrorCondition",
		"()V",
		Java_org_postgresql_pljava_jdbc_Invocation__1clearErrorCondition
		},
		{
		"_register",
		"()V",
		Java_org_postgresql_pljava_jdbc_Invocation__1register
		},
		{ 0, 0, 0 }
	};

	cls = PgObject_getJavaClass("org/postgresql/pljava/jdbc/Invocation");
	s_Invocation_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, invocationMethods);
	s_Invocation_onExit = PgObject_getJavaMethod(cls, "onExit", "(Z)V");
	s_Invocation_s_unhandled = PgObject_getStaticJavaField(
		cls, "s_unhandled", "Ljava/sql/SQLException;");
	JNI_deleteLocalRef(cls);
}

void Invocation_assertConnect(void)
{
	int rslt;
	if(!currentInvocation->hasConnected)
	{
		rslt = SPI_connect();
		if ( SPI_OK_CONNECT != rslt )
			elog(ERROR, "SPI_connect returned %s",
						SPI_result_code_string(rslt));
#if PG_VERSION_NUM >= 100000
		if ( NULL != currentInvocation->triggerData )
		{
			rslt = SPI_register_trigger_data(currentInvocation->triggerData);
			if ( SPI_OK_TD_REGISTER != rslt )
				elog(WARNING, "SPI_register_trigger_data returned %s",
							  SPI_result_code_string(rslt));
		}
#endif
		currentInvocation->hasConnected = true;
	}
}

void Invocation_assertDisconnect(void)
{
	if(currentInvocation->hasConnected)
	{
		SPI_finish();
		currentInvocation->hasConnected = false;
	}
}

/*
 * Return the type map held by the innermost executing PL/Java function's
 * schema loader (the initiating loader that was used to resolve the function).
 * The type map is a map from Java Oid objects to Class<SQLData> class objects,
 * as resolved by that loader. This is effectively Function_currentLoader()
 * followed by JNI-invoking getTypeMap on the loader, but cached to avoid JNI).
 */
jobject Invocation_getTypeMap(void)
{
	Function f = currentInvocation->function;
	return f == 0 ? 0 : Function_getTypeMap(f);
}

void Invocation_pushBootContext(Invocation* ctx)
{
	JNI_pushLocalFrame(LOCAL_FRAME_SIZE);
	ctx->invocation      = 0;
	ctx->function        = 0;
	ctx->frameLimits     = 0;
	ctx->primSlot0.j     = 0L;
	ctx->savedLoader     = 0;
	ctx->hasConnected    = false;
	ctx->upperContext    = CurrentMemoryContext;
	ctx->errorOccurred   = false;
	ctx->inExprContextCB = false;
	ctx->previous        = 0;
#if PG_VERSION_NUM >= 100000
	ctx->triggerData     = 0;
#endif
	currentInvocation    = ctx;
	++s_callLevel;
}

void Invocation_popBootContext(void)
{
	JNI_popLocalFrame(0);
	currentInvocation = 0;
	--s_callLevel;
	/*
	 * Nothing is done here with savedLoader. It is just set to 0 in
	 * pushBootContext (uses can precede allocation of the sentinel value),
	 * and PL/Java functions (which could save a value) aren't called in a
	 * boot context.
	 */
}

void Invocation_pushInvocation(Invocation* ctx)
{
	JNI_pushLocalFrame(LOCAL_FRAME_SIZE);
	ctx->invocation      = 0;
	ctx->function        = 0;
	ctx->frameLimits     = *s_frameLimits;
	ctx->primSlot0       = *s_primSlot0;
	ctx->savedLoader     = pljava_Function_NO_LOADER;
	ctx->hasConnected    = false;
	ctx->upperContext    = CurrentMemoryContext;
	ctx->errorOccurred   = false;
	ctx->inExprContextCB = false;
	ctx->previous        = currentInvocation;
#if PG_VERSION_NUM >= 100000
	ctx->triggerData     = 0;
#endif
	currentInvocation   = ctx;
	++s_callLevel;
}

void Invocation_popInvocation(bool wasException)
{
	Invocation* ctx = currentInvocation->previous;
	bool heavy = FRAME_LIMITS_PUSHED == currentInvocation->frameLimits;
	bool unhandled = currentInvocation->errorOccurred;

	/*
	 * If the more heavyweight parameter-frame push wasn't done, do
	 * the lighter cleanup here.
	 */
	if ( ! heavy )
	{
		/*
		 * The lighter-weight cleanup.
		 */
		*s_frameLimits = currentInvocation->frameLimits;
		*s_primSlot0   = currentInvocation->primSlot0;
	}
	pljava_Function_popFrame(heavy);

	/*
	 * If a Java Invocation instance was created and associated with this
	 * invocation, delete the reference (after calling its onExit method,
	 * indicating whether the return is exceptional or not).
	 */
	if(currentInvocation->invocation != 0)
	{
		JNI_callVoidMethodLocked(
			currentInvocation->invocation, s_Invocation_onExit,
			(wasException || unhandled)
			? JNI_TRUE : JNI_FALSE);
		JNI_deleteGlobalRef(currentInvocation->invocation);
	}

	if ( unhandled )
	{
		jthrowable ex = (jthrowable)JNI_getStaticObjectField(
			s_Invocation_class, s_Invocation_s_unhandled);
		bool already_hit = Exception_isPGUnhandled(ex);
		JNI_setStaticObjectField(
			s_Invocation_class, s_Invocation_s_unhandled, NULL);

		JNI_exceptionStacktraceAtLevel(ex,
			wasException ? DEBUG2 : already_hit ? WARNING : DEBUG1);
	}

	/*
	 * Do nativeRelease for any DualState instances scoped to this invocation.
	 */
	pljava_DualState_nativeRelease(currentInvocation);

	/*
	 * Check for any DualState objects that became unreachable and can be freed.
	 */
	pljava_DualState_cleanEnqueuedInstances();

	if(currentInvocation->hasConnected)
		SPI_finish();

	JNI_popLocalFrame(0);

	if(ctx != 0)
	{
		MemoryContextSwitchTo(ctx->upperContext);
	}

	currentInvocation = ctx;
	--s_callLevel;
}

MemoryContext
Invocation_switchToUpperContext(void)
{
	return MemoryContextSwitchTo(currentInvocation->upperContext);
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _getNestingLevel
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1getNestingLevel(JNIEnv* env, jclass cls)
{
	return s_callLevel;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _getCurrent
 * Signature: ()Lorg/postgresql/pljava/jdbc/Invocation;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1getCurrent(JNIEnv* env, jclass cls)
{
	return currentInvocation->invocation;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _clearErrorCondition
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1clearErrorCondition(JNIEnv* env, jclass cls)
{
	currentInvocation->errorOccurred = false;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _register
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1register(JNIEnv* env, jobject _this)
{
	if ( NULL == currentInvocation->invocation )
	{
		currentInvocation->invocation = (*env)->NewGlobalRef(env, _this);
		return;
	}
	if ( (*env)->IsSameObject(env, currentInvocation->invocation, _this) )
		return;
	BEGIN_NATIVE
	Exception_throw(ERRCODE_INTERNAL_ERROR,
		"mismanaged PL/Java invocation stack");
	END_NATIVE
}
