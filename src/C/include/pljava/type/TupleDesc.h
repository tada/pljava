/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_TupleDesc_h
#define __pljava_TupleDesc_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupdesc.h>

/********************************************************************
 * The TupleDesc java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the TupleDesc structure.
 * 
 * Author: Thomas Hallgren
 ********************************************************************/

/*
 * Create the org.postgresql.pljava.TupleDesc instance
 */
extern jobject TupleDesc_create(JNIEnv* env, TupleDesc tDesc);

#ifdef __cplusplus
}
#endif
#endif
