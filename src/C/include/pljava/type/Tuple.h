/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_Tuple_h
#define __pljava_Tuple_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/*****************************************************************
 * The Tuple java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the HeapTuple structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.Tuple instance
 */
extern jobject Tuple_create(JNIEnv* env, HeapTuple tuple);

#ifdef __cplusplus
}
#endif
#endif
