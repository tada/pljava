/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
 * @author Thomas Hallgren
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
