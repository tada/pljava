/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_Oid_h
#define __pljava_Oid_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Oid java class represents the native Oid.
 * 
 * Author: Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.Oid instance
 */
extern jobject Oid_create(JNIEnv* env, Oid oid);

/*
 * Extract the native Oid from a Java Oid.
 */
extern Oid Oid_getOid(JNIEnv* env, jobject joid);

/*
 * Map a java.sql.Types SQL type to an Oid.
 */
extern Oid Oid_forSqlType(int sqlType);

#ifdef __cplusplus
}
#endif
#endif
