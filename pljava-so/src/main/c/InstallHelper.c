/*
 * Copyright (c) 2015-2024 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
#include <postgres.h>
#include <access/htup_details.h>
#include <access/xact.h>
#include <catalog/pg_language.h>
#include <catalog/pg_proc.h>
#include <commands/dbcommands.h>
#include <commands/extension.h>
#include <commands/portalcmds.h>
#include <executor/spi.h>
#include <miscadmin.h>
#include <libpq/libpq-be.h>
#include <postmaster/autovacuum.h>
#include <tcop/pquery.h>
#include <utils/builtins.h>
#include <utils/lsyscache.h>
#include <utils/memutils.h>
#include <utils/snapmgr.h>
#include <utils/syscache.h>

#if PG_VERSION_NUM >= 120000
#include <catalog/pg_namespace.h>
#define GetNamespaceOid(k1) \
	GetSysCacheOid1(NAMESPACENAME, Anum_pg_namespace_oid, k1)
#else
#define GetNamespaceOid(k1) GetSysCacheOid1(NAMESPACENAME, k1)
#endif

#include "pljava/InstallHelper.h"
#include "pljava/Backend.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/JNICalls.h"
#include "pljava/PgObject.h"
#include "pljava/type/String.h"

#if PG_VERSION_NUM < 170000
#define AmAutoVacuumWorkerProcess() IsAutoVacuumWorkerProcess()
#define AmBackgroundWorkerProcess() IsBackgroundWorker
/*
 * As of 9.6.1, IsBackgroundWorker still does not
 * have PGDLLIMPORT, but MyBgworkerEntry != NULL can be used in MSVC instead.
 *
 * One thing it's needed for is to avoid dereferencing MyProcPort in a
 * background worker, where it's not set.
 */
#if defined(_MSC_VER)
#include <postmaster/bgworker.h>
#define IsBackgroundWorker (MyBgworkerEntry != NULL)
#endif
#endif /* PG_VERSION_NUM < 170000 */

/*
 * The name of the table the extension scripts will create to pass information
 * here. The table name is phrased as an error message because it will appear
 * in one, if installation did not happen because the library had already been
 * loaded.
 */
#define LOADPATH_TBL_NAME "see doc: do CREATE EXTENSION PLJAVA in new session"

static jclass s_InstallHelper_class;
static jmethodID s_InstallHelper_hello;
static jmethodID s_InstallHelper_groundwork;
static jfieldID  s_InstallHelper_MANAGE_CONTEXT_LOADER;

static bool extensionExNihilo = false;

static void checkLoadPath(void);
static void getExtensionLoadPath(void);
static char *origUserName();

char const *pljavaLoadPath = NULL;

bool pljavaLoadingAsExtension = false;

Oid pljavaTrustedOid = InvalidOid;

Oid pljavaUntrustedOid = InvalidOid;

bool pljavaViableXact()
{
	return IsTransactionState() && 'E' != TransactionBlockStatusCode();
}

char *pljavaDbName()
{
	if ( AmAutoVacuumWorkerProcess() || AmBackgroundWorkerProcess() )
	{
		char *shortlived;
		static char *longlived;
		if ( NULL == longlived )
		{
			shortlived = get_database_name(MyDatabaseId);
			if ( NULL != shortlived )
			{
				longlived = MemoryContextStrdup(TopMemoryContext, shortlived);
				pfree(shortlived);
			}
		}
		return longlived;
	}
	return MyProcPort->database_name;
}

static char *origUserName()
{
	if ( AmAutoVacuumWorkerProcess() || AmBackgroundWorkerProcess() )
	{
		char *shortlived;
		static char *longlived;
		if ( NULL == longlived )
		{
			shortlived = GetUserNameFromId(GetAuthenticatedUserId(), false);
			longlived = MemoryContextStrdup(TopMemoryContext, shortlived);
			pfree(shortlived);
		}
		return longlived;
	}
	return MyProcPort->user_name;
}

