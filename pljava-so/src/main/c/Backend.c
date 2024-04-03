/*
 * Copyright (c) 2004-2024 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB - Thomas Hallgren
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
#include <postgres.h>
#include <miscadmin.h>
#ifndef WIN32
#include <libpq/pqsignal.h>
#endif
#include <executor/spi.h>
#include <commands/trigger.h>
#include <utils/elog.h>
#include <utils/guc.h>
#include <fmgr.h>
#include <access/heapam.h>
#include <utils/syscache.h>
#include <utils/timeout.h>
#include <catalog/catalog.h>
#include <catalog/pg_proc.h>
#include <catalog/pg_type.h>

#if PG_VERSION_NUM >= 120000
 #if defined(HAVE_DLOPEN)  ||  PG_VERSION_NUM >= 160000 && ! defined(WIN32)
 #include <dlfcn.h>
 #endif
 #define pg_dlopen(f) dlopen((f), RTLD_NOW | RTLD_GLOBAL)
 #define pg_dlsym(h,s) dlsym((h), (s))
 #define pg_dlclose(h) dlclose((h))
 #define pg_dlerror() dlerror()
#else
 #include <dynloader.h>
#endif

#include <storage/ipc.h>
#include <storage/proc.h>
#include <stdio.h>
#include <ctype.h>
#include <unistd.h>

#include "org_postgresql_pljava_internal_Backend.h"
#include "org_postgresql_pljava_internal_Backend_EarlyNatives.h"
#include "pljava/DualState.h"
#include "pljava/Invocation.h"
#include "pljava/InstallHelper.h"
#include "pljava/Function.h"
#include "pljava/HashMap.h"
#include "pljava/Exception.h"
#include "pljava/Backend.h"
#include "pljava/Session.h"
#include "pljava/SPI.h"
#include "pljava/type/String.h"

/* Include the 'magic block' that PostgreSQL 8.2 and up will use to ensure
 * that a module is not loaded into an incompatible server.
 */ 
#ifdef PG_MODULE_MAGIC
PG_MODULE_MAGIC;
#endif

/* About PGDLLEXPORT. It didn't exist before PG 9.0. In that revision it was
 * defined (for Windows) as __declspec(dllexport) for MSVC, but as
 * __declspec(dllimport) for any other toolchain. That was quickly changed
 * (for 9.0.2 and ever since) as still __declspec(dllexport) for MSVC, but
 * empty for any other toolchain. The explanation for that (in PG commit
 * 844ed5d in November 2010) was that "dllexport and dllwrap don't work well
 * together." There are records as far back as 2002 anyway
 * (e.g. http://lists.gnu.org/archive/html/libtool/2002-09/msg00069.html)
 * calling dllwrap deprecated, PL/Java's Maven build certainly doesn't
 * use it, and I don't know what it would do if it did. It seems too brittle
 * to rely on whatever PGDLLEXPORT might happen to mean across PG versions,
 * and wiser for the moment to cleanly define something here, for the all of
 * three(*) symbols that need it.
 *
 * The only case where it expands to anything is when building with Microsoft
 * Visual Studio. When building with other toolchains it just goes away, even
 * on Windows when building with MinGW (the only other Windows toolchain
 * tested). MinGW can work either way: selectively exporting things based on
 * a __declspec, or with the --export-all-symbols linker option so everything
 * is visible, as on a *n*x platform. PL/Java could in theory choose either
 * approach, but for one detail: there is a (*)fourth symbol that needs to be
 * exported. PG_MODULE_MAGIC defines one, and being a PostgreSQL-supplied macro,
 * it uses PGDLLEXPORT, which expands to nothing for MinGW (in recent PG
 * versions anyway), forcing --export-all-symbols as the answer for MinGW.
 */
#ifdef _MSC_VER
#define PLJAVADLLEXPORT __declspec (dllexport)
#else
#define PLJAVADLLEXPORT
#endif

extern PLJAVADLLEXPORT void _PG_init(void);

#define LOCAL_REFERENCE_COUNT 128

MemoryContext JavaMemoryContext;

static JavaVM* s_javaVM = 0;
static jclass  s_Backend_class;
static bool    s_startingVM;

/*
 * GUC states
 */
static char* libjvmlocation;
static char* vmoptions;
static char* modulepath;
static char* implementors;
static char* policy_urls;
static int   statementCacheSize;
static bool  pljavaDebug;
static bool  pljavaReleaseLingeringSavepoints;
static bool  pljavaEnabled;

static int   java_thread_pg_entry;

static int   s_javaLogLevel;

#if PG_VERSION_NUM < 100000
bool integerDateTimes = false;
static void checkIntTimeType(void);
#endif

static char s_path_var_sep;

extern void Invocation_initialize(void);
extern void Exception_initialize(void);
extern void Exception_initialize2(void);
extern void HashMap_initialize(void);
extern void SPI_initialize(void);
extern void Type_initialize(void);
extern void Function_initialize(void);
extern void Session_initialize(void);
extern void PgSavepoint_initialize(void);
extern void XactListener_initialize(void);
extern void SubXactListener_initialize(void);
extern void SQLInputFromChunk_initialize(void);
extern void SQLOutputToChunk_initialize(void);
extern void SQLOutputToTuple_initialize(void);


typedef struct {
	JavaVMOption* options;
	unsigned int  size;
	unsigned int  capacity;
} JVMOptList;

static void registerGUCOptions(void);
static jint initializeJavaVM(JVMOptList*);
static void JVMOptList_init(JVMOptList*);
static void JVMOptList_delete(JVMOptList*);
static void JVMOptList_add(JVMOptList*, const char*, void*, bool);
static void JVMOptList_addVisualVMName(JVMOptList*);
static void JVMOptList_addModuleMain(JVMOptList*);
static void addUserJVMOptions(JVMOptList*);
static char* getModulePath(const char*);
static jint JNICALL my_vfprintf(FILE*, const char*, va_list)
	pg_attribute_printf(2, 0);
static void _destroyJavaVM(int, Datum);
static void initPLJavaClasses(void);
static void initJavaSession(void);
static void reLogWithChangedLevel(int);

#ifndef WIN32
#define USE_PLJAVA_SIGHANDLERS
#endif

#ifdef USE_PLJAVA_SIGHANDLERS
static void pljavaStatementCancelHandler(int);
static void pljavaDieHandler(int);
static void pljavaQuickDieHandler(int);
#endif

enum initstage
{
	IS_FORMLESS_VOID,
	IS_GUCS_REGISTERED,
	IS_CAND_JVMLOCATION,
	IS_CAND_POLICYURLS,
	IS_PLJAVA_ENABLED,
	IS_CAND_JVMOPENED,
	IS_CREATEVM_SYM_FOUND,
	IS_MISC_ONCE_DONE,
	IS_JAVAVM_OPTLIST,
	IS_JAVAVM_STARTED,
	IS_SIGHANDLERS,
	IS_PLJAVA_FOUND,
	IS_PLJAVA_INSTALLING,
	IS_COMPLETE
};

static enum initstage initstage = IS_FORMLESS_VOID;
static void *libjvm_handle;
static bool jvmStartedAtLeastOnce = false;
static bool alteredSettingsWereNeeded = false;
static bool loadAsExtensionFailed = false;
static bool seenVisualVMName;
static bool seenModuleMain;
static char const visualVMprefix[] = "-Dvisualvm.display.name=";
static char const moduleMainPrefix[] = "-Djdk.module.main=";
static char const policyUrlsGUC[] = "pljava.policy_urls";

/*
 * In a background worker, _PG_init may be called very early, before much of
 * the state needed during PL/Java initialization has even been set up. When
 * that case is detected, initsequencer needs to go just as far as
 * IS_GUCS_REGISTERED and then bail. The GUC assign hooks may then also be
 * invoked as GUC values get copied from the lead process; they also need to
 * return quickly (accomplished by checking this flag in ASSIGNRETURNIFNXACT).
 * Further initialization is thus deferred until the first actual call arrives
 * at the call handler, which resets this flag and rejoins the initsequencer.
 * The same lazy approach needs to be followed during a pg_upgrade (which test-
 * loads libraries, thus calling _PG_init). This flag is set for either case.
 */
static bool deferInit = false;

/*
 * Whether Backend_warnJEP411() should emit a warning when called.
 * Initially true, because it may be called very early from the deferInit check,
 * if pg_upgrade is happening, and should always warn in that case. Thereafter
 * false, unless set true in the initsequencer because InstallHelper_groundwork
 * will be called (PL/Java being installed or upgraded), or in the validator
 * handler because a PL/Java function has been declared or redeclared.
 */
static bool warnJEP411 = true;

/*
 * Don't bother with the warning unless the JVM in use is later than Java 11.
 * 11 is the LTS release prior to the one where JEP 411 gets interesting (17).
 * If a site is sticking to LTS releases, there will be plenty of time to warn
 * on 17. If a site moves with non-LTS releases, start warning as soon as
 * anything > 11 is used.
 *
 * Initially true, so there will be a warning unconditionally in a case
 * (pg_upgrade) where a JVM hasn't been launched to learn its version).
 */
