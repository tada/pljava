/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/SPI_JNI.h"
#include "pljava/Exception.h"
#include "pljava/type/String.h"
#include "pljava/type/SPITupleTable.h"

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
	PLJAVA_ENTRY_FENCE(0)
	char* command = String_createNTS(env, cmd);
	if(command == 0)
		return 0;

	jint result = 0;
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
