/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "org_postgresql_pljava_internal_XactListener.h"

#if ((PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER >= 1) || PGSQL_MAJOR_VER > 8)
#define HAS_2PC 1
#include "access/xact.h"
#endif

static jclass s_XactListener_class;
static jmethodID s_XactListener_onAbort;
static jmethodID s_XactListener_onCommit;
#if HAS_2PC
static jmethodID s_XactListener_onPrepare;
#endif

static void xactCB(XactEvent event, void* arg)
{
	Ptr2Long p2l;
	p2l.longVal = 0L; /* ensure that the rest is zeroed out */
	p2l.ptrVal = arg;
	switch(event)
	{
		case XACT_EVENT_ABORT:
			JNI_callStaticVoidMethod(s_XactListener_class, s_XactListener_onAbort, p2l.longVal);
			break;
		case XACT_EVENT_COMMIT:
			JNI_callStaticVoidMethod(s_XactListener_class, s_XactListener_onCommit, p2l.longVal);
			break;
#ifdef HAS_2PC
		case XACT_EVENT_PREPARE:
			JNI_callStaticVoidMethod(s_XactListener_class, s_XactListener_onPrepare, p2l.longVal);
			break;
#endif
	}
}

extern void XactListener_initialize(void);
void XactListener_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_register",
	  	"(J)V",
	  	Java_org_postgresql_pljava_internal_XactListener__1register
		},
		{
		"_unregister",
	  	"(J)V",
	  	Java_org_postgresql_pljava_internal_XactListener__1unregister
		},
		{ 0, 0, 0 }};

	PgObject_registerNatives("org/postgresql/pljava/internal/XactListener", methods);

	s_XactListener_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/XactListener"));
	s_XactListener_onAbort = PgObject_getStaticJavaMethod(s_XactListener_class, "onAbort", "(J)V");
	s_XactListener_onCommit = PgObject_getStaticJavaMethod(s_XactListener_class, "onCommit", "(J)V");
#if HAS_2PC
	s_XactListener_onPrepare = PgObject_getStaticJavaMethod(s_XactListener_class, "onPrepare", "(J)V");
#endif
}

/*
 * Class:     org_postgresql_pljava_internal_XactListener
 * Method:    _register
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_XactListener__1register(JNIEnv* env, jclass cls, jlong listenerId)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		Ptr2Long p2l;
		p2l.longVal = listenerId;
		RegisterXactCallback(xactCB, p2l.ptrVal);
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
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_XactListener__1unregister(JNIEnv* env, jclass cls, jlong listenerId)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		Ptr2Long p2l;
		p2l.longVal = listenerId;
		UnregisterXactCallback(xactCB, p2l.ptrVal);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("UnregisterXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}
