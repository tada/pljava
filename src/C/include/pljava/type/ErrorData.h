/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_ErrorData_h
#define __pljava_ErrorData_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The ErrorData java class represents the native ErrorData.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

#if (PGSQL_MAJOR_VER < 8)

typedef struct ErrorData
{
	int   elevel;		/* error level */
	int   sqlerrcode;	/* encoded ERRSTATE */
	char* message;		/* primary error message */
} ErrorData;

#endif

/*
 * Create the org.postgresql.pljava.internal.ErrorData instance
 */
extern jobject ErrorData_create(JNIEnv* env, ErrorData* errorData);

/*
 * Extract the native ErrorData from a Java ErrorData.
 */
extern ErrorData* ErrorData_getErrorData(JNIEnv* env, jobject jerrorData);


#ifdef __cplusplus
}
#endif
#endif
