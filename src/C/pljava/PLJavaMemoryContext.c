/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/Backend.h"
#include "pljava/PLJavaMemoryContext.h"

struct _PLJavaChunk;

typedef struct _PLJavaChunk PLJavaChunk;

struct _PLJavaChunk
{
	PLJavaChunk* next;
	PLJavaChunk* prev;
	jweak        weak;
	bool         removed;
};

typedef struct {
	MemoryContextMethods thisMethods;
	MemoryContextMethods superMethods;
	StaleObjectCB staleObjectCB;
	PLJavaChunk* chunkList;
} PLJavaContextMethods;

static void* PLJavaAlloc(MemoryContext context, Size size);
static void  PLJavaFree(MemoryContext context, void* pointer);
static void* PLJavaRealloc(MemoryContext context, void* pointer, Size size);
static Size  PLJavaGetChunkSpace(MemoryContext context, void* pointer);
static void  PLJavaReset(MemoryContext context);
static void  PLJavaDelete(MemoryContext context);
#ifdef MEMORY_CONTEXT_CHECKING
static void PLJavaCheck(MemoryContext context);
#endif

#define PLJAVACHUNKHEADERSIZE MAXALIGN(sizeof(PLJavaChunk))
#define SUPER(ctx) ((PLJavaContextMethods*)(ctx)->methods)->superMethods
#define FULLHEADERSIZE (STANDARDCHUNKHEADERSIZE + PLJAVACHUNKHEADERSIZE)
/**
 * get the java object from the header.
 */
jobject PLJavaMemoryContext_getJavaObject(JNIEnv* env, void* pointer)
{
	jobject object = 0;
	MemoryContext ctx = GetMemoryChunkContext(pointer);
	if(ctx->methods->alloc == PLJavaAlloc)
	{
		PLJavaChunk* chunk = (PLJavaChunk*)((char*)pointer - FULLHEADERSIZE);
		jweak weak = chunk->weak;
		if(weak != 0)
			object = (*env)->NewLocalRef(env, weak);
	}
	return object;
}

/**
 * assign java object to palloc'ed buffer.
 */
void PLJavaMemoryContext_setJavaObject(JNIEnv* env, void* pointer, jobject object)
{
	MemoryContext ctx = GetMemoryChunkContext(pointer);
	if(ctx->methods->alloc == PLJavaAlloc)
	{
		PLJavaChunk* chunk = (PLJavaChunk*)((char*)pointer - FULLHEADERSIZE);
		jweak weak = chunk->weak;
		if(weak != 0)
			(*env)->DeleteWeakGlobalRef(env, weak);

		if(object == 0)
			weak = 0;
		else
			weak = (*env)->NewWeakGlobalRef(env, object);
		chunk->weak = weak;
	}
}

/**
 * Yank the java object from the header
 * 
 * |next,prev,weak|StandardChunkHeader|data|
 *                                    ^
 * incoming pointer ------------------+
 * becomes
 * 
 * |StandardChunkHeader|next,prev,weak|data|
 *                     ^
 * returned pointer
 */
static PLJavaChunk* moveStandardHeaderLeft(char* pointer)
{
	PLJavaChunk* chunk = (PLJavaChunk*)(pointer - FULLHEADERSIZE);
	StandardChunkHeader header = *((StandardChunkHeader*)((char*)pointer - STANDARDCHUNKHEADERSIZE));
	PLJavaChunk* next = chunk->next;
	PLJavaChunk* prev = chunk->prev;
	jweak        weak = chunk->weak;
	bool         removed = chunk->removed;

	PLJavaChunk* movedChunk = (PLJavaChunk*)(pointer - PLJAVACHUNKHEADERSIZE);
	StandardChunkHeader* movedHeader = (StandardChunkHeader*)(pointer - FULLHEADERSIZE);
	movedHeader->context = header.context;
	movedHeader->size = header.size + PLJAVACHUNKHEADERSIZE;
#ifdef MEMORY_CONTEXT_CHECKING
	movedHeader->requested_size = header.requested_size + PLJAVACHUNKHEADERSIZE;
#endif
	movedChunk->next = next;
	movedChunk->prev = prev;
	movedChunk->weak = weak;
	movedChunk->removed = removed;
	return movedChunk;
}

