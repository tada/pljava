/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
 * Author: Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.Portal instance
 */
extern jobject Portal_create(JNIEnv* env, Portal portal);

#ifdef __cplusplus
}
#endif
#endif
