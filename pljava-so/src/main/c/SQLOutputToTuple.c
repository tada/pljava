/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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
#include <postgres.h>
#include "pljava/type/Type.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/SQLOutputToTuple.h"

static jclass    s_SQLOutputToTuple_class;
static jmethodID s_SQLOutputToTuple_init;
static jmethodID s_SQLOutputToTuple_getTuple;

jobject SQLOutputToTuple_create(TupleDesc td)
{
	jobject tupleDesc = pljava_TupleDesc_create(td);
	jobject result = JNI_newObject(s_SQLOutputToTuple_class, s_SQLOutputToTuple_init, tupleDesc);
	JNI_deleteLocalRef(tupleDesc);
	return result;
}

HeapTuple SQLOutputToTuple_getTuple(jobject sqlOutput)
{
	jlong jTup;
	if(sqlOutput == 0)
		return 0;

	jTup = JNI_callLongMethod(sqlOutput, s_SQLOutputToTuple_getTuple);
	if(jTup == 0)
		return 0;

	return JLongGet(HeapTuple, jTup);
}

/* Make this datatype available to the postgres system.
 */
extern void SQLOutputToTuple_initialize(void);
void SQLOutputToTuple_initialize(void)
{
	s_SQLOutputToTuple_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLOutputToTuple"));
	s_SQLOutputToTuple_init = PgObject_getJavaMethod(s_SQLOutputToTuple_class, "<init>", "(Lorg/postgresql/pljava/internal/TupleDesc;)V");
	s_SQLOutputToTuple_getTuple = PgObject_getJavaMethod(s_SQLOutputToTuple_class, "getTuple", "()J");
}
