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
#include "postgres_ext.h"

#include "access/reloptions.h"
#include "commands/explain.h"
#include "foreign/fdwapi.h"
#include "foreign/foreign.h"
#include "optimizer/pathnode.h"
#include "optimizer/planmain.h"
#include "optimizer/restrictinfo.h"

#include "../include/pljava/FDW.h"

// PG_MODULE_MAGIC;

#if (PG_VERSION_NUM < 90500)
// fail...
#endif

/**
 * Sidenotes:
 *
 * PlanState also provides:
 * - Instrumentation *instrument;    // Optional runtime stats for this node
 * - WorkerInstrumentation *worker_instrument;   // per-worker instrumentation
 */

// this needs to be known BEFORE we execute 'CREATE FOREIGN DATA WRAPPER...'
// static const char* FDW_validator_classname = "org/postgresql/pljava/fdw/BlackholeValidator";

/*
 * Notes: copied from fdwapi.h
 *
 * extern Oid  GetForeignServerIdByRelId(Oid relid);
 * extern bool IsImportableForeignTable(const char *tablename,
 *                                      ImportForeignSchemaStmt *stmt);
 * extern Path *GetExistingLocalJoinPath(RelOptInfo *joinrel);
 *
 * extern FdwRoutine *GetFdwRoutine(Oid fdwhandler);
 * extern FdwRoutine *GetFdwRoutineByServerId(Oid serverid);
 * extern FdwRoutine *GetFdwRoutineByRelId(Oid relid);
 * extern FdwRoutine *GetFdwRoutineForRelation(Relation relation, bool makecopy);
 *
 * And from foreign.h
 *
 * extern ForeignServer *GetForeignServer(Oid serverid);
 * extern ForeignServer *GetForeignServerExtended(Oid serverid,
 *                                                bits16 flags);
 * extern ForeignServer *GetForeignServerByName(const char *srvname,
 *                                              bool missing_ok);
 * extern UserMapping *GetUserMapping(Oid userid, Oid serverid);
 * extern ForeignDataWrapper *GetForeignDataWrapper(Oid fdwid);
 * extern ForeignDataWrapper *GetForeignDataWrapperExtended(Oid fdwid,
 *                                                          bits16 flags);
 * extern ForeignDataWrapper *GetForeignDataWrapperByName(const char *fdwname,
 *                                                        bool missing_ok);
 * extern ForeignTable *GetForeignTable(Oid relid);
 *
 * extern List *GetForeignColumnOptions(Oid relid, AttrNumber attnum);
 *
 * extern Oid  get_foreign_data_wrapper_oid(const char *fdwname, bool missing_ok);
 * extern Oid  get_foreign_server_oid(const char *servername, bool missing_ok);
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

static ForeignScan *blackholeGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *rel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *restrictinfo_list,
                        Plan *outer_plan);

static void blackholeBeginForeignScan(ForeignScanState *node,
                                      int eflags);

static TupleTableSlot *blackholeIterateForeignScan(ForeignScanState *node);

static void blackholeReScanForeignScan(ForeignScanState *node);

static void blackholeEndForeignScan(ForeignScanState *node);

/* everything below here is optional */
static int blackholeIsForeignRelUpdatable(Relation rel);
static void blackholeExplainForeignScan(ForeignScanState *node, ExplainState *es);
static List *blackholeImportForeignSchema(ImportForeignSchemaStmt *stmt, Oid serverOid);
static bool blackholeIsForeignScanParallelSafe(PlannerInfo *root, RelOptInfo *rel, RangeTblEntry *rte);
static bool blackholeIsForeignPathAsyncCapable(ForeignPath *path);


/* ------------------------------------------------------------
 * The POSTGRESQL Functions
 * -----------------------------------------------------------*/

