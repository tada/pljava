/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_SPI_h
#define __pljava_SPI_h

#include "pljava/PgObject.h"

#include <executor/spi.h>

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * Some needed additions to the SPI set of functions.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/

#if (PGSQL_MAJOR_VER < 8)
/*
 * Returns the Oid of the type for argument at argIndex. First
 * parameter is at index zero.
 */
extern Oid SPI_getargtypeid(void* plan, int argIndex);

/*
 * Returns the number of arguments for the prepared plan.
 */
extern int SPI_getargcount(void* plan);

/*
 *	Return true if the plan is valid for a SPI_open_cursor call.
 */
extern bool SPI_is_cursor_plan(void* plan);

#endif

extern Datum SPI_initialize(PG_FUNCTION_ARGS);

typedef struct
{
	SubTransactionId xid;
	int  nestingLevel;
	char name[1];
} Savepoint;

extern Savepoint* SPI_setSavepoint(const char* name);

extern void SPI_releaseSavepoint(Savepoint* sp);

extern void SPI_rollbackSavepoint(Savepoint* sp);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
