/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_Portal_h
#define __pljava_Portal_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <utils/portal.h>

/*****************************************************************
 * The Portal java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the Portal structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.Portal instance
 */
extern jobject Portal_create(JNIEnv* env, Portal portal);

#ifdef __cplusplus
}
#endif
#endif
