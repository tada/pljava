/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.TupleTable instance
 */
extern jobject TupleTable_create(JNIEnv* env, TupleTable tupleTable);

#ifdef __cplusplus
}
#endif
#endif
