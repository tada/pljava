/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_SingleRowWriter_h
#define __pljava_SingleRowWriter_h

#include "pljava/PgObject.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupdesc.h>

/*******************************************************************
 * The SingleRowWriter is a single-row ResultSet used when providing
 * values for each row a set that is returned from a function.
 * 
 * Author: Thomas Hallgren
 *******************************************************************/

/*
 * Create an instance of org.postgresql.pljava.jdbc.RowProviderSet
 */
extern jobject SingleRowWriter_create(JNIEnv* env, TupleDesc tupleDesc);

/*
 * Returns the Tuple for the SingleRowWriter and clears it.
 */
extern HeapTuple SingleRowWriter_getTupleAndClear(JNIEnv* env, jobject self);

#ifdef __cplusplus
}
#endif
#endif