char const *pljavaClusterName()
{
	/*
	 * In PostgreSQL of at least 9.5, there's always one (even if it is an empty
	 * string), so PG_GETCONFIGOPTION is safe.
	 */
	return PG_GETCONFIGOPTION("cluster_name");
}

void pljavaCheckExtension( bool *livecheck)
{
	if ( ! creating_extension )
	{
		checkLoadPath();
		return;
	}
	if ( NULL != livecheck )
	{
		*livecheck = true;
		return;
	}
	getExtensionLoadPath();
	if ( NULL != pljavaLoadPath )
		pljavaLoadingAsExtension = true;
}

/*
 * As for pljavaCheckExtension, livecheck == null when called from _PG_init
 * (when the real questions are whether PL/Java itself is being loaded, from
 * what path, and whether or not as an extension). When livecheck is not null,
 * PL/Java is already alive and the caller wants to know if an extension is
 * being created for some other reason. That wouldn't even involve this
 * function, except for the need to work around creating_extension visibility
 * on Windows. So if livecheck isn't null, this function only needs to proceed
 * as far as the CREATING_EXTENSION_HACK and then return.
 */
static void checkLoadPath()
{
	List *l;
	Node *ut;
	LoadStmt *ls;
	PlannedStmt *ps;

	if ( NULL == ActivePortal )
		return;
	l = ActivePortal->stmts;

	if ( NULL == l )
		return;
	if ( 1 < list_length( l) )
		elog(DEBUG2, "ActivePortal lists %d statements", list_length( l));
	ut = (Node *)linitial(l);
	if ( NULL == ut )
	{
		elog(DEBUG2, "got null for first statement from ActivePortal");
		return;
	}

	if ( T_PlannedStmt == nodeTag(ut) )
	{
		ps = (PlannedStmt *)ut;
		if ( CMD_UTILITY != ps->commandType )
		{
			elog(DEBUG2, "ActivePortal has PlannedStmt command type %u",
				 ps->commandType);
			return;
		}
		ut = ps->utilityStmt;
		if ( NULL == ut )
		{
			elog(DEBUG2, "got null for utilityStmt from PlannedStmt");
			return;
		}
	}
	if ( T_LoadStmt != nodeTag(ut) )
		return;

	ls = (LoadStmt *)ut;
	if ( NULL == ls->filename )
	{
		elog(DEBUG2, "got null for a LOAD statement's filename");
		return;
	}
	pljavaLoadPath =
		(char const *)MemoryContextStrdup(TopMemoryContext, ls->filename);
}

static void getExtensionLoadPath()
{
	MemoryContext curr;
	Datum dtm;
	bool isnull;
	StringInfoData buf;

	/*
	 * Check whether sqlj.loadpath exists before querying it. I would more
	 * happily just PG_CATCH() the error and compare to ERRCODE_UNDEFINED_TABLE
	 * but what's required to make that work right is "not terribly well
	 * documented, but the exception-block handling in plpgsql provides a
	 * working model" and that code is a lot more fiddly than you would guess.
	 */
	if ( InvalidOid == get_relname_relid(LOADPATH_TBL_NAME,
		GetNamespaceOid(CStringGetDatum("sqlj"))) )
		return;

	SPI_connect();
	curr = CurrentMemoryContext;
	initStringInfo(&buf);
	appendStringInfo(&buf, "SELECT path, exnihilo FROM sqlj.%s",
		quote_identifier(LOADPATH_TBL_NAME));
	if ( SPI_OK_SELECT == SPI_execute(buf.data,	true, 1) && 1 == SPI_processed )
	{
		MemoryContextSwitchTo(TopMemoryContext);
		pljavaLoadPath = (char const *)SPI_getvalue(
			SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 1);
		MemoryContextSwitchTo(curr);
		dtm = SPI_getbinval(SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 2,
			&isnull);
		if ( isnull )
			elog(ERROR, "defect in CREATE EXTENSION script");
		extensionExNihilo = DatumGetBool(dtm);
	}
	SPI_finish();
}

