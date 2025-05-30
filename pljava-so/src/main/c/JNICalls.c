/*
 * Copyright (c) 2004-2021 Tada AB and other contributors, as listed below.
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

#include <string.h> /* for _MSC_VER *_min_messages hack */

#include "pljava/JNICalls.h"
#include "pljava/Backend.h"
#include "pljava/Invocation.h"
#include "pljava/Exception.h"
#include "pljava/type/ErrorData.h"
#include "pljava/type/String.h"

JNIEnv* jniEnv;
jint (JNICALL *pljava_createvm)(JavaVM **, void **, void *);

void* mainThreadId; /* declared in pljava.h */

static JNIEnv* primordialJNIEnv;

static jobject s_threadLock;

static bool s_refuseOtherThreads = false;
static bool s_doMonitorOps = true;

static jclass    s_Thread_class;
static jmethodID s_Thread_currentThread;
static jfieldID  s_Thread_contextLoader;

static jobject   s_threadObject;

void pljava_JNI_setThreadPolicy(bool refuseOtherThreads, bool doMonitorOps)
{
	s_refuseOtherThreads = refuseOtherThreads;
	s_doMonitorOps = doMonitorOps;
}

/*
 * This file contains very specialized methods for updating the context
 * class loader of a thread, because this is where they can be implemented
 * without the overhead of several calls to wrappers defined here.
 *
 * More lightweight implementations of those can be chosen if the selected
 * thread policy precludes native access from any but the primordial thread.
 */
JNI_ContextLoaderUpdater  *JNI_loaderUpdater;
JNI_ContextLoaderRestorer *JNI_loaderRestorer;

static JNI_ContextLoaderUpdater  _noopUpdater;
static JNI_ContextLoaderRestorer _noopRestorer;

static JNI_ContextLoaderUpdater  _lightUpdater;
static JNI_ContextLoaderRestorer _lightRestorer;

static JNI_ContextLoaderUpdater  _heavyUpdater;
static JNI_ContextLoaderRestorer _heavyRestorer;

void pljava_JNI_threadInitialize(bool manageLoader)
{
	if ( ! manageLoader )
	{
		JNI_loaderUpdater  = _noopUpdater;
		JNI_loaderRestorer = _noopRestorer;
		return;
	}

	s_Thread_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"java/lang/Thread"));
	s_Thread_currentThread = PgObject_getStaticJavaMethod(
		s_Thread_class,
		"currentThread",
		"()"
		"Ljava/lang/Thread;");
	s_Thread_contextLoader = JNI_getFieldIDOrNull(s_Thread_class,
		"contextClassLoader", "Ljava/lang/ClassLoader;");

	if ( NULL == s_Thread_contextLoader )
	{
		ereport(WARNING, (
			errmsg("unable to manage thread context classloaders in this JVM")
		));
		JNI_loaderUpdater  = _noopUpdater;
		JNI_loaderRestorer = _noopRestorer;
	}
	else if ( s_refuseOtherThreads  ||  ! s_doMonitorOps )
	{
		s_threadObject =
			JNI_newGlobalRef(
				JNI_callStaticObjectMethod(
					s_Thread_class, s_Thread_currentThread));
		JNI_loaderUpdater  = _lightUpdater;
		JNI_loaderRestorer = _lightRestorer;
	}
	else
	{
		JNI_loaderUpdater  = _heavyUpdater;
		JNI_loaderRestorer = _heavyRestorer;
	}
}


/*
 * BEGIN_JAVA and END_JAVA are used in JNI wrappers that are not expected to
 * invoke Java methods; all they do is play the game with the scope of the JNI
 * env value that was devised to fail fast if the intended pattern for PL/Java's
 * JNI usage isn't followed.
 *
 * BEGIN_CALL and END_CALL add to that the releasing of the "THREADLOCK"
 * monitor when calling into Java, and reacquiring it on return, that support
 * the java_thread_pg_entry=allow mode of operation, and also checking for
 * exceptions and turning them into PostgreSQL ereports.
 *
 * The _MONITOR_HELD flavors of those skip the monitor operations but still do
 * the exception checks. They are used in a select few *Locked flavors of
 * method call wrappers used where only known and lightweight Java methods will
 * be invoked and not arbitrary methods of user code.
 */
#define BEGIN_JAVA { JNIEnv* env = jniEnv; jniEnv = 0;
#define END_JAVA jniEnv = env; }

#define BEGIN_CALL \
	BEGIN_JAVA \
	if(s_doMonitorOps && ((*env)->MonitorExit(env, s_threadLock) < 0)) \
		elog(ERROR, "Java exit monitor failure");

#define END_CALL endCall(env); }

#define BEGIN_CALL_MONITOR_HELD \
	BEGIN_JAVA

#define END_CALL_MONITOR_HELD endCallMonitorHeld(env); }

