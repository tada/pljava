/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_EOXactListener_h
#define __pljava_EOXactListener_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

/*******************************************************************
 * Functions enabling EOXactCallback registration.
 * 
 * @author Thomas Hallgren
 *
 *******************************************************************/

/**
 * Registers the one and only end-of-transaction listener. Attempts to
 * register more than will cause an exception to be thrown.
 */
extern void EOXactListener_register(JNIEnv* env, jobject listener);

/**
 * Unregister the one and only end-of-transaction listener.
 */
extern void EOXactListener_unregister(JNIEnv* env);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
