/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_PLJavaMemoryContext_h
#define __pljava_PLJavaMemoryContext_h

#include <postgres.h>
#include <utils/memutils.h>
#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * PL/Java MemoryContext. Each chunk allocated in this context can be
 * associated with a Java weak reference that appoints a Java object acting
 * as a wrapper for the chunk. The context provides rapid mapping from
 * a native pointer to its java object by extending the allocation header.
 * A callback takes care of invalidating a wrapper when its associated
 * memory chunk is freed.
 * 
 * @author Thomas Hallgren
 ***********************************************************************/

/* The stale object callback signature.
 */
typedef void (*StaleObjectCB)(JNIEnv* env, jobject object);

/**
 * Creates a PLJavaMemoryContext
 *
 * @param parentContext
 *      The context that will be the owner of this context.
 * @param ctxName
 *		The name of the new context
 * @param func
 *      The callback function that will be called when a memory chunk associated
 * 		with a java object is deleted.
 */
extern MemoryContext PLJavaMemoryContext_create(MemoryContext parentContext, const char* ctxName, StaleObjectCB func);

/**
 * Returns a local reference to the Java object associated with the
 * given pointer or zero if the pointer did not belong to a PLJavaMemoryContext
 * or if no such object was found.
 */
extern jobject PLJavaMemoryContext_getJavaObject(JNIEnv* env, void* pointer);

/**
 * Creates a weak binding from the given pointer to the given object. This method will
 * elog(ERROR) if the pointer does not belong to a PLJavaMemoryContext
 */
extern void PLJavaMemoryContext_setJavaObject(JNIEnv* env, void* pointer, jobject object);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_PLJavaMemoryContext_h */
