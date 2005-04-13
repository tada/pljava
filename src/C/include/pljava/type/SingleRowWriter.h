/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
 * Create a Type for a specific Record type.
 */
extern Type SingleRowWriter_createType(TupleDesc tupleDesc);

/*
 * Create an instance of org.postgresql.pljava.jdbc.RowProviderSet
 */
extern jobject SingleRowWriter_create(JNIEnv* env, jobject tupleDesc);

/*
 * Returns the Tuple for the SingleRowWriter and clears it.
 */
extern HeapTuple SingleRowWriter_getTupleAndClear(JNIEnv* env, jobject self);

#ifdef __cplusplus
}
#endif
#endif