/*
 * Given the Oid of a function believed to be implemented with PL/Java, return
 * the dynamic library path of its language's function-call-handler function
 * (which will of course be PL/Java's library path, if the original belief was
 * correct) ... or NULL if the original belief can't be sustained.
 *
 * If a string is returned, it has been palloc'd in the current context.
 */
char *pljavaFnOidToLibPath(Oid fnOid, char **langName, bool *trusted)
{
	bool isnull;
	HeapTuple procTup;
	Form_pg_proc procStruct;
	Oid langId;
	HeapTuple langTup;
	Form_pg_language langStruct;
	Oid handlerOid;
	Datum probinattr;
	char *probinstring;

	/*
	 * It is proposed that fnOid refers to a function implemented with PL/Java.
	 */
	procTup = SearchSysCache1(PROCOID, ObjectIdGetDatum(fnOid));
	if (!HeapTupleIsValid(procTup))
		elog(ERROR, "cache lookup failed for function %u", fnOid);
	procStruct = (Form_pg_proc) GETSTRUCT(procTup);
	langId = procStruct->prolang;
	ReleaseSysCache(procTup);
	/*
	 * The langId just obtained (if the proposition is proved correct by
	 * surviving the further steps below) is a langId for PL/Java. It could
	 * be cached to simplify later checks. Not today.
	 */
	if ( langId == INTERNALlanguageId || langId == ClanguageId
		|| langId == SQLlanguageId )
		return NULL; /* these can be eliminated without searching syscache. */

	/*
	 * So far so good ... the function thought to be done in PL/Java has at
	 * least not turned out to be internal, or C, or SQL. So, next, look up its
	 * language, and get the Oid for its function call handler.
	 */
	langTup = SearchSysCache1(LANGOID, ObjectIdGetDatum(langId));
	if (!HeapTupleIsValid(langTup))
		elog(ERROR, "cache lookup failed for language %u", langId);
	langStruct = (Form_pg_language) GETSTRUCT(langTup);
	handlerOid = langStruct->lanplcallfoid;
	/*
	 * PL/Java has certainly got a function call handler, so if this language
	 * hasn't, PL/Java it's not.
	 */
	if ( InvalidOid == handlerOid )
	{
		ReleaseSysCache(langTup);
		return NULL;
	}

	/*
	 * Da capo al coda ... handlerOid is another function to be looked up.
	 */
	procTup = SearchSysCache1(PROCOID, ObjectIdGetDatum(handlerOid));
	if (!HeapTupleIsValid(procTup))
		elog(ERROR, "cache lookup failed for function %u", handlerOid);
	procStruct = (Form_pg_proc) GETSTRUCT(procTup);
	/*
	 * If the call handler's not a C function, this isn't PL/Java....
	 */
	if ( ClanguageId != procStruct->prolang )
	{
		ReleaseSysCache(langTup);
		return NULL;
	}

	/*
	 * Now that the handler is known to be a C function, it should have a
	 * probinattr containing the name of its dynamic library.
	 */
	probinattr =
		SysCacheGetAttr(PROCOID, procTup, Anum_pg_proc_probin, &isnull);
	if ( isnull )
		elog(ERROR, "null probin for C function %u", handlerOid);
	if ( NULL != langName )
		*langName = pstrdup(NameStr(langStruct->lanname));
	if ( NULL != trusted )
		*trusted = langStruct->lanpltrusted;
	ReleaseSysCache(langTup);
	probinstring = /* TextDatumGetCString(probinattr); */
		DatumGetCString(DirectFunctionCall1(textout, probinattr)); /*archaic*/
	ReleaseSysCache(procTup);

	/*
	 * About this result: if the caller was initialization code passing a fnOid
	 * known to refer to PL/Java (because it was the function occasioning the
	 * call), then this string can be saved as the dynamic library name for
	 * PL/Java. Otherwise, it is the library name for whatever language is used
	 * by the fnOid passed in, and can be compared to such a saved value to
	 * determine whether that is a PL/Java function or not.
	 */
	return probinstring;
}

