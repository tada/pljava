/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_type_HeapTupleHeader_h
#define __pljava_type_HeapTupleHeader_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/*****************************************************************
 * The HeapTupleHeader java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the HeapTupleHeader structure.
 * 
 * Author: Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.internal.HeapTupleHeader instance
 */
extern jobject HeapTupleHeader_create(JNIEnv* env, HeapTupleHeader tuple);

#ifdef __cplusplus
}
#endif
#endif
