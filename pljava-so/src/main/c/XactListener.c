/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
static jmethodID s_XactListener_invokeListeners;

static void xactCB(XactEvent event, void* arg)
{
	/*
	 * Upstream has, regrettably, not merely added events over the years, but
	 * changed their order, so a mapping is needed. Use a switch with the known
	 * cases enumerated, to improve the chance that a clever compiler will warn
	 * if yet more have been added, and initialize 'mapped' to a value that the
	 * Java code won't mistake for a real one.
	 */
#define CASE(c) \
case XACT_EVENT_##c: \
	mapped = org_postgresql_pljava_internal_XactListener_##c; \
	break

	jint mapped = -1;
	switch(event)
	{
		CASE( COMMIT );
		CASE( ABORT );
		CASE( PREPARE );
		CASE( PRE_COMMIT );
		CASE( PRE_PREPARE );
#if PG_VERSION_NUM >= 90500
		CASE( PARALLEL_COMMIT );
		CASE( PARALLEL_ABORT );
		CASE( PARALLEL_PRE_COMMIT );
#endif
	}

	JNI_callStaticVoidMethod(s_XactListener_class,
		s_XactListener_invokeListeners, mapped);
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

	s_XactListener_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/XactListener"));
	s_XactListener_invokeListeners = PgObject_getStaticJavaMethod(
		s_XactListener_class, "invokeListeners", "(I)V");
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