bool InstallHelper_shouldDeferInit()
{
	if ( AmAutoVacuumWorkerProcess() || AmBackgroundWorkerProcess() )
			return true;

	if ( ! IsBinaryUpgrade )
		return false;

	Backend_warnJEP411(true);
	return true;
}

bool InstallHelper_isPLJavaFunction(Oid fn, char **langName, bool *trusted)
{
	char *itsPath;
	char *pljPath;
	bool result = false;

	itsPath = pljavaFnOidToLibPath(fn, langName, trusted);
	if ( NULL == itsPath )
		return false;

	if ( NULL == pljavaLoadPath )
	{
		pljPath = NULL;
		if ( InvalidOid != pljavaTrustedOid )
			pljPath = pljavaFnOidToLibPath(pljavaTrustedOid, NULL, NULL);
		if ( NULL == pljPath && InvalidOid != pljavaUntrustedOid )
			pljPath = pljavaFnOidToLibPath(pljavaUntrustedOid, NULL, NULL);
		if ( NULL == pljPath )
		{
			elog(WARNING, "unable to determine PL/Java's load path");
			goto finally;
		}
		pljavaLoadPath =
			(char const *)MemoryContextStrdup(TopMemoryContext, pljPath);
		pfree(pljPath);
	}
	result = 0 == strcmp(itsPath, pljavaLoadPath);
finally:
	pfree(itsPath);
	return result;
}

char const *InstallHelper_defaultModulePath(char *pathbuf, char pathsep)
{
	char * const pbend = pathbuf + MAXPGPATH;
	char *pbp = pathbuf;
	size_t remaining;
	int would_have_sprinted;

	get_share_path(my_exec_path, pathbuf);
	join_path_components(pathbuf, pathbuf, "pljava");
	join_path_components(pathbuf, pathbuf, "pljava"); /* puts \0 where - goes */

	for ( ; pbp < pbend && '\0' != *pbp ; ++ pbp )
		;
	if ( pbend == pbp )
		return NULL;

	/*
	 * pbp now points to a \0 that should later be replaced with a hyphen.
	 * The \0-terminated string starting at pathbuf can, for now, be reused
	 * as an argument to snprintf.
	 */

	remaining = (pbend - pbp) - 1;

	would_have_sprinted = snprintf(pbp + 1, remaining, "%s.jar%c%s-api-%s.jar",
		SO_VERSION_STRING, pathsep, pathbuf, SO_VERSION_STRING);

	if ( would_have_sprinted >= remaining )
		return NULL;

	*pbp = '-'; /* overwrite the \0 so now it's a single string. */
	return pathbuf;
}

void InstallHelper_earlyHello()
{
	elog(DEBUG2,
		"pljava-so-" SO_VERSION_STRING " built for (" PG_VERSION_STR ")");
}

