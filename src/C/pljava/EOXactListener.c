/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/EOXactListener.h"

static jmethodID s_EOXactListener_onEOXact;
static jobject s_listener;

#if (PGSQL_MAJOR_VER < 8)
static void onEOXact(bool isCommit, void *arg)
{
	JNIEnv* env = Backend_getJNIEnv();
	if(env == 0)
	{
		/* JVM is no longer active. Unregister the callback.
		 */
		UnregisterEOXactCallback(onEOXact, s_listener);
		s_listener = 0;
	}
	else
	{
		/* TODO: Improve to handle nested transaction events
		 */
		bool saveICJ = isCallingJava;
		isCallingJava = true;
		(*env)->CallVoidMethod(env, s_listener, s_EOXactListener_onEOXact,
			isCommit ? JNI_TRUE : JNI_FALSE);
		isCallingJava = saveICJ;
	}
}
#else
static void onEOXact(XactEvent event, void *arg)
{
	JNIEnv* env = Backend_getJNIEnv();
	if(env == 0)
	{
		/* JVM is no longer active. Unregister the callback.
		 */
		UnregisterXactCallback(onEOXact, s_listener);
		s_listener = 0;
	}
	else if(event == XACT_EVENT_ABORT || event == XACT_EVENT_COMMIT)
	{
		/* TODO: Improve to handle nested transaction events
		 */
		bool saveICJ = isCallingJava;
		isCallingJava = true;
		(*env)->CallVoidMethod(env, s_listener, s_EOXactListener_onEOXact,
			(event == XACT_EVENT_COMMIT) ? JNI_TRUE : JNI_FALSE);
		isCallingJava = saveICJ;
	}
}
#endif

void EOXactListener_register(JNIEnv* env, jobject listener)
{
	if(s_listener != 0)
	{
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"Multiple registration of EOXactListener");
	}
	else
	{
		jclass cls = (*env)->GetObjectClass(env, listener);

		s_EOXactListener_onEOXact = PgObject_getJavaMethod(env, cls, "onEOXact", "(Z)V");
		(*env)->DeleteLocalRef(env, cls);

		s_listener = (*env)->NewGlobalRef(env, listener);
#if (PGSQL_MAJOR_VER < 8)
		RegisterEOXactCallback(onEOXact, s_listener);
#else
		RegisterXactCallback(onEOXact, s_listener);
#endif
	}
}

void EOXactListener_unregister(JNIEnv* env)
{
	if(s_listener != 0)
	{
#if (PGSQL_MAJOR_VER < 8)
		UnregisterEOXactCallback(onEOXact, s_listener);
#else
		UnregisterXactCallback(onEOXact, s_listener);
#endif
		(*env)->DeleteGlobalRef(env, s_listener);
		s_listener = 0;
	}
}
