/*
 * Copyright (c) 2004-2024 Tada AB and other contributors, as listed below.
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
#include <postgres.h>
#include <access/xact.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_PgSavepoint.h"
#include "pljava/PgSavepoint.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/type/String.h"
#include "pljava/SPI.h"

/*
 * Workaround for issue #260, PostgreSQL API breakage by EnterpriseDB. They
 * added a ReleaseCurrentSubTransactionEx function with an added argument, and
 * made ReleaseCurrentSubTransaction call it, passing false. But instead of
 * leaving ReleaseCurrentSubTransaction an actual function that does so, which
 * would not have been an API break, they made it a macro instead, with the
 * result that its address cannot be taken. The reporter of the issue had an
 * inquiry open with EDB for four months trying to get specifics on what
 * versions have that issue, with no useful response. So this workaround is just
 * conditioned on finding ReleaseCurrentSubTransaction defined as a macro.
 */
#ifdef ReleaseCurrentSubTransaction
static void addressableRelease(void);
static void addressableRelease()
{
	ReleaseCurrentSubTransaction();
}
#undef ReleaseCurrentSubTransaction
#define ReleaseCurrentSubTransaction addressableRelease
#endif

static jclass s_PgSavepoint_class;
static jmethodID s_forId;
static jfieldID s_nestLevel;

extern void PgSavepoint_initialize(void);
static void unwind(void (*f)(void), jint xid, jint nestingLevel);
static void assertXid(SubTransactionId);

jobject pljava_PgSavepoint_forId(SubTransactionId subId)
{
	return JNI_callStaticObjectMethodLocked(s_PgSavepoint_class, s_forId,
			(jint)subId);
}

void PgSavepoint_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_set",
		"(Ljava/lang/String;)I",
	  	Java_org_postgresql_pljava_internal_PgSavepoint__1set
		},
		{
		"_release",
		"(II)V",
		Java_org_postgresql_pljava_internal_PgSavepoint__1release
		},
		{
		"_rollback",
		"(II)V",
		Java_org_postgresql_pljava_internal_PgSavepoint__1rollback
		},
		{ 0, 0, 0 }
	};
	PgObject_registerNatives("org/postgresql/pljava/internal/PgSavepoint",
		methods);

	/*
	 * I would rather put this at the top, but it counts as a statement, and
	 * would trigger a declaration-after-statement warning.
	 */
	StaticAssertStmt(sizeof(SubTransactionId) <= sizeof(jint),
		"SubTransactionId wider than jint?!");

	s_PgSavepoint_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/PgSavepoint"));
	s_forId =
		PgObject_getStaticJavaMethod(s_PgSavepoint_class, "forId",
			"(I)Lorg/postgresql/pljava/internal/PgSavepoint;");
	s_nestLevel =
		PgObject_getJavaField(s_PgSavepoint_class, "m_nestLevel", "I");
}

static void unwind(void (*f)(void), jint xid, jint nestingLevel)
{
	while ( nestingLevel < GetCurrentTransactionNestLevel() )
		f();

	if ( nestingLevel == GetCurrentTransactionNestLevel() )
	{
		assertXid((SubTransactionId)xid);
		f();
	}
}

static void assertXid(SubTransactionId xid)
{
	if(xid != GetCurrentSubTransactionId())
	{
		/* Oops. Rollback to top level transaction.
		 */
		ereport(ERROR, (
			errcode(ERRCODE_INVALID_TRANSACTION_TERMINATION),
			errmsg("Subtransaction mismatch at txlevel %d",
				GetCurrentTransactionNestLevel())));
	}
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_PgSavepoint
 * Method:    _set
 * Signature: (Ljava/lang/String;)I;
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_PgSavepoint__1set(JNIEnv* env, jobject this, jstring jname)
{
	jint xid = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		char* name = String_createNTS(jname);
		Invocation_assertConnect();
		JNI_setIntField(this, s_nestLevel, 1+GetCurrentTransactionNestLevel());
		BeginInternalSubTransaction(name);
		xid = GetCurrentSubTransactionId();
		if ( NULL != name )
			pfree(name);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("setSavepoint");
	}
	PG_END_TRY();
	END_NATIVE
	return xid;
}

/*
 * Class:     org_postgresql_pljava_internal_PgSavepoint
 * Method:    _release
 * Signature: (II)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_PgSavepoint__1release(JNIEnv* env, jclass clazz, jint xid, jint nestLevel)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		unwind(ReleaseCurrentSubTransaction, xid, nestLevel);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("releaseSavepoint");
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_PgSavepoint
 * Method:    _rollback
 * Signature: (II)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_PgSavepoint__1rollback(JNIEnv* env, jclass clazz, jint xid, jint nestLevel)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		unwind(RollbackAndReleaseCurrentSubTransaction, xid, nestLevel);
#if PG_VERSION_NUM < 100000
		SPI_restore_connection();
#endif
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("rollbackSavepoint");
	}
	PG_END_TRY();
	END_NATIVE
}
