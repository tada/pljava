/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
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
#if PG_VERSION_NUM >= 90300
#include <access/htup_details.h>
#else
#include <access/htup.h>
#endif
#include <access/xact.h>
#include <catalog/pg_language.h>
#include <catalog/pg_proc.h>
#if PG_VERSION_NUM >= 90100
#include <commands/extension.h>
#endif
#include <commands/portalcmds.h>
#include <executor/spi.h>
#include <miscadmin.h>
#include <libpq/libpq-be.h>
#include <tcop/pquery.h>
#include <utils/builtins.h>
#include <utils/lsyscache.h>
#include <utils/memutils.h>
#if PG_VERSION_NUM >= 80400
#include <utils/snapmgr.h>
#endif
#include <utils/syscache.h>

#if PG_VERSION_NUM >= 120000
#include <catalog/pg_namespace.h>
#define GetNamespaceOid(k1) \
	GetSysCacheOid1(NAMESPACENAME, Anum_pg_namespace_oid, k1)
#elif PG_VERSION_NUM >= 90000
#define GetNamespaceOid(k1) GetSysCacheOid1(NAMESPACENAME, k1)
#else
#define SearchSysCache1(cid, k1) SearchSysCache(cid, k1, 0, 0, 0)
#define GetNamespaceOid(k1) GetSysCacheOid(NAMESPACENAME, k1, 0, 0, 0)
#endif

#include "pljava/InstallHelper.h"
#include "pljava/Backend.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/JNICalls.h"
#include "pljava/PgObject.h"
#include "pljava/type/String.h"

/*
 * Before 9.1, there was no creating_extension. Before 9.5, it did not have
 * PGDLLIMPORT and so was not visible in Windows. In either case, just define
 * it to be false, but also define CREATING_EXTENSION_HACK if on Windows and
 * it needs to be tested for in some roundabout way.
 */
#if PG_VERSION_NUM < 90100 || defined(_MSC_VER) && PG_VERSION_NUM < 90500
#define creating_extension false
#if PG_VERSION_NUM >= 90100
#define CREATING_EXTENSION_HACK
#endif
#endif

/*
 * Before 9.1, there was no IsBinaryUpgrade. Before 9.5, it did not have
 * PGDLLIMPORT and so was not visible in Windows. In either case, just define
 * it to be false; Windows users may have trouble using pg_upgrade to versions
 * earlier than 9.5, but with the current version being 9.6 that should be rare.
 */
#if PG_VERSION_NUM < 90100 || defined(_MSC_VER) && PG_VERSION_NUM < 90500
#define IsBinaryUpgrade false
#endif

/*
 * Before 9.3, there was no IsBackgroundWorker. As of 9.6.1 it still does not
 * have PGDLLIMPORT, but MyBgworkerEntry != NULL can be used in MSVC instead.
 * However, until 9.3.3, even that did not have PGDLLIMPORT, and there's not
 * much to be done about it. BackgroundWorkerness won't be detected in MSVC
 * for 9.3.0 through 9.3.2.
 *
 * One thing it's needed for is to avoid dereferencing MyProcPort in a
 * background worker, where it's not set. Define BGW_HAS_NO_MYPROCPORT if that
 * has to be (and can be) checked.
 */
#if PG_VERSION_NUM < 90300  ||  defined(_MSC_VER) && PG_VERSION_NUM < 90303
#define IsBackgroundWorker false
#else
#define BGW_HAS_NO_MYPROCPORT
#include <commands/dbcommands.h>
#if defined(_MSC_VER)
#include <postmaster/bgworker.h>
#define IsBackgroundWorker (MyBgworkerEntry != NULL)
#endif
#endif

#ifndef PLJAVA_SO_VERSION
#error "PLJAVA_SO_VERSION needs to be defined to compile this file."
#else
#define SO_VERSION_STRING CppAsString2(PLJAVA_SO_VERSION)
#endif

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

static bool extensionExNihilo = false;

