/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_SPI_h
#define __pljava_SPI_h

#include "pljava/PgObject.h"

#include <executor/spi.h>
#include <utils/memutils.h>

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * Some needed additions to the SPI set of functions.
 * 
 * Author: Thomas Hallgren
 *
 ***********************************************************************/

/*
 * Clear the upper context pointer.
 */
void SPI_clearUpperContextInfo(void);

/*
 * Switch memory context to a context that is durable between calls to
 * the call manager but not durable between queries. The old context is
 * returned. This method can be used when creating values that will be
 * returned from the Pl/Java routines. Once the values have been created
 * a call to MemoryContextSwitchTo(oldContext) must follow where oldContext
 * is the context returned from this call.
 */
extern MemoryContext SPI_switchToReturnValueContext(void);

#if (PGSQL_MAJOR_VER == 7 && PGSQL_MINOR_VER < 5)
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

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
