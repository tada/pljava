/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_NativeStruct_h
#define __pljava_NativeStruct_h

#include "pljava/type/Type.h"
#include "pljava/HashMap.h"
#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * The NativeStruct is a Java class that maintains a pointer to a piece of
 * memory allocated with a life cycle that spans a call from the PostgreSQL
 * function manager (using palloc()). Since Java uses a garbage collector
 * and since an object in the Java domain might survive longer than memory
 * allocated using palloc(), some code must assert that pointers from Java
 * objects to such memory is cleared when the function manager call ends.
 * 
 * @author Thomas Hallgren
 *************************************************************************/

/*
 * Associates a HashMap cache with the givem memory context so that the
 * cache is cleared if the context is reset and the cache is deleted
 * any previous cache is restored when the context is deleted.
 */ 
extern void NativeStruct_associateCache(MemoryContext ctx);

/*
 * Obtain a locally bound object form the weak cache. This method
 * will return NULL if no such object is found.
 */
extern jobject NativeStruct_obtain(JNIEnv* env, void* nativePointer);

/*
 * The NativeStruct_init method will assing the pointer value to a Java
 * NativeStruct object and put a Java WeakReference to this object into
 * a HashMap keyed by the native pointer (see HashMap_putByOpaque).
 * This binding serves two purposes; a) A cache so that only one Java object
 * is created for one specific pointer and b) A list of Java objects to be
 * cleared before the current memory context used by palloc goes out of
 * scope.
 */
extern void NativeStruct_init(JNIEnv* env, jobject self, void* nativePointer);

/*
 * Assing the pointer to the java object without adding it to the HashMap.
 */
extern void NativeStruct_setPointer(JNIEnv* env, jobject nativeStruct, void* nativePointer);

/*
 * Return the pointer value stored in a Java NativeStruct.
 */
extern void* NativeStruct_getStruct(JNIEnv* env, jobject nativeStruct);

/*
 * Reset the pointer in the java object and remove the entry from the weak cache.
 */
extern void* NativeStruct_releasePointer(JNIEnv* env, jobject nativeStruct);

/*
 * Allocates a new TypeClass and assigns a default coerceObject method used by
 * all NativeStruct derivates.
 */
extern TypeClass NativeStructClass_alloc(const char* name);

#ifdef __cplusplus
}
#endif
#endif
