/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
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
#include "pljava/type/HeapTupleHeader.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/Invocation.h"
#include "pljava/SQLInputFromTuple.h"

#include "org_postgresql_pljava_jdbc_SQLInputFromTuple.h"

static jclass    s_SQLInputFromTuple_class;
static jmethodID s_SQLInputFromTuple_init;

jobject SQLInputFromTuple_create(HeapTupleHeader hth, TupleDesc td)
{
	jobject tupleDesc;
	jobject result;
	jlong pointer;

	if(hth == 0)
		return 0;

	tupleDesc = TupleDesc_create(td);
	pointer = Invocation_createLocalWrapper(hth);
	result = JNI_newObject(s_SQLInputFromTuple_class, s_SQLInputFromTuple_init, pointer, tupleDesc);
	JNI_deleteLocalRef(tupleDesc);
	return result;
}

/* Make this datatype available to the postgres system.
 */
extern void SQLInputFromTuple_initialize(void);
void SQLInputFromTuple_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_getObject",
		"(JJILjava/lang/Class;)Ljava/lang/Object;",
	  	Java_org_postgresql_pljava_jdbc_SQLInputFromTuple__1getObject
		},
		{
		"_free",
		"(J)V",
		Java_org_postgresql_pljava_jdbc_SQLInputFromTuple__1free
		},
		{ 0, 0, 0 }
	};

	s_SQLInputFromTuple_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLInputFromTuple"));
	PgObject_registerNatives2(s_SQLInputFromTuple_class, methods);
	s_SQLInputFromTuple_init = PgObject_getJavaMethod(s_SQLInputFromTuple_class, "<init>", "(JLorg/postgresql/pljava/internal/TupleDesc;)V");
}

/****************************************
 * JNI methods
 ****************************************/
 
/*
 * Class:     org_postgresql_pljava_jdbc_SQLInputFromTuple
 * Method:    _free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_jdbc_SQLInputFromTuple__1free(JNIEnv* env, jobject _this, jlong hth)
{
	HeapTupleHeader_free(env, hth);
}

/*
 * Class:     org_postgresql_pljava_jdbc_SQLInputFromTuple
 * Method:    _getObject
 * Signature: (JJILjava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_jdbc_SQLInputFromTuple__1getObject(JNIEnv* env, jclass clazz, jlong hth, jlong jtd, jint attrNo, jclass rqcls)
{
	return HeapTupleHeader_getObject(env, hth, jtd, attrNo, rqcls);
}
