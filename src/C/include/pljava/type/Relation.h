/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_Relation_h
#define __pljava_Relation_h

#include "pljava/type/NativeStruct.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <utils/rel.h>

/*******************************************************************
 * The Relation java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the Relation structure.
 * 
 * Author: Thomas Hallgren
 *******************************************************************/

/*
 * Create an instance of org.postgresql.pljava.Relation
 */
extern jobject Relation_create(JNIEnv* env, Relation rel);

#ifdef __cplusplus
}
#endif
#endif
