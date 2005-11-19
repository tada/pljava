/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_HeapTupleHeader_h
#define __pljava_type_HeapTupleHeader_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/*****************************************************************
 * The HeapTupleHeader java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the HeapTupleHeader structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.internal.HeapTupleHeader instance
 */
extern jobject HeapTupleHeader_create(HeapTupleHeader tuple);

#ifdef __cplusplus
}
#endif
#endif
