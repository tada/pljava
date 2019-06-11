/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
#ifndef __pljava_TupleDesc_h
#define __pljava_TupleDesc_h

#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupdesc.h>

/********************************************************************
 * The TupleDesc java class provides JNI
 * access to some of the attributes of the TupleDesc structure.
 * 
 * @author Thomas Hallgren
 ********************************************************************/
 
/*
 * Returns the Type of the column at index. If the returned Type
 * is NULL a Java exception has been initiated and the caller
 * should return to Java ASAP.
 */
extern Type pljava_TupleDesc_getColumnType(TupleDesc tupleDesc, int index);

/*
 * Create the org.postgresql.pljava.TupleDesc instance
 */
extern jobject pljava_TupleDesc_create(TupleDesc tDesc);
extern jobject pljava_TupleDesc_internalCreate(TupleDesc tDesc);
extern void pljava_TupleDesc_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
