/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "org_postgresql_pljava_internal_XactListener.h"

#include "access/xact.h"

static jclass s_XactListener_class;
static jmethodID s_XactListener_onAbort;
static jmethodID s_XactListener_onCommit;
static jmethodID s_XactListener_onPrepare;

static void xactCB(XactEvent event, void* arg)
{
	switch(event)
	{
		case XACT_EVENT_ABORT:
			JNI_callStaticVoidMethod(s_XactListener_class,
				s_XactListener_onAbort);
			break;
		case XACT_EVENT_COMMIT:
			JNI_callStaticVoidMethod(s_XactListener_class,
				s_XactListener_onCommit);
			break;
		case XACT_EVENT_PREPARE:
			JNI_callStaticVoidMethod(s_XactListener_class,
				s_XactListener_onPrepare);
			break;
	}
}

extern void XactListener_initialize(void);
void XactListener_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_register",
		"()V",
		Java_org_postgresql_pljava_internal_XactListener__1register
		},
		{
		"_unregister",
		"()V",
		Java_org_postgresql_pljava_internal_XactListener__1unregister
		},
		{ 0, 0, 0 }
	};

	PgObject_registerNatives("org/postgresql/pljava/internal/XactListener", methods);

	s_XactListener_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/XactListener"));
	s_XactListener_onAbort = PgObject_getStaticJavaMethod(s_XactListener_class, "onAbort", "()V");
	s_XactListener_onCommit = PgObject_getStaticJavaMethod(s_XactListener_class, "onCommit", "()V");
	s_XactListener_onPrepare = PgObject_getStaticJavaMethod(s_XactListener_class, "onPrepare", "()V");
}

/*
 * Class:     org_postgresql_pljava_internal_XactListener
 * Method:    _register
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_XactListener__1register(JNIEnv* env, jclass cls)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		RegisterXactCallback(xactCB, NULL);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("RegisterXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_XactListener
 * Method:    _unregister
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_XactListener__1unregister(JNIEnv* env, jclass cls)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		UnregisterXactCallback(xactCB, NULL);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("UnregisterXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}
