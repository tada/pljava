/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_MemoryContext_h
#define __pljava_MemoryContext_h

#include <postgres.h>
#include <utils/memutils.h>
#include "pljava/HashMap.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * PL/Java MemoryContext callback extension. Adds end of scope callback
 * capabilities to the MemoryContext by intercepting the reset and
 * delete calls.
 * 
 * @author Thomas Hallgren
 ***********************************************************************/

extern MemoryContext returnValueContext;

/*
 * The callback function. The second argument is set to false when
 * the MemoryContext is reset and to true when it is deleted.
 */
typedef void (*EndOfScopeCB)(MemoryContext ctx, bool isDelete);

/**
 * Adds an end-of-scope callback from a MemoryContext.
 *
 * @param ctx
 * 		The context where the callback is registered
 * @param func
 *      The callback function that will be called when the context is
 *      either reset or deleted.
 */
extern void MemoryContext_addEndOfScopeCB(MemoryContext ctx, EndOfScopeCB func);

/**
 * Obtains the native cache associated with this MemoryContex.
 */
extern HashMap MemoryContext_getNativeCache(MemoryContext ctx);

/**
 * Returns true if the MemoryContext has callback capabilities installed.
 */
extern bool MemoryContext_hasCallbackCapability(MemoryContext ctx);

/**
 * Removes an end-of-scope callback from a MemoryContext. The callback is
 * identified using the function pointer.
 *
 * @param ctx
 * 		The context where the callback is registered.
 * @param func
 *      The callback function.
 */
extern void MemoryContext_removeEndOfScopeCB(MemoryContext ctx, EndOfScopeCB func);

/**
 * Associates a native cache with this MemoryContex.
 */
extern void MemoryContext_setNativeCache(MemoryContext ctx, HashMap nativeCache);

/*
 * Switch memory context to a context that is durable between calls to
 * the call manager but not durable between queries. The old context is
 * returned. This method can be used when creating values that will be
 * returned from the Pl/Java routines. Once the values have been created
 * a call to MemoryContextSwitchTo(oldContext) must follow where oldContext
 * is the context returned from this call.
 */
extern MemoryContext MemoryContext_switchToReturnValueContext(void);

/*
 * Returns the nativeCache that's currently in effect, i.e. the nativeCache
 * of the returnValueContext.
 */
extern HashMap MemoryContext_getCurrentNativeCache(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_MemoryContext_h */
