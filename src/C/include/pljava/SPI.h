/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
