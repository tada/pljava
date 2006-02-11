/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include "pljava/MemoryStream.h"

#include "org_postgresql_pljava_internal_MemoryChunkInputStream.h"
#include "org_postgresql_pljava_internal_MemoryChunkOutputStream.h"

static jclass    s_MemoryChunkInputStream_class;
static jclass    s_MemoryChunkOutputStream_class;
static jmethodID s_MemoryChunkInputStream_init;
static jmethodID s_MemoryChunkOutputStream_init;
static jmethodID s_MemoryChunkInputStream_close;
static jmethodID s_MemoryChunkOutputStream_close;

jobject MemoryStream_createInputStream(void* data, size_t sz)
{
	Ptr2Long p2l;
	p2l.longVal = 0L; /* ensure that the rest is zeroed out */
	p2l.ptrVal = data;
	return JNI_newObject(s_MemoryChunkInputStream_class, s_MemoryChunkInputStream_init, p2l.longVal, (jint)sz);
}

jobject MemoryStream_createOutputStream(StringInfo data)
{
	Ptr2Long p2l;
	p2l.longVal = 0L; /* ensure that the rest is zeroed out */
	p2l.ptrVal = data;
	return JNI_newObject(s_MemoryChunkOutputStream_class, s_MemoryChunkOutputStream_init, p2l.longVal);
}

void MemoryStream_closeInputStream(jobject stream)
{
	JNI_callVoidMethod(stream, s_MemoryChunkInputStream_close);
}

void MemoryStream_closeOutputStream(jobject stream)
{
	JNI_callVoidMethod(stream, s_MemoryChunkOutputStream_close);
}

/* Make this datatype available to the postgres system.
 */
extern void MemoryStream_initialize(void);
void MemoryStream_initialize(void)
{
	JNINativeMethod readMethods[] = {
		{
		"_readByte",
	  	"(JI)I",
	  	Java_org_postgresql_pljava_internal_MemoryChunkInputStream__1readByte
		},
		{
		"_readBytes",
	  	"(JI[BII)V",
	  	Java_org_postgresql_pljava_internal_MemoryChunkInputStream__1readBytes
		},
		{ 0, 0, 0 }};

	JNINativeMethod writeMethods[] = {
		{
		"_writeByte",
	  	"(JI)V",
	  	Java_org_postgresql_pljava_internal_MemoryChunkOutputStream__1writeByte
		},
		{
		"_writeBytes",
	  	"(J[BII)V",
	  	Java_org_postgresql_pljava_internal_MemoryChunkOutputStream__1writeBytes
		},
		{ 0, 0, 0 }};

	s_MemoryChunkInputStream_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/MemoryChunkInputStream"));
	PgObject_registerNatives2(s_MemoryChunkInputStream_class, readMethods);
	s_MemoryChunkInputStream_init = PgObject_getJavaMethod(s_MemoryChunkInputStream_class, "<init>", "(JI)V");
	s_MemoryChunkInputStream_close = PgObject_getJavaMethod(s_MemoryChunkInputStream_class, "close", "()V");

	s_MemoryChunkOutputStream_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/MemoryChunkOutputStream"));
	PgObject_registerNatives2(s_MemoryChunkOutputStream_class, writeMethods);
	s_MemoryChunkOutputStream_init = PgObject_getJavaMethod(s_MemoryChunkOutputStream_class, "<init>", "(J)V");
	s_MemoryChunkOutputStream_close = PgObject_getJavaMethod(s_MemoryChunkOutputStream_class, "close", "()V");
}

/****************************************
 * JNI methods
 ****************************************/
 
/*
 * Class:     org_postgresql_pljava_internal_MemoryChunkInputStream
 * Method:    _readByte
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_MemoryChunkInputStream__1readByte(JNIEnv* env, jclass cls, jlong _this, jint pos)
{
	Ptr2Long p2l;
	p2l.longVal = _this;

	/* Bounds checking has already been made */
	return (jint)((unsigned char*)p2l.ptrVal)[pos];
}

/*
 * Class:     org_postgresql_pljava_internal_MemoryChunkInputStream
 * Method:    _readBytes
 * Signature: (JI[BII)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_MemoryChunkInputStream__1readBytes(JNIEnv* env, jclass cls, jlong _this, jint pos, jbyteArray ba, jint off, jint len)
{
	BEGIN_NATIVE
	Ptr2Long p2l;
	p2l.longVal = _this;
	/* Bounds checking has already been made */
	JNI_setByteArrayRegion(ba, off, len, ((jbyte*)p2l.ptrVal) + pos);
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_MemoryChunkOutputStream
 * Method:    _writeByte
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_MemoryChunkOutputStream__1writeByte(JNIEnv* env, jclass cls, jlong _this, jint b)
{
	Ptr2Long p2l;
	unsigned char byte = (unsigned char)b;
	p2l.longVal = _this;

	BEGIN_NATIVE
	appendBinaryStringInfo((StringInfo)p2l.ptrVal, (char*)&byte, 1);
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_MemoryChunkInputStream
 * Method:    _readBytes
 * Signature: (JI[BII)V
 */
#define BYTE_BUF_SIZE 1024
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_MemoryChunkOutputStream__1writeBytes(JNIEnv* env, jclass cls, jlong _this, jbyteArray ba, jint off, jint len)
{
	Ptr2Long p2l;
	jbyte buffer[BYTE_BUF_SIZE];
	p2l.longVal = _this;

	BEGIN_NATIVE
	while(len > 0)
	{
		int copySize = len;
		if(copySize > BYTE_BUF_SIZE)
			copySize = BYTE_BUF_SIZE;
		JNI_getByteArrayRegion(ba, off, copySize, buffer);
		appendBinaryStringInfo((StringInfo)p2l.ptrVal, buffer, copySize);
		off += copySize;
		len -= copySize;
	}
	END_NATIVE
}
