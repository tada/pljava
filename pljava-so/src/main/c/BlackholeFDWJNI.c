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
#include "commands/explain.h"
#include "foreign/fdwapi.h"
#include "foreign/foreign.h"
#include "optimizer/pathnode.h"
#include "optimizer/planmain.h"
#include "optimizer/restrictinfo.h"

#include "pljava/pljava.h"
#include "pljava/FDW.h"

// PG_MODULE_MAGIC;

#if (PG_VERSION_NUM < 90500)
// fail...
#endif

/* ------------------------------------------------------------ */

// had been 'JNI_FDW.h'
#ifndef NEVER

/**
 * Wrapper functions
 */

/*
 * Persistent
 */
struct {
    // void (*addOption)(JNI_FDW_Validator *, int, const char *, const char *) = validator_add_option;
    // bool (*validate)(JNI_FDW_Validator *);

    const JNIEnv *env;
    const jclass validatorClass;
    const jobject instance;
} JNI_FDW_Validator_;

/*
 * Persistent
 */
struct {
    // JNI_FDW_validate_options_for_reuse = fdw_validate_options_for_reuse;

    JNIEnv *env;
    jclass wrapperClass;
    jobject *instance;
} JNI_FDW_Wrapper_;

/*
 * Persistent
 */
struct {
    // JNI_FDW_validate_options_for_reuse = srv_validate_options_for_reuse;
    // void* (*getMetadata)(JNI_FDW_Server *server);

    JNIEnv *env;
    jclass serverClass;
    jobject *instance;
} JNI_FDW_Server_;

/*
 * Persistent
 */
struct {
    // JNI_FDW_validate_options_for_reuse = srv_validate_options_for_reuse;

    JNIEnv *env;
    jclass serverClass;
    jobject *instance;
} JNI_FDW_User_;

/*
 * Persistent
 */
struct {
    // JNI_FDW_validate_options_for_reuse = ft_validate_options_for_reuse;

    // void* (*newPlanState)(JNI_FDW_Table *table, JNI_FDW_User *user);
    // void *(*newScanState)(JNI_FDW_Table *table, JNI_FDW_User *user);

    // void *(getMetaData)(JNI_FDW_Table *table JNI_FDW_User *user);

    // bool (*updateable)(JNI_FDW_Table *table, JNI_FDW_User *user);
    // bool (*supportsConcurrency)(JNI_FDW_Table *table);
    // bool (*supportsAsyncOperations)(JNI_FDW_Table *table);

    // void (*analyze)(JNI_FDW_Table *table);
    // void (*vacuum)(JNI_FDW_Table *table);

    JNIEnv *env;
    jclass tableClass;
    jobject instance;
} JNI_FDW_Table_;

/*
 * Temporary
 */
struct {
    // void (*open)(JNI_FDW_PlanState *planState, PlannerInfo *root, RelOptInfo *baserel, Oid foregntableid);
    // void (*open)(JNI_FDW_PlanState *planState);
    // void (*close)(JNI_FDW_PlanState *planState);

    JNIEnv *env;
    // jclass planStateClass; ?
    jobject *instance;
    jlong rows;

   	// cached values?
    jdouble cost;
    jdouble startup_cost;
    jdouble totalcost;
} JNI_FDW_PlanState_;

/*
 * Temporary
 */
struct {
    // void (*open)(JNI_FDW_ScanState *scanState, ForeignScanState *node, int eflags) = open_scan;
    // void (*next)(JNI_FDW_ScanState *scanState, TableTupleSlot *slot);
    // void (*reset)(JNI_FDW_ScanState *scanState);
    // void (*close)(JNI_FDW_ScanState *scanState);
    // void (*explain)(JNI_FDW_ScanState *scanState);

    JNIEnv *env;
    jobject *instance;
} JNI_FDW_ScanState_;
#endif

/* ------------------------------------------------------------ */

/*

static JNI_FDW_Wrapper *validator_get_wrapper(JNI_FDW_Validator *validator);
static JNI_FDW_Server *wrapper_get_server(JNI_FDW_Wrapper *wrapper);
static JNI_FDW_Table *server_get_table(JNI_FDW_Server *server);
static JNI_FDW_PlanState *table_new_plan(JNI_FDW_Table *table);
static JNI_FDW_ScanState *table_new_scan(JNI_FDW_Table *table);

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
 */

// Note: this does not do memory management yet!

/*
static
jmethodID getMethodID(JNIEnv *env, jclass class, ...)
{
    return env->GetMethodID(class, va_arg);
}
*/

#ifdef USE_JAVA
JNI_FDW_Validator newValidator(JNIEnv *env, const char *validator_classname) {
    JNI_FDW_Validator *validator = (JNI_FDW_Validator *) palloc0(sizeof JNI_FDW_Validator);

    validator->env = env;
    validator->validatorClass = env->FindClass(validator_classname);
    validator->instance = env->AllocObject(fdw->validatorClass);

    return validator;
}

static
JNI_FDW_PlanState *table_new_planstate(JNI_FDW_Table *table) {
    const JNIEnv *env = table->env;

    jmethodID openPlanMethodId = env->GetMethodID(table->tableClass, "newPlanState",
                                                  "(V)[org.postgresql.pljava.fdw.PlanState;");

    const JNI_FDW_PlanState *planState = (JNI_FDW_PlanState *) palloc0(sizeof JNI_FDW_PlanState);
    planState->env = env;
    // table->planStateClass = env->FindClass(planstate_classname);
    planState->instance = env->CallObjectMethod(table->instance, newPlanStateMethodId);
    planState->table = table;
}

static
void *plan_open(JNI_FDW_PlanState *planState, PlannerInfo *root, RelOptInfo *baserel, Oid foreigntableid) {
    const ForeignTable foreignTable = GetForeignTable(foreigntableid);

    const JNIEnv *env = table->env;

    // FIXME: for now we don't pass anything through. However we could after a bit of conversions...
    const jmethodId openPlanMethodId = env->GetMethodID(planState->planStateClass, "open", "(V)V");
    env->CallObjectMethod(planState->instance, openPlanStateMethodId);
}
#endif

#ifdef USE_JAVA
JNIEXPORT
JNI_FDW_ScanState * JNICALL table_new_scan_state(JNI_FDW_Table *table, ForeignScanState *node, int eflag) {
    const JNIEnv *env = table->env;

    // for now we ignore the extra parameters.
    const jmethodID newScanStateMethodId = env->GetMethodID(table->tableClass, "newScanState",
                                                      "(V)[org.postgresql.pljava.fdw.ScanState;");

    const JNI_FDW_ScanState *scanState = (JNI_FDW_ScanState *) palloc0(sizeof JNI_FDW_ScanState);
    scanState->env = env;

    // for now we ignore the extra parameters.
    scanState->instance = env->CallObjectMethod(table->instance, newScanStateMethodId);
    scanState->table = table;

    return planState;
}
#endif

/* ------------------------------------------------------------
 * Rest of JNI Implementation - does not use memory management yet!
 * ------------------------------------------------------------*/
#ifdef USE_JAVA
static void validator_add_option(JNI_FDW_Validator *validator, int relid, String key, String value)
{
    const JNIEnv *env = validator->env;
    const jmethodID addOptionMethodId = env->GetMethodID(validator->validatorClass, "addOption", "(int, String, String)V");

    const jint jrelid = NULL;
    const jstring jkey = NULL;
    const jstring jvalue = NULL;

    env->CallObjectMethod(validator->instance, addOptionMethodId, jrelid, jkey, jvalue);
}
#endif