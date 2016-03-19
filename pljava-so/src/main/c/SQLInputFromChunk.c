/*
 * Copyright (c) 2004-2016 TADA AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
#include <postgres.h>
#include "pljava/SQLInputFromChunk.h"

static jclass    s_SQLInputFromChunk_class;
static jmethodID s_SQLInputFromChunk_init;
static jmethodID s_SQLInputFromChunk_close;

jobject SQLInputFromChunk_create(void* data, size_t sz, bool isJavaBasedScalar)
{
	jobject dbb;
	dbb = JNI_newDirectByteBuffer(data, sz);
	return
		JNI_newObject(s_SQLInputFromChunk_class, s_SQLInputFromChunk_init, dbb,
		isJavaBasedScalar ? JNI_TRUE : JNI_FALSE);
}

void SQLInputFromChunk_close(jobject stream)
{
	JNI_callVoidMethod(stream, s_SQLInputFromChunk_close);
}

/* Make this datatype available to the postgres system.
 */
extern void SQLInputFromChunk_initialize(void);
void SQLInputFromChunk_initialize(void)
{
	s_SQLInputFromChunk_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLInputFromChunk"));
	s_SQLInputFromChunk_init = PgObject_getJavaMethod(s_SQLInputFromChunk_class,
		"<init>", "(Ljava/nio/ByteBuffer;Z)V");
	s_SQLInputFromChunk_close = PgObject_getJavaMethod(s_SQLInputFromChunk_class, "close", "()V");
}