static void elogExceptionMessage(JNIEnv* env, jthrowable exh, int logLevel)
{
	StringInfoData buf;
	int sqlState = ERRCODE_INTERNAL_ERROR;
	JNIEnv* saveEnv = jniEnv;
	jclass exhClass = (*env)->GetObjectClass(env, exh);
	jstring jtmp =
		(jstring)(*env)->CallObjectMethod(env, exhClass, Class_getName);
	/* ExceptionOccurred check is found below ... */

	initStringInfo(&buf);

	jniEnv = env; /* Used by the String operations */

	if ( 0 == (*env)->ExceptionOccurred(env) ) /* ... here */
		String_appendJavaString(&buf, jtmp);
	else
	{
		(*env)->ExceptionClear(env);
		appendStringInfoString(&buf, "<unknown Java class>");
	}

	(*env)->DeleteLocalRef(env, exhClass);
	(*env)->DeleteLocalRef(env, jtmp);

	jtmp = (jstring)(*env)->CallObjectMethod(env, exh, Throwable_getMessage);
	if ( 0 != (*env)->ExceptionOccurred(env) )
	{
		(*env)->ExceptionClear(env);
		jtmp = 0;
	}

	if(jtmp != 0)
	{
		appendStringInfoString(&buf, ": ");
		String_appendJavaString(&buf, jtmp);
		(*env)->DeleteLocalRef(env, jtmp);
	}

	if((*env)->IsInstanceOf(env, exh, SQLException_class))
	{
		jtmp = (*env)->CallObjectMethod(env, exh, SQLException_getSQLState);
		if ( 0 != (*env)->ExceptionOccurred(env) )
		{
			(*env)->ExceptionClear(env);
			jtmp = 0;
		}

		if(jtmp != 0)
		{
			char* s = String_createNTS(jtmp);
			(*env)->DeleteLocalRef(env, jtmp);

			if(strlen(s) >= 5)
				sqlState = MAKE_SQLSTATE(s[0], s[1], s[2], s[3], s[4]);
			pfree(s);
		}
	}
	jniEnv = saveEnv;
	ereport(logLevel, (errcode(sqlState), errmsg("%s", buf.data)));
}

static void printStacktrace(JNIEnv* env, jobject exh, int elevel)
{
#if 100002<=PG_VERSION_NUM || \
	 90607<=PG_VERSION_NUM && PG_VERSION_NUM<100000 || \
	 90511<=PG_VERSION_NUM && PG_VERSION_NUM< 90600 || \
	! defined(_MSC_VER)
	if(elevel >= log_min_messages || elevel >= client_min_messages)
#else
	/* This is gross, but only happens as often as an exception escapes Java
	 * code to be rethrown. There is some renewed interest on pgsql-hackers to
	 * find a good answer for the MSVC PGDLLIMPORT nonsense, and all of this
	 * handling of exceptions and logging could stand some rework anyway.
	 */
	if ( 0 == strncmp("debug", PG_GETCONFIGOPTION("log_min_messages"), 5)
		|| 0 == strncmp("debug", PG_GETCONFIGOPTION("client_min_messages"), 5) )
#endif
	{
		int currLevel = Backend_setJavaLogLevel(elevel);
		(*env)->CallVoidMethod(env, exh, Throwable_printStackTrace);
		(*env)->ExceptionOccurred(env); /* sop for JNI exception-check check */
		Backend_setJavaLogLevel(currLevel);
	}
}

static void endCall(JNIEnv* env)
{
	jobject exh = (*env)->ExceptionOccurred(env);
	if(exh != 0)
		(*env)->ExceptionClear(env);

	if(s_doMonitorOps && ((*env)->MonitorEnter(env, s_threadLock) < 0))
		elog(ERROR, "Java enter monitor failure");

	jniEnv = env;
	if(exh != 0)
	{
		printStacktrace(env, exh, DEBUG1);
		if((*env)->IsInstanceOf(env, exh, ServerException_class))
		{
			/* Rethrow the server error.
			 */
			jobject jed = (*env)->CallObjectMethod(env, exh, ServerException_getErrorData);
			if ( 0 != (*env)->ExceptionOccurred(env) )
			{
				(*env)->ExceptionClear(env);
				jed = 0;
			}

			if(jed != 0)
				ReThrowError(pljava_ErrorData_getErrorData(jed));
		}
		/* There's no return from this call.
		 */
		elogExceptionMessage(env, exh, ERROR);
	}
}

static void endCallMonitorHeld(JNIEnv* env)
{
	jobject exh = (*env)->ExceptionOccurred(env);
	if(exh != 0)
		(*env)->ExceptionClear(env);

	jniEnv = env;
	if(exh != 0)
	{
		printStacktrace(env, exh, DEBUG1);
		if((*env)->IsInstanceOf(env, exh, ServerException_class))
		{
			/* Rethrow the server error.
			 */
			jobject jed = (*env)->CallObjectMethod(env, exh, ServerException_getErrorData);
			if ( 0 != (*env)->ExceptionOccurred(env) )
			{
				(*env)->ExceptionClear(env);
				jed = 0;
			}

			if(jed != 0)
				ReThrowError(pljava_ErrorData_getErrorData(jed));
		}
		/* There's no return from this call.
		 */
		elogExceptionMessage(env, exh, ERROR);
	}
}

bool beginNativeNoErrCheck(JNIEnv* env)
{
	if ( s_refuseOtherThreads  &&  env != primordialJNIEnv )
	{
		env = JNI_setEnv(env);
		Exception_throw(ERRCODE_INTERNAL_ERROR,
			"Attempt by non-initial thread to enter PostgreSQL from Java");
		JNI_setEnv(env);
		return false;
	}

	if((env = JNI_setEnv(env)) != 0)
	{
		/* The backend is *not* awaiting the return of a call to the JVM
		 * so there's no way the JVM can be allowed to call out at this
		 * point.
		 */
		Exception_throw(ERRCODE_INTERNAL_ERROR,
			"An attempt was made to call a PostgreSQL backend function while main thread was not in the JVM");
		JNI_setEnv(env);
		return false;
	}
	return true;
}

