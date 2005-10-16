/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_MemoryContextManaged_h
#define __pljava_MemoryContextManaged_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * The MemoryContextManaged is a Java class that maintains a pointer to a
 * piece of memory allocated in the special JavaMemoryContext.
 *
 * @author Thomas Hallgren
 *************************************************************************/

extern MemoryContext JavaMemoryContext;

/*
 * Return the pointer value stored in a Java wrapper object.
 */
extern void* MemoryContextManaged_getPointer(JNIEnv* env, jobject javaWrapper);

/*
 * Allocates a new TypeClass and assigns a default coerceObject method used by
 * all MemoryContextManaged derivates.
 */
extern TypeClass MemoryContextManagedClass_alloc(const char* name);

#ifdef __cplusplus
}
#endif
#endif
