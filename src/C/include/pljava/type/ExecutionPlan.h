/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_ExecutionPlan_h
#define __pljava_ExecutionPlan_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

/* Class 07 - Dynamic SQL Error */
#define ERRCODE_PARAMETER_COUNT_MISMATCH	MAKE_SQLSTATE('0','7', '0','0','1')

extern Oid SPI_getargtypeid(void* plan, int argIndex);
extern int SPI_getargcount(void* plan);

/*****************************************************************
 * The ExecutionPlan java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the ExecutionPlan structure.
 * 
 * Author: Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.ExecutionPlan instance
 */
extern jobject ExecutionPlan_create(JNIEnv* env, void* plan);

#ifdef __cplusplus
}
#endif
#endif
