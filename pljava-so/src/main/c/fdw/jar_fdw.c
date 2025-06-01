/*-------------------------------------------------------------------------
 *
 * Jar Foreign Data Wrapper for PostgreSQL
 *
 *-------------------------------------------------------------------------
 * Also jar_fdw
 *-------------------------------------------------------------------------
 *
 * Blackhole Foreign Data Wrapper for PostgreSQL
 *
 * Copyright (c) 2013 Andrew Dunstan
 *
 * This software is released under the PostgreSQL Licence
 *
 * Author: Andrew Dunstan <andrew@dunslane.net>
 *
 * IDENTIFICATION
 *          src/fdw/jar_fdw.c
 *
 *-------------------------------------------------------------------------
 */

#include "postgres.h"

#include "access/reloptions.h"
#include "foreign/fdwapi.h"
#include "foreign/foreign.h"
#include "optimizer/pathnode.h"
#include "optimizer/planmain.h"
#include "optimizer/restrictinfo.h"

// note: this does not support TOC!
#include <zzip/lib.h> // @TODO TEMPORARY


PG_MODULE_MAGIC;

static void initialize();

/*
 * Describes the valid options for objects that use this wrapper.
 */
struct JarFdwOption
{
    const char *optname;
    Oid            optcontext;        /* Oid of catalog in which option may appear */
};

/*
 * Valid options for jar_fdw.
 * These options are based on the options for the COPY FROM command.
 * But note that force_not_null and force_null are handled as boolean options
 * attached to a column, not as table options.
 *
 * Note: If you are adding new option for user mapping, you need to modify
 * fileGetOptions(), which currently doesn't bother to look at user mappings.
 */
static const struct JarFdwOption valid_options[] = {
    /* Data source options */
/*
    {"filename", ForeignTableRelationId},
    {"program", ForeignTableRelationId},
*/

    /* Format options */
    /* oids option is not supported */
/*
    {"format", ForeignTableRelationId},
    {"header", ForeignTableRelationId},
    {"delimiter", ForeignTableRelationId},
    {"quote", ForeignTableRelationId},
    {"escape", ForeignTableRelationId},
    {"null", ForeignTableRelationId},
    {"default", ForeignTableRelationId},
    {"encoding", ForeignTableRelationId},
    {"on_error", ForeignTableRelationId},
    {"log_verbosity", ForeignTableRelationId},
    {"reject_limit", ForeignTableRelationId},
    {"force_not_null", AttributeRelationId},
    {"force_null", AttributeRelationId},
*/


    {"filename", ForeignServerRelationId},
    // {"filename", UserMappingRelationId},
    // {"filename", ForeignTableRelationId},

    /*
     * force_quote is not supported by jar_fdw because it's for COPY TO.
     */

    /* Sentinel */
    {NULL, InvalidOid}
};


/*
 * SQL functions
 */
extern Datum jar_fdw_handler(PG_FUNCTION_ARGS);
extern Datum jar_fdw_validator(PG_FUNCTION_ARGS);

PG_FUNCTION_INFO_V1(jar_fdw_handler);
PG_FUNCTION_INFO_V1(jar_fdw_validator);

/* callback functions */
#if (PG_VERSION_NUM >= 90200)
static void jarGetForeignRelSize(PlannerInfo *root,
                           RelOptInfo *baserel,
                           Oid foreigntableid);

static void jarGetForeignPaths(PlannerInfo *root,
                         RelOptInfo *baserel,
                         Oid foreigntableid);

#if (PG_VERSION_NUM < 90500)
static ForeignScan *jarGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *baserel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *scan_clauses);
#else
static ForeignScan *jarGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *baserel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *scan_clauses,
                        Plan *outer_plan
    );
#endif

#else /* 9.1 only */
static FdwPlan *jarPlanForeignScan(Oid foreigntableid, PlannerInfo *root, RelOptInfo *baserel);
#endif

static void jarBeginForeignScan(ForeignScanState *node,
                          int eflags);

static TupleTableSlot *jarIterateForeignScan(ForeignScanState *node);

static void jarReScanForeignScan(ForeignScanState *node);

static void jarEndForeignScan(ForeignScanState *node);

#if (PG_VERSION_NUM >= 90300)
static void jarAddForeignUpdateTargets(
#if (PG_VERSION_NUM >= 140000)
                                 PlannerInfo *root,
                                 Index rtindex,
#else
                                 Query *parsetree,
#endif
                                 RangeTblEntry *target_rte,
                                 Relation target_relation);

static List *jarPlanForeignModify(PlannerInfo *root,
                           ModifyTable *plan,
                           Index resultRelation,
                           int subplan_index);

static void jarBeginForeignModify(ModifyTableState *mtstate,
                            ResultRelInfo *rinfo,
                            List *fdw_private,
                            int subplan_index,
                            int eflags);

static TupleTableSlot *jarExecForeignInsert(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot);

static TupleTableSlot *jarExecForeignUpdate(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot);

static TupleTableSlot *jarExecForeignDelete(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot);

static void jarEndForeignModify(EState *estate,
                          ResultRelInfo *rinfo);

static int    jarIsForeignRelUpdatable(Relation rel);

#endif

static void jarExplainForeignScan(ForeignScanState *node,
                            struct ExplainState * es);

#if (PG_VERSION_NUM >= 90300)
static void jarExplainForeignModify(ModifyTableState *mtstate,
                              ResultRelInfo *rinfo,
                              List *fdw_private,
                              int subplan_index,
                              struct ExplainState * es);
#endif

#if (PG_VERSION_NUM >= 90200)
static bool jarAnalyzeForeignTable(Relation relation,
                             AcquireSampleRowsFunc *func,
                             BlockNumber *totalpages);
#endif

#if (PG_VERSION_NUM >= 90500)

static void jarGetForeignJoinPaths(PlannerInfo *root,
                             RelOptInfo *joinrel,
                             RelOptInfo *outerrel,
                             RelOptInfo *innerrel,
                             JoinType jointype,
                             JoinPathExtraData *extra);


static RowMarkType jarGetForeignRowMarkType(RangeTblEntry *rte,
                               LockClauseStrength strength);