static bool javaGT11 = true;
static bool javaGE17 = false;

static void initsequencer(enum initstage is, bool tolerant);

static bool check_libjvm_location(
	char **newval, void **extra, GucSource source);
static bool check_vmoptions(
	char **newval, void **extra, GucSource source);
static bool check_modulepath(
	char **newval, void **extra, GucSource source);
static bool check_policy_urls(
	char **newval, void **extra, GucSource source);
static bool check_enabled(
	bool *newval, void **extra, GucSource source);
static bool check_java_thread_pg_entry(
	int *newval, void **extra, GucSource source);

/* Check hooks will always allow "setting" a value that is the same as
 * current; otherwise, it would be frustrating to have just found settings
 * that work, and be unable to save them with ALTER DATABASE SET ... because
 * the check hook is called for that too, and would say it is too late....
 */

static bool check_libjvm_location(
	char **newval, void **extra, GucSource source)
{
	if ( initstage < IS_CAND_JVMOPENED )
		return true;
	if ( libjvmlocation == *newval )
		return true;
	if ( libjvmlocation && *newval && 0 == strcmp(libjvmlocation, *newval) )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.libjvm_location\" setting");
	GUC_check_errdetail(
		"Changing the setting can have no effect after "
		"PL/Java has found and opened the library it points to.");
	GUC_check_errhint(
		"To try a different value, exit this session and start a new one.");
	return false;
}

static bool check_vmoptions(
	char **newval, void **extra, GucSource source)
{
	if ( initstage < IS_JAVAVM_OPTLIST )
		return true;
	if ( vmoptions == *newval )
		return true;
	if ( vmoptions && *newval && 0 == strcmp(vmoptions, *newval) )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.vmoptions\" setting");
	GUC_check_errdetail(
		"Changing the setting can have no effect after "
		"PL/Java has started the Java virtual machine.");
	GUC_check_errhint(
		"To try a different value, exit this session and start a new one.");
	return false;
}

static bool check_modulepath(
	char **newval, void **extra, GucSource source)
{
	if ( initstage < IS_JAVAVM_OPTLIST )
		return true;
	if ( modulepath == *newval )
		return true;
	if ( modulepath && *newval && 0 == strcmp(modulepath, *newval) )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.module_path\" setting");
	GUC_check_errdetail(
		"Changing the setting has no effect after "
		"PL/Java has started the Java virtual machine.");
	GUC_check_errhint(
		"To try a different value, exit this session and start a new one.");
	return false;
}

static bool check_policy_urls(
	char **newval, void **extra, GucSource source)
{
	if ( initstage < IS_JAVAVM_OPTLIST )
		return true;
	if ( policy_urls == *newval )
		return true;
	if ( policy_urls && *newval && 0 == strcmp(policy_urls, *newval) )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.policy_urls\" setting");
	GUC_check_errdetail(
		"Changing the setting has no effect after "
		"PL/Java has started the Java virtual machine.");
	GUC_check_errhint(
		"To try a different value, exit this session and start a new one.");
	return false;
}

static bool check_enabled(
	bool *newval, void **extra, GucSource source)
{
	if ( initstage < IS_PLJAVA_ENABLED )
		return true;
	if ( *newval )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.enable\" setting");
	GUC_check_errdetail(
		"Start-up has progressed past the point where it is checked.");
	GUC_check_errhint(
		"For another chance, exit this session and start a new one.");
	return false;
}

static bool check_java_thread_pg_entry(
	int *newval, void **extra, GucSource source)
{
	if ( initstage < IS_PLJAVA_FOUND )
		return true;
	if ( java_thread_pg_entry == *newval )
		return true;
	GUC_check_errmsg(
		"too late to change \"pljava.java_thread_pg_entry\" setting");
	GUC_check_errdetail(
		"Start-up has progressed past the point where it is checked.");
	GUC_check_errhint(
		"For another chance, exit this session and start a new one.");
	return false;
}

#define ASSIGNHOOK(name,type) \
	static void \
	CppConcat(assign_,name)(type newval, void *extra); \
	static void \
	CppConcat(assign_,name)(type newval, void *extra)
#define ASSIGNRETURN(thing)
#define ASSIGNRETURNIFCHECK(thing)
#define ASSIGNRETURNIFNXACT(thing) \
	if (! deferInit && pljavaViableXact()) ; else return
#define ASSIGNSTRINGHOOK(name) ASSIGNHOOK(name, const char *)

#define ASSIGNENUMHOOK(name) ASSIGNHOOK(name,int)
#define ENUMBOOTVAL(entry) ((entry).val)
#define ENUMHOOKRET true

static const struct config_enum_entry java_thread_pg_entry_options[] = {
	{"allow", 0, false}, /* numeric value is bit-coded: */
	{"error", 1, false}, /* 1: C code should refuse JNI calls on wrong thread */
	                     /* 2: C code shouldn't call MonitorEnter/MonitorExit */
	{"block", 3, false}, /* (3: check thread AND skip MonitorEnter/Exit)      */
	                     /* 4: *Java* code should refuse wrong-thread calls   */
	{"throw", 6, false}, /* (6: check in Java AND skip C MonitorEnter/Exit)   */
	{NULL, 0, false}
};

ASSIGNSTRINGHOOK(libjvm_location)
{
	ASSIGNRETURNIFCHECK(newval);
	libjvmlocation = (char *)newval;
	if ( IS_FORMLESS_VOID < initstage && initstage < IS_CAND_JVMOPENED )
	{
		ASSIGNRETURNIFNXACT(newval);
		alteredSettingsWereNeeded = true;
		initsequencer( initstage, true);
	}
	ASSIGNRETURN(newval);
}

ASSIGNSTRINGHOOK(vmoptions)
{
	ASSIGNRETURNIFCHECK(newval);
	vmoptions = (char *)newval;
	if ( IS_FORMLESS_VOID < initstage && initstage < IS_JAVAVM_OPTLIST )
	{
		ASSIGNRETURNIFNXACT(newval);
		alteredSettingsWereNeeded = true;
		initsequencer( initstage, true);
	}
	ASSIGNRETURN(newval);
}

ASSIGNSTRINGHOOK(modulepath)
{
	ASSIGNRETURNIFCHECK(newval);
	modulepath = (char *)newval;
	if ( IS_FORMLESS_VOID < initstage && initstage < IS_JAVAVM_OPTLIST )
	{
		ASSIGNRETURNIFNXACT(newval);
		alteredSettingsWereNeeded = true;
		initsequencer( initstage, true);
	}
	ASSIGNRETURN(newval);
}

ASSIGNSTRINGHOOK(policy_urls)
{
	ASSIGNRETURNIFCHECK(newval);
	policy_urls = (char *)newval;
	if ( IS_FORMLESS_VOID < initstage && initstage < IS_JAVAVM_OPTLIST )
	{
		alteredSettingsWereNeeded = true;
		ASSIGNRETURNIFNXACT(newval);
		initsequencer( initstage, true);
	}
	ASSIGNRETURN(newval);
}

ASSIGNHOOK(enabled, bool)
{
	ASSIGNRETURNIFCHECK(true);
	pljavaEnabled = newval;
	if ( IS_FORMLESS_VOID < initstage && initstage < IS_PLJAVA_ENABLED )
	{
		ASSIGNRETURNIFNXACT(true);
		alteredSettingsWereNeeded = true;
		initsequencer( initstage, true);
	}
	ASSIGNRETURN(true);
}

ASSIGNENUMHOOK(java_thread_pg_entry)
{
	int val = newval;
	ASSIGNRETURNIFCHECK(ENUMHOOKRET);
	pljava_JNI_setThreadPolicy( !!(val&1) /*error*/, !(val&2) /*monitorops*/);
	ASSIGNRETURN(ENUMHOOKRET);
}

