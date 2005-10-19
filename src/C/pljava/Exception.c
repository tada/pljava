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
#include "pljava/CallContext.h"
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

void Exception_checkException()
{
	int sqlState;
	StringInfoData buf;
	jclass exhClass;
	jstring jtmp;
	jthrowable exh = JNI_exceptionOccurred();
	if(exh == 0)
		/*
		 * No exception has been thrown.
		 */
		return;

	JNI_exceptionDescribe();
	JNI_exceptionClear();

	if(JNI_isInstanceOf(exh, s_ServerException_class))
	{
		/* Rethrow the server error.
		 */
		jobject jed = JNI_callObjectMethod(exh, s_ServerException_getErrorData);
		if(jed != 0)
			ReThrowError(ErrorData_getErrorData(jed));
	}
	sqlState = ERRCODE_INTERNAL_ERROR;

	initStringInfo(&buf);

	exhClass = JNI_getObjectClass(exh);
	jtmp = (jstring)JNI_callObjectMethod(exhClass, s_Class_getName);
	String_appendJavaString(&buf, jtmp);
	JNI_deleteLocalRef(exhClass);
	JNI_deleteLocalRef(jtmp);

	jtmp = (jstring)JNI_callObjectMethod(exh, s_Throwable_getMessage);
	if(jtmp != 0)
	{
		appendStringInfoString(&buf, ": ");
		String_appendJavaString(&buf, jtmp);
		JNI_deleteLocalRef(jtmp);
	}

	if(JNI_isInstanceOf(exh, s_SQLException_class))
	{
		jtmp = JNI_callObjectMethod(exh, s_SQLException_getSQLState);
		if(jtmp != 0)
		{
			char* s = String_createNTS(jtmp);
			JNI_deleteLocalRef(jtmp);

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
Exception_featureNotSupported(const char* requestedFeature, const char* introVersion)
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
	jmsg = String_createJavaStringFromNTS(buf.data);
	pfree(buf.data);

	ex = JNI_newObject(s_UnsupportedOperationException_class, s_UnsupportedOperationException_init, jmsg);
	JNI_deleteLocalRef(jmsg);
	JNI_throw(ex);
}

void Exception_throw(int errCode, const char* errMessage, ...)
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

	message = String_createJavaStringFromNTS(buf);

	/* unpack MAKE_SQLSTATE code
	 */
	for (idx = 0; idx < 5; ++idx)
	{
		buf[idx] = PGUNSIXBIT(errCode);
		errCode >>= 6;
	}
	buf[idx] = 0;

	sqlState = String_createJavaStringFromNTS(buf);

	ex = JNI_newObject(s_SQLException_class, s_SQLException_init, message, sqlState);

	JNI_deleteLocalRef(message);
	JNI_deleteLocalRef(sqlState);
	JNI_throw(ex);
	va_end(args);
}

void Exception_throwIllegalArgument(const char* errMessage, ...)
{
    char buf[1024];
	va_list args;
	jstring message;
	jobject ex;

	va_start(args, errMessage);
	vsnprintf(buf, sizeof(buf), errMessage, args);
	ereport(DEBUG3, (errmsg(buf)));

	message = String_createJavaStringFromNTS(buf);

	ex = JNI_newObject(s_IllegalArgumentException_class, s_IllegalArgumentException_init, message);

	JNI_deleteLocalRef(message);
	JNI_throw(ex);
	va_end(args);
}

void Exception_throwSPI(const char* function, int errCode)
{
	Exception_throw(ERRCODE_INTERNAL_ERROR,
		"SPI function SPI_%s failed with error %s", function,
			SPI_result_code_string(errCode));
}

void Exception_throw_ERROR(const char* funcName)
{
	jobject ex;
	jobject ed = ErrorData_getCurrentError();

	FlushErrorState();

	ex = JNI_newObject(s_ServerException_class, s_ServerException_init, ed);
	currentCallContext->errorOccured = true;

	JNI_deleteLocalRef(ed);
	JNI_throw(ex);
}

extern void Exception_initialize(void);
void Exception_initialize()
{
	s_Class_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Class"));

	s_Throwable_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Throwable"));
	s_Throwable_getMessage = PgObject_getJavaMethod(s_Throwable_class, "getMessage", "()Ljava/lang/String;");

	s_IllegalArgumentException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/IllegalArgumentException"));
	s_IllegalArgumentException_init = PgObject_getJavaMethod(s_IllegalArgumentException_class, "<init>", "(Ljava/lang/String;)V");

	s_SQLException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/sql/SQLException"));
	s_SQLException_init = PgObject_getJavaMethod(s_SQLException_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
	s_SQLException_getSQLState = PgObject_getJavaMethod(s_SQLException_class, "getSQLState", "()Ljava/lang/String;");

	s_ServerException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/ServerException"));
	s_ServerException_init = PgObject_getJavaMethod(s_ServerException_class, "<init>", "(Lorg/postgresql/pljava/internal/ErrorData;)V");

	s_ServerException_getErrorData = PgObject_getJavaMethod(s_ServerException_class, "getErrorData", "()Lorg/postgresql/pljava/internal/ErrorData;");

	s_UnsupportedOperationException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/UnsupportedOperationException"));
	s_UnsupportedOperationException_init = PgObject_getJavaMethod(s_UnsupportedOperationException_class, "<init>", "(Ljava/lang/String;)V");

	s_Class_getName = PgObject_getJavaMethod(s_Class_class, "getName", "()Ljava/lang/String;");
}
