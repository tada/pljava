/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_JavaHandle_h
#define __pljava_JavaHandle_h

#include "pljava/type/Type.h"
#include "pljava/HashMap.h"
#include "pljava/MemoryContext.h"
#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * The JavaHandle is a Java class that maintains a pointer to a piece of
 * memory allocated with a life cycle that spans a call from the PostgreSQL
 * function manager (using palloc()). Since Java uses a garbage collector
 * and since an object in the Java domain might survive longer than memory
 * allocated using palloc(), some code must assert that pointers from Java
 * objects to such memory is cleared when the function manager call ends.
 * 
 * @author Thomas Hallgren
 *************************************************************************/

/*
 * Callback that is called from a MemoryContext when it is deleted or
 * reset. Marks all java objects that still refer to objects in the cache as
 * stale.
 */
extern void JavaHandle_releaseCache(HashMap cache);

/*
 * The JavaHandle_init method will assing the pointer value to a Java
 * JavaHandle object and put a Java WeakReference to this object into
 * a HashMap keyed by the native pointer (see HashMap_putByOpaque).
 * This binding serves two purposes; a) A cache so that only one Java object
 * is created for one specific pointer and b) A list of Java objects to be
 * cleared before the current memory context used by palloc goes out of
 * scope.
 */
extern void JavaHandle_init(jobject self, void* nativePointer);

/*
 * Assing the pointer to the java object without adding it to the native
 * cache.
 */
extern void JavaHandle_setPointer(jobject nativeStruct, void* nativePointer);

/*
 * Return the pointer value stored in a Java JavaHandle.
 */
extern void* JavaHandle_getStruct(jobject nativeStruct);

/*
 * Reset the pointer in the java object and remove the entry from the weak cache.
 */
extern void* JavaHandle_releasePointer(jobject nativeStruct);

/*
 * Allocates a new TypeClass and assigns a default coerceObject method used by
 * all JavaHandle derivates.
 */
extern TypeClass JavaHandleClass_alloc(const char* name);

#ifdef __cplusplus
}
#endif
#endif