/*
 * There are a few ways to arrive in the initsequencer.
 * 1. From _PG_init (called exactly once when the library is loaded for ANY
 *    reason).
 *    1a. Because of the command LOAD 'libraryname';
 *        This case can be distinguished because _PG_init will have found the
 *        LOAD command and saved the 'libraryname' in pljavaLoadPath.
 *    1b. Because of a CREATE FUNCTION naming this library. pljavaLoadPath will
 *        be NULL.
 *    1c. By the first actual use of a PL/Java function, causing this library
 *        to be loaded. pljavaLoadPath will be NULL. The called function's Oid
 *        will be available to the call handler once we return from _PG_init,
 *        but it isn't (easily) available here.
 * 2. From the call handler, if initialization isn't complete yet. That can only
 *    mean something failed in the earlier call to _PG_init, and whatever it was
 *    is highly likely to fail again. That may lead to the untidyness of
 *    duplicated diagnostic messages, but for now I like the belt-and-suspenders
 *    approach of making sure the init sequence gets as many chances as possible
 *    to succeed.
 * 3. From a GUC assign hook, if the user has updated a setting that might allow
 *    initialization to succeed. It resumes from where it left off.
 * 4. From the validator handler, if initialization isn't complete yet. That
 *    will definitely happen during pg_upgrade, which is a case where deferInit
 *    will have been set. The validator will then clear deferInit and try to get
 *    further in the init sequence. Importantly, pg_upgrade also sets
 *    check_function_bodies false, which limits the validator's work to a syntax
 *    check of the AS string. The validator therefore will not need to obtain a
 *    schemaLoader or do anything else that requires the sqlj schema to be fully
 *    populated (as, during pg_upgrade, it may not yet be). However, the
 *    validator handler must avoid any action that sets pljavaLoadPath, as a
 *    non-NULL value there would be treated below as case 1a, and trigger an
 *    attempt to set up the sqlj schema.
 *
 * In all cases, the sequence must progress as far as starting the VM and
 * initializing the PL/Java classes. In all cases except 1a, that's enough,
 * assuming the language handlers and schema have all been set up already (or,
 * in case 1b, the user is intent on setting them up explicitly).
 *
 * In case 1a, we can go ahead and test for, and create, the schema, functions,
 * and language entries as needed, using pljavaLoadPath as the library path
 * if creating the language handler functions. One-stop shopping. (The presence
 * of pljavaLoadPath in any of the other cases, such as resumption by an assign
 * hook, indicates it is really a continuation of case 1a.)
 */
static void initsequencer(enum initstage is, bool tolerant)
{
	JVMOptList optList;
	Invocation ctx;
	jint JNIresult;
	char *greeting;

	switch (is)
	{
	case IS_FORMLESS_VOID:
		registerGUCOptions();
		initstage = IS_GUCS_REGISTERED;
		if ( deferInit )
			return;
		warnJEP411 = false;
		/*FALLTHROUGH*/

	case IS_GUCS_REGISTERED:
		if ( NULL == libjvmlocation )
		{
			ereport(WARNING, (
				errmsg("Java virtual machine not yet loaded"),
				errdetail("location of libjvm is not configured"),
				errhint("SET pljava.libjvm_location TO the correct "
						"path to the jvm library (libjvm.so or jvm.dll, etc.)")));
			goto check_tolerant;
		}
		initstage = IS_CAND_JVMLOCATION;
		/*FALLTHROUGH*/

	case IS_CAND_JVMLOCATION:
		if ( NULL == policy_urls )
		{
			ereport(WARNING, (
				errmsg("Java virtual machine not yet loaded"),
				errdetail("Java policy URL(s) not configured"),
				errhint("SET pljava.policy_urls TO the security policy "
						"files PL/Java is to use.")));
			goto check_tolerant;
		}
		initstage = IS_CAND_POLICYURLS;
		/*FALLTHROUGH*/

	case IS_CAND_POLICYURLS:
		if ( ! pljavaEnabled )
		{
			ereport(WARNING, (
				errmsg("Java virtual machine not yet loaded"),
				errdetail(
					"Pausing because \"pljava.enable\" is set \"off\". "),
				errhint(
					"After changing any other settings as necessary, set it "
					"\"on\" to proceed.")));
			goto check_tolerant;
		}
		initstage = IS_PLJAVA_ENABLED;
		/*FALLTHROUGH*/

	case IS_PLJAVA_ENABLED:
		libjvm_handle = pg_dlopen(libjvmlocation);
		if ( NULL == libjvm_handle )
		{
			ereport(WARNING, (
				errmsg("Java virtual machine not yet loaded"),
				errdetail("%s", (char *)pg_dlerror()),
				errhint("SET pljava.libjvm_location TO the correct "
						"path to the jvm library (libjvm.so or jvm.dll, etc.)")));
			goto check_tolerant;
		}
		initstage = IS_CAND_JVMOPENED;
		/*FALLTHROUGH*/

	case IS_CAND_JVMOPENED:
		pljava_createvm =
			(jint (JNICALL *)(JavaVM **, void **, void *))
			pg_dlsym(libjvm_handle, "JNI_CreateJavaVM");
		if ( NULL == pljava_createvm )
		{
			/*
			 * If it hasn't got the symbol, it can't be the right
			 * library, so close/unload it so another can be tried.
			 * Format the dlerror string first: dlclose may clobber it.
			 */
			char *dle = MemoryContextStrdup(ErrorContext, pg_dlerror());
			pg_dlclose(libjvm_handle);
			initstage = IS_CAND_JVMLOCATION;
			ereport(WARNING, (
				errmsg("Java virtual machine not yet started"),
				errdetail("%s", dle),
				errhint("Is the file named in \"pljava.libjvm_location\" "
						"the right one?")));
			goto check_tolerant;
		}
		initstage = IS_CREATEVM_SYM_FOUND;
		/*FALLTHROUGH*/

	case IS_CREATEVM_SYM_FOUND:
		s_javaLogLevel = INFO;
#if PG_VERSION_NUM < 100000
		checkIntTimeType();
#endif
		HashMap_initialize(); /* creates things in TopMemoryContext */
#ifdef PLJAVA_DEBUG
		/* Hard setting for debug. Don't forget to recompile...
		 */
		pljavaDebug = 1;
#endif
		initstage = IS_MISC_ONCE_DONE;
		/*FALLTHROUGH*/

	case IS_MISC_ONCE_DONE:
		JVMOptList_init(&optList); /* uses CurrentMemoryContext */
		seenVisualVMName = false;
		seenModuleMain = false;
		addUserJVMOptions(&optList);
		if ( ! seenVisualVMName )
			JVMOptList_addVisualVMName(&optList);
		if ( ! seenModuleMain )
			JVMOptList_addModuleMain(&optList);
		JVMOptList_add(&optList, "vfprintf", (void*)my_vfprintf, true);
#ifndef GCJ
		JVMOptList_add(&optList, "-Xrs", 0, true);
#endif
		effectiveModulePath = getModulePath("--module-path=");
		if(effectiveModulePath != 0)
		{
			JVMOptList_add(&optList, effectiveModulePath, 0, true);
		}
		initstage = IS_JAVAVM_OPTLIST;
		/*FALLTHROUGH*/

	case IS_JAVAVM_OPTLIST:
		/* Register an on_proc_exit handler that destroys the VM if it has
		 * been started. It will also log a last-ditch message if the VM happens
		 * to rudely call exit() rather than returning a non-OK result.
		 */
		on_proc_exit(_destroyJavaVM, 0);
		s_startingVM = true;
		JNIresult = initializeJavaVM(&optList); /* frees the optList */
		s_startingVM = false;
		if( JNI_OK != JNIresult )
		{
			initstage = IS_MISC_ONCE_DONE; /* optList has been freed */
			StaticAssertStmt(sizeof(jint) <= sizeof(long int),
				"jint wider than long int?!");
			ereport(WARNING,
				(errmsg("failed to create Java virtual machine"),
				 errdetail("JNI_CreateJavaVM returned an error code: %ld",
					(long int)JNIresult),
				 jvmStartedAtLeastOnce ?
					errhint("Because an earlier attempt during this session "
					"did start a VM before failing, this probably means your "
					"Java runtime environment does not support more than one "
					"VM creation per session.  You may need to exit this "
					"session and start a new one.") : 0));
			goto check_tolerant;
		}
		jvmStartedAtLeastOnce = true;
		elog(DEBUG2, "successfully created Java virtual machine");
		initstage = IS_JAVAVM_STARTED;
		/*FALLTHROUGH*/

	case IS_JAVAVM_STARTED:
#ifdef USE_PLJAVA_SIGHANDLERS
		pqsignal(SIGINT,  pljavaStatementCancelHandler);
		pqsignal(SIGTERM, pljavaDieHandler);
		pqsignal(SIGQUIT, pljavaQuickDieHandler);
#endif
		initstage = IS_SIGHANDLERS;
		/*FALLTHROUGH*/

	case IS_SIGHANDLERS:
		Invocation_pushBootContext(&ctx);
		PG_TRY();
		{
			initPLJavaClasses();
			initJavaSession();
			Invocation_popBootContext();
			initstage = IS_PLJAVA_FOUND;
		}
		PG_CATCH();
		{
			MemoryContextSwitchTo(ctx.upperContext); /* leave ErrorContext */
			Invocation_popBootContext();
			initstage = IS_MISC_ONCE_DONE;
			/* We can't stay here...
			 */
			if ( tolerant )
				reLogWithChangedLevel(WARNING); /* so xact is not aborted */
			else
			{
				EmitErrorReport(); /* no more unwinding, just log it */
				/* Seeing an ERROR emitted to the log, without leaving the
				 * transaction aborted, would violate the principle of least
				 * astonishment. But at check_tolerant below, another ERROR will
				 * be thrown immediately, so the transaction effect will be as
				 * expected and this ERROR will contribute information beyond
				 * what is in the generic one thrown down there.
				 */
				FlushErrorState();
			}
		}
		PG_END_TRY();
		if ( IS_PLJAVA_FOUND != initstage )
		{
			/* JVM initialization failed for some reason. Destroy
			 * the VM if it exists. Perhaps the user will try
			 * fixing the pljava.module_path and make a new attempt.
			 */
			ereport(WARNING, (
				errmsg("failed to load initial PL/Java classes"),
				errhint("The most common reason is that \"pljava.module_path\" "
					"needs to be set, naming the proper \"pljava.jar\" "
					"and \"pljava-api.jar\" files, separated by the correct "
					"path separator for this platform.")
					));
			pljava_DualState_unregister();
			_destroyJavaVM(0, 0);
			goto check_tolerant;
		}
		/*FALLTHROUGH*/

	case IS_PLJAVA_FOUND:
		greeting = InstallHelper_hello();
		ereport(NULL != pljavaLoadPath ? NOTICE : DEBUG1, (
				errmsg("PL/Java loaded"),
				errdetail("versions:\n%s", greeting)));
		pfree(greeting);
		initstage = IS_PLJAVA_INSTALLING;
		/*FALLTHROUGH*/

	case IS_PLJAVA_INSTALLING:
		if ( NULL != pljavaLoadPath )
		{
			warnJEP411 = javaGT11;
			InstallHelper_groundwork(); /* sqlj schema, language handlers, ...*/
		}
		initstage = IS_COMPLETE;
		/*FALLTHROUGH*/

	case IS_COMPLETE:
		pljavaLoadingAsExtension = false;
		if ( alteredSettingsWereNeeded )
		{
			/* Use this StringInfoData to conditionally construct part of the
			 * hint string suggesting ALTER DATABASE ... SET ... FROM CURRENT
			 * provided the server is >= 9.2 where that will actually work.
			 * In 9.3, psprintf appeared, which would make this all simpler,
			 * but if 9.3+ were all that had to be supported, this would all
			 * be moot anyway. Doing the initStringInfo inside the ereport
			 * ensures the string is allocated in ErrorContext and won't leak.
			 * Don't remove the extra parens grouping
			 * (initStringInfo, appendStringInfo, errhint) ... with the parens,
			 * that's a comma expression, which is sequenced; without them, they
			 * are just function parameters with evaluation order unknown.
			 */
			StringInfoData buf;

			ereport(NOTICE, (
				errmsg("PL/Java successfully started after adjusting settings"),
				(initStringInfo(&buf),
				appendStringInfo(&buf, \
					"using ALTER DATABASE %s SET ... FROM CURRENT or ", \
					pljavaDbName()),
				errhint("The settings that worked should be saved (%s"
					"in the \"%s\" file). For a reminder of what has been set, "
					"try: SELECT name, setting FROM pg_settings WHERE name LIKE"
					" 'pljava.%%' AND source = 'session'",
					buf.data,
					superuser()
						? PG_GETCONFIGOPTION("config_file")
						: "postgresql.conf"))));

			if ( loadAsExtensionFailed )
			{
#if PG_VERSION_NUM < 130000
#define MOREHINT \
					"\"CREATE EXTENSION pljava FROM unpackaged\""
#else
#define MOREHINT \
					"\"CREATE EXTENSION pljava VERSION unpackaged\", " \
					"then (after starting another new session) " \
					"\"ALTER EXTENSION pljava UPDATE\""
#endif
				ereport(NOTICE, (errmsg(
					"PL/Java load successful after failed CREATE EXTENSION"),
					errdetail(
					"PL/Java is now installed, but not as an extension."),
					errhint(
					"To correct that, either COMMIT or ROLLBACK, make sure "
					"the working settings are saved, exit this session, and "
					"in a new session, either: "
					"1. if committed, run "
					MOREHINT
					", or 2. "
					"if rolled back, simply \"CREATE EXTENSION pljava\" again."
					)));
#undef MOREHINT
			}
		}
		return;

	default:
		ereport(ERROR, (
			errmsg("cannot set up PL/Java"),
			errdetail(
				"An unexpected stage was reached in the startup sequence."),
			errhint(
				"Please report the circumstances to the PL/Java maintainers.")
			));
	}

check_tolerant:
	if ( pljavaLoadingAsExtension )
	{
		tolerant = false;
		loadAsExtensionFailed = true;
		pljavaLoadingAsExtension = false;
	}
	if ( !tolerant )
	{
		ereport(ERROR, (
			errcode(ERRCODE_OBJECT_NOT_IN_PREREQUISITE_STATE),
			errmsg(
				"cannot use PL/Java before successfully completing its setup"),
			errhint(
				"Check the log for messages closely preceding this one, "
				"detailing what step of setup failed and what will be needed, "
				"probably setting one of the \"pljava.\" configuration "
				"variables, to complete the setup. If there is not enough "
				"help in the log, try again with different settings for "
				"\"log_min_messages\" or \"log_error_verbosity\".")));
	}
}

