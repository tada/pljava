/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/SPI_JNI.h"
#include "pljava/type/String.h"
#include "pljava/type/SPITupleTable.h"

/*
 * Class:     org_postgresql_pljava_SPI
 * Method:    exec
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_SPI_exec(JNIEnv* env, jclass cls, jstring cmd, jint count)
{
	char* command = String_createNTS(env, cmd);
	jint result = (jint)SPI_exec(command, (int)count);
	pfree(command);
	return result;
}

/*
 * Class:     org_postgresql_pljava_SPI
 * Method:    getProcessed
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_SPI_getProcessed(JNIEnv* env, jclass cls)
{
	return (jint)SPI_processed;
}

/*
 * Class:     org_postgresql_pljava_SPI
 * Method:    getResult
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_SPI_getResult(JNIEnv* env, jclass cls)
{
	return (jint)SPI_result;
}

/*
 * Class:     org_postgresql_pljava_SPI
 * Method:    getTupTable
 * Signature: ()Lorg/postgresql/pljava/TupleTable;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_SPI_getTupTable(JNIEnv* env, jclass cls)
{
	return SPITupleTable_create(env, SPI_tuptable);
}
