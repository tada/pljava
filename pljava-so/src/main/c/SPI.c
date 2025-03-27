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
 */
#include "org_postgresql_pljava_internal_SPI.h"
#include "pljava/SPI.h"
#include "pljava/Invocation.h"
#include "pljava/Exception.h"
#include "pljava/type/String.h"
#include "pljava/type/TupleTable.h"

#include <funcapi.h>
#include <access/xact.h>
#if defined(NEED_MISCADMIN_FOR_STACK_BASE)
#include <miscadmin.h>
#endif

/*
 * Yes, this macro works because the class's simple name happens to be SPI
 * and it defines constants named without the SPI_ prefix the PG source uses.
 */
#define CONFIRMCONST(c) \
StaticAssertStmt((c) == (org_postgresql_pljava_internal_##c), \
	"Java/C value mismatch for " #c)

static jclass s_TupleList_SPI_class;
static jmethodID s_TupleList_SPI_init;

extern void SPI_initialize(void);
void SPI_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_exec",
		"(Ljava/lang/String;I)I",
		Java_org_postgresql_pljava_internal_SPI__1exec
		},
		{
		"_endXact",
		"(Z)V",
		Java_org_postgresql_pljava_internal_SPI__1endXact
		},
		{
		"_getTupTable",
		"(Lorg/postgresql/pljava/internal/TupleDesc;)Lorg/postgresql/pljava/internal/TupleTable;",
		Java_org_postgresql_pljava_internal_SPI__1getTupTable
		},
		{
		"_mapTupTable",
		"(Lorg/postgresql/pljava/pg/TupleTableSlotImpl;JI)Lorg/postgresql/pljava/pg/TupleList;",
		Java_org_postgresql_pljava_internal_SPI__1mapTupTable
		},
		{
		"_freeTupTable",
		"()V",
		Java_org_postgresql_pljava_internal_SPI__1freeTupTable
		},
		{ 0, 0, 0 }};
	/*
	 * See also ModelUtils.c for newer methods associated with SPI.EarlyNatives.
	 */

	PgObject_registerNatives("org/postgresql/pljava/internal/SPI", methods);

	s_TupleList_SPI_class = JNI_newGlobalRef(
		PgObject_getJavaClass("org/postgresql/pljava/pg/TupleList$SPI"));
	s_TupleList_SPI_init = PgObject_getJavaMethod(s_TupleList_SPI_class, 
		"<init>",
		"(Lorg/postgresql/pljava/pg/TupleTableSlotImpl;JLjava/nio/ByteBuffer;)V"
	);

	/*
	 * Statically assert that the Java code has the right values for these.
	 * I would rather have this at the top, but these count as statements and
	 * would trigger a declaration-after-statment warning.
	 */
	CONFIRMCONST(SPI_ERROR_CONNECT);
	CONFIRMCONST(SPI_ERROR_COPY);
	CONFIRMCONST(SPI_ERROR_OPUNKNOWN);
	CONFIRMCONST(SPI_ERROR_UNCONNECTED);
	CONFIRMCONST(SPI_ERROR_CURSOR);
	CONFIRMCONST(SPI_ERROR_ARGUMENT);
	CONFIRMCONST(SPI_ERROR_PARAM);
	CONFIRMCONST(SPI_ERROR_TRANSACTION);
	CONFIRMCONST(SPI_ERROR_NOATTRIBUTE);
	CONFIRMCONST(SPI_ERROR_NOOUTFUNC);
	CONFIRMCONST(SPI_ERROR_TYPUNKNOWN);
#if PG_VERSION_NUM >= 100000
	CONFIRMCONST(SPI_ERROR_REL_DUPLICATE);
	CONFIRMCONST(SPI_ERROR_REL_NOT_FOUND);
#endif

	CONFIRMCONST(SPI_OK_CONNECT);
	CONFIRMCONST(SPI_OK_FINISH);
	CONFIRMCONST(SPI_OK_FETCH);
	CONFIRMCONST(SPI_OK_UTILITY);
	CONFIRMCONST(SPI_OK_SELECT);
	CONFIRMCONST(SPI_OK_SELINTO);
	CONFIRMCONST(SPI_OK_INSERT);
	CONFIRMCONST(SPI_OK_DELETE);
	CONFIRMCONST(SPI_OK_UPDATE);
	CONFIRMCONST(SPI_OK_CURSOR);
	CONFIRMCONST(SPI_OK_INSERT_RETURNING);
	CONFIRMCONST(SPI_OK_DELETE_RETURNING);
	CONFIRMCONST(SPI_OK_UPDATE_RETURNING);
	CONFIRMCONST(SPI_OK_REWRITTEN);
