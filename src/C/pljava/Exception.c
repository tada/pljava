/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <executor/spi.h>

#include "pljava/Exception.h"
#include "pljava/type/String.h"

static jclass s_Class_class;
static jmethodID s_Class_getName;

static jclass    s_Throwable_class;
static jmethodID s_Throwable_getMessage;

static jclass    s_SQLException_class;
static jmethodID s_SQLException_init;
static jmethodID s_SQLException_getSQLState;

void Exception_checkException(JNIEnv* env)
{
	jthrowable exh = (*env)->ExceptionOccurred(env);
	if(exh == 0)
		/*
		 * No exception has been thrown.
		 */
		return;

	(*env)->ExceptionClear(env);
	int sqlState = ERRCODE_INTERNAL_ERROR;

	StringInfoData buf;
	initStringInfo(&buf);

	jclass exhClass = (*env)->GetObjectClass(env, exh);
	jstring jtmp = (jstring)(*env)->CallObjectMethod(env, exhClass, s_Class_getName);
	String_appendJavaString(env, &buf, jtmp);
	(*env)->DeleteLocalRef(env, exhClass);
	(*env)->DeleteLocalRef(env, jtmp);

	jtmp = (jstring)(*env)->CallObjectMethod(env, exh, s_Throwable_getMessage);
	if(jtmp != 0)
	{
		appendStringInfoString(&buf, ": ");
		String_appendJavaString(env, &buf, jtmp);
		(*env)->DeleteLocalRef(env, jtmp);
	}

	if((*env)->IsInstanceOf(env, exh, s_SQLException_class))
	{
		jtmp = (*env)->CallObjectMethod(env, exh, s_SQLException_getSQLState);
		if(jtmp != 0)
		{
			char* s = String_createNTS(env, jtmp);
			(*env)->DeleteLocalRef(env, jtmp);

			if(strlen(s) >= 5)
				sqlState = MAKE_SQLSTATE(s[0], s[1], s[2], s[3], s[4]);
			pfree(s);
		}
	}

	/* There's no return from this call.
	 */
	ereport(ERROR, (errcode(sqlState), errmsg(buf.data)));
}	

void Exception_throw(JNIEnv* env, int errCode, const char* errMessage, ...)
{
	va_list args;
	va_start(args, errMessage);
    char buf[1024];

    vsnprintf(buf, sizeof(buf), errMessage, args);
	jstring message = String_createJavaStringFromNTS(env, buf);

	/* unpack MAKE_SQLSTATE code
	 */
	int idx;
	for (idx = 0; idx < 5; ++idx)
	{
		buf[idx] = PGUNSIXBIT(errCode);
		errCode >>= 6;
	}
	buf[idx] = 0;

	jstring sqlState = String_createJavaStringFromNTS(env, buf);

	jobject ex = (*env)->NewObject(
		env, s_SQLException_class, s_SQLException_init,
		message, sqlState);

	(*env)->DeleteLocalRef(env, message);
	(*env)->DeleteLocalRef(env, sqlState);
	(*env)->Throw(env, ex);
}

void Exception_throwSPI(JNIEnv* env, const char* function)
{
	Exception_throw(env, ERRCODE_INTERNAL_ERROR,
		"SPI function SPI_%s failed with error code %d", function, SPI_result);
}

PG_FUNCTION_INFO_V1(Exception_initialize);

Datum Exception_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Class_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/Class"));

	s_Throwable_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/Throwable"));

	s_SQLException_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/sql/SQLException"));

	s_Class_getName = PgObject_getJavaMethod(env, s_Class_class,
			"getName", "()Ljava/lang/String;");

	s_SQLException_init = PgObject_getJavaMethod(env, s_SQLException_class,
			"<init>", "(Ljava/lang/String;Ljava/lang/String;)V");

	s_Throwable_getMessage = PgObject_getJavaMethod(env, s_Throwable_class,
			"getMessage", "()Ljava/lang/String;");

	s_SQLException_getSQLState = PgObject_getJavaMethod(env, s_SQLException_class,
			"getSQLState", "()Ljava/lang/String;");

	PG_RETURN_VOID();
}