/*
 * A function having everything to do with logging, which ought to be factored
 * out one day to make a start on the Thoughts-on-logging wiki ideas.
 */
static void reLogWithChangedLevel(int level)
{
	ErrorData *edata = CopyErrorData();
	int sqlstate = edata->sqlerrcode;
	int category = ERRCODE_TO_CATEGORY(sqlstate);
	FlushErrorState();
	if ( WARNING > level )
	{
		if ( ERRCODE_SUCCESSFUL_COMPLETION != category )
			sqlstate = ERRCODE_SUCCESSFUL_COMPLETION;
	}
	else if ( WARNING == level )
	{
		if ( ERRCODE_WARNING != category && ERRCODE_NO_DATA != category )
			sqlstate = ERRCODE_WARNING;
	}
	else if ( ERRCODE_WARNING == category || ERRCODE_NO_DATA == category ||
		ERRCODE_SUCCESSFUL_COMPLETION == category )
		sqlstate = ERRCODE_INTERNAL_ERROR;

	edata->elevel = level;
	edata->sqlerrcode = sqlstate;
	PG_TRY();
	{
		ThrowErrorData(edata);
	}
	PG_CATCH();
	{
		FreeErrorData(edata); /* otherwise this wouldn't happen in ERROR case */
		PG_RE_THROW();
	}
	PG_END_TRY();
	FreeErrorData(edata);
}

void _PG_init()
{
	char *sep;

	if ( IS_PLJAVA_INSTALLING == initstage )
		return; /* creating handler functions will cause recursive call */

	InstallHelper_earlyHello();

	/*
	 * Find the platform's path separator. Java knows it, but that's no help in
	 * preparing the launch options before it is launched. PostgreSQL knows what
	 * it is, but won't directly say; give it some choices and it'll pick one.
	 * Alternatively, let Maven or Ant determine and add a -D at build time from
	 * the path.separator property. Maybe that's cleaner?
	 */
	sep = first_path_var_separator(":;");
	if ( NULL == sep )
		elog(ERROR,
			"PL/Java cannot determine the path separator this platform uses");
	s_path_var_sep = *sep;

	if ( InstallHelper_shouldDeferInit() )
		deferInit = true;
	else
		pljavaCheckExtension( NULL);
	initsequencer( initstage, true);
}

