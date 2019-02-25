/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
#ifndef __pljava_Invocation_h
#define __pljava_Invocation_h

#include <postgres.h>
#if PG_VERSION_NUM >= 100000
#include <commands/trigger.h>
#endif
#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

struct Invocation_
{
	/**
	 * A Java object representing the current invocation. This
	 * field will be NULL if no such object has been requested.
	 */
	jobject       invocation;
	
	/**
	 * The context to use when allocating values that are to be
	 * returned from the call.
	 */
	MemoryContext upperContext;

	/**
	 * Set when an SPI_connect is issued. Ensures that SPI_finish
	 * is called when the function exits.
	 */
	bool          hasConnected;

	/**
	 * Set to true if the call originates from an ExprContextCallback. When
	 * it does, we should not close any cursors.
	 */
	bool          inExprContextCB;

	/**
	 * Set to true if the executing function is trusted
	 */
	bool          trusted;

	/**
	 * The currently executing Function.
	 */
	Function      function;
	
	/**
	 * Set to true if an elog with a severity >= ERROR
	 * has occured. All calls from Java to the backend will
	 * be prevented until this flag is reset (by a rollback
	 * of a savepoint or function exit).
	 */
	bool          errorOccured;

#if PG_VERSION_NUM >= 100000
	/**
	 * TriggerData pointer, if the function is being called as a trigger,
	 * so it can be passed to SPI_register_trigger_data if the function connects
	 * to SPI.
	 */
	TriggerData*  triggerData;
#endif

	/**
	 * The previous call context when nested function calls
	 * are made or 0 if this call is at the top level.
	 */
	Invocation*  previous;
};

extern Invocation* currentInvocation;


extern void Invocation_assertConnect(void);

extern void Invocation_assertDisconnect(void);

extern void Invocation_pushBootContext(Invocation* ctx);

extern void Invocation_popBootContext(void);

extern void Invocation_pushInvocation(Invocation* ctx, bool trusted);

extern void Invocation_popInvocation(bool wasException);

extern jobject Invocation_getTypeMap(void);

/*
 * Switch memory context to a context that is durable between calls to
 * the call manager but not durable between queries. The old context is
 * returned. This method can be used when creating values that will be
 * returned from the Pl/Java routines. Once the values have been created
 * a call to MemoryContextSwitchTo(oldContext) must follow where oldContext
 * is the context returned from this call.
 */
extern MemoryContext Invocation_switchToUpperContext(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Invocation_h */