/**
 * Insert the java object into the header:
 * 
 * |StandardChunkHeader|next,prev,weak|data|
 *                     ^
 * incoming pointer ---+
 *
 * becomes:
 * 
 * |next,prev,weak|StandardChunkHeader|data|
 * ^
 * returned pointer
 */
static PLJavaChunk* moveStandardHeaderRight(char* pointer)
{
	PLJavaChunk* chunk = (PLJavaChunk*)pointer;
	StandardChunkHeader header = *((StandardChunkHeader*)(pointer - STANDARDCHUNKHEADERSIZE));
	PLJavaChunk* next = chunk->next;
	PLJavaChunk* prev = chunk->prev;
	jweak        weak = chunk->weak;
	bool         removed = chunk->removed;

	PLJavaChunk* movedChunk = (PLJavaChunk*)(pointer - STANDARDCHUNKHEADERSIZE);
	StandardChunkHeader* movedHeader = (StandardChunkHeader*)(pointer + PLJAVACHUNKHEADERSIZE - STANDARDCHUNKHEADERSIZE);
	movedHeader->context = header.context;
	movedHeader->size = header.size - PLJAVACHUNKHEADERSIZE;
#ifdef MEMORY_CONTEXT_CHECKING
	movedHeader->requested_size = header.requested_size - PLJAVACHUNKHEADERSIZE;
#endif
	movedChunk->next = next;
	movedChunk->prev = prev;
	movedChunk->weak = weak;
	movedChunk->removed = removed;
	return movedChunk;
}

static void markObjectStale(JNIEnv* env, MemoryContext context, jweak weak)
{
	if(weak != 0)
	{
		jobject object;
		elog(DEBUG1, "PLJavaMemoryContext(%s)->markObjectStale", context->name);
		object = (*env)->NewLocalRef(env, weak);
		(*env)->DeleteWeakGlobalRef(env, weak);
		if(object != 0)
		{
			((PLJavaContextMethods*)context->methods)->staleObjectCB(env, object);
			(*env)->DeleteLocalRef(env, object);
		}
	}	
}

static void markAllObjectsStale(JNIEnv* env, MemoryContext context)
{
	PLJavaContextMethods* methods = (PLJavaContextMethods*)context->methods;
	PLJavaChunk* chunk = methods->chunkList;
	if(chunk != 0)
	{
		PLJavaChunk* curr = chunk;
		do
		{
			markObjectStale(env, context, curr->weak);
			curr = curr->next;
		} while(curr != chunk);
		methods->chunkList = 0;
	}
}

static void reallyFree(MemoryContext context, PLJavaChunk* chunk)
{
	PLJavaContextMethods* methods;
	PLJavaChunk* next;

	if(chunk->weak != 0)
		markObjectStale(Backend_getJNIEnv(), context, chunk->weak);

	methods = (PLJavaContextMethods*)context->methods;
	next = chunk->next;
	if(methods->chunkList == chunk)
	{
		if(next == chunk)
			methods->chunkList = 0;
		else
			methods->chunkList = next;
	}
	if(chunk != next)
	{
		PLJavaChunk* prev = chunk->prev;
		next->prev = prev;
		prev->next = next;
	}
	methods->superMethods.free_p(context, moveStandardHeaderLeft((char*)chunk + FULLHEADERSIZE));
}

static void* PLJavaAlloc(MemoryContext context, Size size)
{
	/* Allocate sizeof(jobject) more then requested.
	 */
	void* pointer;
	PLJavaContextMethods* methods;
	PLJavaChunk* chunk;
	PLJavaChunk* prev;

	methods = (PLJavaContextMethods*)context->methods;
	chunk = methods->chunkList;
	if(chunk != 0)
	{
		PLJavaChunk* curr = chunk->prev;
		while(curr != chunk)
		{
			prev = curr->prev;
			if(curr->removed)
				reallyFree(context, curr);
			curr = prev;
		}
		if(chunk->removed)
			reallyFree(context, chunk);
	}

	pointer = methods->superMethods.alloc(context, size + PLJAVACHUNKHEADERSIZE);
	chunk = moveStandardHeaderRight((char*)pointer);
	prev = methods->chunkList;
	if(prev == 0)
	{
		chunk->next = chunk;
		chunk->prev = chunk;
		methods->chunkList = chunk;
	}
	else
	{
		PLJavaChunk* next = prev->next;
		chunk->next = next;
		chunk->prev = prev;
		prev->next = chunk;
		next->prev = chunk;
	}
	chunk->weak = 0;
	chunk->removed = false;
	return (char*)pointer + PLJAVACHUNKHEADERSIZE;
}

