/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
 * @author Thomas Hallgren
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
