/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/SPI.h"
#include "pljava/SPI_JNI.h"
#include "pljava/Exception.h"
#include "pljava/type/String.h"
#include "pljava/type/SPITupleTable.h"

#include <executor/spi_priv.h> /* Needed to get to the argtypes of the plan */

static MemoryContext upperContext = 0;

void SPI_clearUpperContextInfo()
{
	upperContext = 0;
}

/* Dirty patch that shows when the upper context is deleted or reset

static MemoryContextMethods* originalMethods = 0;

static void myDelete(MemoryContext ctx)
{
	elog(LOG, "Deleting context 0x%lx", (long)((void*)ctx));
	originalMethods->delete(ctx);
}
	
static void myReset(MemoryContext ctx)
{
	elog(LOG, "Doing reset on context 0x%lx", (long)((void*)ctx));
	originalMethods->reset(ctx);
}

static void installSPIContextLogPatch()
{
	void* tmp = SPI_palloc(4);
	MemoryContext upperContext = GetMemoryChunkContext(tmp);

	if(originalMethods == 0 || originalMethods == upperContext->methods)
	{
		originalMethods = upperContext->methods;
		MemoryContextMethods* repl = (MemoryContextMethods*)malloc(sizeof(MemoryContextMethods));
		memcpy(repl, originalMethods, sizeof(MemoryContextMethods));
		upperContext->methods = repl;
		repl->delete = myDelete;
		repl->reset = myReset;
	}
	SPI_pfree(tmp);
}
 */
/*
static MemoryContext _SPI_getUpperContext()
{
	if(upperContext == 0)
	{
		void* tmp = SPI_palloc(16);
		upperContext = QueryContext;
		if(originalMethods == 0 || originalMethods == upperContext->methods)
		{
			originalMethods = upperContext->methods;
			MemoryContextMethods* repl = (MemoryContextMethods*)malloc(sizeof(MemoryContextMethods));
			memcpy(repl, originalMethods, sizeof(MemoryContextMethods));
			upperContext->methods = repl;
			repl->delete = myDelete;
			repl->reset = myReset;
		}
		SPI_pfree(tmp);
	}
	return upperContext;
}
*/
MemoryContext SPI_switchToReturnValueContext()
{
	/* Tried the upper context here but it's destroyed between calls
	 * to the call manager.
	 */
	return MemoryContextSwitchTo(QueryContext);
}

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
	PLJAVA_TRY
	{
		result = (jint)SPI_exec(command, (int)count);
		pfree(command);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "SPI_exec");
	}
	PLJAVA_TCEND
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
