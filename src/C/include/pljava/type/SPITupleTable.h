/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_SPITupleTable_h
#define __pljava_SPITupleTable_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <executor/spi.h>

/*****************************************************************
 * The SPITupleTable java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the SPITupleTable structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.SPITupleTable instance
 */
extern jobject SPITupleTable_create(JNIEnv* env, SPITupleTable* SPITupleTable);

#ifdef __cplusplus
}
#endif
#endif