#if (PG_VERSION_NUM >= 120000)
static void jarRefetchForeignRow(EState *estate,
                   ExecRowMark *erm,
                   Datum rowid,
                   TupleTableSlot *slot,
                   bool *updated);
#else
static HeapTuple jarRefetchForeignRow(EState *estate,
                           ExecRowMark *erm,
                           Datum rowid,
                           bool *updated);
#endif
static List *jarImportForeignSchema(ImportForeignSchemaStmt *stmt,
                             Oid serverOid);

#endif

/*
 * structures used by the FDW
 *
 * These next structures are not actually used by jar,but something like
 * them will be needed by anything more complicated that does actual work.
 */

/*
 * Describes the valid options for objects that use this wrapper.
 */
struct jarFdwOption
{
    const char *optname;
    Oid            optcontext;        /* Oid of catalog in which option may appear */
};

/*
 * The plan state is set up in jarGetForeignRelSize and stashed away in
 * baserel->fdw_private and fetched in jarGetForeignPaths.
 */
typedef struct
{
    char       *foo;
    int            bar;
} JarFdwPlanState;

/*
 * The scan state is for maintaining state for a scan, eiher for a
 * SELECT or UPDATE or DELETE.
 *
 * It is set up in jarBeginForeignScan and stashed in node->fdw_state
 * and subsequently used in jarIterateForeignScan,
 * jarEndForeignScan and jarReScanForeignScan.
 */
typedef struct
{
    char       *baz;
    int            blurfl;
} JarFdwScanState;

/*
 * The modify state is for maintaining state of modify operations.
 *
 * It is set up in jarBeginForeignModify and stashed in
 * rinfo->ri_FdwState and subsequently used in jarExecForeignInsert,
 * jarExecForeignUpdate, jarExecForeignDelete and
 * jarEndForeignModify.
 */
typedef struct
{
    char       *chimp;
    int            chump;
} JarFdwModifyState;


Datum
jar_fdw_handler(PG_FUNCTION_ARGS)
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
#if (PG_VERSION_NUM >= 90200)
    fdwroutine->GetForeignRelSize = jarGetForeignRelSize; /* S U D */
    fdwroutine->GetForeignPaths = jarGetForeignPaths;        /* S U D */
    fdwroutine->GetForeignPlan = jarGetForeignPlan;        /* S U D */
#else
    fdwroutine->PlanForeignScan = jarPlanForeignScan;     /* S */
#endif
    fdwroutine->BeginForeignScan = jarBeginForeignScan;    /* S U D */
    fdwroutine->IterateForeignScan = jarIterateForeignScan;        /* S */
    fdwroutine->ReScanForeignScan = jarReScanForeignScan; /* S */
    fdwroutine->EndForeignScan = jarEndForeignScan;        /* S U D */

    /* remainder are optional - use NULL if not required */
    /* support for insert / update / delete */
#if (PG_VERSION_NUM >= 90300)
    fdwroutine->IsForeignRelUpdatable = jarIsForeignRelUpdatable;
    fdwroutine->AddForeignUpdateTargets = jarAddForeignUpdateTargets;        /* U D */
    fdwroutine->PlanForeignModify = jarPlanForeignModify; /* I U D */
    fdwroutine->BeginForeignModify = jarBeginForeignModify;        /* I U D */
    fdwroutine->ExecForeignInsert = jarExecForeignInsert; /* I */
    fdwroutine->ExecForeignUpdate = jarExecForeignUpdate; /* U */
    fdwroutine->ExecForeignDelete = jarExecForeignDelete; /* D */
    fdwroutine->EndForeignModify = jarEndForeignModify;    /* I U D */
#endif

    /* support for EXPLAIN */
    fdwroutine->ExplainForeignScan = jarExplainForeignScan;        /* EXPLAIN S U D */
#if (PG_VERSION_NUM >= 90300)
    fdwroutine->ExplainForeignModify = jarExplainForeignModify;    /* EXPLAIN I U D */
#endif

#if (PG_VERSION_NUM >= 90200)
    /* support for ANALYSE */
    fdwroutine->AnalyzeForeignTable = jarAnalyzeForeignTable;        /* ANALYZE only */
#endif


#if (PG_VERSION_NUM >= 90500)
    /* Support functions for IMPORT FOREIGN SCHEMA */
    fdwroutine->ImportForeignSchema = jarImportForeignSchema;

    /* Support for scanning foreign joins */
    fdwroutine->GetForeignJoinPaths = jarGetForeignJoinPaths;

    /* Support for locking foreign rows */
    fdwroutine->GetForeignRowMarkType = jarGetForeignRowMarkType;
    fdwroutine->RefetchForeignRow = jarRefetchForeignRow;

#endif


    PG_RETURN_POINTER(fdwroutine);
}

#if (PG_VERSION_NUM >= 90200)
static void
jarGetForeignRelSize(PlannerInfo *root,
                           RelOptInfo *baserel,
                           Oid foreigntableid)
{
    /*
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

    JarFdwPlanState *plan_state;

    elog(DEBUG1, "entering function %s", __func__);

    baserel->rows = 0;

    plan_state = palloc0(sizeof(JarFdwPlanState));
    baserel->fdw_private = (void *) plan_state;

    /* initialize required state in plan_state */

}

