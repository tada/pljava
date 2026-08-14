/*
 * Copyright (c) 2004-2026 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#ifndef __pljava_type_Array_h
#define __pljava_type_Array_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupmacs.h>

/***********************************************************************
 * Array related stuff.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/

extern ArrayType* createArrayType(jsize nElems, size_t elemSize, Oid elemType, bool withNulls);
extern void arraySetNull(uint8* bitmap, int offset, bool flag);
extern bool arrayIsNull(const uint8* bitmap, int offset);

extern Type Array_fromOid(Oid typeId, Type elementType);
extern Type Array_fromOid2(Oid typeId, Type elementType, DatumCoercer coerceDatum, ObjectCoercer coerceObject);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
