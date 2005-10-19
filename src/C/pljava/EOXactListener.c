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

static void onEOXact(XactEvent event, void *arg)
{
	if(event == XACT_EVENT_ABORT || event == XACT_EVENT_COMMIT)
	{
		/* TODO: Improve to handle nested transaction events
		 */
		JNI_callVoidMethod(s_listener, s_EOXactListener_onEOXact,
			(event == XACT_EVENT_COMMIT) ? JNI_TRUE : JNI_FALSE);
	}
}

void EOXactListener_register(jobject listener)
{
	if(s_listener != 0)
	{
		Exception_throw(ERRCODE_INTERNAL_ERROR,
			"Multiple registration of EOXactListener");
	}
	else
	{
		jclass cls = JNI_getObjectClass(listener);

		s_EOXactListener_onEOXact = PgObject_getJavaMethod(cls, "onEOXact", "(Z)V");
		JNI_deleteLocalRef(cls);

		s_listener = JNI_newGlobalRef(listener);
		RegisterXactCallback(onEOXact, s_listener);
	}
}

void EOXactListener_unregister()
{
	if(s_listener != 0)
	{
		UnregisterXactCallback(onEOXact, s_listener);
		JNI_deleteGlobalRef(s_listener);
		s_listener = 0;
	}
}