static void
jarGetForeignPaths(PlannerInfo *root,
                         RelOptInfo *baserel,
                         Oid foreigntableid)
{
    /*
     * Create possible access paths for a scan on a foreign table. This is
     * called during query planning. The parameters are the same as for
     * GetForeignRelSize, which has already been called.
     *
     * This function must generate at least one access path (ForeignPath node)
     * for a scan on the foreign table and must call add_path to add each such
     * path to baserel->pathlist. It's recommended to use
     * create_foreignscan_path to build the ForeignPath nodes. The function
     * can generate multiple access paths, e.g., a path which has valid
     * pathkeys to represent a pre-sorted result. Each access path must
     * contain cost estimates, and can contain any FDW-private information
     * that is needed to identify the specific scan method intended.
     */

    /*
     * JarFdwPlanState *plan_state = baserel->fdw_private;
     */

    Cost        startup_cost,
                total_cost;

    elog(DEBUG1, "entering function %s", __func__);

    startup_cost = 0;
    total_cost = startup_cost + baserel->rows;

    /* Create a ForeignPath node and add it as only possible path */
    add_path(baserel, (Path *)
             create_foreignscan_path(root, baserel,
#if (PG_VERSION_NUM >= 90600)
                                     NULL,      /* default pathtarget */
#endif
                                     baserel->rows,
#if (PG_VERSION_NUM >= 180000)
                                     0,         /* no disabled nodes */
#endif
                                     startup_cost,
                                     total_cost,
                                     NIL,        /* no pathkeys */
                                     NULL,        /* no outer rel either */
#if (PG_VERSION_NUM >= 90500)
                                     NULL,      /* no extra plan */
#endif
#if (PG_VERSION_NUM >= 170000)
                                     NIL, /* no fdw_restrictinfo list */
#endif
                                     NIL));        /* no fdw_private data */
}


#if (PG_VERSION_NUM < 90500)
static ForeignScan *
jarGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *baserel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *scan_clauses)
#else
static ForeignScan *
jarGetForeignPlan(PlannerInfo *root,
                        RelOptInfo *baserel,
                        Oid foreigntableid,
                        ForeignPath *best_path,
                        List *tlist,
                        List *scan_clauses,
                        Plan *outer_plan)
#endif
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
     *
     */

    /*
     * JarFdwPlanState *plan_state = baserel->fdw_private;
     */

    Index        scan_relid = baserel->relid;

    /*
     * We have no native ability to evaluate restriction clauses, so we just
     * put all the scan_clauses into the plan node's qual list for the
     * executor to check. So all we have to do here is strip RestrictInfo
     * nodes from the clauses and ignore pseudoconstants (which will be
     * handled elsewhere).
     */

    elog(DEBUG1, "entering function %s", __func__);

    scan_clauses = extract_actual_clauses(scan_clauses, false);

    /* Create the ForeignScan node */
#if(PG_VERSION_NUM < 90500)
    return make_foreignscan(tlist,
                            scan_clauses,
                            scan_relid,
                            NIL,    /* no expressions to evaluate */
                            NIL);        /* no private state either */
#else
    return make_foreignscan(tlist,
                            scan_clauses,
                            scan_relid,
                            NIL,    /* no expressions to evaluate */
                            NIL,    /* no private state either */
                            NIL,    /* no custom tlist */
                            NIL,    /* no remote quals */
                            outer_plan);
#endif

}

#else

static FdwPlan *
jarPlanForeignScan(Oid foreigntableid, PlannerInfo *root, RelOptInfo *baserel)
{
    FdwPlan    *fdwplan;
    fdwplan = makeNode(FdwPlan);
    fdwplan->fdw_private = NIL;
    fdwplan->startup_cost = 0;
    fdwplan->total_cost = 0;
    return fdwplan;
}

#endif


static void
jarBeginForeignScan(ForeignScanState *node,
                          int eflags)
{
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

    JarFdwScanState * scan_state = palloc0(sizeof(JarFdwScanState));
    node->fdw_state = scan_state;

    elog(DEBUG1, "entering function %s", __func__);

}


static TupleTableSlot *
jarIterateForeignScan(ForeignScanState *node)
{
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


    /* ----
     * JarFdwScanState *scan_state =
     *     (JarFdwScanState *) node->fdw_state;
     * ----
     */

    TupleTableSlot *slot = node->ss.ss_ScanTupleSlot;

    elog(DEBUG1, "entering function %s", __func__);

    ExecClearTuple(slot);

    /* get the next record, if any, and fill in the slot */

    /* then return the slot */
    return slot;
}


static void
jarReScanForeignScan(ForeignScanState *node)
{
    /*
     * Restart the scan from the beginning. Note that any parameters the scan
     * depends on may have changed value, so the new scan does not necessarily
     * return exactly the same rows.
     */

    /* ----
     * JarFdwScanState *scan_state =
     *     (JarFdwScanState *) node->fdw_state;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

}


static void
jarEndForeignScan(ForeignScanState *node)
{
    /*
     * End the scan and release resources. It is normally not important to
     * release palloc'd memory, but for example open files and connections to
     * remote servers should be cleaned up.
     */

    /* ----
     * JarFdwScanState *scan_state =
     *     (JarFdwScanState *) node->fdw_state;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

}


#if (PG_VERSION_NUM >= 90300)
static void
jarAddForeignUpdateTargets(
#if (PG_VERSION_NUM >= 140000)
                                 PlannerInfo *root,
                                 Index rtindex,
#else
                                 Query *parsetree,
#endif
                                 RangeTblEntry *target_rte,
                                 Relation target_relation)
{
    /*
     * UPDATE and DELETE operations are performed against rows previously
     * fetched by the table-scanning functions. The FDW may need extra
     * information, such as a row ID or the values of primary-key columns, to
     * ensure that it can identify the exact row to update or delete. To
     * support that, this function can add extra hidden, or "junk", target
     * columns to the list of columns that are to be retrieved from the
     * foreign table during an UPDATE or DELETE.
     *
     * To do that, add TargetEntry items to parsetree->targetList, containing
     * expressions for the extra values to be fetched. Each such entry must be
     * marked resjunk = true, and must have a distinct resname that will
     * identify it at execution time. Avoid using names matching ctidN or
     * wholerowN, as the core system can generate junk columns of these names.
     *
     * This function is called in the rewriter, not the planner, so the
     * information available is a bit different from that available to the
     * planning routines. parsetree is the parse tree for the UPDATE or DELETE
     * command, while target_rte and target_relation describe the target
     * foreign table.
     *
     * If the AddForeignUpdateTargets pointer is set to NULL, no extra target
     * expressions are added. (This will make it impossible to implement
     * DELETE operations, though UPDATE may still be feasible if the FDW
     * relies on an unchanging primary key to identify rows.)
     */

    elog(DEBUG1, "entering function %s", __func__);

}


