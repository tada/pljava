/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/EOXactListener.h"

static jmethodID s_EOXactListener_onEOXact;
static jobject s_listener;

static void onEOXact(bool isCommit, void* arg)
{
	JNIEnv* env = Backend_getMainEnv();
	if(env == 0)
	{
		/* JVM is no longer active. Unregister the callback.
		 */
		UnregisterEOXactCallback(onEOXact, s_listener);
		s_listener = 0;
	}
	else
	{
		bool saveICJ = isCallingJava;
		isCallingJava = true;
		(*env)->CallVoidMethod(env, s_listener, s_EOXactListener_onEOXact,
			isCommit ? JNI_TRUE : JNI_FALSE);
		isCallingJava = saveICJ;
	}
}

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
		RegisterEOXactCallback(onEOXact, s_listener);
	}
}

void EOXactListener_unregister(JNIEnv* env)
{
	if(s_listener != 0)
	{
		UnregisterEOXactCallback(onEOXact, s_listener);
		(*env)->DeleteGlobalRef(env, s_listener);
		s_listener = 0;
	}
}
