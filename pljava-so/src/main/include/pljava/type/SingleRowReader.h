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
#ifndef __pljava_type_SingleRowReader_h
#define __pljava_type_SingleRowReader_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/htup.h>

/*****************************************************************
 * The SingleRowReader java class presents a ResultSet view of a
 * single tuple, represented by a HeapTupleHeader and a TupleDesc
 * describing its structure.
 * 
 * @author Thomas Hallgren (as HeapTupleHeader.h)
 *****************************************************************/

extern void pljava_SingleRowReader_initialize(void);

extern jobject pljava_SingleRowReader_getTupleDesc(HeapTupleHeader);

extern jobject pljava_SingleRowReader_create(HeapTupleHeader);

#ifdef __cplusplus
}
#endif
#endif
