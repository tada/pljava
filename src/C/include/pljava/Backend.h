/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_Backend_h
#define __pljava_Backend_h

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Backend contains the call handler, initialization of the
 * PL/Java, access to config variables, and logging.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

extern JNIEnv* Backend_getMainEnv(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