#if PG_VERSION_NUM >= 100000
	CONFIRMCONST(SPI_OK_REL_REGISTER);
	CONFIRMCONST(SPI_OK_REL_UNREGISTER);
	CONFIRMCONST(SPI_OK_TD_REGISTER);
#endif
#if PG_VERSION_NUM >= 150000
	CONFIRMCONST(SPI_OK_MERGE);
#endif

#if PG_VERSION_NUM >= 110000
	CONFIRMCONST(SPI_OPT_NONATOMIC);
#endif
}

/****************************************
 * JNI methods
 * See also ModelUtils.c for newer methods associated with SPI.EarlyNatives.
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _exec
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_SPI__1exec(JNIEnv* env, jclass cls, jstring cmd, jint count)
{
	jint result = 0;

	BEGIN_NATIVE
	char* command = String_createNTS(cmd);
	if(command != 0)
	{
		STACK_BASE_VARS
		STACK_BASE_PUSH(env)
		PG_TRY();
		{
			Invocation_assertConnect();
			result = (jint)SPI_exec(command, (int)count);
			if(result < 0)
				Exception_throwSPI("exec", result);
	
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_exec");
		}
		PG_END_TRY();
		pfree(command);
		STACK_BASE_POP()
	}
	END_NATIVE	
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _endXact
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_SPI__1endXact(JNIEnv* env, jclass cls, jboolean rollback)
{
	char *where = rollback ? "SPI_rollback" : "SPI_commit";
	BEGIN_NATIVE
	STACK_BASE_VARS
	STACK_BASE_PUSH(env)
	PG_TRY();
	{
		Invocation_assertConnect();
		if ( rollback )
			SPI_rollback();
		else
			SPI_commit();

	}
	PG_CATCH();
	{
		Exception_throw_ERROR(where);
	}
	PG_END_TRY();
	STACK_BASE_POP()
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _getTupTable
 * Signature: (Lorg/postgresql/pljava/internal/TupleDesc;)Lorg/postgresql/pljava/internal/TupleTable;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_SPI__1getTupTable(JNIEnv* env, jclass cls, jobject td)
{
	jobject tupleTable = 0;
	if(SPI_tuptable != 0)
	{
		BEGIN_NATIVE
		tupleTable = TupleTable_create(SPI_tuptable, td);
		END_NATIVE
	}
	return tupleTable;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _mapTupTable
 * Signature: (Lorg/postgresql/pljava/pg/TupleTableSlotImpl;JI)Lorg/postgresql/pljava/pg/TupleList;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_SPI__1mapTupTable(JNIEnv* env, jclass cls, jobject ttsi, jlong p, jint sizeToMap)
{
	jobject tupleList = NULL;
	Ptr2Long p2l;
	SPITupleTable *tuptbl;
	jobject bb;
	if ( p != 0 )
	{
		BEGIN_NATIVE_AND_TRY
		p2l.longVal = p;
		tuptbl = (SPITupleTable *)p2l.ptrVal;
		bb = JNI_newDirectByteBuffer(tuptbl->vals, sizeToMap);
		tupleList = JNI_newObjectLocked(
			s_TupleList_SPI_class, s_TupleList_SPI_init, ttsi, p, bb);
		END_NATIVE_AND_CATCH("_mapTupleTable")
	}
	if ( 0 != tupleList  &&  SPI_tuptable == tuptbl )
		SPI_tuptable = NULL; /* protect from legacy _freetuptable below */
	return tupleList;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _freeTupTable
 * Signature: ()V;
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_SPI__1freeTupTable(JNIEnv* env, jclass cls)
{
	if(SPI_tuptable != 0)
	{
		BEGIN_NATIVE
		SPI_freetuptable(SPI_tuptable);
		SPI_tuptable = 0;
		END_NATIVE
	}
}
