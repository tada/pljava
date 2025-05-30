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

#include "org_postgresql_pljava_internal_Invocation.h"
#include "org_postgresql_pljava_internal_Invocation_EarlyNatives.h"
#include "pljava/Invocation.h"
#include "pljava/Function.h"
#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"
#include "pljava/Backend.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"

#define LOCAL_FRAME_SIZE 128

static jclass    s_Invocation_class;
static jmethodID s_Invocation_onExit;
static jfieldID  s_Invocation_s_unhandled;

/**
 * All of these initial values are as were formerly set in pushBootContext,
 * leaving it to set only upperContext (a value that's not statically known).
 * When nestLevel is zero, no call into a PL/Java function is in progress.
 */
Invocation currentInvocation[] =
{
	{
		.nestLevel = 0,
		.hasDual = false,
		.errorOccurred = false,
		.hasConnected = false,
		.inExprContextCB = false,
		.nonAtomic = false,
		.upperContext = NULL,
		.savedLoader = NULL,
		.function = NULL,
#if PG_VERSION_NUM >= 100000
		.triggerData = NULL,
#endif
		.previous = NULL,
		.primSlot0.j = 0L,
		.frameLimits = 0
	}
};

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
		"_window",
		"()Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_Invocation_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

#define CONFIRMOFFSET(fld) \
StaticAssertStmt(offsetof(Invocation,fld) == \
(org_postgresql_pljava_internal_Invocation_OFFSET_##fld), \
	"Java/C offset mismatch for " #fld)

	CONFIRMOFFSET(nestLevel);
	CONFIRMOFFSET(hasDual);
	CONFIRMOFFSET(errorOccurred);
	CONFIRMOFFSET(upperContext);

#undef CONFIRMOFFSET

	cls = PgObject_getJavaClass("org/postgresql/pljava/internal/Invocation$EarlyNatives");
	PgObject_registerNatives2(cls, invocationMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/internal/Invocation");
	s_Invocation_class = JNI_newGlobalRef(cls);
	s_Invocation_onExit = PgObject_getStaticJavaMethod(cls, "onExit", "(IZ)V");
	s_Invocation_s_unhandled = PgObject_getStaticJavaField(
		cls, "s_unhandled", "Ljava/sql/SQLException;");
	JNI_deleteLocalRef(cls);
}

void Invocation_assertConnect(void)
{
	int rslt;
	if(!currentInvocation->hasConnected)
	{
		rslt = SPI_connect_ext(
			currentInvocation->nonAtomic ? SPI_OPT_NONATOMIC : 0);
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

void Invocation_pushBootContext(Invocation* ctx)
{
	JNI_pushLocalFrame(LOCAL_FRAME_SIZE);
	*ctx = *currentInvocation;
	currentInvocation->previous = ctx;
	currentInvocation->upperContext = CurrentMemoryContext;
	++ currentInvocation->nestLevel;
}

void Invocation_popBootContext(void)
{
	JNI_popLocalFrame(0);
	*currentInvocation = *currentInvocation->previous;
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
	*ctx = *currentInvocation;
	currentInvocation->previous = ctx;
	ctx = currentInvocation; /* just to keep the notation compact below */
	ctx->function        = 0;
	ctx->frameLimits     = *s_frameLimits;
	ctx->primSlot0       = *s_primSlot0;
	ctx->savedLoader     = pljava_Function_NO_LOADER;
	ctx->hasConnected    = false;
	ctx->nonAtomic       = false;
	ctx->upperContext    = CurrentMemoryContext;
	ctx->errorOccurred   = false;
	ctx->inExprContextCB = false;
#if PG_VERSION_NUM >= 100000
	ctx->triggerData     = 0;
#endif
	ctx->hasDual = false;
	++ ctx->nestLevel;
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
	if ( currentInvocation->hasDual )
	{
		JNI_callStaticVoidMethodLocked(
			s_Invocation_class, s_Invocation_onExit,
			(jint)currentInvocation->nestLevel,
			(wasException || unhandled)
			? JNI_TRUE : JNI_FALSE);
	}

	if(currentInvocation->hasConnected)
		SPI_finish();

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

	JNI_popLocalFrame(0);

	/*
	 * Return to the context that was effective at pushInvocation of *this*
	 * invocation.
	 */
	MemoryContextSwitchTo(currentInvocation->upperContext);

	/*
	 * Check for any DualState objects that became unreachable and can be freed.
	 * In this late position, it might find things that became unreachable with
	 * the release of SPI contexts or JNI local frame references; having first
	 * switched back to the upperContext, the chance that any contexts possibly
	 * released in cleaning could be the current one are minimized.
	 */
	pljava_DualState_cleanEnqueuedInstances();

	*currentInvocation = *ctx;
}

MemoryContext
Invocation_switchToUpperContext(void)
{
	return MemoryContextSwitchTo(currentInvocation->upperContext);
}

/*
 * Class:     org_postgresql_pljava_internal_Invocation_EarlyNatives
 * Method:    _window
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Invocation_00024EarlyNatives__1window(JNIEnv* env, jobject _cls)
{
	return (*env)->NewDirectByteBuffer(env,
		currentInvocation, sizeof *currentInvocation);
}
