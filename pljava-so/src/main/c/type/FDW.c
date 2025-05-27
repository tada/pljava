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

#include "pljava/type/UDT_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/Tuple.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/SQLInputFromChunk.h"
#include "pljava/SQLOutputToChunk.h"
#include "pljava/SQLInputFromTuple.h"
#include "pljava/SQLOutputToTuple.h"


Datum UDT_input(UDT udt, PG_FUNCTION_ARGS)
{
	jstring jstr;
	jobject obj;
	char* txt;

	if(!UDT_isScalar(udt))
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("UDT with Oid %d is not scalar", Type_getOid((Type)udt))));

	noTypmodYet(udt, fcinfo);

	txt = PG_GETARG_CSTRING(0);

	if(Type_getLength((Type)udt) == -2)
	{
		/*
		 * ASSUMPTION 1 is in play here. UDT_input is passed a cstring holding
		 * the human-used external representation, and, just because this UDT is
		 * also declared with length -2, that external representation is being
		 * copied directly here as the internal representation, without even
		 * invoking any of the UDT's code.
		 */
		if(txt != 0)
			txt = pstrdup(txt);
		PG_RETURN_CSTRING(txt);
	}
	/*
	 * Length != -2 so we do the expected: call parse to construct a Java object
	 * from the external representation, then _UDT_coerceObject to get the
	 * internal representation from the object.
	 */
	jstr = String_createJavaStringFromNTS(txt);
	obj  = pljava_Function_udtParseInvoke(udt->parse, jstr, udt->sqlTypeName);
	JNI_deleteLocalRef(jstr);

	return _UDT_coerceObject((Type)udt, obj);
}
