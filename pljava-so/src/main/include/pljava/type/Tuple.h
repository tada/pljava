/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Tuple_h
#define __pljava_Tuple_h

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
extern jobject pljava_Tuple_create(HeapTuple tuple);
extern jobject pljava_Tuple_internalCreate(HeapTuple tuple, bool mustCopy);
extern jobjectArray pljava_Tuple_createArray(
	HeapTuple* tuples, jint size, bool mustCopy);

/*
 * Return a java object at given index from a HeapTuple (with a best effort to
 * produce an object of class rqcls if it is not null).
 */
extern jobject pljava_Tuple_getObject(
	TupleDesc tupleDesc, HeapTuple tuple, int index, jclass rqcls);
extern void pljava_Tuple_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