static List *
jarPlanForeignModify(PlannerInfo *root,
                           ModifyTable *plan,
                           Index resultRelation,
                           int subplan_index)
{
    /*
     * Perform any additional planning actions needed for an insert, update,
     * or delete on a foreign table. This function generates the FDW-private
     * information that will be attached to the ModifyTable plan node that
     * performs the update action. This private information must have the form
     * of a List, and will be delivered to BeginForeignModify during the
     * execution stage.
     *
     * root is the planner's global information about the query. plan is the
     * ModifyTable plan node, which is complete except for the fdwPrivLists
     * field. resultRelation identifies the target foreign table by its
     * rangetable index. subplan_index identifies which target of the
     * ModifyTable plan node this is, counting from zero; use this if you want
     * to index into plan->plans or other substructure of the plan node.
     *
     * If the PlanForeignModify pointer is set to NULL, no additional
     * plan-time actions are taken, and the fdw_private list delivered to
     * BeginForeignModify will be NIL.
     */

    elog(DEBUG1, "entering function %s", __func__);

    return NULL;
}


static void
jarBeginForeignModify(ModifyTableState *mtstate,
                            ResultRelInfo *rinfo,
                            List *fdw_private,
                            int subplan_index,
                            int eflags)
{
    /*
     * Begin executing a foreign table modification operation. This routine is
     * called during executor startup. It should perform any initialization
     * needed prior to the actual table modifications. Subsequently,
     * ExecForeignInsert, ExecForeignUpdate or ExecForeignDelete will be
     * called for each tuple to be inserted, updated, or deleted.
     *
     * mtstate is the overall state of the ModifyTable plan node being
     * executed; global data about the plan and execution state is available
     * via this structure. rinfo is the ResultRelInfo struct describing the
     * target foreign table. (The ri_FdwState field of ResultRelInfo is
     * available for the FDW to store any private state it needs for this
     * operation.) fdw_private contains the private data generated by
     * PlanForeignModify, if any. subplan_index identifies which target of the
     * ModifyTable plan node this is. eflags contains flag bits describing the
     * executor's operating mode for this plan node.
     *
     * Note that when (eflags & EXEC_FLAG_EXPLAIN_ONLY) is true, this function
     * should not perform any externally-visible actions; it should only do
     * the minimum required to make the node state valid for
     * ExplainForeignModify and EndForeignModify.
     *
     * If the BeginForeignModify pointer is set to NULL, no action is taken
     * during executor startup.
     */

    JarFdwModifyState *modify_state =
        palloc0(sizeof(JarFdwModifyState));
    rinfo->ri_FdwState = modify_state;

    elog(DEBUG1, "entering function %s", __func__);

}


static TupleTableSlot *
jarExecForeignInsert(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot)
{
    /*
     * Insert one tuple into the foreign table. estate is global execution
     * state for the query. rinfo is the ResultRelInfo struct describing the
     * target foreign table. slot contains the tuple to be inserted; it will
     * match the rowtype definition of the foreign table. planSlot contains
     * the tuple that was generated by the ModifyTable plan node's subplan; it
     * differs from slot in possibly containing additional "junk" columns.
     * (The planSlot is typically of little interest for INSERT cases, but is
     * provided for completeness.)
     *
     * The return value is either a slot containing the data that was actually
     * inserted (this might differ from the data supplied, for example as a
     * result of trigger actions), or NULL if no row was actually inserted
     * (again, typically as a result of triggers). The passed-in slot can be
     * re-used for this purpose.
     *
     * The data in the returned slot is used only if the INSERT query has a
     * RETURNING clause. Hence, the FDW could choose to optimize away
     * returning some or all columns depending on the contents of the
     * RETURNING clause. However, some slot must be returned to indicate
     * success, or the query's reported rowcount will be wrong.
     *
     * If the ExecForeignInsert pointer is set to NULL, attempts to insert
     * into the foreign table will fail with an error message.
     *
     */

    /* ----
     * JarFdwModifyState *modify_state =
     *     (JarFdwModifyState *) rinfo->ri_FdwState;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

    return slot;
}


static TupleTableSlot *
jarExecForeignUpdate(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot)
{
    /*
     * Update one tuple in the foreign table. estate is global execution state
     * for the query. rinfo is the ResultRelInfo struct describing the target
     * foreign table. slot contains the new data for the tuple; it will match
     * the rowtype definition of the foreign table. planSlot contains the
     * tuple that was generated by the ModifyTable plan node's subplan; it
     * differs from slot in possibly containing additional "junk" columns. In
     * particular, any junk columns that were requested by
     * AddForeignUpdateTargets will be available from this slot.
     *
     * The return value is either a slot containing the row as it was actually
     * updated (this might differ from the data supplied, for example as a
     * result of trigger actions), or NULL if no row was actually updated
     * (again, typically as a result of triggers). The passed-in slot can be
     * re-used for this purpose.
     *
     * The data in the returned slot is used only if the UPDATE query has a
     * RETURNING clause. Hence, the FDW could choose to optimize away
     * returning some or all columns depending on the contents of the
     * RETURNING clause. However, some slot must be returned to indicate
     * success, or the query's reported rowcount will be wrong.
     *
     * If the ExecForeignUpdate pointer is set to NULL, attempts to update the
     * foreign table will fail with an error message.
     *
     */

    /* ----
     * JarFdwModifyState *modify_state =
     *     (JarFdwModifyState *) rinfo->ri_FdwState;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

    return slot;
}


static TupleTableSlot *
jarExecForeignDelete(EState *estate,
                           ResultRelInfo *rinfo,
                           TupleTableSlot *slot,
                           TupleTableSlot *planSlot)
{
    /*
     * Delete one tuple from the foreign table. estate is global execution
     * state for the query. rinfo is the ResultRelInfo struct describing the
     * target foreign table. slot contains nothing useful upon call, but can
     * be used to hold the returned tuple. planSlot contains the tuple that
     * was generated by the ModifyTable plan node's subplan; in particular, it
     * will carry any junk columns that were requested by
     * AddForeignUpdateTargets. The junk column(s) must be used to identify
     * the tuple to be deleted.
     *
     * The return value is either a slot containing the row that was deleted,
     * or NULL if no row was deleted (typically as a result of triggers). The
     * passed-in slot can be used to hold the tuple to be returned.
     *
     * The data in the returned slot is used only if the DELETE query has a
     * RETURNING clause. Hence, the FDW could choose to optimize away
     * returning some or all columns depending on the contents of the
     * RETURNING clause. However, some slot must be returned to indicate
     * success, or the query's reported rowcount will be wrong.
     *
     * If the ExecForeignDelete pointer is set to NULL, attempts to delete
     * from the foreign table will fail with an error message.
     */

    /* ----
     * JarFdwModifyState *modify_state =
     *     (JarFdwModifyState *) rinfo->ri_FdwState;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

    return slot;
}


static void
jarEndForeignModify(EState *estate,
                          ResultRelInfo *rinfo)
{
    /*
     * End the table update and release resources. It is normally not
     * important to release palloc'd memory, but for example open files and
     * connections to remote servers should be cleaned up.
     *
     * If the EndForeignModify pointer is set to NULL, no action is taken
     * during executor shutdown.
     */

    /* ----
     * JarFdwModifyState *modify_state =
     *     (JarFdwModifyState *) rinfo->ri_FdwState;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

}

static int
jarIsForeignRelUpdatable(Relation rel)
{
    /*
     * Report which update operations the specified foreign table supports.
     * The return value should be a bit mask of rule event numbers indicating
     * which operations are supported by the foreign table, using the CmdType
     * enumeration; that is, (1 << CMD_UPDATE) = 4 for UPDATE, (1 <<
     * CMD_INSERT) = 8 for INSERT, and (1 << CMD_DELETE) = 16 for DELETE.
     *
     * If the IsForeignRelUpdatable pointer is set to NULL, foreign tables are
     * assumed to be insertable, updatable, or deletable if the FDW provides
     * ExecForeignInsert, ExecForeignUpdate, or ExecForeignDelete
     * respectively. This function is only needed if the FDW supports some
     * tables that are updatable and some that are not. (Even then, it's
     * permissible to throw an error in the execution routine instead of
     * checking in this function. However, this function is used to determine
     * updatability for display in the information_schema views.)
     */

    elog(DEBUG1, "entering function %s", __func__);

    return (1 << CMD_UPDATE) | (1 << CMD_INSERT) | (1 << CMD_DELETE);
}
#endif