static void initPLJavaClasses(void)
{
	jfieldID fID;
	int javaMajor;
	JNINativeMethod backendMethods[] =
	{
		{
		"isCallingJava",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Backend_isCallingJava
		},
		{
		"isReleaseLingeringSavepoints",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Backend_isReleaseLingeringSavepoints
		},
		{
		"_getConfigOption",
		"(Ljava/lang/String;)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Backend__1getConfigOption
		},
		{
		"_getStatementCacheSize",
		"()I",
		Java_org_postgresql_pljava_internal_Backend__1getStatementCacheSize
		},
		{
		"_log",
		"(ILjava/lang/String;)V",
		Java_org_postgresql_pljava_internal_Backend__1log
		},
		{
		"_clearFunctionCache",
		"()V",
		Java_org_postgresql_pljava_internal_Backend__1clearFunctionCache
		},
		{
		"_isCreatingExtension",
		"()Z",
		Java_org_postgresql_pljava_internal_Backend__1isCreatingExtension
		},
		{
		"_myLibraryPath",
		"()Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Backend__1myLibraryPath
		},
		{
		"_pokeJEP411",
		"(Ljava/lang/Class;Ljava/lang/Object;)V",
		Java_org_postgresql_pljava_internal_Backend__1pokeJEP411
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod earlyMethods[] =
	{
		{
		"_forbidOtherThreads",
		"()Z",
		Java_org_postgresql_pljava_internal_Backend_00024EarlyNatives__1forbidOtherThreads
		},
		{
		"_defineClass",
		"(Ljava/lang/String;Ljava/lang/ClassLoader;[B)Ljava/lang/Class;",
		Java_org_postgresql_pljava_internal_Backend_00024EarlyNatives__1defineClass
		},
		{ 0, 0, 0 }
	};
	jclass cls;

	JavaMemoryContext = AllocSetContextCreate(TopMemoryContext,
												"PL/Java",
												ALLOCSET_DEFAULT_SIZES);

	Exception_initialize();

	elog(DEBUG2,
		"checking for a PL/Java Backend class on the given module path");

	cls = PgObject_getJavaClass(
		"org/postgresql/pljava/internal/Backend$EarlyNatives");
	PgObject_registerNatives2(cls, earlyMethods);

	cls = PgObject_getJavaClass("org/postgresql/pljava/internal/Backend");
	elog(DEBUG2, "successfully loaded Backend class");
	s_Backend_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(s_Backend_class, backendMethods);

	fID = PgObject_getStaticJavaField(s_Backend_class, "JAVA_MAJOR", "I");
	javaMajor = JNI_getStaticIntField(s_Backend_class, fID);
	javaGT11 = 11 <  javaMajor;
	javaGE17 = 17 <= javaMajor;

	fID = PgObject_getStaticJavaField(s_Backend_class,
		"THREADLOCK", "Ljava/lang/Object;");
	JNI_setThreadLock(JNI_getStaticObjectField(s_Backend_class, fID));

	Invocation_initialize();
	Exception_initialize2();
	SPI_initialize();
	Type_initialize();
	pljava_DualState_initialize();
	Function_initialize();
	Session_initialize();
	PgSavepoint_initialize();
	XactListener_initialize();
	SubXactListener_initialize();
	SQLInputFromChunk_initialize();
	SQLOutputToChunk_initialize();
	SQLOutputToTuple_initialize();

	InstallHelper_initialize();
}

int Backend_setJavaLogLevel(int logLevel)
{
	int oldLevel = s_javaLogLevel;
	s_javaLogLevel = logLevel;
	return oldLevel;
}
	
/**
 * Special purpose logging function called from JNI when verbose is enabled.
 */
static jint JNICALL my_vfprintf(FILE* fp, const char* format, va_list args)
{
	static char const * const cap_format =
		"WARNING: JNI local refs: %u, exceeds capacity: %u";
	static char const at_prefix[] = "\tat ";
	static char const locked_prefix[] = "\t- locked <";
	static char const class_prefix[] = "(a ";
	static char const culprit[] =
		" com.sun.management.internal.DiagnosticCommandImpl.";
	static char const nostack[] =
		"No stacktrace, probably called from PostgreSQL";
	static enum matchstate
	{
		VFP_INITIAL,
		VFP_MAYBE,
		VFP_ATE_AT,
		VFP_ATE_LOCKED
	}
	state = VFP_INITIAL;
	static unsigned int lastlive, lastcap;

	char buf[1024];
	char* ep;
	char* bp = buf;
	unsigned int live, cap;
	int got;
	char const *detail;

    vsnprintf(buf, sizeof(buf), format, args);

	/* Try to eliminate annoying -Xcheck:jni messages from deep in JMX that
	 * nothing can be done about here.
	 */
	for ( ;; state = VFP_INITIAL )
	{
		switch ( state )
		{
		case VFP_INITIAL:
			got = sscanf(buf, cap_format, &live, &cap);
			if ( 2 != got )
				break;
			lastlive = live;
			lastcap = cap;
			state = VFP_MAYBE;
			return 0;

		case VFP_MAYBE:
			if ( 0 != strncmp(buf, at_prefix, sizeof at_prefix - 1) )
				detail = nostack;
			else
			{
				detail = buf;
				state = VFP_ATE_AT;
				if ( NULL != strstr(buf, culprit) )
					return 0;
			}
			ereport(INFO, (
				errmsg_internal(cap_format, lastlive, lastcap),
				errdetail_internal("%s", detail),
				errhint(
					"To pinpoint location, set a breakpoint on this ereport "
					"and follow stacktrace to a functionExit(), its caller "
					"(a JNI method), and the immediate caller of that.")));
			if ( nostack == detail )
				continue;
			return 0;

		case VFP_ATE_AT:
			if ( 0 == strncmp(buf, at_prefix, sizeof at_prefix - 1) )
				return 0; /* remain in ATE_AT state */
			if ( 0 != strncmp(buf, locked_prefix, sizeof locked_prefix - 1) )
				continue;
			state = VFP_ATE_LOCKED;
			return 0;

		case VFP_ATE_LOCKED:
			if ( 0 != strncmp(buf, class_prefix, sizeof class_prefix - 1) )
				continue;
			state = VFP_ATE_AT;
			return 0;
		}
		break;
	}

    /* Trim off trailing newline and other whitespace.
     */
	ep = bp + strlen(bp) - 1;
    while(ep >= bp && isspace(*ep))
 		--ep;
 	++ep;
 	*ep = 0;

    elog(s_javaLogLevel, "%s", buf);
    return 0;
}

/*
 * Append those parts of path that has not yet been appended. The HashMap unique is
 * keeping track of what has been appended already. First appended part will be
 * prefixed with prefix.
 */
static void appendPathParts(const char* path, StringInfoData* bld, HashMap unique, const char* prefix)
{
	StringInfoData buf;
	if(path == 0 || strlen(path) == 0)
		return;

	for (;;)
	{
		char* pathPart;
		char* sep;
		size_t len;

		if(*path == 0)
			break;

		sep = first_path_var_separator(path);

		if(sep == path)
		{
			/* Ignore zero length components.
			 */
			++path;
			continue;
		}

		if ( NULL == sep )
			len = strlen(path);
		else
			len = sep - path;

		initStringInfo(&buf);
		if(*path == '$')
		{
			if( (len == 7 || first_dir_separator(path) == path + 7)
				&& strncmp(path, "$libdir", 7) == 0)
			{
				char pathbuf[MAXPGPATH];
				get_pkglib_path(my_exec_path, pathbuf);
				len -= 7;
				path += 7;
				appendStringInfoString(&buf, pathbuf);
			}
			else
				ereport(ERROR, (
					errcode(ERRCODE_INVALID_NAME),
					errmsg("invalid macro name '%*s' in PL/Java module path",
						(int)len, path)));
		}

		if(len > 0)
		{
			appendBinaryStringInfo(&buf, path, (int)len);
			path += len;
		}

		pathPart = buf.data;
		if(HashMap_getByString(unique, pathPart) == 0)
		{
			if(HashMap_size(unique) == 0)
				appendStringInfo(bld, "%s", prefix);
			else
				appendStringInfoChar(bld, s_path_var_sep);
			appendStringInfo(bld, "%s", pathPart);
			HashMap_putByString(unique, pathPart, (void*)1);
		}
		pfree(pathPart);
		if(*path == 0)
			break;
		++path; /* Skip path var separator */
	}
}

/*
 * Get the module path. Result is always freshly palloc'd.
 * No longer relies on an environment variable. What CLASSPATH variable might
 * happen to be randomly set in the environment of a PostgreSQL backend?
 */
static char* getModulePath(const char* prefix)
{
	char* path;
	HashMap unique = HashMap_create(13, CurrentMemoryContext);
	StringInfoData buf;
	initStringInfo(&buf);
	appendPathParts(modulepath, &buf, unique, prefix);
	PgObject_free((PgObject)unique);
	path = buf.data;
	if(strlen(path) == 0)
	{
		pfree(path);
		path = 0;
	}
	return path;
}

#ifdef USE_PLJAVA_SIGHANDLERS

static void pljavaStatementCancelHandler(int signum)
{
	if(!proc_exit_inprogress)
	{
		/* Never service the interrupt immediately. In order to find out if
		 * its safe, we would need to know what kind of threading mechanism
		 * the VM uses. That would count for a lot of conditional code.
		 */
		QueryCancelPending = true;
		InterruptPending = true;
	}
}

static void pljavaDieHandler(int signum)
{
	if(!proc_exit_inprogress)
	{
		/* Never service the interrupt immediately. In order to find out if
		 * its safe, we would need to know what kind of threading mechanism
		 * the VM uses. That would count for a lot of conditional code.
		 */
		ProcDiePending = true;
		InterruptPending = true;
	}
}

static void pljavaQuickDieHandler(int signum)
{
	/* Just die. No ereporting here since we don't know what thread this is.
	 */
	exit(1);
}

static sigjmp_buf recoverBuf;
static void terminationTimeoutHandler()
{
	kill(MyProcPid, SIGQUIT);
	
	/* Some sleep to get the SIGQUIT a chance to generate
	 * the needed output.
	 */
	pg_usleep(1);

	/* JavaVM did not die within the alloted time
	 */
	siglongjmp(recoverBuf, 1);
}
#endif

/*
 * proc_exit callback to tear down the JVM
 */
static void _destroyJavaVM(int status, Datum dummy)
{
	if(s_javaVM == 0)
	{
		if ( s_startingVM )
		{
			ereport(FATAL, (
				errcode(ERRCODE_INTERNAL_ERROR),
				errmsg("the Java VM exited while loading PL/Java"),
				errdetail(
					"The Java VM's exit forces this session to end."),
				errhint(
					"This has been known to happen when the entry in "
					"pljava.module_path for the pljava-api jar has been "
					"misspelled or the jar cannot be opened. If "
					"logging_collector is active, there may be useful "
					"information in the log.")
					));
		}
	}
	else
	{
		Invocation ctx;
#ifdef USE_PLJAVA_SIGHANDLERS
		TimeoutId tid;

		Invocation_pushBootContext(&ctx);
		if(sigsetjmp(recoverBuf, 1) != 0)
		{
			elog(DEBUG2,
				"needed to forcibly shut down the Java virtual machine");
			s_javaVM = 0;
			currentInvocation = 0;
			return;
		}

		tid = RegisterTimeout(USER_TIMEOUT, terminationTimeoutHandler);
		enable_timeout_after(tid, 5000);

		elog(DEBUG2, "shutting down the Java virtual machine");
		JNI_destroyVM(s_javaVM);

		disable_timeout(tid, false);
#else
		Invocation_pushBootContext(&ctx);
		elog(DEBUG2, "shutting down the Java virtual machine");
		JNI_destroyVM(s_javaVM);
#endif
		elog(DEBUG2, "done shutting down the Java virtual machine");
		s_javaVM = 0;
		currentInvocation = 0;
	}
}

static void JVMOptList_init(JVMOptList* jol)
{
	jol->options  = (JavaVMOption*)palloc(10 * sizeof(JavaVMOption));
	jol->size     = 0;
	jol->capacity = 10;
}

static void JVMOptList_delete(JVMOptList* jol)
{
	JavaVMOption* opt = jol->options;
	JavaVMOption* top = opt + jol->size;
	while(opt < top)
	{
		pfree(opt->optionString);
		opt++;
	}
	pfree(jol->options);
}

static void JVMOptList_add(JVMOptList* jol, const char* optString, void* extraInfo, bool mustCopy)
{
	JavaVMOption* added;

	int newPos = jol->size;
	if(newPos >= jol->capacity)
	{
		int newCap = jol->capacity * 2;
		JavaVMOption* newOpts = (JavaVMOption*)palloc(newCap * sizeof(JavaVMOption));
		memcpy(newOpts, jol->options, newPos * sizeof(JavaVMOption));
		pfree(jol->options);
		jol->options = newOpts;
		jol->capacity = newCap;
	}
	added = jol->options + newPos;
	if(mustCopy)
		optString = pstrdup(optString);

	added->optionString = (char*)optString;
	added->extraInfo    = extraInfo;
	jol->size++;

	if ( 0 == strncmp(optString, visualVMprefix, sizeof visualVMprefix - 1) )
		seenVisualVMName = true;

	if ( 0 == strncmp(optString, moduleMainPrefix, sizeof moduleMainPrefix-1) )
		seenModuleMain = true;

	elog(DEBUG2, "Added JVM option string \"%s\"", optString);
}

static void JVMOptList_addVisualVMName(JVMOptList* jol)
{
	char const *clustername = pljavaClusterName();
	StringInfoData buf;
	initStringInfo(&buf);
	if ( '\0' == *clustername )
		appendStringInfo(&buf, "%sPL/Java:%d:%s",
			visualVMprefix, MyProcPid, pljavaDbName());
	else
		appendStringInfo(&buf, "%sPL/Java:%s:%d:%s",
			visualVMprefix, clustername, MyProcPid, pljavaDbName());
	JVMOptList_add(jol, buf.data, 0, false);
}

static void JVMOptList_addModuleMain(JVMOptList* jol)
{
	StringInfoData buf;
	initStringInfo(&buf);
	appendStringInfo(&buf, "%s%s",
		moduleMainPrefix, "org.postgresql.pljava");
	JVMOptList_add(jol, buf.data, 0, false);
}

/* Split JVM options. The string is split on whitespace unless the
 * whitespace is found within a string or is escaped by backslash. A
 * backslash escaped quote is not considered a string delimiter.
 */
static void addUserJVMOptions(JVMOptList* optList)
{
	const char* cp = vmoptions;
	
	if(cp != NULL)
	{
		StringInfoData buf;
		char quote = 0;
		char c;

		initStringInfo(&buf);
		for(;;)
		{
			c = *cp++;
			switch(c)
			{
				case 0:
					break;

				case '"':
				case '\'':
					if(quote == c)
						quote = 0;
					else
						quote = c;
					appendStringInfoChar(&buf, c);
					continue;

				case '\\':
					appendStringInfoChar(&buf, '\\');
					c = *cp++;	/* Interpret next character verbatim */
					if(c == 0)
						break;
					appendStringInfoChar(&buf, c);
					continue;
					
				default:
					if(quote == 0 && isspace((int)c))
					{
						while((c = *cp++) != 0)
						{
							if(!isspace((int)c))
								break;
						}

						if(c == 0)
							break;

						if(c != '-')
							appendStringInfoChar(&buf, ' ');
						else if(buf.len > 0)
						{
							/* Whitespace followed by '-' triggers new
							 * option declaration.
							 */
							JVMOptList_add(optList, buf.data, 0, true);
							buf.len = 0;
							buf.data[0] = 0;
						}
					}
					appendStringInfoChar(&buf, c);
					continue;
			}
			break;
		}
		if(buf.len > 0)
			JVMOptList_add(optList, buf.data, 0, true);
		pfree(buf.data);
	}
}

/**
 *  Initialize the session
 */
static void initJavaSession(void)
{
	jclass sessionClass = PgObject_getJavaClass("org/postgresql/pljava/internal/Session");
	jmethodID init = PgObject_getStaticJavaMethod(sessionClass, "init", "()V");
	JNI_callStaticVoidMethod(sessionClass, init);
	JNI_deleteLocalRef(sessionClass);

	if(JNI_exceptionCheck())
	{
		JNI_exceptionDescribe();
		JNI_exceptionClear();
		ereport(ERROR, (
			errcode(ERRCODE_INTERNAL_ERROR),
			errmsg("Unable to initialize java session")));
	}
}

#if PG_VERSION_NUM < 100000
static void checkIntTimeType(void)
{
	const char* idt = PG_GETCONFIGOPTION("integer_datetimes");

	integerDateTimes = (strcmp(idt, "on") == 0);
	elog(DEBUG2, integerDateTimes ? "Using integer_datetimes" : "Not using integer_datetimes");
}
#endif

static jint initializeJavaVM(JVMOptList *optList)
{
	jint jstat;
	JavaVMInitArgs vm_args;

	if(pljavaDebug)
	{
		elog(INFO, "Backend pid = %d. Attach the debugger and set pljavaDebug to false to continue", getpid());
		while(pljavaDebug)
			pg_usleep(1000000L);
	}

	vm_args.nOptions = optList->size;
	vm_args.options  = optList->options;
	vm_args.version  = JNI_VERSION_9;
	vm_args.ignoreUnrecognized = JNI_FALSE;

	elog(DEBUG2, "creating Java virtual machine");

	jstat = JNI_createVM(&s_javaVM, &vm_args);

	if(jstat == JNI_OK && JNI_exceptionCheck())
	{
		JNI_exceptionDescribe();
		JNI_exceptionClear();
		jstat = JNI_ERR;
	}
	JVMOptList_delete(optList);

	return jstat;
}

#define GUCBOOTVAL(v) (v),
#define GUCBOOTASSIGN(a, v)
#define GUCFLAGS(f) (f),
#define GUCCHECK(h) (h),

#define BOOL_GUC(name, short_desc, long_desc, valueAddr, bootValue, context, \
                 flags, check_hook, assign_hook, show_hook) \
	GUCBOOTASSIGN((valueAddr), (bootValue)) \
	DefineCustomBoolVariable((name), (short_desc), (long_desc), (valueAddr), \
		GUCBOOTVAL(bootValue) (context), GUCFLAGS(flags) GUCCHECK(check_hook) \
		(assign_hook), (show_hook))

#define INT_GUC(name, short_desc, long_desc, valueAddr, bootValue, minValue, \
				maxValue, context, flags, check_hook, assign_hook, show_hook) \
	GUCBOOTASSIGN((valueAddr), (bootValue)) \
	DefineCustomIntVariable((name), (short_desc), (long_desc), (valueAddr), \
		GUCBOOTVAL(bootValue) (minValue), (maxValue), (context), \
		GUCFLAGS(flags) GUCCHECK(check_hook) (assign_hook), (show_hook))

