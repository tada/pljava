/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_SPI.h"
#include "pljava/SPI.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"
#include "pljava/type/String.h"
#include "pljava/type/SPITupleTable.h"

Datum SPI_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	JNINativeMethod methods[] = {
		{
		"_exec",
	  	"(Ljava/lang/String;I)I",
	  	Java_org_postgresql_pljava_internal_SPI__1exec
		},
		{
		"_getProcessed",
		"()I",
		Java_org_postgresql_pljava_internal_SPI__1getProcessed
		},
		{
		"_getResult",
		"()I",
		Java_org_postgresql_pljava_internal_SPI__1getResult
		},
		{
		"_getTupTable",
		"()Lorg/postgresql/pljava/internal/SPITupleTable;",
		Java_org_postgresql_pljava_internal_SPI__1getTupTable
		},
		{ 0, 0, 0 }};

	PgObject_registerNatives(env, "org/postgresql/pljava/internal/SPI", methods);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _exec
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_SPI__1exec(JNIEnv* env, jclass cls, jstring cmd, jint count)
{
	char* command;
	jint result;
	PLJAVA_ENTRY_FENCE(0)

	command = String_createNTS(env, cmd);
	if(command == 0)
		return 0;

	result = 0;
	Backend_pushJavaFrame(env);
	PG_TRY();
	{
		result = (jint)SPI_exec(command, (int)count);
		if(result < 0)
			Exception_throwSPI(env, "exec", result);

		Backend_popJavaFrame(env);
		pfree(command);
	}
	PG_CATCH();
	{
		Backend_popJavaFrame(env);
		Exception_throw_ERROR(env, "SPI_exec");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _getProcessed
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_SPI__1getProcessed(JNIEnv* env, jclass cls)
{
	return (jint)SPI_processed;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _getResult
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_SPI__1getResult(JNIEnv* env, jclass cls)
{
	return (jint)SPI_result;
}

/*
 * Class:     org_postgresql_pljava_internal_SPI
 * Method:    _getTupTable
 * Signature: ()Lorg/postgresql/pljava/internal/SPITupleTable;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_SPI__1getTupTable(JNIEnv* env, jclass cls)
{
	PLJAVA_ENTRY_FENCE(0)
	return SPITupleTable_create(env, SPI_tuptable);
}

#include <executor/spi_priv.h> /* Needed to get to the argtypes of the plan */

#if (PGSQL_MAJOR_VER == 7 && PGSQL_MINOR_VER < 5)

Oid SPI_getargtypeid(void* plan, int argIndex)
{
	if (plan == NULL || argIndex < 0 || argIndex >= ((_SPI_plan*)plan)->nargs)
	{
		SPI_result = SPI_ERROR_ARGUMENT;
		return InvalidOid;
	}
	return ((_SPI_plan*)plan)->argtypes[argIndex];
}

int SPI_getargcount(void* plan)
{
	if (plan == NULL)
	{
		SPI_result = SPI_ERROR_ARGUMENT;
		return -1;
	}
	return ((_SPI_plan*)plan)->nargs;
}

bool SPI_is_cursor_plan(void* plan)
{
	List* qtlist;
	_SPI_plan* spiplan = (_SPI_plan*)plan;
	if (spiplan == NULL)
	{
		SPI_result = SPI_ERROR_ARGUMENT;
		return false;
	}

	qtlist = spiplan->qtlist;
	if(length(spiplan->ptlist) == 1 && length(qtlist) == 1)
	{
		Query* queryTree = (Query*)lfirst((List*)lfirst(qtlist));
		if(queryTree->commandType == CMD_SELECT && queryTree->into == NULL)
			return true;
	}
	return false;
}
#endif

#if (PGSQL_MAJOR_VER >= 8)
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

Savepoint* SPI_setSavepoint(const char* name)
{
	/* We let the savepoint live in the current MemoryContext. It will be released
	 * or rolled back even if the creator forgets about it.
	 */
	Savepoint* sp = (Savepoint*)palloc(sizeof(Savepoint) + strlen(name));
	BeginInternalSubTransaction((char*)name);
	sp->nestingLevel = GetCurrentTransactionNestLevel();
	sp->xid = GetCurrentSubTransactionId();
	strcpy(sp->name, name);
	return sp;
}

void SPI_releaseSavepoint(Savepoint* sp)
{
	while(sp->nestingLevel < GetCurrentTransactionNestLevel())
		ReleaseCurrentSubTransaction();

	if(sp->nestingLevel == GetCurrentTransactionNestLevel())
	{
		assertXid(sp->xid);
		ReleaseCurrentSubTransaction();
	}
	pfree(sp);
}

void SPI_rollbackSavepoint(Savepoint* sp)
{
	while(sp->nestingLevel < GetCurrentTransactionNestLevel())
		RollbackAndReleaseCurrentSubTransaction();

	if(sp->nestingLevel == GetCurrentTransactionNestLevel())
	{
		assertXid(sp->xid);
		RollbackAndReleaseCurrentSubTransaction();
	}
	pfree(sp);
}
#endif
