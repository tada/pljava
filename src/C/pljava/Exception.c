/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/spi.h>

#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"
#include "pljava/type/String.h"
#include "pljava/type/ErrorData.h"

static jclass s_Class_class;
static jmethodID s_Class_getName;

static jclass    s_Throwable_class;
static jmethodID s_Throwable_getMessage;

static jclass    s_IllegalArgumentException_class;
static jmethodID s_IllegalArgumentException_init;

static jclass    s_SQLException_class;
static jmethodID s_SQLException_init;
static jmethodID s_SQLException_getSQLState;

static jclass    s_ServerException_class;
static jmethodID s_ServerException_init;
static jmethodID s_ServerException_getErrorData;

static jclass    s_UnsupportedOperationException_class;
static jmethodID s_UnsupportedOperationException_init;

void Exception_checkException(JNIEnv* env)
{
	int sqlState;
	StringInfoData buf;
	jclass exhClass;
	jstring jtmp;
	bool saveicj = isCallingJava;
	jthrowable exh = (*env)->ExceptionOccurred(env);
	if(exh == 0)
		/*
		 * No exception has been thrown.
		 */
		return;

	isCallingJava = true;
	(*env)->ExceptionDescribe(env);
	(*env)->ExceptionClear(env);
	isCallingJava = saveicj;

#if(PGSQL_MAJOR_VER < 8)
	if(currentCallContext->errorOccured)
		/*
		 * Oops, just re-throw this one.
		 */
		PG_RE_THROW();
#else
	if((*env)->IsInstanceOf(env, exh, s_ServerException_class))
	{
		/* Rethrow the server error.
		 */
		jobject jed;
		isCallingJava = true;
		jed = (*env)->CallObjectMethod(env, exh, s_ServerException_getErrorData);
		isCallingJava = saveicj;

		if(jed != 0)
		{
			ErrorData* ed = ErrorData_getErrorData(env, jed);
			(*env)->DeleteLocalRef(env, jed);
			ReThrowError(ed);
		}
	}
#endif
	sqlState = ERRCODE_INTERNAL_ERROR;

	initStringInfo(&buf);

	isCallingJava = true;
	exhClass = (*env)->GetObjectClass(env, exh);
	jtmp = (jstring)(*env)->CallObjectMethod(env, exhClass, s_Class_getName);
	String_appendJavaString(env, &buf, jtmp);
	(*env)->DeleteLocalRef(env, exhClass);
	(*env)->DeleteLocalRef(env, jtmp);
	jtmp = (jstring)(*env)->CallObjectMethod(env, exh, s_Throwable_getMessage);
	isCallingJava = saveicj;

	if(jtmp != 0)
	{
		appendStringInfoString(&buf, ": ");
		String_appendJavaString(env, &buf, jtmp);
		(*env)->DeleteLocalRef(env, jtmp);
	}

	if((*env)->IsInstanceOf(env, exh, s_SQLException_class))
	{
		isCallingJava = true;
		jtmp = (*env)->CallObjectMethod(env, exh, s_SQLException_getSQLState);
		isCallingJava = saveicj;

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

void
Exception_featureNotSupported(JNIEnv* env, const char* requestedFeature, const char* introVersion)
{
	jstring jmsg;
	jobject ex;
	StringInfoData buf;
	initStringInfo(&buf);
	appendStringInfoString(&buf, "Feature: ");
	appendStringInfoString(&buf, requestedFeature);
	appendStringInfoString(&buf, " lacks support in PostgreSQL version ");
	appendStringInfo(&buf, "%d.%d", PGSQL_MAJOR_VER, PGSQL_MINOR_VER);
	appendStringInfoString(&buf, ". It was introduced in version ");
	appendStringInfoString(&buf, introVersion);

	ereport(DEBUG3, (errmsg(buf.data)));
	jmsg = String_createJavaStringFromNTS(env, buf.data);
	pfree(buf.data);

	ex = PgObject_newJavaObject(
		env, s_UnsupportedOperationException_class, s_UnsupportedOperationException_init, jmsg);
	(*env)->DeleteLocalRef(env, jmsg);
	(*env)->Throw(env, ex);
}

void Exception_throw(JNIEnv* env, int errCode, const char* errMessage, ...)
{
    char buf[1024];
	va_list args;
	jstring message;
	jstring sqlState;
	jobject ex;
	int idx;

	va_start(args, errMessage);
	vsnprintf(buf, sizeof(buf), errMessage, args);
	ereport(DEBUG3, (errcode(errCode), errmsg(buf)));

	message = String_createJavaStringFromNTS(env, buf);

	/* unpack MAKE_SQLSTATE code
	 */
	for (idx = 0; idx < 5; ++idx)
	{
		buf[idx] = PGUNSIXBIT(errCode);
		errCode >>= 6;
	}
	buf[idx] = 0;

	sqlState = String_createJavaStringFromNTS(env, buf);

	ex = PgObject_newJavaObject(
		env, s_SQLException_class, s_SQLException_init,
		message, sqlState);

	(*env)->DeleteLocalRef(env, message);
	(*env)->DeleteLocalRef(env, sqlState);
	(*env)->Throw(env, ex);
	va_end(args);
}

void Exception_throwIllegalArgument(JNIEnv* env, const char* errMessage, ...)
{
    char buf[1024];
	va_list args;
	jstring message;
	jobject ex;

	va_start(args, errMessage);
	vsnprintf(buf, sizeof(buf), errMessage, args);
	ereport(DEBUG3, (errmsg(buf)));

	message = String_createJavaStringFromNTS(env, buf);

	ex = PgObject_newJavaObject(
		env, s_IllegalArgumentException_class, s_IllegalArgumentException_init, message);

	(*env)->DeleteLocalRef(env, message);
	(*env)->Throw(env, ex);
	va_end(args);
}

void Exception_throwSPI(JNIEnv* env, const char* function, int errCode)
{
#if (PGSQL_MAJOR_VER >= 8)
	Exception_throw(env, ERRCODE_INTERNAL_ERROR,
		"SPI function SPI_%s failed with error %s", function,
			SPI_result_code_string(errCode));
#else
	Exception_throw(env, ERRCODE_INTERNAL_ERROR,
		"SPI function SPI_%s failed with error code %d", function, errCode);
#endif
}

void Exception_throw_ERROR(JNIEnv* env, const char* funcName)
{
	jobject ed;
	jobject ex;
	ErrorData* errData;
#if (PGSQL_MAJOR_VER >= 8)
	MemoryContext_switchToUpperContext();
	errData = CopyErrorData();
	FlushErrorState();
#else
	StringInfoData buf;
	MemoryContext_switchToUpperContext();
	errData = (ErrorData*)palloc(sizeof(ErrorData));
	initStringInfo(&buf);
	appendStringInfoString(&buf, "Error when calling: ");
	appendStringInfoString(&buf, funcName);
	errData->sqlerrcode = ERRCODE_INTERNAL_ERROR;
	errData->message = buf.data;
#endif
	ed = ErrorData_create(env, errData);
	ex = PgObject_newJavaObject(
		env, s_ServerException_class, s_ServerException_init, ed);
	ereport(DEBUG3, (errcode(errData->sqlerrcode), errmsg(errData->message)));
	currentCallContext->errorOccured = true;
	(*env)->DeleteLocalRef(env, ed);
	(*env)->Throw(env, ex);
}

PG_FUNCTION_INFO_V1(Exception_initialize);

Datum Exception_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Class_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/Class"));

	s_Throwable_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/Throwable"));

	s_Throwable_getMessage = PgObject_getJavaMethod(env, s_Throwable_class,
			"getMessage", "()Ljava/lang/String;");

	s_IllegalArgumentException_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/IllegalArgumentException"));

	s_IllegalArgumentException_init = PgObject_getJavaMethod(env, s_IllegalArgumentException_class,
			"<init>", "(Ljava/lang/String;)V");

	s_SQLException_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/sql/SQLException"));

	s_SQLException_init = PgObject_getJavaMethod(env, s_SQLException_class,
			"<init>", "(Ljava/lang/String;Ljava/lang/String;)V");

	s_SQLException_getSQLState = PgObject_getJavaMethod(env, s_SQLException_class,
			"getSQLState", "()Ljava/lang/String;");

	s_ServerException_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/ServerException"));

	s_ServerException_init = PgObject_getJavaMethod(env, s_ServerException_class,
			"<init>", "(Lorg/postgresql/pljava/internal/ErrorData;)V");

	s_ServerException_getErrorData = PgObject_getJavaMethod(env, s_ServerException_class,
			"getErrorData", "()Lorg/postgresql/pljava/internal/ErrorData;");

	s_UnsupportedOperationException_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/UnsupportedOperationException"));

	s_UnsupportedOperationException_init = PgObject_getJavaMethod(env, s_UnsupportedOperationException_class,
			"<init>", "(Ljava/lang/String;)V");

	s_Class_getName = PgObject_getJavaMethod(env, s_Class_class,
			"getName", "()Ljava/lang/String;");

	PG_RETURN_VOID();
}
