/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/PgSavepoint.h"
#include "org_postgresql_pljava_internal_SubXactListener.h"

#include <access/xact.h>

static jclass s_SubXactListener_class;
static jmethodID s_SubXactListener_invokeListeners;

static void subXactCB(
	SubXactEvent event, SubTransactionId mySubid, SubTransactionId parentSubid,
	void* arg)
{
	/*
	 * Map the subids to PgSavepoints first - this function upcalls into Java
	 * without releasing the Backend.THREADLOCK monitor, so the called methods
	 * can know they're on the PG thread; Backend.threadMayEnterPG() is true.
	 * It is important to look up mySubid before parentSubid, as it is possible
	 * a new PgSavepoint instance is under construction in the 'nursery', and
	 * will be assigned the first id to be looked up.
	 */
	jobject     sp = pljava_PgSavepoint_forId(mySubid);
	jobject parent = pljava_PgSavepoint_forId(parentSubid);

	/*
	 * These upcalls are made with the monitor released. We are, of course, ON
	 * the PG thread, but this time with no monitor held to prevent another
	 * thread from stepping in. These methods should not blindly assert
	 * Backend.threadMayEnterPG(), as for some java_thread_pg_entry settings it
	 * won't be true. This is the legacy behavior, so not changed for 1.5.x.
	 *
	 * The event ordinal can simply be passed to Java, as long as upstream
	 * hasn't changed the order; list the known ones in a switch, for a better
	 * chance that a clever compiler will warn if upstream has added any.
	 */
	switch(event)
	{
		case SUBXACT_EVENT_START_SUB:
		case SUBXACT_EVENT_COMMIT_SUB:
		case SUBXACT_EVENT_ABORT_SUB:
		case SUBXACT_EVENT_PRE_COMMIT_SUB:
			JNI_callStaticVoidMethod(s_SubXactListener_class,
				s_SubXactListener_invokeListeners, (jint)event, sp, parent);
	}
}

extern void SubXactListener_initialize(void);
void SubXactListener_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_register",
		"()V",
	  	Java_org_postgresql_pljava_internal_SubXactListener__1register
		},
		{
		"_unregister",
		"()V",
	  	Java_org_postgresql_pljava_internal_SubXactListener__1unregister
		},
		{ 0, 0, 0 }
	};

	PgObject_registerNatives("org/postgresql/pljava/internal/SubXactListener", methods);

	s_SubXactListener_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/SubXactListener"));
	s_SubXactListener_invokeListeners  =
		PgObject_getStaticJavaMethod(s_SubXactListener_class, "invokeListeners",
			"(ILorg/postgresql/pljava/internal/PgSavepoint;"
			"Lorg/postgresql/pljava/internal/PgSavepoint;)V");

#define CONFIRMCONST(c) \
StaticAssertStmt((SUBXACT_EVENT_##c) == \
(org_postgresql_pljava_internal_SubXactListener_##c), \
	"Java/C value mismatch for " #c)

	CONFIRMCONST(      START_SUB );
	CONFIRMCONST(     COMMIT_SUB );
	CONFIRMCONST(      ABORT_SUB );
	CONFIRMCONST( PRE_COMMIT_SUB );
}

/*
 * Class:     org_postgresql_pljava_internal_SubXactListener
 * Method:    _register
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_SubXactListener__1register(JNIEnv* env, jclass cls)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		RegisterSubXactCallback(subXactCB, NULL);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("RegisterSubXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_SubXactListener
 * Method:    _unregister
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_SubXactListener__1unregister(JNIEnv* env, jclass cls)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		UnregisterSubXactCallback(subXactCB, NULL);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("UnregisterSubXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}
