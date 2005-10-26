/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Invocation_h
#define __pljava_Invocation_h

#include <postgres.h>
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
	
	/**
	 * The previous call context when nested function calls
	 * are made or 0 if this call is at the top level.
	 */
	Invocation*  previous;
};

extern Invocation* currentInvocation;


extern void Invocation_assertConnect(void);

extern void Invocation_pushBootContext(Invocation* ctx);

extern void Invocation_popBootContext(void);

extern void Invocation_pushInvocation(Invocation* ctx, bool trusted);

extern void Invocation_popInvocation(bool wasException);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Invocation_h */
