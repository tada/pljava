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

/*
 * Switch memory context to a context that is durable between calls to
 * the call manager but not durable between queries. The old context is
 * returned. This method can be used when creating values that will be
 * returned from the Pl/Java routines. Once the values have been created
 * a call to MemoryContextSwitchTo(oldContext) must follow where oldContext
 * is the context returned from this call.
 */
extern MemoryContext MemoryContext_switchToUpperContext(void);

/*
 * Returns the nativeCache that's currently in effect.
 */
extern HashMap MemoryContext_getCurrentNativeCache(void);

/*
 * Obtain a locally bound object form the weak cache. This method
 * will return NULL if no such object is found.
 */
extern jobject MemoryContext_lookupNative(JNIEnv* env, void* nativePointer);

/*
 * Remove the native pointer from the cache if present.
 */
extern void MemoryContext_dropNative(JNIEnv* env, void* nativePointer);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_MemoryContext_h */