Datum
blackhole_fdw_handler(PG_FUNCTION_ARGS)
{
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

    /* Support for scanning foreign joins */
    fdwroutine->GetForeignJoinPaths = NULL;

    /* Functions for remote upper-relation (post scan/join) planning */
    fdwroutine->GetForeignUpperPaths = NULL;

    /* Functions for modifying foreign tables */
    fdwroutine->AddForeignUpdateTargets = NULL;        /* U D */
    fdwroutine->PlanForeignModify = NULL; /* I U D */
    fdwroutine->BeginForeignModify = NULL;        /* I U D */
    fdwroutine->ExecForeignInsert = NULL; /* I */
    fdwroutine->ExecForeignUpdate = NULL; /* U */
    fdwroutine->ExecForeignDelete = NULL; /* D */
    fdwroutine->EndForeignModify = NULL;    /* I U D */

	/* Next-Generation functions for modifying foreign tables? */
    fdwroutine->PlanDirectModify = NULL;
    fdwroutine->BeginDirectModify = NULL;
    fdwroutine->IterateDirectModify = NULL;
    fdwroutine->EndDirectModify = NULL;

    /* Support for SELECT FOR UPODATE/SHARE row locking */
    fdwroutine->GetForeignRowMarkType = NULL;
    fdwroutine->RefetchForeignRow = NULL;
    fdwroutine->RecheckForeignScan = NULL;

    /* support for EXPLAIN */
    fdwroutine->ExplainForeignScan = blackholeExplainForeignScan;        /* EXPLAIN S U D */
    fdwroutine->ExplainForeignModify = NULL;    /* EXPLAIN I U D */
    fdwroutine->ExplainDirectModify = NULL;

    /* Support functions for ANALYZE */
    fdwroutine->AnalyzeForeignTable = NULL; /* ANALYZE only */

    /* Support functions for IMPORT FOREIGN SCHEMA */
    fdwroutine->ImportForeignSchema = blackholeImportForeignSchema;

    /* Support functions for TRUNCATE */
#if (PG_VERSION_NUM >= 14000)
    fdwroutine->ExecForeignTruncate = NULL;
#endif

    /* Support functions for parallelism under Gather node */
    fdwroutine->IsForeignScanParallelSafe = blackholeIsForeignScanParallelSafe;
    fdwroutine->EstimateDSMForeignScan = NULL;
    fdwroutine->InitializeDSMForeignScan = NULL;
    fdwroutine->ReInitializeDSMForeignScan = NULL;
    fdwroutine->InitializeWorkerForeignScan = NULL;
    fdwroutine->ShutdownForeignScan = NULL;

    /* Support functions for path reparameterization. */
    fdwroutine->ReparameterizeForeignPathByChild = NULL;

#if (PG_VERSION_NUM >= 14000)
    /* Support functions for asynchronous execution */
    fdwroutine->IsForeignPathAsyncCapable = blackholeIsForeignPathAsyncCapable;
    fdwroutine->ForeignAsyncRequest = NULL;
    fdwroutine->ForeignAsyncConfigureWait = NULL;
    fdwroutine->ForeignAsyncNotify = NULL;
#endif

    PG_RETURN_POINTER(fdwroutine);
}

Datum
blackhole_fdw_validator(PG_FUNCTION_ARGS)
{
    List *options_list = untransformRelOptions(PG_GETARG_DATUM(0));

    elog(DEBUG1, "entering function %s", __func__);

    /* collect options */

    /* validate them */
#ifdef USE_JAVA
    JNI_FDW_Validator newValidator = call constructor (static method)
#endif

    if (list_length(options_list) > 0)
        ereport(ERROR,
            (errcode(ERRCODE_FDW_INVALID_OPTION_NAME),
                errmsg("invalid options"),
                errhint("Simple FDW does not support any options")));

    PG_RETURN_VOID();
}

static void
blackholeShowInfo(Oid foreigntableid, RelOptInfo *rel)
{
    // rel->serverid;
    // rel->userid; // may be InvalidOid = current user)
    // rel->useriscurrent
    // rel->fdwroutine
    // rel->fdw_private

    // rel->rows
    // rel->relid (only base rel, not joins)
    // rel->min_attr  (often <0)
    // rel->max_attr;

    // ForeignTable *table = GetForeignTableExtended(foreigntableid, flags);  // expects relid?
    // ForeignServer *server = GetForeignServerExtended(table->serverid, flags);

    // ForeignTable *table = GetForeignTable(rel->relid);  // expects relid?
    // ForeignServer *server = GetForeignServer(table->serverid);
    // ForeignDataWrapper *fdw = GetForeignDataWrapper(server->fdwid);

    // List *ftOptions = table->options;
    // List *srvOptions = server->options;
    // List *fdwOptions = fdw->options;
    // List *userOptions = um->options;
    // List *columnOptions = GetForeignColumnOptions(rel->relid, (AttrNumber) 0);

    // macro
    // char *username = MappingUserName(userid)

    // Oid serverId = svr->serverid;
    // Oid fdwid = svr->fdwid;
    // Oid srvOwnerId = svr->ownerid;
    // char *servername = svr->servername;
    // char *serverType = svr->servertype; // optional
    // char *serverVersion = svr->serverversion; // optional

    // Oid fwdid = fdw->fdwid;
    // Oid owner = fdw->owner;
    // char *fdwname = fdw->fdwname;
    // Oid fdwhandler = fdw->fdwhandler;
    // Oid fdwvalidator = fdw->fdwvalidator;

    // Oid usermappingoid = um->umid;
    // Oid userId = um->userId;
    // Oid umServerId = um->serverId;

    // wrapper->options;
    // wrapper->owner;
}

