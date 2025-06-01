/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
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

/**
 * Also me. :-)
 */
#include <postgres.h>
#include <catalog/pg_namespace.h>
#include <utils/builtins.h>
#include <utils/bytea.h>
#include <utils/typcache.h>
#include <libpq/pqformat.h>
#include <funcapi.h>

#include "pljava/type/FDW_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/SQLInputFromChunk.h"
#include "pljava/SQLOutputToChunk.h"
#include "pljava/SQLInputFromTuple.h"
#include "pljava/SQLOutputToTuple.h"

/**
 * Totally ignore the details...
 *
 * Assume I'm reading a zip or jar file...
 */


/**
 * (TBD..)
 */
Datum FDW_scan_plan(FDW_Table fdw, PG_FUNCTION_ARGS)
{
    // return object that will be stored as 'scan_state' and
    // used in subsequent calls
	Datum datum = NULL;

	return datum;
}

/**
 * Jave method that verifies the zip file exists and readable.
 * It returns a 'scan_state' object that will be provided in
 * all subsequent calls.
 */
Datum FDW_scan_open(FDW_Table fdw, PG_FUNCTION_ARGS)
{
	jobject obj = NULL;

    // placeholder
	// txt = PG_GETARG_CSTRING(0);
	// jstr = String_createJavaStringFromNTS(txt);

    // the action
	// obj  = pljava_Function_fdwParseInvoke(fdw->parse, jstr, fdw->sqlTypeName);
	// JNI_deleteLocalRef(jstr);

	return _FDW_coerceObject((Type)fdw, obj);
}

/**
 * Call java method that opens the zip file and reads the first (or next)
 * record. The java method is provided the 'scan_state' from above,
 * it's where the open InputStream would be kept, etc. The java function
 * must return something that can be passed to the existing ResultSet
 * code. (Probably.) I don't think we need to require the java code
 * to return an actual ResultSet because that requires a ton of unnecessary
 * methods - it's probably enough to use a Map<>.
 *
 * How we'll handle pl/java-implemented UDT is left as an exercise for
 * the user.
 */
Datum FDW_scan_next(FDW_Table fdw, PG_FUNCTION_ARGS)
{
	jobject obj = NULL;

    // call java
    // use existing ResultSet logic?

	return _FDW_coerceObject((Type)fdw, obj);
}

/**
 * Call java method that closes the zip file and releases any other
 * resources.
 */
Datum FDW_scan_close(FDW_Table fdw, PG_FUNCTION_ARGS)
{
	jobject obj = NULL;

    // call java
    // delete internal reference to scan_state

	return _FDW_coerceObject((Type)fdw, obj);
}
