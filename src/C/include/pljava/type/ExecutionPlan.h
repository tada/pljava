/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
extern bool SPI_is_cursor_plan(void* plan);

/*****************************************************************
 * The ExecutionPlan java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the ExecutionPlan structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.ExecutionPlan instance
 */
extern jobject ExecutionPlan_create(JNIEnv* env, void* plan);

/*
 * When a statement is forgotten, i.e. not closed, its finalizer
 * will eventually make an attempt to free up the allocated
 * memory. The finalizer does this by adding an entry to a
 * "death row" to be executed when PL/Java regard it safe to do
 * so. Typically, this happens at the end of a function or
 * trigger call.
 */
extern void ExecutionPlan_executeAllOnDeathRow(JNIEnv* env);

#ifdef __cplusplus
}
#endif
#endif