static void
blackholeGetForeignRelSize(PlannerInfo *root,
                           RelOptInfo *baserel,
                           Oid foreigntableid) {
    /*1
     * Obtain relation size estimates for a foreign table. This is called at
     * the beginning of planning for a query that scans a foreign table. root
     * is the planner's global information about the query; baserel is the
     * planner's information about this table; and foreigntableid is the
     * pg_class OID of the foreign table. (foreigntableid could be obtained
     * from the planner data structures, but it's passed explicitly to save
     * effort.)
     *
     * This function should update baserel->rows to be the expected number of
     * rows returned by the table scan, after accounting for the filtering
     * done by the restriction quals. The initial value of baserel->rows is
     * just a constant default estimate, which should be replaced if at all
     * possible. The function may also choose to update baserel->width if it
     * can compute a better estimate of the average result row width.
     */

    elog(NOTICE, "entering function %s", __func__);

#ifdef USE_JAVA
	// JAVA CONSTRUCTOR based on foreigntable id
    baserel->fdw_private = JNI_getForeignRelSize(user, root, baserel);

    // JAVA METHOD
    /* initialize required state in plan_state */
    (JNI_FDW_PlanState *baserel->plan_state)->open(plan_state);
#endif
    // baserel->rows = plan_state->rows;
}

static void
blackholeGetForeignPaths(PlannerInfo *root,
                         RelOptInfo *rel,
                         Oid foreigntableid) {
    /*
     * Create possible access paths for a scan on a foreign table. This is
     * called during query planning. The parameters are the same as for
     * GetForeignRelSize, which has already been called.
     *
     * This function must generate at least one access path (ForeignPath node)
     * for a scan on the foreign table and must call add_path to add each such
     * path to rel->pathlist. It's recommended to use
     * create_foreignscan_path to build the ForeignPath nodes. The function
     * can generate multiple access paths, e.g., a path which has valid
     * pathkeys to represent a pre-sorted result. Each access path must
     * contain cost estimates, and can contain any FDW-private information
     * that is needed to identify the specific scan method intended.
     */

    PathTarget *target = NULL;
    List *pathkeys = NIL;
    Relids required_outer = NULL;
    Path *fdw_outerpath = NULL; // extra plan
    List *fdw_restrictinfo = NIL;
    List *options = NIL;

    Cost startup_cost = 0;
    Cost total_cost = startup_cost + rel->rows;

	// JNI_FDW_PlanState *plan_state = NULL;

    // elog(NOTICE, "server: %s (%s) type: %s (%d)", server->servername, server->serverversion, server->servertype, server->fdwid);
    // elog(NOTICE, "server: %s (%d)", server->servername, server->fdwid);

    // elog(NOTICE, "table:, rel: %d, serverid: %d", table->relid, table->serverid);
    // table->options;

    // List *to_list = table->options;

#ifdef USE_JAVA
	// NOTE: the fact that we see the `foreigntableid` parameter means that
	// this is probably a null value and we need to call a constructor.
	// I remember the documentation referred to a state being available
	// but the `fdw_private` ptr was still null.
	plan_state = ...

	// now update the plan_state.
#endif

    /* Create a ForeignPath node and add it as only possible path */
    add_path(rel, (Path *) create_foreignscan_path(
                 root,
                 rel,
                 target,
                 rel->rows,     // planState->rows
#if (PG_VERSION_NUM >= 180000)
				 0,         /* no disabled nodes */
#endif
                 startup_cost,  // planState->startup_cost
                 total_cost,    // planState->total_cost
                 pathkeys,
                 required_outer,
                 fdw_outerpath,
#if (PG_VERSION_NUM >= 170000)
                 fdw_restrictinfo,
#endif
                 options));
}

