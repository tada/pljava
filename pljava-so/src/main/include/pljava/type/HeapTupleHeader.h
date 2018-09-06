/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_HeapTupleHeader_h
#define __pljava_type_HeapTupleHeader_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/*****************************************************************
 * The HeapTupleHeader java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the HeapTupleHeader structure.
 * 
 * @author Thomas Hallgren
 *
 * (As of git commit a4f6c9e, there are uses of this C interface,
 * but no uses of the Java class.)
 *****************************************************************/

extern jobject HeapTupleHeader_getTupleDesc(HeapTupleHeader ht);

extern jobject HeapTupleHeader_getObject(
	JNIEnv* env, jlong hth, jlong jtd, jint attrNo, jclass rqcls);

extern void HeapTupleHeader_free(JNIEnv* env, jlong hth);

#ifdef __cplusplus
}
#endif
#endif
