/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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

extern JNIEnv* Backend_getMainEnv(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