static void
jarExplainForeignScan(ForeignScanState *node,
                            struct ExplainState * es)
{
    /*
     * Print additional EXPLAIN output for a foreign table scan. This function
     * can call ExplainPropertyText and related functions to add fields to the
     * EXPLAIN output. The flag fields in es can be used to determine what to
     * print, and the state of the ForeignScanState node can be inspected to
     * provide run-time statistics in the EXPLAIN ANALYZE case.
     *
     * If the ExplainForeignScan pointer is set to NULL, no additional
     * information is printed during EXPLAIN.
     */

    elog(DEBUG1, "entering function %s", __func__);

}


#if (PG_VERSION_NUM >= 90300)
static void
jarExplainForeignModify(ModifyTableState *mtstate,
                              ResultRelInfo *rinfo,
                              List *fdw_private,
                              int subplan_index,
                              struct ExplainState * es)
{
    /*
     * Print additional EXPLAIN output for a foreign table update. This
     * function can call ExplainPropertyText and related functions to add
     * fields to the EXPLAIN output. The flag fields in es can be used to
     * determine what to print, and the state of the ModifyTableState node can
     * be inspected to provide run-time statistics in the EXPLAIN ANALYZE
     * case. The first four arguments are the same as for BeginForeignModify.
     *
     * If the ExplainForeignModify pointer is set to NULL, no additional
     * information is printed during EXPLAIN.
     */

    /* ----
     * JarFdwModifyState *modify_state =
     *     (JarFdwModifyState *) rinfo->ri_FdwState;
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

}
#endif


#if (PG_VERSION_NUM >= 90200)
static bool
jarAnalyzeForeignTable(Relation relation,
                             AcquireSampleRowsFunc *func,
                             BlockNumber *totalpages)
{
    /* ----
     * This function is called when ANALYZE is executed on a foreign table. If
     * the FDW can collect statistics for this foreign table, it should return
     * true, and provide a pointer to a function that will collect sample rows
     * from the table in func, plus the estimated size of the table in pages
     * in totalpages. Otherwise, return false.
     *
     * If the FDW does not support collecting statistics for any tables, the
     * AnalyzeForeignTable pointer can be set to NULL.
     *
     * If provided, the sample collection function must have the signature:
     *
     *      int
     *      AcquireSampleRowsFunc (Relation relation, int elevel,
     *                             HeapTuple *rows, int targrows,
     *                             double *totalrows,
     *                             double *totaldeadrows);
     *
     * A random sample of up to targrows rows should be collected from the
     * table and stored into the caller-provided rows array. The actual number
     * of rows collected must be returned. In addition, store estimates of the
     * total numbers of live and dead rows in the table into the output
     * parameters totalrows and totaldeadrows. (Set totaldeadrows to zero if
     * the FDW does not have any concept of dead rows.)
     * ----
     */

    elog(DEBUG1, "entering function %s", __func__);

    return false;
}
#endif


#if (PG_VERSION_NUM >= 90500)
static void
jarGetForeignJoinPaths(PlannerInfo *root,
                             RelOptInfo *joinrel,
                             RelOptInfo *outerrel,
                             RelOptInfo *innerrel,
                             JoinType jointype,
                             JoinPathExtraData *extra)
{
    /*
     * Create possible access paths for a join of two (or more) foreign tables
     * that all belong to the same foreign server. This optional function is
     * called during query planning. As with GetForeignPaths, this function
     * should generate ForeignPath path(s) for the supplied joinrel, and call
     * add_path to add these paths to the set of paths considered for the
     * join. But unlike GetForeignPaths, it is not necessary that this
     * function succeed in creating at least one path, since paths involving
     * local joining are always possible.
     *
     * Note that this function will be invoked repeatedly for the same join
     * relation, with different combinations of inner and outer relations; it
     * is the responsibility of the FDW to minimize duplicated work.
     *
     * If a ForeignPath path is chosen for the join, it will represent the
     * entire join process; paths generated for the component tables and
     * subsidiary joins will not be used. Subsequent processing of the join
     * path proceeds much as it does for a path scanning a single foreign
     * table. One difference is that the scanrelid of the resulting
     * ForeignScan plan node should be set to zero, since there is no single
     * relation that it represents; instead, the fs_relids field of the
     * ForeignScan node represents the set of relations that were joined. (The
     * latter field is set up automatically by the core planner code, and need
     * not be filled by the FDW.) Another difference is that, because the
     * column list for a remote join cannot be found from the system catalogs,
     * the FDW must fill fdw_scan_tlist with an appropriate list of
     * TargetEntry nodes, representing the set of columns it will supply at
     * runtime in the tuples it returns.
     */

    elog(DEBUG1, "entering function %s", __func__);

}


