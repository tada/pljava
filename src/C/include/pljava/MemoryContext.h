/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_MemoryContext_h
#define __pljava_MemoryContext_h

#include <postgres.h>
#include <utils/memutils.h>

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
typedef void (*EndOfScopeCB)(MemoryContext origin, bool isDelete);

/*
 * Callback function to be used when an allocated element is released
 * using pfree. The callback is called just prior to the real free so
 * deadElement is still valid here.
 */
typedef void (*FreeCB)(MemoryContext origin, void* deadElement);

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
 * Returns the current clientData value.
 */
extern void* MemoryContext_getClientData(MemoryContext ctx);

/**
 * Assings a clientData value to be passed to the end-of-scope callbacks
 * for this context.
 */
extern void MemoryContext_setClientData(MemoryContext ctx, void* clientData);


/**
 * Installs a callback that is called everytime a free is issued on the
 * given context. The old callback is returned. It is valid to pass NULL
 * as a callback.
 */
extern FreeCB MemoryContext_setFreeCB(MemoryContext ctx, FreeCB func);

/**
 * Returns true if the MemoryContext has callback capabilities installed.
 */
extern bool MemoryContext_hasCallbackCapability(MemoryContext ctx);

/**
 * Removes an end-of-scope callback from a MemoryContext. The callback is
 * identified using the function and the clientData.
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
extern MemoryContext MemoryContext_switchToReturnValueContext(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_MemoryContext_h */