char *InstallHelper_hello()
{
	char pathbuf[MAXPGPATH];
	Invocation ctx;
	jstring nativeVer;
	jstring serverBuiltVer;
	jstring serverRunningVer;
#if PG_VERSION_NUM >= 120000
	FunctionCallInfoBaseData
#else
	FunctionCallInfoData
#endif
		fcinfo;
	text *runningVer;
	jstring user;
	jstring dbname;
	jstring clustername;
	jstring ddir;
	jstring ldir;
	jstring sdir;
	jstring edir;
	jstring greeting;
	char *greetingC;
	char const *clusternameC = pljavaClusterName();
	jboolean manageContext = JNI_getStaticBooleanField(s_InstallHelper_class,
		s_InstallHelper_MANAGE_CONTEXT_LOADER);

	pljava_JNI_threadInitialize(JNI_TRUE == manageContext);

	Invocation_pushBootContext(&ctx);
	nativeVer = String_createJavaStringFromNTS(SO_VERSION_STRING);
	serverBuiltVer = String_createJavaStringFromNTS(PG_VERSION_STR);

	InitFunctionCallInfoData(fcinfo, NULL, 0,
	InvalidOid, /* collation */
	NULL, NULL);
	runningVer = DatumGetTextP(pgsql_version(&fcinfo));
	serverRunningVer = String_createJavaString(runningVer);
	pfree(runningVer);

	user = String_createJavaStringFromNTS(origUserName());
	dbname = String_createJavaStringFromNTS(pljavaDbName());
	if ( '\0' == *clusternameC )
		clustername = NULL;
	else
		clustername = String_createJavaStringFromNTS(clusternameC);

	ddir = String_createJavaStringFromNTS(DataDir);

	get_pkglib_path(my_exec_path, pathbuf);
	ldir = String_createJavaStringFromNTS(pathbuf);

	get_share_path(my_exec_path, pathbuf);
	sdir = String_createJavaStringFromNTS(pathbuf);

	get_etc_path(my_exec_path, pathbuf);
	edir = String_createJavaStringFromNTS(pathbuf);

	greeting = JNI_callStaticObjectMethod(
		s_InstallHelper_class, s_InstallHelper_hello,
		nativeVer, serverBuiltVer, serverRunningVer,
		user, dbname, clustername,
		ddir, ldir, sdir, edir);

	JNI_deleteLocalRef(nativeVer);
	JNI_deleteLocalRef(serverBuiltVer);
	JNI_deleteLocalRef(serverRunningVer);
	JNI_deleteLocalRef(user);
	JNI_deleteLocalRef(dbname);
	if ( NULL != clustername )
		JNI_deleteLocalRef(clustername);
	JNI_deleteLocalRef(ddir);
	JNI_deleteLocalRef(ldir);
	JNI_deleteLocalRef(sdir);
	JNI_deleteLocalRef(edir);
	greetingC = String_createNTS(greeting);
	JNI_deleteLocalRef(greeting);
	Invocation_popBootContext();
	return greetingC;
}

void InstallHelper_groundwork()
{
	Invocation ctx;
	bool snapshot_set = false;
	Invocation_pushInvocation(&ctx);
	ctx.function = Function_INIT_WRITER;
	if ( ! ActiveSnapshotSet() )
	{
		PushActiveSnapshot(GetTransactionSnapshot());
		snapshot_set = true;
	}
	PG_TRY();
	{
		char const *lpt = LOADPATH_TBL_NAME;
		char const *lptq = quote_identifier(lpt);
		jstring pljlp = String_createJavaStringFromNTS(pljavaLoadPath);
		jstring jlpt = String_createJavaStringFromNTS(lpt);
		jstring jlptq = String_createJavaStringFromNTS(lptq);
		if ( lptq != lpt )
			pfree((void *)lptq);
		JNI_callStaticVoidMethod(
			s_InstallHelper_class, s_InstallHelper_groundwork,
			pljlp, jlpt, jlptq,
			pljavaLoadingAsExtension ? JNI_TRUE : JNI_FALSE,
			extensionExNihilo ? JNI_TRUE : JNI_FALSE);
		JNI_deleteLocalRef(pljlp);
		JNI_deleteLocalRef(jlpt);
		JNI_deleteLocalRef(jlptq);
		if ( snapshot_set )
		{
			PopActiveSnapshot();
		}
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
		if ( snapshot_set )
		{
			PopActiveSnapshot();
		}
		Invocation_popInvocation(true);
		PG_RE_THROW();
	}
	PG_END_TRY();
}

void InstallHelper_initialize()
{
	s_InstallHelper_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/InstallHelper"));
	s_InstallHelper_MANAGE_CONTEXT_LOADER = PgObject_getStaticJavaField(
		s_InstallHelper_class, "MANAGE_CONTEXT_LOADER", "Z");
	s_InstallHelper_hello = PgObject_getStaticJavaMethod(s_InstallHelper_class,
		"hello",
		"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
		"Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
		"Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;"
		"Ljava/lang/String;)Ljava/lang/String;");
	s_InstallHelper_groundwork = PgObject_getStaticJavaMethod(
		s_InstallHelper_class, "groundwork",
		"(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)V");
}