#define STRING_GUC(name, short_desc, long_desc, valueAddr, bootValue, context, \
				   flags, check_hook, assign_hook, show_hook) \
	GUCBOOTASSIGN((char const **)(valueAddr), (bootValue)) \
	DefineCustomStringVariable((name), (short_desc), (long_desc), (valueAddr), \
		GUCBOOTVAL(bootValue) (context), GUCFLAGS(flags) GUCCHECK(check_hook) \
		(assign_hook), (show_hook))

#define ENUM_GUC(name, short_desc, long_desc, valueAddr, bootValue, options, \
				 context, flags, check_hook, assign_hook, show_hook) \
	GUCBOOTASSIGN((valueAddr), (bootValue)) \
	DefineCustomEnumVariable((name), (short_desc), (long_desc), (valueAddr), \
		GUCBOOTVAL(bootValue) (options), (context), GUCFLAGS(flags) \
		GUCCHECK(check_hook) (assign_hook), (show_hook))

#ifndef PLJAVA_LIBJVMDEFAULT
#define PLJAVA_LIBJVMDEFAULT "libjvm"
#endif

#define PLJAVA_ENABLE_DEFAULT true

#if PG_VERSION_NUM < 110000
#define PLJAVA_IMPLEMENTOR_FLAGS GUC_LIST_INPUT | GUC_LIST_QUOTE
#else
#define PLJAVA_IMPLEMENTOR_FLAGS GUC_LIST_INPUT
#endif

