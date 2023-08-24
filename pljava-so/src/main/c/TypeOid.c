/*
 * Copyright (c) 2019-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

#include <postgres.h>
#include <catalog/pg_type.h>

#include "pljava/pljava.h"
#include "org_postgresql_pljava_jdbc_TypeOid.h"

/*
 * A compilation unit with no run-time purpose, merely to hold a bunch of
 * StaticAssertStmts to confirm at compile time that we haven't fat-fingered
 * any of the OID constants that are known to the Java code.
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == (org_postgresql_pljava_jdbc_TypeOid_##c), \
	"Java/C value mismatch for " #c)

/*
 * Class:     org_postgresql_pljava_jdbc_TypeOid
 * Method:    _dummy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_postgresql_pljava_jdbc_TypeOid__1dummy(JNIEnv * env, jclass cls)
{
	CONFIRMCONST(InvalidOid);
	CONFIRMCONST(INT2OID);
	CONFIRMCONST(INT4OID);
	CONFIRMCONST(INT8OID);
	CONFIRMCONST(TEXTOID);
	CONFIRMCONST(NUMERICOID);
	CONFIRMCONST(FLOAT4OID);
	CONFIRMCONST(FLOAT8OID);
	CONFIRMCONST(BOOLOID);
	CONFIRMCONST(DATEOID);
	CONFIRMCONST(TIMEOID);
	CONFIRMCONST(TIMESTAMPOID);
	CONFIRMCONST(TIMESTAMPTZOID);
	CONFIRMCONST(BYTEAOID);
	CONFIRMCONST(VARCHAROID);
	CONFIRMCONST(OIDOID);
	CONFIRMCONST(BPCHAROID);
	CONFIRMCONST(PG_NODE_TREEOID);
	CONFIRMCONST(TRIGGEROID);
}