static ForeignScan *
blackholeGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *rel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *restrictinfo_list,
                        Plan *outer_plan)
{
    /*
     * Create a ForeignScan plan node from the selected foreign access path.
     * This is called at the end of query planning. The parameters are as for
     * GetForeignRelSize, plus the selected ForeignPath (previously produced
     * by GetForeignPaths), the target list to be emitted by the plan node,
     * and the restriction clauses to be enforced by the plan node.
     *
     * This function must create and return a ForeignScan plan node; it's
     * recommended to use make_foreignscan to build the ForeignScan node.
     */

    Index		scan_relid = rel->relid;

    /*
     * We have no native ability to evaluate restriction clauses, so we just
     * put all the scan_clauses into the plan node's qual list for the
     * executor to check. So all we have to do here is strip RestrictInfo
     * nodes from the clauses and ignore pseudoconstants (which will be
     * handled elsewhere).
     */

    bool pseudocontent = false;

    /* Create the ForeignScan node */
    List *fdw_exprs = NIL; // expressions to evaluate
    List *fdw_private = NIL; // private state
    List *fdw_scan_tlist = NIL; // custom tlist
    List *fdw_recheck_quals = NIL; // remote quals
    List *restrictions = extract_actual_clauses(restrictinfo_list, pseudocontent);
    ForeignScan *scan = NULL;

	// JNI_FDW_ScanState *scan_state;

    elog(NOTICE, "entering function %s", __func__);

#ifdef USE_JAVA
	// the presence of the `foreigntableid` says that we should be calling a constructor.

	// update the variables mentioned above.
#endif

    scan = make_foreignscan(tlist,
                            restrictions,
                            scan_relid,
                            fdw_exprs,
                            fdw_private,
                            fdw_scan_tlist,
                            fdw_recheck_quals,
                            outer_plan);

#ifdef USE_JAVA
    // I'm not sure we do this here... see next method
   // scan->fdw_private = scanState;
#endif

    return scan;
}

static void
blackholeBeginForeignScan(ForeignScanState *node,
                          int eflags) {
    /*
     * Begin executing a foreign scan. This is called during executor startup.
     * It should perform any initialization needed before the scan can start,
     * but not start executing the actual scan (that should be done upon the
     * first call to IterateForeignScan). The ForeignScanState node has
     * already been created, but its fdw_state field is still NULL.
     * Information about the table to scan is accessible through the
     * ForeignScanState node (in particular, from the underlying ForeignScan
     * plan node, which contains any FDW-private information provided by
     * GetForeignPlan). eflags contains flag bits describing the executor's
     * operating mode for this plan node.
     *
     * Note that when (eflags & EXEC_FLAG_EXPLAIN_ONLY) is true, this function
     * should not perform any externally-visible actions; it should only do
     * the minimum required to make the node state valid for
     * ExplainForeignScan and EndForeignScan.
     *
     */

    // ScanState scanState = (ForeignScan *) node->ss
    // PlanState planState = (ForeignScan *) node->ss.ps;

//    Plan       *plan = (ForeignScan *) node->ss.ps.plan;
//    EState     *state = (ForeignScan *) node->ss.ps.state;
//    List	   *options;
    bool        explainOnly = eflags & EXEC_FLAG_EXPLAIN_ONLY;

	// this is initially set to NULL as a marker for 'explainOnly'
    JNI_FDW_ScanState *scan_state = NULL;

    elog(NOTICE, "entering function %s", __func__);

    /* Fetch options of foreign table */
//    fileGetOptions(RelationGetRelid(node->ss.ss_currentRelation),
//                   &filename, &is_program, &options);

    /* Add any options from the plan (currently only convert_selectively) */
/*
    if (plan_state != NULL)
    {
    	options = list_concat(options, plan_state);
  	}
*/

    /*
     * Do nothing in EXPLAIN (no ANALYZE) case.  node->fdw_state stays NULL.
     */
	if (explainOnly)
    {
 		// this function should not perform any externally-visible actions;
 		// it should only do the minimum required to make the node state valid for
     	// ExplainForeignScan and EndForeignScan.

     	return;
   	}

    /*
     * From FileFDW
     * Create CopyState from FDW options.  We always acquire all columns, so
     * as to match the expected ScanTupleSlot signature.
     */
    /*
    cstate = BeginCopyFrom(NULL,
                           node->ss.ss_currentRelation,
                           NULL,
                           filename,
                           is_program,
                           NULL,
                           NIL,
                           options);
                           */

    // plan_state = (JNI_FDW_PlanState *) plan->fdw_private;
#ifdef USE_JAVA
	//  'explain only' is always false here...
    scan_state = jni_create_blackhole_fdw_scan_state(node, explainOnly);

	if (scan_state != NULL) {
    	scan_state->open(scan_state);
    }
#endif

    node->fdw_state = scan_state;
}