static RowMarkType
jarGetForeignRowMarkType(RangeTblEntry *rte,
                               LockClauseStrength strength)
{
    /*
     * Report which row-marking option to use for a foreign table. rte is the
     * RangeTblEntry node for the table and strength describes the lock
     * strength requested by the relevant FOR UPDATE/SHARE clause, if any. The
     * result must be a member of the RowMarkType enum type.
     *
     * This function is called during query planning for each foreign table
     * that appears in an UPDATE, DELETE, or SELECT FOR UPDATE/SHARE query and
     * is not the target of UPDATE or DELETE.
     *
     * If the GetForeignRowMarkType pointer is set to NULL, the ROW_MARK_COPY
     * option is always used. (This implies that RefetchForeignRow will never
     * be called, so it need not be provided either.)
     */

    elog(DEBUG1, "entering function %s", __func__);

    return ROW_MARK_COPY;

}

#if (PG_VERSION_NUM >= 120000)
static void jarRefetchForeignRow(EState *estate,
                   ExecRowMark *erm,
                   Datum rowid,
                   TupleTableSlot *slot,
                   bool *updated)
#else
static HeapTuple
jarRefetchForeignRow(EState *estate,
                           ExecRowMark *erm,
                           Datum rowid,
                           bool *updated)
#endif
{
    /*
     * Re-fetch one tuple from the foreign table, after locking it if
     * required. estate is global execution state for the query. erm is the
     * ExecRowMark struct describing the target foreign table and the row lock
     * type (if any) to acquire. rowid identifies the tuple to be fetched.
     * updated is an output parameter.
     *
     * This function should return a palloc'ed copy of the fetched tuple, or
     * NULL if the row lock couldn't be obtained. The row lock type to acquire
     * is defined by erm->markType, which is the value previously returned by
     * GetForeignRowMarkType. (ROW_MARK_REFERENCE means to just re-fetch the
     * tuple without acquiring any lock, and ROW_MARK_COPY will never be seen
     * by this routine.)
     *
     * In addition, *updated should be set to true if what was fetched was an
     * updated version of the tuple rather than the same version previously
     * obtained. (If the FDW cannot be sure about this, always returning true
     * is recommended.)
     *
     * Note that by default, failure to acquire a row lock should result in
     * raising an error; a NULL return is only appropriate if the SKIP LOCKED
     * option is specified by erm->waitPolicy.
     *
     * The rowid is the ctid value previously read for the row to be
     * re-fetched. Although the rowid value is passed as a Datum, it can
     * currently only be a tid. The function API is chosen in hopes that it
     * may be possible to allow other datatypes for row IDs in future.
     *
     * If the RefetchForeignRow pointer is set to NULL, attempts to re-fetch
     * rows will fail with an error message.
     */

    elog(DEBUG1, "entering function %s", __func__);

#if (PG_VERSION_NUM < 120000)
    return NULL;
#endif
}


static List *
jarImportForeignSchema(ImportForeignSchemaStmt *stmt,
                             Oid serverOid)
{
    /*
     * Obtain a list of foreign table creation commands. This function is
     * called when executing IMPORT FOREIGN SCHEMA, and is passed the parse
     * tree for that statement, as well as the OID of the foreign server to
     * use. It should return a list of C strings, each of which must contain a
     * CREATE FOREIGN TABLE command. These strings will be parsed and executed
     * by the core server.
     *
     * Within the ImportForeignSchemaStmt struct, remote_schema is the name of
     * the remote schema from which tables are to be imported. list_type
     * identifies how to filter table names: FDW_IMPORT_SCHEMA_ALL means that
     * all tables in the remote schema should be imported (in this case
     * table_list is empty), FDW_IMPORT_SCHEMA_LIMIT_TO means to include only
     * tables listed in table_list, and FDW_IMPORT_SCHEMA_EXCEPT means to
     * exclude the tables listed in table_list. options is a list of options
     * used for the import process. The meanings of the options are up to the
     * FDW. For example, an FDW could use an option to define whether the NOT
     * NULL attributes of columns should be imported. These options need not
     * have anything to do with those supported by the FDW as database object
     * options.
     *
     * The FDW may ignore the local_schema field of the
     * ImportForeignSchemaStmt, because the core server will automatically
     * insert that name into the parsed CREATE FOREIGN TABLE commands.
     *
     * The FDW does not have to concern itself with implementing the filtering
     * specified by list_type and table_list, either, as the core server will
     * automatically skip any returned commands for tables excluded according
     * to those options. However, it's often useful to avoid the work of
     * creating commands for excluded tables in the first place. The function
     * IsImportableForeignTable() may be useful to test whether a given
     * foreign-table name will pass the filter.
     */

    elog(DEBUG1, "entering function %s", __func__);

    return NULL;
}


/*
 * Validate the generic options given to a FOREIGN DATA WRAPPER, SERVER,
 * USER MAPPING or FOREIGN TABLE that uses jar_fdw.
 *
 * Raise an ERROR if the option or its value is considered invalid.
 */