static void checkLoadPath( bool *livecheck);
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
#ifdef BGW_HAS_NO_MYPROCPORT
	char *shortlived;
	static char *longlived;
	if ( IsBackgroundWorker )
	{
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
#endif
	return MyProcPort->database_name;
}

static char *origUserName()
{
#ifdef BGW_HAS_NO_MYPROCPORT
	if ( IsBackgroundWorker )
	{
#if PG_VERSION_NUM >= 90500
		char *shortlived;
		static char *longlived;
		if ( NULL == longlived )
		{
			shortlived = GetUserNameFromId(GetAuthenticatedUserId(), false);
			longlived = MemoryContextStrdup(TopMemoryContext, shortlived);
			pfree(shortlived);
		}
		return longlived;
#else
		ereport(ERROR, (
			errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
			errmsg("PL/Java in a background worker not supported "
				"in this PostgreSQL version"),
			errhint("PostgreSQL 9.5 is the first version to support "
				"PL/Java in a background worker.")));
#endif
	}
#endif
	return MyProcPort->user_name;
}

char const *pljavaClusterName()
{
	/*
	 * If PostgreSQL isn't at least 9.5, there can't BE a cluster name, and if
	 * it is, then there's always one (even if it is an empty string), so
	 * PG_GETCONFIGOPTION is safe.
	 */
#if PG_VERSION_NUM < 90500
	return "";
#else
	return PG_GETCONFIGOPTION("cluster_name");
#endif
}

void pljavaCheckExtension( bool *livecheck)
{
	if ( ! creating_extension )
	{
		checkLoadPath( livecheck);
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
static void checkLoadPath( bool *livecheck)
{
	List *l;
	Node *ut;
	LoadStmt *ls;
#if PG_VERSION_NUM >= 80300
	PlannedStmt *ps;
#else
	Query *ps;
#endif

#ifndef CREATING_EXTENSION_HACK
	if ( NULL != livecheck )
		return;
#endif
	if ( NULL == ActivePortal )
		return;
	l = ActivePortal->
#if PG_VERSION_NUM >= 80300
		stmts;
#else
		parseTrees;
#endif
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
#if PG_VERSION_NUM >= 80300
	if ( T_PlannedStmt == nodeTag(ut) )
	{
		ps = (PlannedStmt *)ut;
#else
	if ( T_Query == nodeTag(ut) )
	{
		ps = (Query *)ut;
#endif
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
#ifdef CREATING_EXTENSION_HACK
		if ( T_CreateExtensionStmt == nodeTag(ut) )
		{
			if ( NULL != livecheck )
			{
				*livecheck = true;
				return;
			}
			getExtensionLoadPath();
			if ( NULL != pljavaLoadPath )
				pljavaLoadingAsExtension = true;
		}
#endif
		return;
	if ( NULL != livecheck )
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
	return IsBackgroundWorker || IsBinaryUpgrade;
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

	Invocation_pushBootContext(&ctx);
	nativeVer = String_createJavaStringFromNTS(SO_VERSION_STRING);
	serverBuiltVer = String_createJavaStringFromNTS(PG_VERSION_STR);

#if PG_VERSION_NUM >= 90100
	InitFunctionCallInfoData(fcinfo, NULL, 0,
	InvalidOid, /* collation */
	NULL, NULL);
#else
	InitFunctionCallInfoData(fcinfo, NULL, 0,
	NULL, NULL);
#endif
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
#if PG_VERSION_NUM >= 80400
	if ( ! ActiveSnapshotSet() )
	{
		PushActiveSnapshot(GetTransactionSnapshot());
#else
	if ( NULL == ActiveSnapshot )
	{
		ActiveSnapshot = CopySnapshot(GetTransactionSnapshot());
#endif
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
#if PG_VERSION_NUM >= 80400
			PopActiveSnapshot();
#else
			ActiveSnapshot = NULL;
#endif
		}
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
		if ( snapshot_set )
		{
#if PG_VERSION_NUM >= 80400
			PopActiveSnapshot();
#else
			ActiveSnapshot = NULL;
#endif
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