bool beginNative(JNIEnv* env)
{
	if (!currentInvocation)
	{
		env = JNI_setEnv(env);
		Exception_throw(ERRCODE_INTERNAL_ERROR, "An attempt was made to call a PostgreSQL backend function in a transaction callback.  At the end of a transaction you may not access the database any longer.");
		JNI_setEnv(env);
		return false;
	}

	if(currentInvocation->errorOccurred)
	{
		/* An elog with level higher than ERROR was issued. The transaction
		 * state is unknown. There's no way the JVM is allowed to enter the
		 * backend at this point.
		 */
		env = JNI_setEnv(env);
		Exception_throw_unhandled();
		JNI_setEnv(env);
		return false;
	}
	return beginNativeNoErrCheck(env);
}

jboolean JNI_callBooleanMethod(jobject object, jmethodID methodID, ...)
{
	jboolean result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callBooleanMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jboolean JNI_callBooleanMethodV(jobject object, jmethodID methodID, va_list args)
{
	jboolean result;
	BEGIN_CALL
	result = (*env)->CallBooleanMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jbyte JNI_callByteMethod(jobject object, jmethodID methodID, ...)
{
	jbyte result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callByteMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jbyte JNI_callByteMethodV(jobject object, jmethodID methodID, va_list args)
{
	jbyte result;
	BEGIN_CALL
	result = (*env)->CallByteMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jdouble JNI_callDoubleMethod(jobject object, jmethodID methodID, ...)
{
	jdouble result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callDoubleMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jdouble JNI_callDoubleMethodV(jobject object, jmethodID methodID, va_list args)
{
	jdouble result;
	BEGIN_CALL
	result = (*env)->CallDoubleMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jfloat JNI_callFloatMethod(jobject object, jmethodID methodID, ...)
{
	jfloat result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callFloatMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jfloat JNI_callFloatMethodV(jobject object, jmethodID methodID, va_list args)
{
	jfloat result;
	BEGIN_CALL
	result = (*env)->CallFloatMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jint JNI_callIntMethod(jobject object, jmethodID methodID, ...)
{
	jint result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callIntMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jint JNI_callIntMethodV(jobject object, jmethodID methodID, va_list args)
{
	jint result;
	BEGIN_CALL
	result = (*env)->CallIntMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jint JNI_callIntMethodLocked(jobject object, jmethodID methodID, ...)
{
	jint result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callIntMethodLockedV(object, methodID, args);
	va_end(args);
	return result;
}

jint JNI_callIntMethodLockedV(jobject object, jmethodID methodID, va_list args)
{
	jint result;
	BEGIN_CALL_MONITOR_HELD
	result = (*env)->CallIntMethodV(env, object, methodID, args);
	END_CALL_MONITOR_HELD
	return result;
}

jlong JNI_callLongMethod(jobject object, jmethodID methodID, ...)
{
	jlong result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callLongMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jlong JNI_callLongMethodV(jobject object, jmethodID methodID, va_list args)
{
	jlong result;
	BEGIN_CALL
	result = (*env)->CallLongMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jlong JNI_callLongMethodLocked(jobject object, jmethodID methodID, ...)
{
	jlong result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callLongMethodLockedV(object, methodID, args);
	va_end(args);
	return result;
}

jlong JNI_callLongMethodLockedV(jobject object, jmethodID methodID, va_list args)
{
	jlong result;
	BEGIN_CALL_MONITOR_HELD
	result = (*env)->CallLongMethodV(env, object, methodID, args);
	END_CALL_MONITOR_HELD
	return result;
}

jshort JNI_callShortMethod(jobject object, jmethodID methodID, ...)
{
	jshort result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callShortMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jshort JNI_callShortMethodV(jobject object, jmethodID methodID, va_list args)
{
	jshort result;
	BEGIN_CALL
	result = (*env)->CallShortMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jobject JNI_callObjectMethod(jobject object, jmethodID methodID, ...)
{
	jobject result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callObjectMethodV(object, methodID, args);
	va_end(args);
	return result;
}

jobject JNI_callObjectMethodV(jobject object, jmethodID methodID, va_list args)
{
	jobject result;
	BEGIN_CALL
	result = (*env)->CallObjectMethodV(env, object, methodID, args);
	END_CALL
	return result;
}

jobject JNI_callObjectMethodLocked(jobject object, jmethodID methodID, ...)
{
	jobject result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callObjectMethodLockedV(object, methodID, args);
	va_end(args);
	return result;
}

jobject JNI_callObjectMethodLockedV(jobject object, jmethodID methodID, va_list args)
{
	jobject result;
	BEGIN_CALL_MONITOR_HELD
	result = (*env)->CallObjectMethodV(env, object, methodID, args);
	END_CALL_MONITOR_HELD
	return result;
}

jboolean JNI_callStaticBooleanMethod(jclass clazz, jmethodID methodID, ...)
{
	jboolean result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticBooleanMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jboolean JNI_callStaticBooleanMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jboolean result;
	BEGIN_CALL
	result = (*env)->CallStaticBooleanMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jboolean JNI_callStaticBooleanMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jboolean result;
	BEGIN_CALL
	result = (*env)->CallStaticBooleanMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jbyte JNI_callStaticByteMethod(jclass clazz, jmethodID methodID, ...)
{
	jbyte result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticByteMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jbyte JNI_callStaticByteMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jbyte result;
	BEGIN_CALL
	result = (*env)->CallStaticByteMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jbyte JNI_callStaticByteMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jbyte result;
	BEGIN_CALL
	result = (*env)->CallStaticByteMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jshort JNI_callStaticShortMethod(jclass clazz, jmethodID methodID, ...)
{
	jshort result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticShortMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jshort JNI_callStaticShortMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jshort result;
	BEGIN_CALL
	result = (*env)->CallStaticShortMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jchar JNI_callStaticCharMethod(jclass clazz, jmethodID methodID, ...)
{
	jchar result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticCharMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jchar JNI_callStaticCharMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jchar result;
	BEGIN_CALL
	result = (*env)->CallStaticCharMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jdouble JNI_callStaticDoubleMethod(jclass clazz, jmethodID methodID, ...)
{
	jdouble result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticDoubleMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jdouble JNI_callStaticDoubleMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jdouble result;
	BEGIN_CALL
	result = (*env)->CallStaticDoubleMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jdouble JNI_callStaticDoubleMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jdouble result;
	BEGIN_CALL
	result = (*env)->CallStaticDoubleMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jfloat JNI_callStaticFloatMethod(jclass clazz, jmethodID methodID, ...)
{
	jfloat result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticFloatMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jfloat JNI_callStaticFloatMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jfloat result;
	BEGIN_CALL
	result = (*env)->CallStaticFloatMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jfloat JNI_callStaticFloatMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jfloat result;
	BEGIN_CALL
	result = (*env)->CallStaticFloatMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jint JNI_callStaticIntMethod(jclass clazz, jmethodID methodID, ...)
{
	jint result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticIntMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jint JNI_callStaticIntMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jint result;
	BEGIN_CALL
	result = (*env)->CallStaticIntMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jint JNI_callStaticIntMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jint result;
	BEGIN_CALL
	result = (*env)->CallStaticIntMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jlong JNI_callStaticLongMethod(jclass clazz, jmethodID methodID, ...)
{
	jlong result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticLongMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jlong JNI_callStaticLongMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jlong result;
	BEGIN_CALL
	result = (*env)->CallStaticLongMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jlong JNI_callStaticLongMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jlong result;
	BEGIN_CALL
	result = (*env)->CallStaticLongMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jobject JNI_callStaticObjectMethod(jclass clazz, jmethodID methodID, ...)
{
	jobject result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticObjectMethodV(clazz, methodID, args);
	va_end(args);
	return result;
}

jobject JNI_callStaticObjectMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jobject result;
	BEGIN_CALL
	result = (*env)->CallStaticObjectMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

jobject JNI_callStaticObjectMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	jobject result;
	BEGIN_CALL
	result = (*env)->CallStaticObjectMethodV(env, clazz, methodID, args);
	END_CALL
	return result;
}

jobject JNI_callStaticObjectMethodLocked(jclass clazz, jmethodID methodID, ...)
{
	jobject result;
	va_list args;
	va_start(args, methodID);
	result = JNI_callStaticObjectMethodLockedV(clazz, methodID, args);
	va_end(args);
	return result;
}

jobject JNI_callStaticObjectMethodLockedV(jclass clazz, jmethodID methodID, va_list args)
{
	jobject result;
	BEGIN_CALL_MONITOR_HELD
	result = (*env)->CallStaticObjectMethodV(env, clazz, methodID, args);
	END_CALL_MONITOR_HELD
	return result;
}

jshort JNI_callStaticShortMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	jshort result;
	BEGIN_CALL
	result = (*env)->CallStaticShortMethodA(env, clazz, methodID, args);
	END_CALL
	return result;
}

void JNI_callStaticVoidMethod(jclass clazz, jmethodID methodID, ...)
{
	va_list args;
	va_start(args, methodID);
	JNI_callStaticVoidMethodV(clazz, methodID, args);
	va_end(args);
}

void JNI_callStaticVoidMethodA(jclass clazz, jmethodID methodID, jvalue* args)
{
	BEGIN_CALL
	(*env)->CallStaticVoidMethodA(env, clazz, methodID, args);
	END_CALL
}

void JNI_callStaticVoidMethodV(jclass clazz, jmethodID methodID, va_list args)
{
	BEGIN_CALL
	(*env)->CallStaticVoidMethodV(env, clazz, methodID, args);
	END_CALL
}

void JNI_callStaticVoidMethodLocked(jclass clazz, jmethodID methodID, ...)
{
	va_list args;
	va_start(args, methodID);
	JNI_callStaticVoidMethodLockedV(clazz, methodID, args);
	va_end(args);
}

void JNI_callStaticVoidMethodLockedV(jclass clazz, jmethodID methodID, va_list args)
{
	BEGIN_CALL_MONITOR_HELD
	(*env)->CallStaticVoidMethodV(env, clazz, methodID, args);
	END_CALL_MONITOR_HELD
}

void JNI_callVoidMethod(jobject object, jmethodID methodID, ...)
{
	va_list args;
	va_start(args, methodID);
	JNI_callVoidMethodV(object, methodID, args);
	va_end(args);
}

void JNI_callVoidMethodV(jobject object, jmethodID methodID, va_list args)
{
	BEGIN_CALL
	(*env)->CallVoidMethodV(env, object, methodID, args);
	END_CALL
}

void JNI_callVoidMethodLocked(jobject object, jmethodID methodID, ...)
{
	va_list args;
	va_start(args, methodID);
	JNI_callVoidMethodLockedV(object, methodID, args);
	va_end(args);
}

void JNI_callVoidMethodLockedV(jobject object, jmethodID methodID, va_list args)
{
	BEGIN_CALL_MONITOR_HELD
	(*env)->CallVoidMethodV(env, object, methodID, args);
	END_CALL_MONITOR_HELD
}

jint JNI_createVM(JavaVM** javaVM, JavaVMInitArgs* vmArgs)
{
	JNIEnv* env = 0;
	jint jstat = pljava_createvm(javaVM, (void **)&env, vmArgs);
	if(jstat == JNI_OK)
	{
		jniEnv = env;
		primordialJNIEnv = env;
		mainThreadId = env;
	}
	return jstat;
}

void JNI_deleteGlobalRef(jobject object)
{
	BEGIN_JAVA
	(*env)->DeleteGlobalRef(env, object);
	END_JAVA
}

void JNI_deleteLocalRef(jobject object)
{
	BEGIN_JAVA
	(*env)->DeleteLocalRef(env, object);
	END_JAVA
}

void JNI_deleteWeakGlobalRef(jweak object)
{
	BEGIN_JAVA
	(*env)->DeleteWeakGlobalRef(env, object);
	END_JAVA
}

jint JNI_destroyVM(JavaVM *vm)
{
	jint result;
	BEGIN_JAVA
	result = (*vm)->DestroyJavaVM(vm);
	END_JAVA
	jniEnv = 0;
	s_threadLock = 0;
	return result;
}

jboolean JNI_exceptionCheck(void)
{
	jboolean result;
	BEGIN_JAVA
	result = (*env)->ExceptionCheck(env);
	END_JAVA
	return result;
}

void JNI_exceptionClear(void)
{
	BEGIN_JAVA
	(*env)->ExceptionClear(env);
	END_JAVA
}

void JNI_exceptionDescribe(void)
{
	/* The ExceptionDescribe will print on stderr. Not a good idea
	 * since the exception itself might have been caused by an unwriteable
	 * stdout/stderr (happens when running as a windows service
	 *
	 * (*env)->ExceptionDescribe(env);
	 */
	jthrowable exh;
	BEGIN_JAVA
	exh = (*env)->ExceptionOccurred(env);
	if(exh != 0)
	{
		(*env)->ExceptionClear(env);
		printStacktrace(env, exh, DEBUG1);
		elogExceptionMessage(env, exh, WARNING);
	}
	END_JAVA
}

void JNI_exceptionStacktraceAtLevel(jthrowable exh, int elevel)
{
	BEGIN_JAVA
	elogExceptionMessage(env, exh, elevel);
	printStacktrace(env, exh, elevel);
	END_JAVA
}

jthrowable JNI_exceptionOccurred(void)
{
	jthrowable result;
	BEGIN_JAVA
	result = (*env)->ExceptionOccurred(env);
	END_JAVA
	return result;
}

jclass JNI_findClass(const char* className)
{
	jclass result;
	BEGIN_JAVA
	result = (*env)->FindClass(env, className);
	END_JAVA
	return result;
}

jsize JNI_getArrayLength(jarray array)
{
	jsize result;
	BEGIN_JAVA
	result = (*env)->GetArrayLength(env, array);
	END_JAVA
	return result;
}

jbyte* JNI_getByteArrayElements(jbyteArray array, jboolean* isCopy)
{
	jbyte* result;
	BEGIN_JAVA
	result = (*env)->GetByteArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getByteArrayRegion(jbyteArray array, jsize start, jsize len, jbyte* buf)
{
	BEGIN_JAVA
	(*env)->GetByteArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jboolean* JNI_getBooleanArrayElements(jbooleanArray array, jboolean* isCopy)
{
	jboolean* result;
	BEGIN_JAVA
	result = (*env)->GetBooleanArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getBooleanArrayRegion(jbooleanArray array, jsize start, jsize len, jboolean* buf)
{
	BEGIN_JAVA
	(*env)->GetBooleanArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jdouble* JNI_getDoubleArrayElements(jdoubleArray array, jboolean* isCopy)
{
	jdouble* result;
	BEGIN_JAVA
	result = (*env)->GetDoubleArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getDoubleArrayRegion(jdoubleArray array, jsize start, jsize len, jdouble* buf)
{
	BEGIN_JAVA
	(*env)->GetDoubleArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jfieldID JNI_getFieldID(jclass clazz, const char* name, const char* sig)
{
	jfieldID result;
	BEGIN_JAVA
	result = (*env)->GetFieldID(env, clazz, name, sig);
	END_JAVA
	return result;
}

jfieldID JNI_getFieldIDOrNull(jclass clazz, const char* name, const char* sig)
{
	jfieldID result;
	jobject exh;
	BEGIN_CALL
	result = (*env)->GetFieldID(env, clazz, name, sig);
	if(result == 0) {
		exh = (*env)->ExceptionOccurred(env);
		if ( 0 != exh )
		{
			/*
			 * Ignore a NoSuchFieldError, but not any other exception.
			 * This operation order (first clear the pending exception, then
			 * do the IsInstanceOf check, then Throw again if not the expected
			 * class) avoids a benign -Xcheck:JNI warning about calling
			 * IsInstanceOf while an exception is pending.
			 */
			(*env)->ExceptionClear(env);
			if ( ! (*env)->IsInstanceOf(env, exh, NoSuchFieldError_class) )
				(*env)->Throw(env, exh);
			(*env)->DeleteLocalRef(env, exh);
		}
	}
	END_CALL
	return result;
}

jfloat* JNI_getFloatArrayElements(jfloatArray array, jboolean* isCopy)
{
	jfloat* result;
	BEGIN_JAVA
	result = (*env)->GetFloatArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getFloatArrayRegion(jfloatArray array, jsize start, jsize len, jfloat* buf)
{
	BEGIN_JAVA
	(*env)->GetFloatArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jint* JNI_getIntArrayElements(jintArray array, jboolean* isCopy)
{
	jint* result;
	BEGIN_JAVA
	result = (*env)->GetIntArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getIntArrayRegion(jintArray array, jsize start, jsize len, jint* buf)
{
	BEGIN_JAVA
	(*env)->GetIntArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jint JNI_getIntField(jobject object, jfieldID field)
{
	jint result;
	BEGIN_JAVA
	result = (*env)->GetIntField(env, object, field);
	END_JAVA
	return result;
}

jlong* JNI_getLongArrayElements(jlongArray array, jboolean* isCopy)
{
	jlong* result;
	BEGIN_JAVA
	result = (*env)->GetLongArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getLongArrayRegion(jlongArray array, jsize start, jsize len, jlong* buf)
{
	BEGIN_JAVA
	(*env)->GetLongArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jlong JNI_getLongField(jobject object, jfieldID field)
{
	jlong result;
	BEGIN_JAVA
	result = (*env)->GetLongField(env, object, field);
	END_JAVA
	return result;
}

jmethodID JNI_getMethodID(jclass clazz, const char* name, const char* sig)
{
	jmethodID result;
	BEGIN_JAVA
	result = (*env)->GetMethodID(env, clazz, name, sig);
	END_JAVA
	return result;
}

jobject JNI_getObjectArrayElement(jobjectArray array, jsize index)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->GetObjectArrayElement(env, array, index);
	END_JAVA
	return result;
}

jclass JNI_getObjectClass(jobject obj)
{
	jclass result;
	BEGIN_JAVA
	result = (*env)->GetObjectClass(env, obj);
	END_JAVA
	return result;
}

jshort* JNI_getShortArrayElements(jshortArray array, jboolean* isCopy)
{
	jshort* result;
	BEGIN_JAVA
	result = (*env)->GetShortArrayElements(env, array, isCopy);
	END_JAVA
	return result;
}

void JNI_getShortArrayRegion(jshortArray array, jsize start, jsize len, jshort* buf)
{
	BEGIN_JAVA
	(*env)->GetShortArrayRegion(env, array, start, len, buf);
	END_JAVA
}

jfieldID JNI_getStaticFieldID(jclass clazz, const char* name, const char* sig)
{
	jfieldID result;
	BEGIN_JAVA
	result = (*env)->GetStaticFieldID(env, clazz, name, sig);
	END_JAVA
	return result;
}

jmethodID JNI_getStaticMethodID(jclass clazz, const char* name, const char* sig)
{
	jmethodID result;
	BEGIN_CALL
	result = (*env)->GetStaticMethodID(env, clazz, name, sig);
	END_CALL
	return result;
}

jmethodID JNI_getStaticMethodIDOrNull(jclass clazz, const char* name, const char* sig)
{
	jmethodID result;
	jobject exh;
	BEGIN_CALL
	result = (*env)->GetStaticMethodID(env, clazz, name, sig);
	if(result == 0) {
		exh = (*env)->ExceptionOccurred(env);
		if ( 0 != exh )
		{
			/*
			 * Ignore a NoSuchMethodError, but not any other exception.
			 * This operation order (first clear the pending exception, then
			 * do the IsInstanceOf check, then Throw again if not the expected
			 * class) avoids a benign -Xcheck:JNI warning about calling
			 * IsInstanceOf while an exception is pending.
			 */
			(*env)->ExceptionClear(env);
			if ( ! (*env)->IsInstanceOf(env, exh, NoSuchMethodError_class) )
				(*env)->Throw(env, exh);
			(*env)->DeleteLocalRef(env, exh);
		}
	}
	END_CALL
	return result;
}

jboolean JNI_getStaticBooleanField(jclass clazz, jfieldID field)
{
	jboolean result;
	BEGIN_JAVA
	result = (*env)->GetStaticBooleanField(env, clazz, field);
	END_JAVA
	return result;
}

jint JNI_getStaticIntField(jclass clazz, jfieldID field)
{
	jint result;
	BEGIN_JAVA
	result = (*env)->GetStaticIntField(env, clazz, field);
	END_JAVA
	return result;
}

jobject JNI_getStaticObjectField(jclass clazz, jfieldID field)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->GetStaticObjectField(env, clazz, field);
	END_JAVA
	return result;
}

const char* JNI_getStringUTFChars(jstring string, jboolean* isCopy)
{
	const char* result;
	BEGIN_JAVA
	result = (*env)->GetStringUTFChars(env, string, isCopy);
	END_JAVA
	return result;
}

jboolean JNI_hasNullArrayElement(jobjectArray array)
{
	jsize idx;
	jboolean foundNull = JNI_FALSE;
	BEGIN_JAVA
	idx = (*env)->GetArrayLength(env, array);
	while(--idx >= 0)
	{
		if((*env)->GetObjectArrayElement(env, array, idx) != 0)
			continue;
		foundNull = JNI_TRUE;
		break;
	}
	END_JAVA
	return foundNull;
}

jboolean JNI_isCallingJava(void)
{
	return jniEnv == 0;
}

jboolean JNI_isInstanceOf(jobject obj, jclass clazz)
{
	jboolean result;
	BEGIN_JAVA
	result = (*env)->IsInstanceOf(env, obj, clazz);
	END_JAVA
	return result;
}

jboolean JNI_isSameObject(jobject obj1, jobject obj2)
{
	jboolean result;
	BEGIN_JAVA
	result = (*env)->IsSameObject(env, obj1, obj2);
	END_JAVA
	return result;
}

jbyteArray JNI_newByteArray(jsize length)
{
	jbyteArray result;
	BEGIN_JAVA
	result = (*env)->NewByteArray(env, length);
	END_JAVA
	return result;
}

jbooleanArray JNI_newBooleanArray(jsize length)
{
	jbooleanArray result;
	BEGIN_JAVA
	result = (*env)->NewBooleanArray(env, length);
	END_JAVA
	return result;
}

jobjectArray JNI_newObjectArray(jsize length, jclass elementClass, jobject initialElement)
{
	jobjectArray result;
	BEGIN_JAVA
	result = (*env)->NewObjectArray(env, length, elementClass, initialElement);
	END_JAVA
	return result;
}

jobject JNI_newDirectByteBuffer(void* address, jlong capacity)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->NewDirectByteBuffer(env, address, capacity);
	END_JAVA
	return result;
}

jdoubleArray JNI_newDoubleArray(jsize length)
{
	jdoubleArray result;
	BEGIN_JAVA
	result = (*env)->NewDoubleArray(env, length);
	END_JAVA
	return result;
}

jfloatArray JNI_newFloatArray(jsize length)
{
	jfloatArray result;
	BEGIN_JAVA
	result = (*env)->NewFloatArray(env, length);
	END_JAVA
	return result;
}

jobject JNI_newGlobalRef(jobject object)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->NewGlobalRef(env, object);
	END_JAVA
	return result;
}

jintArray JNI_newIntArray(jsize length)
{
	jintArray result;
	BEGIN_JAVA
	result = (*env)->NewIntArray(env, length);
	END_JAVA
	return result;
}

jobject JNI_newLocalRef(jobject object)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->NewLocalRef(env, object);
	END_JAVA
	return result;
}

jlongArray JNI_newLongArray(jsize length)
{
	jlongArray result;
	BEGIN_JAVA
	result = (*env)->NewLongArray(env, length);
	END_JAVA
	return result;
}

jshortArray JNI_newShortArray(jsize length)
{
	jshortArray result;
	BEGIN_JAVA
	result = (*env)->NewShortArray(env, length);
	END_JAVA
	return result;
}

jstring JNI_newStringUTF(const char* bytes)
{
	jstring result;
	BEGIN_JAVA
	result = (*env)->NewStringUTF(env, bytes);
	END_JAVA
	return result;
}

jint JNI_pushLocalFrame(jint capacity)
{
	jint result;
	BEGIN_JAVA
	result = (*env)->PushLocalFrame(env, capacity);
	END_JAVA
	return result;
}

jobject JNI_popLocalFrame(jobject resultObj)
{
	jobject result;
	BEGIN_JAVA
	result = (*env)->PopLocalFrame(env, resultObj);
	END_JAVA
	return result;
}

jweak JNI_newWeakGlobalRef(jobject object)
{
	jweak result;
	BEGIN_JAVA
	result = (*env)->NewWeakGlobalRef(env, object);
	END_JAVA
	return result;
}

jobject JNI_newObject(jclass clazz, jmethodID ctor, ...)
{
	jobject result;
	va_list args;
	va_start(args, ctor);
	result = JNI_newObjectV(clazz, ctor, args);
	va_end(args);
	return result;
}

jobject JNI_newObjectV(jclass clazz, jmethodID ctor, va_list args)
{
	jobject result;
	BEGIN_CALL
	result = (*env)->NewObjectV(env, clazz, ctor, args);
	END_CALL
	return result;
}

jobject JNI_newObjectLocked(jclass clazz, jmethodID ctor, ...)
{
	jobject result;
	va_list args;
	va_start(args, ctor);
	result = JNI_newObjectLockedV(clazz, ctor, args);
	va_end(args);
	return result;
}

jobject JNI_newObjectLockedV(jclass clazz, jmethodID ctor, va_list args)
{
	jobject result;
	BEGIN_CALL_MONITOR_HELD
	result = (*env)->NewObjectV(env, clazz, ctor, args);
	END_CALL_MONITOR_HELD
	return result;
}

void JNI_releaseByteArrayElements(jbyteArray array, jbyte* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseByteArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseBooleanArrayElements(jbooleanArray array, jboolean* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseBooleanArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseDoubleArrayElements(jdoubleArray array, jdouble* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseDoubleArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseFloatArrayElements(jfloatArray array, jfloat* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseFloatArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseIntArrayElements(jintArray array, jint* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseIntArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseLongArrayElements(jlongArray array, jlong* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseLongArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseShortArrayElements(jshortArray array, jshort* elems, jint mode)
{
	BEGIN_JAVA
	(*env)->ReleaseShortArrayElements(env, array, elems, mode);
	END_JAVA
}

void JNI_releaseStringUTFChars(jstring string, const char *utf)
{
	BEGIN_JAVA
	(*env)->ReleaseStringUTFChars(env, string, utf);
	END_JAVA
}

jint JNI_registerNatives(jclass clazz, const JNINativeMethod* methods, jint nMethods)
{
	jint result;
	BEGIN_JAVA
	result = (*env)->RegisterNatives(env, clazz, methods, nMethods);
	END_JAVA
	return result;
}

void JNI_setByteArrayRegion(jbyteArray array, jsize start, jsize len, jbyte* buf)
{
	BEGIN_JAVA
	(*env)->SetByteArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setBooleanArrayRegion(jbooleanArray array, jsize start, jsize len, jboolean* buf)
{
	BEGIN_JAVA
	(*env)->SetBooleanArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setDoubleArrayRegion(jdoubleArray array, jsize start, jsize len, jdouble* buf)
{
	BEGIN_JAVA
	(*env)->SetDoubleArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setFloatArrayRegion(jfloatArray array, jsize start, jsize len, jfloat* buf)
{
	BEGIN_JAVA
	(*env)->SetFloatArrayRegion(env, array, start, len, buf);
	END_JAVA
}

JNIEnv* JNI_setEnv(JNIEnv* env)
{
	JNIEnv* oldEnv = jniEnv;
	jniEnv = env;
	return oldEnv;
}

void JNI_setIntArrayRegion(jintArray array, jsize start, jsize len, jint* buf)
{
	BEGIN_JAVA
	(*env)->SetIntArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setLongArrayRegion(jlongArray array, jsize start, jsize len, jlong* buf)
{
	BEGIN_JAVA
	(*env)->SetLongArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setIntField(jobject object, jfieldID field, jint value)
{
	BEGIN_JAVA
	(*env)->SetIntField(env, object, field, value);
	END_JAVA
}

void JNI_setLongField(jobject object, jfieldID field, jlong value)
{
	BEGIN_JAVA
	(*env)->SetLongField(env, object, field, value);
	END_JAVA
}

void JNI_setObjectArrayElement(jobjectArray array, jsize index, jobject value)
{
	BEGIN_JAVA
	(*env)->SetObjectArrayElement(env, array, index, value);
	END_JAVA
}

void JNI_setShortArrayRegion(jshortArray array, jsize start, jsize len, jshort* buf)
{
	BEGIN_JAVA
	(*env)->SetShortArrayRegion(env, array, start, len, buf);
	END_JAVA
}

void JNI_setStaticObjectField(jclass clazz, jfieldID field, jobject value)
{
	BEGIN_JAVA
	(*env)->SetStaticObjectField(env, clazz, field, value);
	END_JAVA
}

void JNI_setThreadLock(jobject lockObject)
{
	BEGIN_JAVA
	s_threadLock = (*env)->NewGlobalRef(env, lockObject);
	if(NULL != s_threadLock  &&  (*env)->MonitorEnter(env, s_threadLock) < 0)
		elog(ERROR, "Java enter monitor failure (initial)");
	END_JAVA
}

jint JNI_throw(jthrowable obj)
{
	jint result;
	BEGIN_JAVA
	result = (*env)->Throw(env, obj);
	END_JAVA
	return result;
}

/*
 * Implementations of the context class loader updater and restorer.
 * The loader reference passed in is not to be deleted. If saved anywhere,
 * a new global ref is to be taken, and later deleted when restored.
 */

static inline void _updaterCommon(JNIEnv *env, jobject thread, jobject loader)
{
	jobject old = (*env)->GetObjectField(env, thread, s_Thread_contextLoader);

	/*
	 * If it is not already the loader we want, change it, and set
	 * currentInvocation->savedLoader to restore it later. If this is
	 * a top-level invocation, we don't care what it gets restored to, so lie,
	 * and save loader there instead of old. If there are many consecutive
	 * top-level calls with the same context loader, that will save work later.
	 *
	 * If it is already the loader we want, again we check for a top-level call,
	 * and can leave currentInvocation->savedLoader completely unset in that
	 * case, so the restore call will be skipped completely. If not a top-level
	 * call, though, see that it gets restored to what the caller might expect,
	 * even if it somehow got changed.
	 */

	if ( ! (*env)->IsSameObject(env, old, loader) )
	{
		(*env)->SetObjectField(env, thread, s_Thread_contextLoader, loader);

		currentInvocation->savedLoader = (*env)->NewGlobalRef(env,
			( NULL == currentInvocation->previous ) ? loader : old);
	}
	else if ( NULL != currentInvocation->previous )
		currentInvocation->savedLoader = (*env)->NewGlobalRef(env, old);

	(*env)->DeleteLocalRef(env, old);
}

static void _heavyUpdater(jobject loader)
{
	jobject thread;
	jobject exh;

	BEGIN_JAVA

	thread =
		(*env)->CallStaticObjectMethod(env,
			s_Thread_class, s_Thread_currentThread); /* should never fail */

	exh = (*env)->ExceptionOccurred(env); /* but mollify -Xcheck:jni anyway */
	if(exh != 0)
	{
		(*env)->ExceptionClear(env);
		elogExceptionMessage(env, exh, ERROR);
	}

	_updaterCommon(env, thread, loader);

	(*env)->DeleteLocalRef(env, thread);

	END_JAVA
}

void _heavyRestorer()
{
	jobject thread;
	jobject value;
	jobject exh;

	BEGIN_JAVA

	thread =
		(*env)->CallStaticObjectMethod(env,
			s_Thread_class, s_Thread_currentThread); /* should never fail */

	exh = (*env)->ExceptionOccurred(env); /* but mollify -Xcheck:jni anyway */
	if(exh != 0)
	{
		(*env)->ExceptionClear(env);
		elogExceptionMessage(env, exh, ERROR);
	}

	value = currentInvocation->savedLoader;

	(*env)->SetObjectField(env, thread, s_Thread_contextLoader, value);
	(*env)->DeleteGlobalRef(env, value);
	(*env)->DeleteLocalRef(env, thread);

	END_JAVA
}

static void _lightUpdater(jobject loader)
{
	BEGIN_JAVA

	_updaterCommon(env, s_threadObject, loader);

	END_JAVA
}

void _lightRestorer()
{
	jobject value;

	BEGIN_JAVA

	value = currentInvocation->savedLoader;

	(*env)->SetObjectField(env, s_threadObject, s_Thread_contextLoader, value);
	(*env)->DeleteGlobalRef(env, value);

	END_JAVA
}

static void _noopUpdater(jobject loader)
{
}

void _noopRestorer()
{
}
