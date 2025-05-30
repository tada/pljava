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
#include <executor/spi.h>

#include "pljava/Backend.h"
#include "pljava/Invocation.h"
#include "pljava/Exception.h"
#include "pljava/type/String.h"
#include "pljava/type/ErrorData.h"

jclass Class_class;
jmethodID Class_getName;
jmethodID Class_getCanonicalName;

jclass    ServerException_class;
jmethodID ServerException_getErrorData;
jmethodID ServerException_obtain;

jclass    Throwable_class;
jmethodID Throwable_getMessage;
jmethodID Throwable_printStackTrace;

static jclass    UnhandledPGException_class;
static jmethodID UnhandledPGException_obtain;

jclass    IllegalArgumentException_class;
jmethodID IllegalArgumentException_init;

jclass    SQLException_class;
jmethodID SQLException_init;
jmethodID SQLException_getSQLState;

jclass    UnsupportedOperationException_class;
jmethodID UnsupportedOperationException_init;

jclass    NoSuchFieldError_class;
jclass    NoSuchMethodError_class;

bool Exception_isPGUnhandled(jthrowable ex)
{
	return JNI_isInstanceOf(ex, UnhandledPGException_class);
}

void
Exception_featureNotSupported(const char* requestedFeature, const char* introVersion)
{
	jstring jmsg;
	jobject ex;
	StringInfoData buf;
	initStringInfo(&buf);
	PG_TRY();
	{
		appendStringInfoString(&buf, "Feature: ");
		appendStringInfoString(&buf, requestedFeature);
		appendStringInfoString(&buf, " lacks support in PostgreSQL version ");
		appendStringInfo(&buf, "%d.%d",
						PG_VERSION_NUM / 10000,
#if PG_VERSION_NUM >= 100000
						(PG_VERSION_NUM) % 10000
#else
						(PG_VERSION_NUM / 100) % 100
#endif
		);
		appendStringInfoString(&buf, ". It was introduced in version ");
		appendStringInfoString(&buf, introVersion);
	
		ereport(DEBUG3, (errmsg("%s", buf.data)));
		jmsg = String_createJavaStringFromNTS(buf.data);
	
		ex = JNI_newObject(UnsupportedOperationException_class, UnsupportedOperationException_init, jmsg);
		JNI_deleteLocalRef(jmsg);
		JNI_throw(ex);
	}
	PG_CATCH();
	{
		ereport(WARNING, (errcode(ERRCODE_INTERNAL_ERROR), errmsg("Exception while generating exception: %s", buf.data)));
	}
	PG_END_TRY();
	pfree(buf.data);
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
	ereport(DEBUG3, (errcode(errCode), errmsg("%s", buf)));

	PG_TRY();
	{
		message = String_createJavaStringFromNTS(buf);
	
		/* unpack MAKE_SQLSTATE code
		 */
		for (idx = 0; idx < 5; ++idx)
		{
			buf[idx] = (char)PGUNSIXBIT(errCode);
			errCode >>= 6;
		}
		buf[idx] = 0;
	
		sqlState = String_createJavaStringFromNTS(buf);
	
		ex = JNI_newObject(SQLException_class, SQLException_init, message, sqlState);
	
		JNI_deleteLocalRef(message);
		JNI_deleteLocalRef(sqlState);
		JNI_throw(ex);
	}
	PG_CATCH();
	{
		ereport(WARNING, (errcode(errCode), errmsg("Exception while generating exception: %s", buf)));
	}
	PG_END_TRY();
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
	ereport(DEBUG3, (errmsg("%s", buf)));

	PG_TRY();
	{
		message = String_createJavaStringFromNTS(buf);
	
		ex = JNI_newObject(IllegalArgumentException_class, IllegalArgumentException_init, message);
	
		JNI_deleteLocalRef(message);
		JNI_throw(ex);
	}
	PG_CATCH();
	{
		ereport(WARNING, (errcode(ERRCODE_INTERNAL_ERROR), errmsg("Exception while generating exception: %s", buf)));
	}
	PG_END_TRY();
	va_end(args);
}

void Exception_throwSPI(const char* function, int errCode)
{
	Exception_throw(ERRCODE_INTERNAL_ERROR,
		"SPI function SPI_%s failed with error %s", function,
			SPI_result_code_string(errCode));
}

void Exception_throw_unhandled()
{
	jobject ex;
	PG_TRY();
	{
		ex = JNI_callStaticObjectMethodLocked(
			UnhandledPGException_class, UnhandledPGException_obtain);
		JNI_throw(ex);
	}
	PG_CATCH();
	{
		elog(WARNING, "Exception while generating exception");
	}
	PG_END_TRY();
}

void Exception_throw_ERROR(const char* funcName)
{
	jobject ex;
	PG_TRY();
	{
		jobject ed = pljava_ErrorData_getCurrentError();
	
		FlushErrorState();
	
		ex = JNI_callStaticObjectMethodLocked(
			ServerException_class, ServerException_obtain, ed);
		currentInvocation->errorOccurred = true;

		elog(DEBUG2, "Exception in function %s", funcName);

		JNI_deleteLocalRef(ed);
		JNI_throw(ex);
	}
	PG_CATCH();
	{
		elog(WARNING, "Exception while generating exception");
	}
	PG_END_TRY();
}

extern void Exception_initialize(void);
void Exception_initialize(void)
{
	Class_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Class"));

	Throwable_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Throwable"));
	Throwable_getMessage = PgObject_getJavaMethod(Throwable_class, "getMessage", "()Ljava/lang/String;");
	Throwable_printStackTrace = PgObject_getJavaMethod(Throwable_class, "printStackTrace", "()V");

	IllegalArgumentException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/IllegalArgumentException"));
	IllegalArgumentException_init = PgObject_getJavaMethod(IllegalArgumentException_class, "<init>", "(Ljava/lang/String;)V");

	SQLException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/sql/SQLException"));
	SQLException_init = PgObject_getJavaMethod(SQLException_class, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
	SQLException_getSQLState = PgObject_getJavaMethod(SQLException_class, "getSQLState", "()Ljava/lang/String;");

	UnsupportedOperationException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/UnsupportedOperationException"));
	UnsupportedOperationException_init = PgObject_getJavaMethod(UnsupportedOperationException_class, "<init>", "(Ljava/lang/String;)V");

	NoSuchFieldError_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/NoSuchFieldError"));
	NoSuchMethodError_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/NoSuchMethodError"));

	Class_getName = PgObject_getJavaMethod(Class_class, "getName", "()Ljava/lang/String;");
	Class_getCanonicalName = PgObject_getJavaMethod(Class_class,
		"getCanonicalName", "()Ljava/lang/String;");
}

extern void Exception_initialize2(void);
void Exception_initialize2(void)
{
	ServerException_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/ServerException"));
	ServerException_obtain = PgObject_getStaticJavaMethod(
		ServerException_class, "obtain",
		"(Lorg/postgresql/pljava/internal/ErrorData;)"
		"Lorg/postgresql/pljava/internal/ServerException;");

	ServerException_getErrorData = PgObject_getJavaMethod(ServerException_class, "getErrorData", "()Lorg/postgresql/pljava/internal/ErrorData;");

	UnhandledPGException_class = (jclass)JNI_newGlobalRef(
		PgObject_getJavaClass(
			"org/postgresql/pljava/internal/UnhandledPGException"));
	UnhandledPGException_obtain = PgObject_getStaticJavaMethod(
		UnhandledPGException_class, "obtain",
		"()Lorg/postgresql/pljava/internal/UnhandledPGException;");
}
