/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Backend_h
#define __pljava_Backend_h

#include "pljava/Function.h"

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Backend contains the call handler, initialization of the
 * PL/Java, access to config variables, and logging.
 * 
 * @author Thomas Hallgren
 *****************************************************************/
struct CallContext_;
typedef struct CallContext_ CallContext;

struct CallContext_
{
	/**
	 * Current JNI environment
	 */
	JNIEnv*       jniEnv;

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
	CallContext*  previous;
};

extern CallContext* currentCallContext;

extern void Backend_assertConnect(void);

extern void Backend_assertDisconnect(void);

extern void Backend_pushCallContext(CallContext* ctx, bool trusted);

extern void Backend_popCallContext(void);

extern JNIEnv* Backend_getJNIEnv(void);

extern void Backend_pushJavaFrame(JNIEnv* env);

extern void Backend_popJavaFrame(JNIEnv* env);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
