/**
 * Actual implementation of minimal FDW based on the 'blackhole_fdw'
 * project. For simplicity all of the comments and unused functions
 * have been removed.
 *
 * The purpose of this file is to demonstrate the ability of C-based
 * FDW implementation to successfully interact with a java object
 * that implements the FDW interfaces.
 *
 * The first milestone is simply sending a NOTICE from the java:
 * method.
 */
#include "postgres.h"

#include "access/reloptions.h"
#include "foreign/fdwapi.h"
#include "foreign/foreign.h"
#include "optimizer/pathnode.h"
#include "optimizer/planmain.h"
#include "optimizer/restrictinfo.h"

#include "../include/pljava/FDW.h"

PG_MODULE_MAGIC;

#if (PG_VERSION_NUM < 90500)
// fail...
#endif

/*
 * SQL functions
 */
extern Datum blackhole_fdw_handler(PG_FUNCTION_ARGS);

extern Datum blackhole_fdw_validator(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(blackhole_fdw_handler);
PG_FUNCTION_INFO_V1(blackhole_fdw_validator);


/* callback functions */
static void blackholeGetForeignRelSize(PlannerInfo *root,
                                       RelOptInfo *baserel,
                                       Oid foreigntableid);

static void blackholeGetForeignPaths(PlannerInfo *root,
                                     RelOptInfo *baserel,
                                     Oid foreigntableid);

static FdwPlan *blackholePlanForeignScan(Oid foreigntableid, PlannerInfo *root, RelOptInfo *baserel);

static void blackholeBeginForeignScan(ForeignScanState *node,
                                      int eflags);

static TupleTableSlot *blackholeIterateForeignScan(ForeignScanState *node);

static void blackholeReScanForeignScan(ForeignScanState *node);

static void blackholeEndForeignScan(ForeignScanState *node);

/* everything below here is optional */

static int blackholeIsForeignRelUpdatable(Relation rel);

// TODO: locate 'ExplainState'
// static void blackholeExplainForeignScan(ForeignScanState *node, struct ExplainState *es);

#if (PG_VERSION_NUM >= 120000)
static void blackholeRefetchForeignRow(EState *estate,
                   ExecRowMark *erm,
                   Datum rowid,
                   TupleTableSlot *slot,
                   bool *updated);
#else

static HeapTuple blackholeRefetchForeignRow(EState *estate,
                                            ExecRowMark *erm,
                                            Datum rowid,
                                            bool *updated);

#endif

static List *blackholeImportForeignSchema(ImportForeignSchemaStmt *stmt,
                                          Oid serverOid);

#endif

static bool blackholeAnalyzeForeignTable(Relation relation,
                                         AcquireSampleRowsFunc *func,
                                         BlockNumber *totalpages);

#if (PG_VERSION_NUM >= 120000)
static void blackholeRefetchForeignRow(EState *estate,
                   ExecRowMark *erm,
                   Datum rowid,
                   TupleTableSlot *slot,
                   bool *updated);
#else

static HeapTuple blackholeRefetchForeignRow(EState *estate,
                                            ExecRowMark *erm,
                                            Datum rowid,
                                            bool *updated);

#endif

/* ------------------------------------------------------------
 * The POSTGRESQL Functions
 * -----------------------------------------------------------*/
// this needs to be known BEFORE we execute 'CREATE FOREIGN DATA WRAPPER...'
static const char FDW_validator_classname = "org/postgresql/pljava/fdw/BlackholeValidator";
static const char FDW_handler_classname = "org/postgresql/pljava/fdw/BlackholeHandler";  // ???

Datum
blackhole_fdw_handler(PG_FUNCTION_ARGS) {
    FdwRoutine *fdwroutine = makeNode(FdwRoutine);

    elog(DEBUG1, "entering function %s", __func__);

    /*
     * assign the handlers for the FDW
     *
     * This function might be called a number of times. In particular, it is
     * likely to be called for each INSERT statement. For an explanation, see
     * core postgres file src/optimizer/plan/createplan.c where it calls
     * GetFdwRoutineByRelId(().
     */

    /* Required by notations: S=SELECT I=INSERT U=UPDATE D=DELETE */

    /* these are required */
    fdwroutine->GetForeignRelSize = blackholeGetForeignRelSize; /* S U D */
    fdwroutine->GetForeignPaths = blackholeGetForeignPaths;        /* S U D */
    fdwroutine->GetForeignPlan = blackholeGetForeignPlan;        /* S U D */
    fdwroutine->BeginForeignScan = blackholeBeginForeignScan;    /* S U D */
    fdwroutine->IterateForeignScan = blackholeIterateForeignScan;        /* S */
    fdwroutine->ReScanForeignScan = blackholeReScanForeignScan; /* S */
    fdwroutine->EndForeignScan = blackholeEndForeignScan;        /* S U D */

    /* remainder are optional - use NULL if not required */
    /* support for insert / update / delete */
    fdwroutine->IsForeignRelUpdatable = blackholeIsForeignRelUpdatable;
    fdwroutine->AddForeignUpdateTargets = NULL;        /* U D */
    fdwroutine->PlanForeignModify = NULL; /* I U D */
    fdwroutine->BeginForeignModify = NULL;        /* I U D */
    fdwroutine->ExecForeignInsert = NULL; /* I */
    fdwroutine->ExecForeignUpdate = NULL; /* U */
    fdwroutine->ExecForeignDelete = NULL; /* D */
    fdwroutine->EndForeignModify = NULL;    /* I U D */

    /* support for EXPLAIN */
    // fdwroutine->ExplainForeignScan = blackholeExplainForeignScan;        /* EXPLAIN S U D */
    fdwroutine->ExplainForeignScan = NULL;        /* EXPLAIN S U D */
    fdwroutine->ExplainForeignModify = NULL;    /* EXPLAIN I U D */

    /* support for ANALYSE */
    fdwroutine->AnalyzeForeignTable = blackholeAnalyzeForeignTable;        /* ANALYZE only */

    /* Support functions for IMPORT FOREIGN SCHEMA */
    fdwroutine->ImportForeignSchema = blackholeImportForeignSchema;

    /* Support for scanning foreign joins */
    fdwroutine->GetForeignJoinPaths = NULL;

    /* Support for locking foreign rows */
    fdwroutine->GetForeignRowMarkType = NULL:
    fdwroutine->RefetchForeignRow = blackholeRefetchForeignRow;

    // none of the newer functions are handled yet - they deal with 'direct' access, concurrency, and async.

    PG_RETURN_POINTER(fdwroutine);
}

Datum
blackhole_fdw_validator(PG_FUNCTION_ARGS) {
    List *options_list = untransformRelOptions(PG_GETARG_DATUM(0));

    elog(DEBUG1, "entering function %s", __func__);

    /* make sure the options are valid */

    /* no options are supported */
    JNIEnv *env = NULL;
    JNI_FDW_Validator newValidator(JNIEnv *env, const char *validator_classname);

                        errhint("Blackhole FDW does not support any options")));

    PG_RETURN_POINTER(fdwroutine);

    PG_RETURN_VOID();
}

