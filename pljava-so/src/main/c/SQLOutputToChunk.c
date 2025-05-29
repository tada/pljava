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
#include "pljava/SQLOutputToChunk.h"

#include "org_postgresql_pljava_jdbc_SQLOutputToChunk.h"

static jclass    s_SQLOutputToChunk_class;
static jmethodID s_SQLOutputToChunk_init;
static jmethodID s_SQLOutputToChunk_close;
static jmethodID s_Buffer_position;

jobject SQLOutputToChunk_create(StringInfo data, bool isJavaBasedScalar)
{
	jobject dbb;
	dbb = JNI_newDirectByteBuffer(data->data, data->maxlen);
	if ( 0 < data->len )
		JNI_callObjectMethodLocked(dbb, s_Buffer_position, data->len);
	return JNI_newObject(s_SQLOutputToChunk_class, s_SQLOutputToChunk_init,
		PointerGetJLong(data), dbb, isJavaBasedScalar ? JNI_TRUE : JNI_FALSE);
}

void SQLOutputToChunk_close(jobject stream)
{
	/*
	 * The close method calls ensureCapacity(0), so thanks to _ensureCapacity
	 * below, on return the StringInfo len is correct and the contents are
	 * NUL terminated, (re-)establishing the StringInfo invariant.
	 */
	JNI_callVoidMethod(stream, s_SQLOutputToChunk_close);
}

/* Make this datatype available to the postgres system.
 */
extern void SQLOutputToChunk_initialize(void);
void SQLOutputToChunk_initialize(void)
{
	jclass Buffer_class;

	JNINativeMethod methods[] = {
		{
		"_ensureCapacity",
		"(JLjava/nio/ByteBuffer;II)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_jdbc_SQLOutputToChunk__1ensureCapacity
		},
		{ 0, 0, 0 }};

	s_SQLOutputToChunk_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SQLOutputToChunk"));
	PgObject_registerNatives2(s_SQLOutputToChunk_class, methods);
	s_SQLOutputToChunk_init = PgObject_getJavaMethod(s_SQLOutputToChunk_class,
		"<init>", "(JLjava/nio/ByteBuffer;Z)V");
	s_SQLOutputToChunk_close = PgObject_getJavaMethod(s_SQLOutputToChunk_class, "close", "()V");

	Buffer_class = PgObject_getJavaClass("java/nio/Buffer");
	s_Buffer_position = PgObject_getJavaMethod(Buffer_class, "position",
		"(I)Ljava/nio/Buffer;");
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_jdbc_SQLOutputToChunk
 * Method:    _ensureCapacity
 * Signature: (JLjava/nio/ByteBuffer;II)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL Java_org_postgresql_pljava_jdbc_SQLOutputToChunk__1ensureCapacity
  (JNIEnv *env, jclass cls, jlong hdl, jobject bb, jint pos, jint needed)
{
	StringInfo str = JLongGet(StringInfo, hdl);
	char *oldp;
	int oldmax;

	BEGIN_NATIVE
	str->len = pos;
	oldp = str->data;
	oldmax = str->maxlen;
	enlargeStringInfo(str, needed);
	/*
	 * The StringInfo functions maintain an invariant that the contents are
	 * NUL-terminated. That is *not* assured in general while Java pokes at it
	 * via the ByteBuffer, but is restored here at every call to _ensureCapacity
	 * (of which one is guaranteed to happen at close). Because room for a NUL
	 * is always arranged by enlargeStringInfo, there is room for this even if
	 * zero was passed for 'needed', as happens when closing.
	 */
	str->data[pos] = '\0';
	if ( oldp == str->data && oldmax == str->maxlen )
		goto done;
	bb = JNI_newDirectByteBuffer(str->data, str->maxlen);
	if ( NULL == bb )
		goto done;
	if ( 0 < pos )
		JNI_callObjectMethodLocked(bb, s_Buffer_position, pos);
done:
	END_NATIVE
	return bb;
}
