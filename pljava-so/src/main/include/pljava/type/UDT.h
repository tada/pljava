/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
 * Non-null values for {parse,read,write,toString}MH can be passed as arguments
 * here as a shortcut in case the registration is coming from Function.c and the
 * handles are already known (they are in fact Invocables, but were method
 * handles before, and MH still suggests their purpose and makes shorter names).
 * If passed as NULL and needed, upcalls will be made to obtain them.
 */
extern UDT UDT_registerUDT(jclass clazz, Oid typeId, Form_pg_type pgType,
	bool hasTupleDesc, bool isJavaBasedScalar, jobject parseMH, jobject readMH,
	jobject writeMH, jobject toStringMH);

typedef Datum (*UDTFunction)(UDT udt, PG_FUNCTION_ARGS);

#ifdef __cplusplus
}
#endif
#endif