static void registerGUCOptions(void)
{
	static char pathbuf[MAXPGPATH];

	STRING_GUC(
		"pljava.libjvm_location",
		"Path to the libjvm (.so, .dll, etc.) file in Java's jre/lib area",
		NULL, /* extended description */
		&libjvmlocation,
		PLJAVA_LIBJVMDEFAULT,
		PGC_SUSET,
		GUC_SUPERUSER_ONLY,    /* flags */
		check_libjvm_location,
		assign_libjvm_location,
		NULL); /* show hook */

	STRING_GUC(
		"pljava.vmoptions",
		"Options sent to the JVM when it is created",
		NULL, /* extended description */
		&vmoptions,
		NULL, /* boot value */
		PGC_SUSET,
		GUC_SUPERUSER_ONLY,    /* flags */
		check_vmoptions,
		assign_vmoptions,
		NULL); /* show hook */

	STRING_GUC(
		"pljava.module_path",
		"Module path to be used by the JVM",
		NULL, /* extended description */
		&modulepath,
		InstallHelper_defaultModulePath(pathbuf,s_path_var_sep),/* boot value */
		PGC_SUSET,
		GUC_SUPERUSER_ONLY,    /* flags */
		check_modulepath,
		assign_modulepath,
		NULL); /* show hook */

	STRING_GUC(
		policyUrlsGUC,
		"URLs to Java security policy file(s) for PL/Java's use",
		"Quote each URL and separate with commas. Any URL may begin (inside "
		"the quotes) with n= where n is the index of the Java "
		"policy.url.n property to set. If not specified, the first will "
		"become policy.url.2 (following the JRE-installed policy) with "
		"subsequent entries following in sequence. The last entry may be a "
		"bare = (still quoted) to prevent use of any higher-numbered policy "
		"URLs from the java.security file.",
		&policy_urls,
		"\"file:${org.postgresql.sysconfdir}/pljava.policy\",\"=\"",
		PGC_SUSET,
		PLJAVA_IMPLEMENTOR_FLAGS | GUC_SUPERUSER_ONLY,
		check_policy_urls, /* check hook */
		assign_policy_urls,
		NULL); /* show hook */

	BOOL_GUC(
		"pljava.debug",
		"Stop the backend to attach a debugger",
		NULL, /* extended description */
		&pljavaDebug,
		false, /* boot value */
		PGC_USERSET,
		0,    /* flags */
		NULL, /* check hook */
		NULL, NULL); /* assign hook, show hook */

	INT_GUC(
		"pljava.statement_cache_size",
		"Size of the prepared statement MRU cache",
		NULL, /* extended description */
		&statementCacheSize,
		11,   /* boot value */
		0, 512,   /* min, max values */
		PGC_USERSET,
		0,    /* flags */
		NULL, /* check hook */
		NULL, NULL); /* assign hook, show hook */

	BOOL_GUC(
		"pljava.release_lingering_savepoints",
		"If true, lingering savepoints will be released on function exit. "
		"If false, they will be rolled back",
		NULL, /* extended description */
		&pljavaReleaseLingeringSavepoints,
		false, /* boot value */
		PGC_USERSET,
		0,    /* flags */
		NULL, /* check hook */
		NULL, NULL); /* assign hook, show hook */

	BOOL_GUC(
		"pljava.enable",
		"If off, the Java virtual machine will not be started until set on.",
		"This is mostly of use on PostgreSQL versions < 9.2, where option "
		"settings changed before LOADing PL/Java may be rejected, so they must "
		"be made after LOAD, but before the virtual machine is started.",
		&pljavaEnabled,
		PLJAVA_ENABLE_DEFAULT, /* boot value */
		PGC_USERSET,
		0,    /* flags */
		check_enabled, /* check hook */
		assign_enabled,
		NULL); /* show hook */

	STRING_GUC(
		"pljava.implementors",
		"Implementor names recognized in deployment descriptors",
		NULL, /* extended description */
		&implementors,
		"postgresql", /* boot value */
		PGC_USERSET,
		PLJAVA_IMPLEMENTOR_FLAGS,
		NULL, /* check hook */
		NULL, NULL); /* assign hook, show hook */

	ENUM_GUC(
		"pljava.java_thread_pg_entry",
		"Policy for entry to PG code by Java threads other than the main one",
		"If 'allow', any Java thread can enter PG while the main thread has "
		"entered Java. If 'error', any thread other than the main one will "
		"incur an exception if it tries to enter PG. If 'block', the main "
		"thread will never release its lock, so any other thread that tries "
		"to enter PG will indefinitely block. If 'throw', like 'error', other "
		"threads will incur an exception, but earlier: it will be thrown "
		"in Java, before the JNI boundary into C is even crossed.",
		&java_thread_pg_entry,
		ENUMBOOTVAL(java_thread_pg_entry_options[0]), /* allow */
		java_thread_pg_entry_options,
		PGC_USERSET,
		0, /* flags */
		check_java_thread_pg_entry, /* check hook */
		assign_java_thread_pg_entry,
		NULL); /* display hook */

	EmitWarningsOnPlaceholders("pljava");
}

#undef GUCBOOTVAL
#undef GUCBOOTASSIGN
#undef GUCFLAGS
#undef GUCCHECK
#undef BOOL_GUC
#undef INT_GUC
#undef STRING_GUC
#undef ENUM_GUC
#undef PLJAVA_ENABLE_DEFAULT
#undef PLJAVA_IMPLEMENTOR_FLAGS

static inline Datum internalCallHandler(bool trusted, PG_FUNCTION_ARGS);

