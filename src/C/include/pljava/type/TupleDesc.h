/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
 * @author Thomas Hallgren
 ********************************************************************/
 
/*
 * Returns the Type of the column at index. If the returned Type
 * is NULL a Java exception has been initiated and the caller
 * should return to Java ASAP.
 */
extern Type TupleDesc_getColumnType(JNIEnv* env, TupleDesc tupleDesc, int index);

/*
 * Create the org.postgresql.pljava.TupleDesc instance
 */
extern jobject TupleDesc_create(JNIEnv* env, TupleDesc tDesc);

/*
 * Obtain a TupleDesc for a specific Oid from the TupleDesc cache. If no
 * TupleDesc is found, one is created in the TopMemoryContext and added
 * to the cache.
 */
extern TupleDesc TupleDesc_forOid(Oid oid);

#ifdef __cplusplus
}
#endif
#endif
