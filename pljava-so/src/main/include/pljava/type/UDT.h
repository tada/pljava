/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_UDT_h
#define __pljava_type_UDT_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * The UDT class extends the Type and adds the members necessary to
 * perform standard Postgres input/output and send/receive conversion.
 * 
 * @author Thomas Hallgren
 *
 **************************************************************************/

struct UDT_;
typedef struct UDT_* UDT;

extern Datum UDT_input(UDT udt, PG_FUNCTION_ARGS);
extern Datum UDT_output(UDT udt, PG_FUNCTION_ARGS);
extern Datum UDT_receive(UDT udt, PG_FUNCTION_ARGS);
extern Datum UDT_send(UDT udt, PG_FUNCTION_ARGS);

extern bool UDT_isScalar(UDT udt);

/*
 * Register that a Java class is the UDT implementation for a PostgreSQL typeID.
 *
 * Only one of hasTupleDesc / isJavaBasedScalar can be true, and the parseMH
 * argument is only used in the scalar case. A readMH is needed for the scalar
 * or the composite case.
 *
 * If null is supplied for readMH, or for parseMH in the scalar case, an upcall
 * to Java will be made to obtain the handle. Handles can be passed as arguments
 * here as a shortcut in case the registration is coming from Function.c and the
 * handles are already known.
 */
extern UDT UDT_registerUDT(jclass clazz, Oid typeId, Form_pg_type pgType,
	bool hasTupleDesc, bool isJavaBasedScalar, jobject parseMH, jobject readMH);

typedef Datum (*UDTFunction)(UDT udt, PG_FUNCTION_ARGS);

#ifdef __cplusplus
}
#endif
#endif
