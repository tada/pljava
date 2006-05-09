/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
#ifndef __pljava_type_Array_h
#define __pljava_type_Array_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * Array related stuff.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
extern ArrayType* createArrayType(jsize nElems, size_t elemSize, Oid elemType);
#else
extern ArrayType* createArrayType(jsize nElems, size_t elemSize, Oid elemType, bool withNulls);
extern void arraySetNull(bits8* bitmap, int offset, bool flag);
extern bool arrayIsNull(const bits8* bitmap, int offset);
#endif

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