Datum
jar_fdw_validator(PG_FUNCTION_ARGS)
{
    List       *options_list = untransformRelOptions(PG_GETARG_DATUM(0));
    Oid            catalog = PG_GETARG_OID(1);
    char       *filename = NULL;
    // DefElem    *force_not_null = NULL;
    // DefElem    *force_null = NULL;
    List       *other_options = NIL;
    ListCell   *cell;

    /*
     * Check that only options supported by jar_fdw, and allowed for the
     * current object type, are given.
     */
    foreach(cell, options_list)
    {
        DefElem    *def = (DefElem *) lfirst(cell);

        if (!is_valid_option(def->defname, catalog))
        {
            const struct JarFdwOption *opt;
            const char *closest_match;
            ClosestMatchState match_state;
            bool        has_valid_options = false;

            /*
             * Unknown option specified, complain about it. Provide a hint
             * with a valid option that looks similar, if there is one.
             */
            initClosestMatch(&match_state, def->defname, 4);
            for (opt = valid_options; opt->optname; opt++)
            {
                if (catalog == opt->optcontext)
                {
                    has_valid_options = true;
                    updateClosestMatch(&match_state, opt->optname);
                }
            }

            closest_match = getClosestMatch(&match_state);
            ereport(ERROR,
                    (errcode(ERRCODE_FDW_INVALID_OPTION_NAME),
                     errmsg("invalid option \"%s\"", def->defname),
                     has_valid_options ? closest_match ?
                     errhint("Perhaps you meant the option \"%s\".",
                             closest_match) : 0 :
                     errhint("There are no valid options in this context.")));
        }

        /*
         * Separate out filename, program, and column-specific options, since
         * ProcessCopyOptions won't accept them.
         */
        if (strcmp(def->defname, "filename") == 0)
            // || strcmp(def->defname, "program") == 0)
        {
            if (filename)
                ereport(ERROR,
                        (errcode(ERRCODE_SYNTAX_ERROR),
                         errmsg("conflicting or redundant options")));

            /*
             * Check permissions for changing which file or program is used by
             * the jar_fdw.
             *
             * Only members of the role 'pg_read_server_files' are allowed to
             * set the 'filename' option of a jar_fdw foreign table, while
             * only members of the role 'pg_execute_server_program' are
             * allowed to set the 'program' option.  This is because we don't
             * want regular users to be able to control which file gets read
             * or which program gets executed.
             *
             * Putting this sort of permissions check in a validator is a bit
             * of a crock, but there doesn't seem to be any other place that
             * can enforce the check more cleanly.
             *
             * Note that the valid_options[] array disallows setting filename
             * and program at any options level other than foreign table ---
             * otherwise there'd still be a security hole.
             */
            if (strcmp(def->defname, "filename") == 0) {
                if (has_privs_of_role(GetUserId(), ROLE_PG_READ_SERVER_FILES)) {
                    filename = defGetString(def);
                    initialize(filename);
                } else {
                    ereport(ERROR,
                            (errcode(ERRCODE_INSUFFICIENT_PRIVILEGE),
                             errmsg("permission denied to set the \"%s\" option of a jar_fdw foreign table",
                                    "filename"),
                             errdetail("Only roles with privileges of the \"%s\" role may set this option.",
                                       "pg_read_server_files")));
                }
            }

/*
            if (strcmp(def->defname, "program") == 0 &&
                !has_privs_of_role(GetUserId(), ROLE_PG_EXECUTE_SERVER_PROGRAM))
                ereport(ERROR,
                        (errcode(ERRCODE_INSUFFICIENT_PRIVILEGE),
                         errmsg("permission denied to set the \"%s\" option of a jar_fdw foreign table",
                                "program"),
                         errdetail("Only roles with privileges of the \"%s\" role may set this option.",
                                   "pg_execute_server_program")));

            filename = defGetString(def);
*/
        }

        else
            other_options = lappend(other_options, def);
    }

    /*
     * Either filename or program option is required for jar_fdw foreign
     * tables.
     */
    if (catalog == ForeignTableRelationId && filename == NULL)
        ereport(ERROR,
                (errcode(ERRCODE_FDW_DYNAMIC_PARAMETER_VALUE_NEEDED),
                 errmsg("either filename or program is required for jar_fdw foreign tables")));

    PG_RETURN_VOID();
}

// -

/*
 * FOR REFERENCE _
 *
struct zzip_file
{
    struct zzip_dir* dir;
    int fd;
    int method;
    zzip_size_t restlen;
    zzip_size_t crestlen;
    zzip_size_t usize;
    zzip_size_t csize;
    /--  added dataoffset member - data offset from start of zipfile --
    zzip_off_t dataoffset;
    char* buf32k;
    zzip_off_t offset; /-- offset from the start of zipfile... --
    z_stream d_stream;
    zzip_plugin_io_t io;
};

struct zzip_dir_hdr
{
    uint32_t    d_usize;        /-- uncompressed size --
    uint32_t    d_csize;        /-- compressed size --
    uint32_t    d_crc32;        /-- the adler32-checksum --
    uint32_t    d_off;          /-- offset of file in zipfile --
    uint16_t    d_reclen;       /-- next dir_hdr structure offset --
    uint16_t    d_namlen;       /-- explicit namelen of d_name --
    uint8_t     d_compr;        /-- the compression type, 0 = store, 8 = inflate --
    char        d_name[1];      /-- the actual name of the entry, may contain DIRSEPs --
};
#define _ZZIP_DIRENT_HAVE_D_NAMLEN
#define _ZZIP_DIRENT_HAVE_D_OFF
#define _ZZIP_DIRENT_HAVE_D_RECLEN

/*
 * you shall not use this struct anywhere else than in zziplib sources.
 --
struct zzip_dir
{
    int fd;
    int errcode; /-- zzip_error_t --
    long refcount;
    struct { /-- reduce a lot of alloc/deallocations by caching these: --
    int * volatile locked;
        struct zzip_file * volatile fp;
        char * volatile buf32k;
    } cache;
    struct zzip_dir_hdr * hdr0;  /-- zfi; --
    struct zzip_dir_hdr * hdr;   /-- zdp; directory pointer, for dirent stuff --
    struct zzip_file * currentfp; /-- last fp used... --
    struct zzip_dirent dirent;
    void*  realdir;               /-- e.g. DIR* from posix dirent.h --
    char*  realname;
    zzip_strings_t* fileext;      /-- list of fileext to test for --
    zzip_plugin_io_t io;          /-- vtable for io routines --
};

 */

