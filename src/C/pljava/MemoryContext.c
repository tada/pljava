/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include "pljava/MemoryContext.h"

/* Single linked list of callback definitions. Each containing
 * a function and a client data pointer.
 */
struct _MctxCBLink;
typedef struct _MctxCBLink MctxCBLink;

struct _MctxCBLink
{
	MctxCBLink*  next;
	EndOfScopeCB callback;
	void*        clientData;
};

/* Extended version of the MemoryContextMethods structure that holds on
 * to the original structure and a callback chain.
 */
typedef struct {
	MemoryContextMethods  methods;
	MemoryContextMethods* prev;
	MctxCBLink*           cbChain;
} ExtendedCtxMethods;

MemoryContext returnValueContext;

/**
 * Calls all user defined callbacks with their associated clientData as the
 * first argument and true as the second. Restores the original methods and finally
 * calls the original delete function for the MemoryContext.
 */
static void mctxDelete(MemoryContext ctx)
{
	ExtendedCtxMethods* exm = (ExtendedCtxMethods*)ctx->methods;
	MctxCBLink* cbs = exm->cbChain;
	while(cbs != 0)
	{
		MctxCBLink* nxt = cbs->next;
		(*cbs->callback)(cbs->clientData, true);
		pfree(cbs);
		cbs = nxt;
	}
	ctx->methods = exm->prev;
	pfree(exm);
	(*ctx->methods->delete)(ctx);
}

/**
 * Calls all user defined callbacks with their associated clientData as the
 * first argument and false as the second. Finally calls the original reset
 * function for the MemoryContext.
 */
static void mctxReset(MemoryContext ctx)
{
	ExtendedCtxMethods* exm = (ExtendedCtxMethods*)ctx->methods;
	MctxCBLink* cbs = exm->cbChain;
	while(cbs != 0)
	{
		(*cbs->callback)(cbs->clientData, false);
		cbs = cbs->next;
	}
	(*exm->prev->reset)(ctx);
}

static void* parentContextAlloc(MemoryContext ctx, size_t size)
{
	MemoryContext pctx = ctx->parent;
	if(pctx == 0)
		elog(ERROR, "TopMemoryContext cannot be callback enhanced");
	return MemoryContextAlloc(pctx, size);
}

/**
 * Ensures that the given context has an extended MemoryContextMethods struct
 * capable of holding on to user defined callbacks.
 */
static ExtendedCtxMethods* MemoryContext_ensureCallbackCapability(MemoryContext ctx)
{
	ExtendedCtxMethods* exm;
	MemoryContextMethods* methods = ctx->methods;

	if(methods->reset == mctxReset)
		exm = (ExtendedCtxMethods*)methods;
	else
	{
		exm = (ExtendedCtxMethods*)parentContextAlloc(ctx, sizeof(ExtendedCtxMethods));
		memcpy(exm, methods, sizeof(MemoryContextMethods));
		exm->prev = methods;
		exm->cbChain = 0;
		exm->methods.delete = mctxDelete;
		exm->methods.reset  = mctxReset;
		ctx->methods = (MemoryContextMethods*)exm;
	}
	return exm;
}

/**
 * Returns true if the MemoryContext has callback capabilities installed.
 */
bool MemoryContext_hasCallbackCapability(MemoryContext ctx)
{
	return ctx->methods->reset == mctxReset;
}

/**
 * Adds an end-of-scope callback from a MemoryContext.
 *
 * @param ctx
 * 		The context where the callback is registered
 * @param func
 *      The callback function that will be called when the context is
 *      either reset or deleted.
 * @param clientData
 *      Data to be passed to the callback function
 */     
void MemoryContext_addEndOfScopeCB(MemoryContext ctx, EndOfScopeCB func, void* clientData)
{
	ExtendedCtxMethods* exm = MemoryContext_ensureCallbackCapability(ctx);
	MctxCBLink* link = (MctxCBLink*)parentContextAlloc(ctx, sizeof(MctxCBLink));
	link->callback = func;
	link->clientData = clientData;
	link->next = exm->cbChain;
	exm->cbChain = link;
}

/**
 * Removes an end-of-scope callback from a MemoryContext. The callback is
 * identified using the function and the clientData.
 *
 * @param ctx
 * 		The context where the callback is registered.
 * @param func
 *      The callback function.
 * @param clientData
 *      Data to registered with the callback function
 */
void MemoryContext_removeEndOfScopeCB(MemoryContext ctx, EndOfScopeCB func, void* clientData)
{
	MemoryContextMethods* methods = ctx->methods;

	if(methods->reset == mctxReset)
	{
		ExtendedCtxMethods* exm = (ExtendedCtxMethods*)methods;
		MctxCBLink* prev = 0;
		MctxCBLink* curr = exm->cbChain;
		while(curr != 0)
		{
			if(curr->callback == func && curr->clientData == clientData)
			{
				if(prev == 0)
					exm->cbChain = curr->next;
				else
					prev->next = curr->next;
				pfree(curr);
				break;
			}
			prev = curr;
			curr = curr->next;
		}
	}
}

MemoryContext MemoryContext_switchToReturnValueContext()
{
	return MemoryContextSwitchTo(returnValueContext);
}
