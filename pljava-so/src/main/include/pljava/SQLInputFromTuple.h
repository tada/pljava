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
#ifndef __pljava_SQLInputFromTuple_h
#define __pljava_SQLInputFromTuple_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/***********************************************************************
 * Provides mapping between java.sql.SQLInput and a HeapTupleHeader
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/

extern void pljava_SQLInputFromTuple_initialize(void);

extern jobject pljava_SQLInputFromTuple_create(HeapTupleHeader hth);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