/*

 - I know from context that the Foreign Data Wrapper, Server, and Foreign Table have OIDs associated with them...

Datum
blackhole_fdw_server(PG_FUNCTION_ARGS) {
    FdwServer *fdwserver = makeNode(FdwServer);
    PG_RETURN_POINTER(fdwserver);
}

Datum
blackhole_fdw_table(PG_FUNCTION_ARGS) {
    FdwTable *fdwtable = makeNode(FdwTable);
    PG_RETURN_POINTER(fdwtable);
}
 */

/* ------------------------------------------------------------
 * The JNI headers
 * ------------------------------------------------------------*/

static typedef struct JNI_FDW_Wrapper JNI_FDW_Wrapper;
static typedef struct JNI_FDW_Server JNI_FDW_Server;
static typedef struct JNI_FDW_Table JNI_FDW_Table;
static typedef struct JNI_FDW_PlanState JNI_FDW_PlanState;
static typedef struct JNI_FDW_ScanState JNI_FDW_ScanState;

static JNI_FDW_Wrapper *validator_get_wrapper(JNI_FDW_Validator *validator);
static JNI_FDW_Server *wrapper_get_server(JNI_FDW_Wrapper *wrapper);
static JNI_FDW_Table *server_get_table(JNI_FDW_Server *server);
static JNI_FDW_PlanState *table_new_plan(JNI_FDW_Table *table);
static JNI_FDW_ScanState *table_new_scan(JNI_FDW_Table *table

// not all functions...

static void validator_add_option(JNI_FDW_Validator *, int relid, String key, String value);
static bool validator_validate(JNI_FDW_Validator *);

static JNI_FDW_PlanState *table_new_planstate(JNI_FDW_Table *table);
static void plan_open(JNI_FDW_Table *table, PlannerInfo *root, RelOptInfo *baserel, Oid foreigntableid);
static void plan_close(JNI_FDW_PlanState *plan_state);

static void scan_open(JNI_FDW_ScanState *scan_state, ForeignScanState *node, int eflag);
static void scan_next(JNI_FDW_ScanState *scan_state, Slot *slot);
static void scan_reset(JNI_FDW_ScanState *scan_state);
static void scan_close(JNI_FDW_ScanState *scan_state);

static JNI_FDW_ScanState *table_new_scanPlan(JNI_FDW_Table *table);

// Note: this does not do memory management yet!

static
jmethodId getMethodId(JNIEnv *env, jclass class, ...)
{
    return env->GetMethodIdw(class, vargargs);
}

/**
 * Public: fdwvalidator method
 */
static
typedef struct {
    void (*addOption)(JNI_FDW_Validator *, int, const char *, const char *) = validator_add_option;
    bool (*validate)(JNI_FDW_Validator *) = validator_validate;
    JNI_FDW_Wrapper *(*get_wrapper)(JNI_FDW_Validator *) = validator_get_wrapper;

    const JNIEnv *env;
    const jclass validatorClass;
    const jobject instance;
} JNI_FDW_Validator;

JNI_FDW_Validator newValidator(JNIEnv *env, const char *validator_classname) {
    JNI_FDW_Validator *validator = (JNI_FDW_Validator *) palloc0(sizeof JNI_FDW_Validator);

    validator->env = env;
    validator->validatorClass = env->FindClass(validator_classname);
    validator->instance = env->AllocObject(fdw->validatorClass);

    return validator;
}

/* FIXME - how to handle 'handler' since the 'create foreign wrapper' requires it but we start with the validator? */

/* FIXME - how to we pass wrapper, server, and table options to each? We have them in the Validator function...*/

/* FIXME - plus shouldn't the validation already know the associated wrapper, server, and table?... */


/**
 * Public - Foreign Data Wrapper
 */
static
typedef struct {
    jobject (*newServer)(JNI_FDW_Wrapper *) = wrapper_new_server;

    JNIEnv *env;
    jclass wrapperClass
    jobject *instance;

    JNI_FDW_Validator *validator;
} JNI_FDW_Wrapper;

static
JNI_FDW_Wrapper *validator_new_wrapper(JNI_FDW_Validator *validator, const char *handler_classname)
{
    const JNIEnv *env = validator->env;
    jmethodId validateMethodId = env->GetMethodID(validator->validatorClass, "validate", "(V)[org.postgresql.pljava.fdw.Wrapper;");

    const JNI_FDW_Wrapper *wrapper = (JNI_FDW_Wrapper *) palloc0(sizeof JNI_FDW_Wrapper);

    wrapper->env = validator->env;
    wrapper->wrapperClass = env->FindClass(validator_classname);
    wrapper->instance = env->CallObjectMethod(validator->instance, validator->validateMethodId);
    wrapper->validator = validator;

    return wrapper;
}

/*
 * Public - Server
 */
static
typedef struct {
    void* (*newTable)(void) = server_new_table;

    JNIEnv *env;
    jclass serverClass;
    jobject *instance;

    JNI_FDW_Table *wrqpper;
} JNI_FDW_Server;

static
JNI_FDW_Server *wrapper_new_server(JNI_FDW_Wrapper *wrapper)
{
    const JNIEnv *env = wrapper->env;
    jmethodId newServerMethodId = env->GetMethodID(wrapper->wrapperClass, "newServer", "(V)[org.postgresql.pljava.fdw.Server;");

    const JNI_FDW_Server *server = (JNI_FDW_Server *) palloc0(sizeof JNI_FDW_Server);
    server->env = env;
//    server->serverClass = env->FindClass(validator_classname);
    server->instance = env->CallObjectMethod(wrapper->instance, newServerMethodId);
    server->wrapper = wrapper;

    return server;
}

/*
 * Public - Foreign Table
 */
static
typedef struct {
    void* (*newPlanState)(JNI_FDW_Table *table);
    void *(*newScanState)(JNI_FDW_Table *table);
    void (*analyze)(JNI_FDW_Table *table);

    JNIEnv *env;
    jclass tableClass;
    jobject instance;

    JNI_FDW_Table *server;
} JNI_FDW_Table;

static
JNI_FDW_Table *server_new_table(JNI_FDW_Server *server) {
    const JNIEnv *env = server->env;
    jmethodId newTableMethodId = env->GetMethodID(server->serverClass, "newTable",
                                                  "(V)[org.postgresql.pljava.fdw.Table;");

    const JNI_FDW_Table *table = (JNI_FDW_Table *) palloc0(sizeof JNI_FDW_Table);
    table->env = env;
    // table->tableClass = env->FindClass(table_classname);
    table->instance = env->CallObjectMethod(wrapper->instance, newTableMethodId);
    table->server = server;

    return table;
}

/*
 * Private - plan state
 */
static
typedef struct {
    void (*open)(JNI_FDW_PlanState *planState, PlannerInfo *root, RelOptInfo *baserel, Oid foregntableid) = plan_open;
    void (*close)(JNI_FDW_PlanState *planState);

    JNIEnv *env;
    jobject *instance;

    JNI_FDW_Table *table;

} JNI_FDW_PlanState;

static
JNI_FDW_PlanState *table_new_planstate(JNI_FDW_Table *table) {
    const JNIEnv *env = table->env;

    jmethodId openPlanMethodId = env->GetMethodID(table->tableClass, "newPlanState",
                                                  "(V)[org.postgresql.pljava.fdw.PlanState;");

    const JNI_FDW_PlanState *planState = (JNI_FDW_PlanState *) palloc0(sizeof JNI_FDW_PlanState);
    planState->env = env;
    // table->planStateClass = env->FindClass(planstate_classname);
    planState->instance = env->CallObjectMethod(table->instance, newPlanStateMethodId);
    planState->table = table;
}

static
void *plan_open(JNI_FDW_PlanState *planState, PlannerInfo *root, RelOptInfo *baserel, Oid foreigntableid) {
    const JNIEnv *env = table->env;

    // FIXME: for now we don't pass anything through. However we could after a bit of conversions...
    const jmethodId openPlanMethodId = env->GetMethodID(planState->planStateClass, "open", "(V)V");
    env->CallObjectMethod(planState->instance, openPlanStateMethodId);
}

/*
 * Private - scan state
 */
static
typedef struct {
    void (*open)(JNI_FDW_ScanState *scanState, ForeignScanState *node, int eflags) = open_scan;
    void (*next)(JNI_FDW_ScanState *scanState, TableTupleSlot *slot);
    void (*reset)(JNI_FDW_ScanState *scanState);
    void (*close)(JNI_FDW_ScanState *scanState);
    void (*explain)(JNI_FDW_ScanState *scanState);

    JNIEnv *env;
    jobject *instance;

    JNI_FDW_Table *table;
} JNI_FDW_ScanState;

static
JNI_FDW_ScanState *table_new_scan_state(JNI_FDW_Table *table, ForeignScanState *node, int eflag) {
    const JNIEnv *env = table->env;

    // for now we ignore the extra parameters.
    const jmethodId newScanStateMethodId = env->GetMethodID(table->tableClass, "newScanState",
                                                      "(V)[org.postgresql.pljava.fdw.ScanState;");

    const JNI_FDW_ScanState *scanState = (JNI_FDW_ScanState *) palloc0(sizeof JNI_FDW_ScanState);
    scanState->env = env;

    // for now we ignore the extra parameters.
    scanState->instance = env->CallObjectMethod(table->instance, newScanStateMethodId);
    scanState->table = table;

    return planState;
}

static
void scan_open(JNI_FDW_ScanState *scanState, ForeignScanState *node, int eflag) {

}

/* ------------------------------------------------------------
 * Rest of JNI Implementation - does not use memory management yet!
 * ------------------------------------------------------------*/
static void validator_add_option(JNI_FDW_Validator *validator, int relid, String key, String value)
{
    const JNIEnv *env = validator->env;
    const jmethodId addOptionMethodId = env->GetMethodID(validator->validatorClass, "addOption", "(int, String, String)V");

    const jint jrelid = NULL;
    const jstring jkey = NULL;
    const jstring jvalue = NULL;

    env->CallObjectMethod(validator->instance, addOptionMethodId, jrelid, jkey, jvalue);
}

/* ------------------------------------------------------------
 * The POSTGRESQL implementations
 * -----------------------------------------------------------*/

/* ------------------------------------------------------------
 * FIXME: How do we get to specific JNI_FDW_Table ??
 * ------------------------------------------------------------*/


/**
 * Called to get an estimated size of the foreign table.
 *
 * Note: this can be a no-op.
 */
static void
blackholeGetForeignRelSize(PlannerInfo *root,
                           RelOptInfo *baserel,
                           Oid foreigntableid) {

    // FIXME - this should be available... somewhere...
    const JNI_FDW_Table table = NULL;
    JNI_FDW_Plan plan_state;

    elog(DEBUG1, "entering function %s", __func__);

    plan_state = table->newPlan(root, baserel, foreigntableid);
    baserel->fdw_private = (void *) plan_state;

    baserel->rows = plan_state->rows;

    /* initialize required state in plan_state */

}

/**
 * SELECT: Called to find the location of the foreign table's resources.
 */
static void
blackholeGetForeignPaths(PlannerInfo *root,
                         RelOptInfo *baserel,
                         Oid foreigntableid) {

    /*
     * BlackholeFdwPlanState *plan_state = baserel->fdw_private;
     */

    Cost startup_cost,
            total_cost;

    elog(DEBUG1, "entering function %s", __func__);

    startup_cost = 0;
    total_cost = startup_cost + baserel->rows;

    /* Create a ForeignPath node and add it as only possible path */
    add_path(baserel, (Path *)
            create_foreignscan_path(root, baserel,
                    NULL,      /* default pathtarget */
                    baserel->rows,
#if (PG_VERSION_NUM >= 180000)
                    0,         /* no disabled nodes */
#endif
                    startup_cost,
                    total_cost,
                    NIL,        /* no pathkeys */
                    NULL,        /* no outer rel either */
                    NULL,      /* no extra plan */
#if (PG_VERSION_NUM >= 170000)
                    NIL, /* no fdw_restrictinfo list */
#endif
                    NIL));        /* no fdw_private data */
}

/**
 * SELECT: Called to plan a foreign scan.
 */
static FdwPlan *
blackholePlanForeignScan(Oid foreigntableid, PlannerInfo *root, RelOptInfo *baserel) {
    FdwPlan *fdwplan;
    fdwplan = makeNode(FdwPlan);
    fdwplan->fdw_private = NIL;
    fdwplan->startup_cost = 0;
    fdwplan->total_cost = 0;
    return fdwplan;
}

/**
 * SELECT: Called before the first tuple has been retrieved. It allows
 * last-second validation of the parameters.
 */
static void
blackholeBeginForeignScan(ForeignScanState *node, int eflags) {
    // FIXME: how to get JNI_FDW_table?
    const JNI_FDW_Table *table = NULL;
    const JNI_FDW_ScanState table->newScan(table, node, eflags);

    elog(DEBUG1, "entering function %s", __func__);

    // I'm not sure if this is called before or after test below...
    scan_state = table->begin(table, node, eflags);

    if (eflags & EXEC_FLAG_EXPLAIN_ONLY) {
        return;
    }

    node->fdw_state = scan_state;
}

/**
 * SELECT: Called to retrieve each tuple in the foreign table.
 * Note: the external resource must be opened in this function.
 */
static TupleTableSlot *
blackholeIterateForeignScan(ForeignScanState *node) {
    const TableTupleType slot = node->ss.ss_ScanTupleSlot();
    const JNI_FDW_ScanState *scan_state = (JNI_FDW_Scan *) node->fdw_state();

    elog(DEBUG1, "entering function %s", __func__);

    // is this EXPLAIN_ONLY ?
    if (scan_state == NULL) {
        return;
    }

    ExecClearTuple(slot);
    scan_state->next(scan_state, slot);

    // additional processing?

    return slot;
}

/**
 * SELECT: Called to reset internal state to initial conditions.
 */
static void
blackholeReScanForeignScan(ForeignScanState *node) {
    const JNI_FDW_ScanState *scan_state = (JNI_FDW_Scan *) node->fdw_state();

    elog(DEBUG1, "entering function %s", __func__);

    // is this EXPLAIN_ONLY ?
    if (scan_state == null) {
        return;
    }

    scan_state->reset(scan_state);
}

/*
 * SELECT: Called after the last row has been returned.
 */
static void
blackholeEndForeignScan(ForeignScanState *node) {
    const JNI_FDW_ScanState *scan_state = (JNI_FDW_Scan *) node->fdw_state();

    elog(DEBUG1, "entering function %s", __func__);

    scan_state->close(scan_state);
    // scan->table.removeScan(scan);   ???

    pfree(scan_state);
}

/**
 * Called when EXPLAIN is executed. This allows us to provide
 * Wrapper, Server, and Table options like URLs, etc.
 */
static void
blackholeExplainForeignScan(ForeignScanState *node,
                            struct ExplainState *es) {

    elog(DEBUG1, "entering function %s", __func__);

}

/**
 * Called when ANALYZE is executed on a foreign table.
 */
static bool
blackholeAnalyzeForeignTable(Relation relation,
                             AcquireSampleRowsFunc *func,
                             BlockNumber *totalpages) {

    elog(DEBUG1, "entering function %s", __func__);

    return false;
}

/**
 * Called when two or more foreign tables are on the same foreign server.
 */
static void
blackholeGetForeignJoinPaths(PlannerInfo *root,
                             RelOptInfo *joinrel,
                             RelOptInfo *outerrel,
                             RelOptInfo *innerrel,
                             JoinType jointype,
                             JoinPathExtraData *extra) {

    elog(DEBUG1, "entering function %s", __func__);
}

/**
 * LOCK-AWARE - called to re-fetch a tuple from a foreign table
 */
#if (PG_VERSION_NUM >= 120000)
static void blackholeRefetchForeignRow(EState *estate,
                   ExecRowMark *erm,
                   Datum rowid,
                   TupleTableSlot *slot,
                   bool *updated)
#else

static HeapTuple
blackholeRefetchForeignRow(EState *estate,
                           ExecRowMark *erm,
                           Datum rowid,
                           bool *updated)
#endif
{

    elog(DEBUG1, "entering function %s", __func__);

#if (PG_VERSION_NUM < 120000)
    return NULL;
#endif
}


/*
 * Called when IMPORT FOREIGN SCHEMA is executed.
 */
static List *
blackholeImportForeignSchema(ImportForeignSchemaStmt *stmt,
                             Oid serverOid) {

    elog(DEBUG1, "entering function %s", __func__);

    return NULL;
}
