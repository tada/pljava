/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_ResultSetProvider_h
#define __pljava_type_ResultSetProvider_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <access/tupdesc.h>

/*******************************************************************
 * The ResultSetProvider is a multi-row ResultSet used when a function
 * returns a SETOF a complex type or a record.
 * 
 * @author Thomas Hallgren
 *******************************************************************/

/*
 * Create a Type for a specific Record type.
 */
extern Type ResultSetProvider_createType(Oid typid, TupleDesc tupleDesc);

#ifdef __cplusplus
}
#endif
#endif
