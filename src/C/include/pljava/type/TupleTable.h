/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_TupleTable_h
#define __pljava_TupleTable_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <executor/tuptable.h>

/*****************************************************************
 * The TupleTable java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the TupleTable structure.
 * 
 * Author: Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.TupleTable instance
 */
extern jobject TupleTable_create(JNIEnv* env, TupleTable tupleTable);

#ifdef __cplusplus
}
#endif
#endif