static void PLJavaFree(MemoryContext context, void* pointer)
{
	((PLJavaChunk*)((char*)pointer - FULLHEADERSIZE))->removed = true;
}

static void* PLJavaRealloc(MemoryContext context, void* pointer, Size size)
{
	pointer = moveStandardHeaderLeft((char*)pointer);
	pointer = SUPER(context).realloc(context, pointer, size + PLJAVACHUNKHEADERSIZE);
	moveStandardHeaderRight((char*)pointer);
	return (char*)pointer + PLJAVACHUNKHEADERSIZE;
}

static Size PLJavaGetChunkSpace(MemoryContext context, void* pointer)
{
	Size chunkSpace;
	pointer = moveStandardHeaderLeft((char*)pointer);
	chunkSpace = SUPER(context).get_chunk_space(context, pointer);
	moveStandardHeaderRight((char*)pointer);
	return chunkSpace;
}

static void PLJavaDelete(MemoryContext context)
{
	elog(DEBUG1, "PLJavaMemoryContext(%s)->delete", context->name);

	markAllObjectsStale(Backend_getJNIEnv(), context);
	SUPER(context).delete(context);
}

static void PLJavaReset(MemoryContext context)
{
	elog(DEBUG1, "PLJavaMemoryContext(%s)->reset", context->name);

	markAllObjectsStale(Backend_getJNIEnv(), context);
	SUPER(context).reset(context);
}

#ifdef MEMORY_CONTEXT_CHECKING
static void PLJavaCheck(MemoryContext context)
{
	PLJavaChunk* chunk = ((PLJavaContextMethods*)context->methods)->chunkList;
	if(chunk != 0)
	{
		PLJavaChunk* curr = chunk;
		do
		{
			PLJavaChunk* next = curr->next;
			moveStandardHeaderLeft(((char*)curr) + FULLHEADERSIZE);
			curr = next;
		} while(curr != chunk);
	}
	SUPER(context).check(context);
	if(chunk != 0)
	{
		PLJavaChunk* next = chunk;
		do
		{
			moveStandardHeaderRight(((char*)next) + STANDARDCHUNKHEADERSIZE);
			next = next->next;
		} while(next != chunk);
	}
}
#endif

MemoryContext PLJavaMemoryContext_create(MemoryContext parentContext, const char* ctxName, StaleObjectCB staleObjectCB)
{
	MemoryContext ctx = AllocSetContextCreate(parentContext,
									 ctxName,
									 ALLOCSET_DEFAULT_MINSIZE,
									 ALLOCSET_DEFAULT_INITSIZE,
									 ALLOCSET_DEFAULT_MAXSIZE);

	PLJavaContextMethods* pljavaMethods = (PLJavaContextMethods*)MemoryContextAlloc(parentContext, sizeof(PLJavaContextMethods));
	MemoryContextMethods* thisMethods = &pljavaMethods->thisMethods;
	memcpy(thisMethods, ctx->methods, sizeof(MemoryContextMethods));
	memcpy(&pljavaMethods->superMethods, thisMethods, sizeof(MemoryContextMethods));

	thisMethods->alloc = PLJavaAlloc;
	thisMethods->free_p = PLJavaFree;
	thisMethods->realloc = PLJavaRealloc;
	thisMethods->get_chunk_space = PLJavaGetChunkSpace;
	thisMethods->reset = PLJavaReset;
	thisMethods->delete = PLJavaDelete;
#ifdef MEMORY_CONTEXT_CHECKING
	thisMethods->check = PLJavaCheck;
#endif

	pljavaMethods->staleObjectCB = staleObjectCB;
	pljavaMethods->chunkList = 0;
	ctx->methods = (MemoryContextMethods*)pljavaMethods;
	elog(DEBUG1, "Created PLJavaMemoryContext(%s)", ctxName);
	return ctx;
}