static TupleTableSlot *
blackholeIterateForeignScan(ForeignScanState *node) {
    /*
     * Fetch one row from the foreign source, returning it in a tuple table
     * slot (the node's ScanTupleSlot should be used for this purpose). Return
     * NULL if no more rows are available. The tuple table slot infrastructure
     * allows either a physical or virtual tuple to be returned; in most cases
     * the latter choice is preferable from a performance standpoint. Note
     * that this is called in a short-lived memory context that will be reset
     * between invocations. Create a memory context in BeginForeignScan if you
     * need longer-lived storage, or use the es_query_cxt of the node's
     * EState.
     *
     * The rows returned must match the column signature of the foreign table
     * being scanned. If you choose to optimize away fetching columns that are
     * not needed, you should insert nulls in those column positions.
     *
     * Note that PostgreSQL's executor doesn't care whether the rows returned
     * violate any NOT NULL constraints that were defined on the foreign table
     * columns — but the planner does care, and may optimize queries
     * incorrectly if NULL values are present in a column declared not to
     * contain them. If a NULL value is encountered when the user has declared
     * that none should be present, it may be appropriate to raise an error
     * (just as you would need to do in the case of a data type mismatch).
     */

    JNI_FDW_ScanState *scan_state = (JNI_FDW_ScanState *) node->fdw_state;
    TupleTableSlot *slot = node->ss.ss_ScanTupleSlot;

    /* get the current slot and clear it */
    ExecClearTuple(slot);

    /*
     * Do nothing in EXPLAIN (no ANALYZE) case.  node->fdw_state stays NULL.
     */
    if (node->fdw_state == NULL) {
        return NULL;
    }

    elog(NOTICE, "entering function %s", __func__);

    if (scan_state != NULL)
    {
#ifdef USE_JAVA
        /* get the next record, if any, and fill in the slot */
    	scan_state->next(scan_state);
   	    populate slot
#endif
  	}

    /* then return the slot */
    return slot;
}


static void
blackholeReScanForeignScan(ForeignScanState *node) {
    /*
     * Restart the scan from the beginning. Note that any parameters the scan
     * depends on may have changed value, so the new scan does not necessarily
     * return exactly the same rows.
     */

    JNI_FDW_ScanState *scan_state = (JNI_FDW_ScanState *) node->fdw_state;

    elog(NOTICE, "entering function %s", __func__);

	if (scan_state != NULL)
	{
#ifdef USE_JAVA
		// note: should this include 'user' parameter?
    	scan_state->reset(scan_state);
#endif
	}
}


static void
blackholeEndForeignScan(ForeignScanState *node) {
    /*
     * End the scan and release resources. It is normally not important to
     * release palloc'd memory, but for example open files and connections to
     * remote servers should be cleaned up.
     */

    JNI_FDW_ScanState *scan_state = (JNI_FDW_ScanState *) node->fdw_state;

    /*
     * Do nothing in EXPLAIN (no ANALYZE) case.  node->fdw_state stays NULL.
     */
    if (node->fdw_state == NULL) {
        return;
    }

    elog(NOTICE, "entering function %s", __func__);

	if (scan_state != NULL)
	{
#ifdef USE_JAVA
    	scan_state->close(scan_state);
#endif
    	pfree(scan_state);
	}
}

static int
blackholeIsForeignRelUpdatable(Relation rel)
{
    // TODO: check FDW_user...
    return 0;
}

/*
 * fileExplainForeignScan
 *		Produce extra output for EXPLAIN
 */
static void
blackholeExplainForeignScan(ForeignScanState *node, ExplainState *es)
{
    // List	   *options;

#ifdef NEVER
    // this comes from FileFdw.

    /* Fetch options --- we only need filename and is_program at this point  */
    fileGetOptions(RelationGetRelid(node->ss.ss_currentRelation),
                   &filename, &is_program, &options);

    if (is_program)
        ExplainPropertyText("Foreign Program", filename, es);
    else
        ExplainPropertyText("Foreign File", filename, es);

    /* Suppress file size if we're not showing cost details */
    if (es->costs)
    {
        struct stat stat_buf;

        if (!is_program &&
            stat(filename, &stat_buf) == 0)
            ExplainPropertyInteger("Foreign File Size", "b",
                                   (int64) stat_buf.st_size, es);
    }
#endif
}

static List *blackholeImportForeignSchema(ImportForeignSchemaStmt *stmt, Oid serverOid)
{
    return NIL;
}

static bool blackholeIsForeignScanParallelSafe(PlannerInfo *root,
                                      RelOptInfo *rel,
                                      RangeTblEntry *rte)
{
    return false;
}

static bool blackholeIsForeignPathAsyncCapable(ForeignPath *path) {
    return false;
}