/**
 * ZZIP library
 */
static
void initialize(const char *filename) {
    ZZIP_MEM_DISK* dir = zzip_mem_disk_open (filename);
    // ZZIP_MEM_DISK* zzip_mem_disk_fdopen (int fd);

    // long r = zzip_mem_disk_load (ZZIP_MEM_DISK* dir, ZZIP_DISK* disk);

/*
    ZZIP_MEM_ENTRY* zzip_mem_disk_findfirst(ZZIP_MEM_DISK* dir);
    ZZIP_MEM_ENTRY* zzip_mem_disk_findnext(ZZIP_MEM_DISK* dir, ZZIP_MEM_ENTRY* entry);
    ZZIP_MEM_ENTRY* zzip_mem_entry_findnext(ZZIP_MEM_ENTRY* entry);
*/

    void zzip_mem_disk_unload (dir);

// _zzip_restrict ??
    void zzip_mem_disk_close (dir);

/*
    ZZIP_MEM_DISK* zzip_mem_disk_open (char* filename);
    ZZIP_MEM_DISK* zzip_mem_disk_fdopen (int fd);
    void zzip_mem_disk_close (ZZIP_MEM_DISK* _zzip_restrict dir);

    long zzip_mem_disk_load (ZZIP_MEM_DISK* dir, ZZIP_DISK* disk);
    void zzip_mem_disk_unload (ZZIP_MEM_DISK* dir);

 // ZZIP_EXTRA_BLOCK* zzip_mem_entry_extra_block (ZZIP_MEM_ENTRY* entry, short datatype) ZZIP_GNUC_DEPRECATED;
 // ZZIP_EXTRA_BLOCK* zzip_mem_entry_find_extra_block (ZZIP_MEM_ENTRY* entry, short datatype, zzip_size_t blocksize);

    ZZIP_MEM_ENTRY* zzip_mem_disk_findfirst(ZZIP_MEM_DISK* dir);
    ZZIP_MEM_ENTRY* zzip_mem_disk_findnext(ZZIP_MEM_DISK* dir, ZZIP_MEM_ENTRY* entry);
    ZZIP_MEM_ENTRY* zzip_mem_entry_findnext(ZZIP_MEM_ENTRY* entry);
    ZZIP_MEM_ENTRY* zzip_mem_disk_findfile(ZZIP_MEM_DISK* dir,
                           char* filename, ZZIP_MEM_ENTRY* after,
                   zzip_strcmp_fn_t compare);

    ZZIP_MEM_ENTRY* zzip_mem_disk_findmatch(ZZIP_MEM_DISK* dir,
                            char* filespec, ZZIP_MEM_ENTRY* after,
                zzip_fnmatch_fn_t compare, int flags);

    x zzip_mem_entry_usize(_e_);
    x zzip_mem_entry_csize(_e_);
    x zzip_mem_entry_data_encrypted(_e_);
    x zzip_mem_entry_data_streamed(_e_);
    x zzip_mem_entry_data_comprlevel(_e_);
    x zzip_mem_entry_data_stored(_e_);
    x zzip_mem_entry_data_deflated(_e_);

    ZZIP_MEM_DISK_FILE* zzip_mem_entry_fopen (ZZIP_MEM_DISK* dir, ZZIP_MEM_ENTRY* entry);
    ZZIP_MEM_DISK_FILE* zzip_mem_disk_fopen (ZZIP_MEM_DISK* dir, char* filename);
    _zzip_size_t zzip_mem_disk_fread (void* ptr, _zzip_size_t size, _zzip_size_t nmemb,
                         ZZIP_MEM_DISK_FILE* file);

    int zzip_mem_disk_fclose (ZZIP_MEM_DISK_FILE* file);
    int zzip_mem_disk_feof (ZZIP_MEM_DISK_FILE* file);

    /-- convert dostime of entry to unix time_t --
    long zzip_disk_entry_get_mktime(ZZIP_DISK_ENTRY* entry);
*/

/*
    ZZIP_DIR * zzip_dir_fdopen(int fd, zzip_error_t * errcode_p);

    ZZIP_DIR* zzip_dir_fdopen_ext_io(int fd, zzip_error_t * errorcode_p,
                           zzip_strings_t* ext, const zzip_plugin_io_t io);
    int          zzip_dir_close(ZZIP_DIR * dir);
*/

/*
_zzip_export
int             zzip_dir_read(ZZIP_DIR * dir, ZZIP_DIRENT * dirent);

_zzip_export
ZZIP_DIR *     zzip_opendir(zzip_char_t* filename);
_zzip_export
int              zzip_closedir(ZZIP_DIR * dir);
_zzip_export
ZZIP_DIRENT *     zzip_readdir(ZZIP_DIR * dir);
_zzip_export
void          zzip_rewinddir(ZZIP_DIR * dir);
_zzip_export
zzip_off_t      zzip_telldir(ZZIP_DIR * dir);
_zzip_export
void         zzip_seekdir(ZZIP_DIR * dir, zzip_off_t offset);

*/

/*
 * the stdc variant to open/read/close files. - Take note of the freopen()
 * call as it may reuse an existing preparsed copy of a zip central directory
 *
_zzip_export
ZZIP_FILE*      zzip_freopen(zzip_char_t* name, zzip_char_t* mode, ZZIP_FILE*);
_zzip_export
ZZIP_FILE*      zzip_fopen(zzip_char_t* name, zzip_char_t* mode);
_zzip_export
zzip_size_t     zzip_fread(void *ptr, zzip_size_t size, zzip_size_t nmemb, 
               ZZIP_FILE * file);
_zzip_export
int          zzip_fclose(ZZIP_FILE * fp);
*/


/*
 * reading info of a single file
 * zzip/stat.c
 *
_zzip_export
int        zzip_dir_stat(ZZIP_DIR * dir, zzip_char_t* name,
                  ZZIP_STAT * zs, int flags);
_zzip_export
int        zzip_file_stat(ZZIP_FILE * fp, ZZIP_STAT * zs);
_zzip_export
int        zzip_fstat(ZZIP_FILE * fp, ZZIP_STAT * zs);
*/
}

