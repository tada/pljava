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
#include "org_postgresql_pljava_internal_ErrorData.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/ErrorData.h"
#include "pljava/type/String.h"

static jclass    s_ErrorData_class;
static jmethodID s_ErrorData_init;
static jmethodID s_ErrorData_getNativePointer;

jobject pljava_ErrorData_getCurrentError(void)
{
	jobject jed;

	MemoryContext curr = MemoryContextSwitchTo(JavaMemoryContext);
	ErrorData* errorData = CopyErrorData();
	MemoryContextSwitchTo(curr);

	/*
	 * Passing (jlong)0 as the ResourceOwner means this will never be matched by
	 * a nativeRelease call; that's appropriate (for now) as the ErrorData copy
	 * is being made into JavaMemoryContext, which never gets reset, so only
	 * unreachability from the Java side will free it.
	 */
	jed = JNI_newObjectLocked(s_ErrorData_class, s_ErrorData_init,
		pljava_DualState_key(), (jlong)0, PointerGetJLong(errorData));
	return jed;
}

ErrorData* pljava_ErrorData_getErrorData(jobject jed)
{	
	return JLongGet(ErrorData *,
		JNI_callLongMethod(jed, s_ErrorData_getNativePointer));
}

/* Make this datatype available to the postgres system.
 */
void pljava_ErrorData_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_getErrorLevel",
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getErrorLevel
		},
		{
		"_isOutputToServer",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_ErrorData__1isOutputToServer
		},
		{
		"_isOutputToClient",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_ErrorData__1isOutputToClient
		},
		{
		"_isShowFuncname",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_ErrorData__1isShowFuncname
		},
		{
		"_getFilename",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getFilename
		},
		{
		"_getLineno",
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getLineno
		},
		{
		"_getFuncname",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getFuncname
		},
		{
		"_getSqlState",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getSqlState
		},
		{
		"_getMessage",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getMessage
		},
		{
		"_getDetail",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getDetail
		},
		{
		"_getHint",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getHint
		},
		{
		"_getContextMessage",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getContextMessage
		},
		{
		"_getCursorPos",
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getCursorPos
		},
		{
		"_getInternalPos",
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getInternalPos
		},
		{
		"_getInternalQuery",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getInternalQuery
		},
		{
		"_getSavedErrno",
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_ErrorData__1getSavedErrno
		},
		{ 0, 0, 0 }
	};

	s_ErrorData_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/ErrorData"));
	PgObject_registerNatives2(s_ErrorData_class, methods);
	s_ErrorData_init = PgObject_getJavaMethod(s_ErrorData_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJ)V");
	s_ErrorData_getNativePointer = PgObject_getJavaMethod(s_ErrorData_class, "getNativePointer", "()J");
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getErrorLevel
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ErrorData__1getErrorLevel(JNIEnv* env, jclass cls, jlong _this)
{
	return JLongGet(ErrorData *, _this)->elevel;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getMessage
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getMessage(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->message);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getSqlState
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getSqlState(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	char buf[6];
	int errCode;
	int idx;

	/* unpack MAKE_SQLSTATE code
	 */
	errCode = JLongGet(ErrorData *, _this)->sqlerrcode;
	for (idx = 0; idx < 5; ++idx)
	{
		buf[idx] = (char)PGUNSIXBIT(errCode); /*why not cast in macro?*/
		errCode >>= 6;
	}
	buf[idx] = 0;
	result = String_createJavaStringFromNTS(buf);

	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToServer
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData__1isOutputToServer(JNIEnv* env, jclass cls, jlong _this)
{
	return (jboolean)JLongGet(ErrorData *, _this)->output_to_server;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToClient
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData__1isOutputToClient(JNIEnv* env, jclass cls, jlong _this)
{
	return (jboolean)JLongGet(ErrorData *, _this)->output_to_client;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isShowFuncname
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData__1isShowFuncname(JNIEnv* env, jclass cls, jlong _this)
{
#if PG_VERSION_NUM < 140000
	return (jboolean)JLongGet(ErrorData *, _this)->show_funcname;
#else
	return JNI_FALSE;
#endif
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFilename
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getFilename(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->filename);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getLineno
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getLineno(JNIEnv* env, jclass cls, jlong _this)
{
	return (jint)JLongGet(ErrorData *, _this)->lineno;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFuncname
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getFuncname(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->funcname);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getDetail
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getDetail(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->detail);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getHint
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getHint(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->hint);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getContextMessage
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getContextMessage(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;
	BEGIN_NATIVE_NO_ERRCHECK
	result =
		String_createJavaStringFromNTS(JLongGet(ErrorData *, _this)->context);
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getCursorPos
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getCursorPos(JNIEnv* env, jclass cls, jlong _this)
{
	return (jint)JLongGet(ErrorData *, _this)->cursorpos;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalPos
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getInternalPos(JNIEnv* env, jclass cls, jlong _this)
{
	return (jint)JLongGet(ErrorData *, _this)->internalpos;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalQuery
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getInternalQuery(JNIEnv* env, jclass cls, jlong _this)
{
	jstring result = 0;

	BEGIN_NATIVE_NO_ERRCHECK
	result = String_createJavaStringFromNTS(
		JLongGet(ErrorData *, _this)->internalquery);
	END_NATIVE

	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getSavedErrno
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData__1getSavedErrno(JNIEnv* env, jclass cls, jlong _this)
{
	return (jint)JLongGet(ErrorData *, _this)->saved_errno;
}
