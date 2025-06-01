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
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_FDW_h
#define __pljava_type_FDW_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * This defines the required SPI.
 *
 * Important - there is not a one-to-one relationship between the Wrapper,
 * Server, and Foreign Table so we need to specify the latter in most,
 * but not, cases. For now I'm assuming the worst.
 *
 * In practice it may be easiest (and safest) to have FDW -> FDW_Table and
 * rely on the C implementation to determine the correct object to use.
 **************************************************************************/

struct FDW_
typedef struct FDW_* FDW

struct FDW_Table_
typedef struct FDW_Table_* FDW_Table

/**
 * This defines the Service Provide Interface (SPI) that the java class
 * must implement. It is only called from the backend FDW implementation.
 *
 * For now I'm skipping the advanced features for parallel and/or
 * asychronous execution.
 */

/*
 * Design notes
 *
 * I've replaced 'begin' with 'open' and 'end' with 'close' since that's
 * more closely aligned with what Java uses - and we'll definitely want
 * the java side implement Autocloseable in order to ensure resources are
 * released.
 *
 * For simplificity I've reduced the options to key-value pairs instead
 * of a Map. This allows us to handle repeated options. We also need to keep
 * track of there the option is defined since there's a precedence order.
 *
 * For clarity I've kept the methods that manipulate data short
 * (FDW_next(), FDW_insert(), FDW_truncate()) while the methods
 * that interact with the server backend have a fuller name.
 */

/* FdwValidator functions */

// this is (Enum, String key, String value). The Enum is
// important due to precedence rules, and the simple (key, value)
// pair is important since a key may be duplicated at a single
// level. E.g., providing multiple servers in a cluster or for
// fallbacks.
extern DATUM FDW_set_option(FDW_Table table, PG_FUNCTION_ARGS);

// or
extern DATUM FDW_wrapper_set_option(FDW fdw, PG_FUNCTION_ARGS);
extern DATUM FDW_server_set_option(FDW_Server server, PG_FUNCTION_ARGS);
extern DATUM FDW_table_set_option(FDW_Table table, PG_FUNCTION_ARGS);

// validation can be performed at both the individual option
// and overall collection of options level. The latter also
// allows us to use a Builder pattern where the options are
// provided to the builder above and this triggers the actual
// build().
extern DATUM FDW_validate(FDW_Table table, PG_FUNCTION_ARGS);

/* Required FdwRoutines functions */
/* They can be no-ops */

// PlannerInfo, RelOptInfo, Oid - can be empty method, sets private plan_state
extern DATUM FDW_get_relsize(FDW_Table table, PG_FUNCTION_ARGS);

// PlannerInfo, RelOptInfo, Oid - can be empty method
extern DATUM FDW_get_foreign_paths(FDW_Table table, PG_FUNCTION_ARGS);

extern DATUM FDW_get_plan(FDW_Table table, PG_FUNCTION_ARGS);

// optional but a good idea even if read-only since it is a
// way to track resources
extern Datum FDW_analyze(FDW_Table fdw, PG_FUNCTION_ARGS);

// optional but a good way to ensure consistency between FDW and database
extern Datum FDW_importSchema(FDW_Table fdw, PG_FUNCTION_ARGS);

/* Everything below this is optional */

extern Datum FDW_get_join_paths(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_get_uppers_path(FDW_Table table, PG_FUNCTION_ARGS); // planning
extern Datum FDW_add_update_targets(FDW_Table table, PG_FUNCTION_ARGS);

extern Datum FDW_is_rel_updatable(FDW_Table table, PG_FUNCTION_ARGS);

// I/U/D preparation

extern Datum FDW_scan_open(FDW_Table table, PG_FUNCTION_ARGS);  //  'begin', sets scan state  REQUIRED
extern Datum FDW_scan_close(FDW_Table table, PG_FUNCTION_ARGS);  // 'end' REQUIRED
extern Datum FDW_scan_explain(FDW_Table table, PG_FUNCTION_ARGS);

extern Datum FDW_insert_open(FDW_Table table, PG_FUNCTION_ARGS);  // 'begin'
extern Datum FDW_insert_close(FDW_Table table, PG_FUNCTION_ARGS);  // 'end'

extern Datum FDW_modify_plan(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_modify_open(FDW_Table table, PG_FUNCTION_ARGS);  // 'begin'
extern Datum FDW_modify_close(FDW_Table table, PG_FUNCTION_ARGS);  // 'end'
extern Datum FDW_modify_explain(FDW_Table table, PG_FUNCTION_ARGS);

extern Datum FDW_direct_plan(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_direct_open(FDW_Table table, PG_FUNCTION_ARGS);  // 'begin'
extern Datum FDW_direct_close(FDW_Table table, PG_FUNCTION_ARGS);  // 'end'
extern Datum FDW_direct_explain(FDW_Table table, PG_FUNCTION_ARGS);

extern Datum FDW_scan_get_batch_size(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_modify_get_batch_size(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_direct_get_batch_size(FDW_Table table, PG_FUNCTION_ARGS);

// I/U/D action

extern Datum FDW_next(FDW_Table table, PG_FUNCTION_ARGS);  //  'iterate'  REQUIRED
extern Datum FDW_reset(FDW_Table table, PG_FUNCTION_ARGS);  // 'rescan'  REQUIRED

extern Datum FDW_insert(FDW_Table table, PG_FUNCTION_ARGS); // ExecInsert
extern Datum FDW_insert_batch(FDW_Table table, PG_FUNCTION_ARGS); // ExecInsertBatch
extern Datum FDW_update(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_delete(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_truncate(FDW_Table table, PG_FUNCTION_ARGS);

extern Datum FDW_direct_iterate(FDW_Table table, PG_FUNCTION_ARGS);

// row locking
extern Datum FDW_get_row_mark_type(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_refetch_row(FDW_Table table, PG_FUNCTION_ARGS);
extern Datum FDW_recheck_scan(FDW_Table table, PG_FUNCTION_ARGS);

// parallel execution
extern Datum FDW_is_scan_parallel_safe(FDW_Table table, PG_FUNCTION_ARGS);
// rest are skipped...

// async execution
extern Datum FDW_is_path_async_safe(FDW_Table table, PG_FUNCTION_ARGS);  // different paths may differ
// rest are skipped...

typedef Datum (*FDWFunction)(FDW_Table table, PG_FUNCTION_ARGS);

#ifdef __cplusplus
}
#endif
#endif
