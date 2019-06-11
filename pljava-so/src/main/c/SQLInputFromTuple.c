/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
#include "pljava/type/SingleRowReader.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/DualState.h"
#include "pljava/Invocation.h"
#include "pljava/SQLInputFromTuple.h"

static jclass    s_SQLInputFromTuple_class;
static jmethodID s_SQLInputFromTuple_init;

jobject pljava_SQLInputFromTuple_create(HeapTupleHeader hth)
{
	Ptr2Long p2lht;
	Ptr2Long p2lro;
	jobject result;
	jobject jtd = pljava_SingleRowReader_getTupleDesc(hth);

	p2lht.longVal = 0L;
	p2lro.longVal = 0L;

	p2lht.ptrVal = hth;
	p2lro.ptrVal = currentInvocation;

	result =
		JNI_newObjectLocked(s_SQLInputFromTuple_class, s_SQLInputFromTuple_init,
			pljava_DualState_key(), p2lro.longVal, p2lht.longVal, jtd);

	JNI_deleteLocalRef(jtd);
	return result;
}

/* Make this datatype available to the postgres system.
 */
void pljava_SQLInputFromTuple_initialize(void)
{
	jclass cls =
		PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLInputFromTuple");
	s_SQLInputFromTuple_init = PgObject_getJavaMethod(cls, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJLorg/postgresql/pljava/internal/TupleDesc;)V");
	s_SQLInputFromTuple_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
}
