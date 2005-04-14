/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_ComplexType_h
#define __pljava_type_ComplexType_h

#include "pljava/HashMap.h"
#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupdesc.h>

struct ComplexType_;
typedef struct ComplexType_* ComplexType;

/*
 * Create a new ComplexType for the type given by typeId.
 */
extern ComplexType ComplexType_allocInstance(TypeClass complexTypeClass, Oid typeId);

/*
 * Obtain a new ComplexType for the given oid and typeid. The type will be cached if
 * the oid is something other than RECORDOID.
 */
#if (PGSQL_MAJOR_VER < 8)
extern ComplexType ComplexType_createType(TypeClass complexTypeClass, HashMap idCache, Oid typid, TupleDesc tupleDesc);
#else
extern ComplexType ComplexType_createType(TypeClass complexTypeClass, HashMap idCache, HashMap modCache, TupleDesc tupleDesc);
#endif

/**
 * Allocate a new ComplexType class
 */
extern TypeClass ComplexTypeClass_alloc(const char* typeName);

#ifdef __cplusplus
}
#endif
#endif