extern PLJAVADLLEXPORT Datum javau_call_handler(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(javau_call_handler);

/*
 * This is the entry point for all untrusted calls.
 */
Datum javau_call_handler(PG_FUNCTION_ARGS)
{
	return internalCallHandler(false, fcinfo);
}

extern PLJAVADLLEXPORT Datum java_call_handler(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(java_call_handler);

/*
 * This is the entry point for all trusted calls.
 */
Datum java_call_handler(PG_FUNCTION_ARGS)
{
	return internalCallHandler(true, fcinfo);
}

static inline Datum
internalCallHandler(bool trusted, PG_FUNCTION_ARGS)
{
	Invocation ctx;
	Datum retval = 0;
	Oid funcoid = fcinfo->flinfo->fn_oid;
	bool forTrigger = CALLED_AS_TRIGGER(fcinfo);

	/*
	 * Just in case it could be helpful in offering diagnostics later, hang
	 * on to an Oid that is known to refer to PL/Java (because it got here).
	 * It's cheap, and can be followed back to the right language and
	 * handler function entries later if needed.
	 */
	*(trusted ? &pljavaTrustedOid : &pljavaUntrustedOid) = funcoid;
	if ( IS_COMPLETE != initstage )
	{
		deferInit = false;
		initsequencer( initstage, false);
	}

	Invocation_pushInvocation(&ctx);
	PG_TRY();
	{
		retval = Function_invoke(
			funcoid, trusted, forTrigger, false, true, fcinfo);
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
		Invocation_popInvocation(true);
		PG_RE_THROW();
	}
	PG_END_TRY();
	return retval;
}

static Datum internalValidator(bool trusted, PG_FUNCTION_ARGS);

extern PLJAVADLLEXPORT Datum javau_validator(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(javau_validator);

Datum javau_validator(PG_FUNCTION_ARGS)
{
	return internalValidator(false, fcinfo);
}

extern PLJAVADLLEXPORT Datum java_validator(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(java_validator);

Datum java_validator(PG_FUNCTION_ARGS)
{
	return internalValidator(true, fcinfo);
}

static Datum internalValidator(bool trusted, PG_FUNCTION_ARGS)
{
	Oid funcoid = PG_GETARG_OID(0);
	Invocation ctx;
	Oid *oidSaveLocation = NULL;

	if (!CheckFunctionValidatorAccess(fcinfo->flinfo->fn_oid, funcoid))
		PG_RETURN_VOID();

	/*
	 * In the call handler, which could be called heavily, funcoid gets
	 * unconditionally stored to one of these two locations, rather than
	 * spending extra cycles deciding whether to store it or not. A validator
	 * will not be called as heavily, and can afford to check here whether
	 * an Oid needs to be stored or not. The situation to avoid is where
	 * funcoid gets stored here, as an Oid from which PL/Java's library path can
	 * be found, but the function then gets rejected by the validator, leaving
	 * the stored Oid invalid and useless for that purpose. Therefore, choose
	 * here whether and where to store it, but store it only within the PG_TRY
	 * block, and replace with InvalidOid again in the PG_CATCH.
	 */
	if ( trusted )
	{
		if ( InvalidOid == pljavaTrustedOid )
			oidSaveLocation = &pljavaTrustedOid;
	}
	else
	{
		if ( InvalidOid == pljavaUntrustedOid )
			oidSaveLocation = &pljavaUntrustedOid;
	}

	if ( IS_PLJAVA_INSTALLING > initstage )
	{
		if ( check_function_bodies ) /* We're gonna need a JVM */
		{
			deferInit = false;
			initsequencer( initstage, false);
		}
		else /* Can try to start one, but if no go, just assume function's ok */
		{
			initsequencer( initstage, true);
			if ( IS_PLJAVA_INSTALLING > initstage )
			{
				if ( javaGT11 )
					warnJEP411 = true;
				PG_RETURN_VOID();
			}
		}
	}

	Invocation_pushInvocation(&ctx);
	PG_TRY();
	{
		if ( NULL != oidSaveLocation )
			*oidSaveLocation = funcoid;

		Function_invoke(
			funcoid, trusted, false, true, check_function_bodies, NULL);
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
		if ( NULL != oidSaveLocation )
			*oidSaveLocation = InvalidOid;

		Invocation_popInvocation(true);
		PG_RE_THROW();
	}
	PG_END_TRY();

	if ( javaGT11 )
		warnJEP411 = true;
	PG_RETURN_VOID();
}

/*
 * Called at the ends of committing transactions to emit a warning about future
 * JEP 411 impacts, at most once per session, if any PL/Java functions were
 * declared or redeclared in the transaction, or if PL/Java was installed or
 * upgraded. Also called from InstallHelper, if pg_upgrade is happening.
 * Yes, this is a bit tangled. The tracking of function declaration happens
 * above in the validator handler, and PL/Java installation/upgrade is detected
 * in the initsequencer.
 */
void Backend_warnJEP411(bool isCommit)
{
	static bool warningEmitted = false; /* once only per session */

	if ( warningEmitted  ||  ! warnJEP411 )
		return;

	if ( ! isCommit )
	{
		warnJEP411 = false;
		return;
	}

	warningEmitted = true;

	ereport(javaGE17 ? WARNING : NOTICE, (
		errmsg(
			"[JEP 411] migration advisory: there will be a Java version "
			"(after Java 17) that will be unable to run PL/Java %s "
			 "with policy enforcement", SO_VERSION_STRING),
		errdetail(
			"This PL/Java version enforces security policy using important "
			"Java features that will be phased out in future Java versions. "
			"Those changes will come in releases after Java 17."),
		errhint(
			"For migration planning, this version of PL/Java can still "
			"enforce policy in Java versions up to and including 22, "
			"and Java 17 and 21 are positioned as long-term support releases. "
			"For details on how PL/Java will adapt, please bookmark "
			"https://github.com/tada/pljava/wiki/JEP-411")
	));
}

/****************************************
 * JNI methods
 ****************************************/
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
	return JNI_VERSION_9;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _getConfigOption
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNICALL Java_org_postgresql_pljava_internal_Backend__1getConfigOption(JNIEnv* env, jclass cls, jstring jkey)
{
	jstring result = 0;
	
	BEGIN_NATIVE
	char* key = String_createNTS(jkey);
	if(key != 0)
	{
		PG_TRY();
		{
			const char *value;
			if ( 0 == strcmp(policyUrlsGUC, key) )
				value = policy_urls;
			else
				value = PG_GETCONFIGOPTION(key);
			pfree(key);
			if(value != 0)
				result = String_createJavaStringFromNTS(value);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("GetConfigOption");
		}
		PG_END_TRY();
	}
	END_NATIVE
	return result;
}


/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _getStatementCacheSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Backend__1getStatementCacheSize(JNIEnv* env, jclass cls)
{
	return statementCacheSize;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _log
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
JNICALL Java_org_postgresql_pljava_internal_Backend__1log(JNIEnv* env, jclass cls, jint logLevel, jstring jstr)
{
	BEGIN_NATIVE_NO_ERRCHECK
	char* str = String_createNTS(jstr);
	if(str != 0)
	{
		PG_TRY();
		{
			elog(logLevel, "%s", str);
			pfree(str);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("ereport");
		}
		PG_END_TRY();
	}
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    isCallingJava
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_isCallingJava(JNIEnv* env, jclass cls)
{
	return JNI_isCallingJava();
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    isReleaseLingeringSavepoints
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_isReleaseLingeringSavepoints(JNIEnv* env, jclass cls)
{
	return pljavaReleaseLingeringSavepoints ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _clearFunctionCache
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Backend__1clearFunctionCache(JNIEnv* env, jclass cls)
{
	BEGIN_NATIVE_NO_ERRCHECK
	Function_clearFunctionCache();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _isCreatingExtension
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend__1isCreatingExtension(JNIEnv *env, jclass cls)
{
	bool inExtension = false;
	pljavaCheckExtension( &inExtension);
	return inExtension ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _myLibraryPath
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Backend__1myLibraryPath(JNIEnv *env, jclass cls)
{
	jstring result = NULL;

	BEGIN_NATIVE

	if ( NULL == pljavaLoadPath )
	{
		Oid funcoid = pljavaTrustedOid;

		if ( InvalidOid == funcoid )
			funcoid = pljavaUntrustedOid;
		if ( InvalidOid == funcoid )
			return NULL;

		/*
		 * Result not needed, but pljavaLoadPath is set as a side effect.
		 */
		InstallHelper_isPLJavaFunction(funcoid, NULL, NULL);
	}

	if ( NULL != pljavaLoadPath )
		result = String_createJavaStringFromNTS(pljavaLoadPath);

	END_NATIVE

	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _pokeJEP411
 * Signature: (Ljava/lang/Class;Ljava/lang/Object;)V
 *
 * This method is hideously dependent on unexposed JDK internals. But then,
 * the fact that it's needed at all is hideous already. Java, any language,
 * is classic infrastructure. Other layers, like this, are built atop it, and
 * others in turn use those layers. The idea that the language developers would
 * arrogate to themselves the act of sending an inappropriately low-level
 * message directly to ultimate users, insisting that the stack layers above
 * cannot intercept it and notify the higher-level users in terms that fit
 * the abstractions meaningful there, leaves an uneasy picture of how
 * a development team can begin to lose sight of who is providing what to whom
 * and why.
 *
 * At least as of the time of this writing, System has a CallersHolder class
 * holding a map recording classes for which the warning has already been sent.
 * Poking the 'caller' class into that map works to suppress the warning.
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Backend__1pokeJEP411(JNIEnv *env, jclass cls, jclass caller, jobject token)
{
	jclass callersHolder;
	jfieldID callers;
	jobject map;
	jclass mapClass;
	jmethodID put;

	BEGIN_NATIVE

	callersHolder = JNI_findClass("java/lang/System$CallersHolder");
	if ( NULL == callersHolder )
		goto failed;

	callers = JNI_getStaticFieldID(callersHolder, "callers", "Ljava/util/Map;");
	if ( NULL == callers )
		goto failed;

	map = JNI_getStaticObjectField(callersHolder, callers);
	if ( NULL == map )
		goto failed;

	mapClass = JNI_getObjectClass(map);
	put = JNI_getMethodID(mapClass,
		"put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

	JNI_callObjectMethodLocked(map, put, caller, token);
	goto done;

failed:
	JNI_exceptionClear();

done:
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Backend_EarlyNatives
 * Method:    _forbidOtherThreads
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_00024EarlyNatives__1forbidOtherThreads(JNIEnv *env, jclass cls)
{
	return (java_thread_pg_entry & 4) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend_EarlyNatives
 * Method:    _defineClass
 * Signature: (Ljava/lang/String;Ljava/lang/ClassLoader;[B)Ljava/lang/Class;
 */
JNIEXPORT jclass JNICALL
Java_org_postgresql_pljava_internal_Backend_00024EarlyNatives__1defineClass(JNIEnv *env, jclass cls, jstring name, jobject loader, jbyteArray image)
{
	const char *utfName;
	jbyte *bytes;
	jsize nbytes;
	jclass newcls;
	static bool oneShot = false;

	if ( oneShot )
		return NULL;
	oneShot = true;

	utfName = (*env)->GetStringUTFChars(env, name, NULL);
	bytes = (*env)->GetByteArrayElements(env, image, NULL);
	nbytes = (*env)->GetArrayLength(env, image);
	newcls = (*env)->DefineClass(env, utfName, loader, bytes, nbytes);
	(*env)->ReleaseByteArrayElements(env, image, bytes, JNI_ABORT);
	(*env)->ReleaseStringUTFChars(env, name, utfName);
	return newcls;
}
