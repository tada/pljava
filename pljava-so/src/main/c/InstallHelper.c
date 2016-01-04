/*
 * Copyright (c) 2015 Tada AB and other contributors, as listed below.
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
#include <utils/syscache.h>

#if PG_VERSION_NUM < 90000
#define SearchSysCache1(cid, k1) SearchSysCache(cid, k1, 0, 0, 0)
#define GetSysCacheOid1(cid, k1) GetSysCacheOid(cid, k1, 0, 0, 0)
#endif

#include "pljava/InstallHelper.h"
#include "pljava/Function.h"
#include "pljava/Invocation.h"
#include "pljava/JNICalls.h"
#include "pljava/PgObject.h"
#include "pljava/type/String.h"

/*
 * CppAsString2 first appears in PG8.4.  Once the compatibility target reaches
 * 8.4, this fallback will not be needed.
 */
#ifndef CppAsString2
#define CppAsString2(x) CppAsString(x)
#endif

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

#ifndef PLJAVA_SO_VERSION
#error "PLJAVA_SO_VERSION needs to be defined to compile this file."
#else
#define SO_VERSION_STRING CppAsString2(PLJAVA_SO_VERSION)
#endif

static jclass s_InstallHelper_class;
static jmethodID s_InstallHelper_hello;
static jmethodID s_InstallHelper_groundwork;

static void checkLoadPath( bool *livecheck);
static void getExtensionLoadPath();

char const *pljavaLoadPath = NULL;

bool pljavaLoadingAsExtension = false;

Oid pljavaTrustedOid = InvalidOid;

Oid pljavaUntrustedOid = InvalidOid;

char *pljavaDbName()
{
	return MyProcPort->database_name;
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

#ifndef CREATING_EXTENSION_HACK
	if ( NULL != livecheck )
		return;
#endif
	if ( NULL == ActivePortal )
		return;
	l = ActivePortal->stmts;
	if ( NULL == l )
		return;
	if ( 1 < list_length( l) )
		elog(DEBUG1, "ActivePortal lists %d statements", list_length( l));
	ut = (Node *)linitial(l);
	if ( NULL == ut )
	{
		elog(DEBUG1, "got null for first statement from ActivePortal");
		return;
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
		elog(DEBUG1, "got null for a LOAD statement's filename");
		return;
	}
	pljavaLoadPath =
		(char const *)MemoryContextStrdup( TopMemoryContext, ls->filename);
}

static void getExtensionLoadPath()
{
	MemoryContext curr;

	/*
	 * Check whether sqlj.loadpath exists before querying it. I would more
	 * happily just PG_CATCH() the error and compare to ERRCODE_UNDEFINED_TABLE
	 * but what's required to make that work right is "not terribly well
	 * documented, but the exception-block handling in plpgsql provides a
	 * working model" and that code is a lot more fiddly than you would guess.
	 */
	if ( InvalidOid == get_relname_relid("loadpath",
		GetSysCacheOid1(NAMESPACENAME, CStringGetDatum("sqlj"))) )
		return;

	SPI_connect();
	curr = CurrentMemoryContext;
	if ( SPI_OK_SELECT == SPI_execute(
		"SELECT s FROM sqlj.loadpath", true, 1) && 1 == SPI_processed )
	{
		MemoryContextSwitchTo(TopMemoryContext);
		pljavaLoadPath = (char const *)SPI_getvalue(
			SPI_tuptable->vals[0], SPI_tuptable->tupdesc, 1);
		MemoryContextSwitchTo(curr);
	}
	SPI_finish();
}

char *pljavaFnOidToLibPath(Oid myOid)
{
	bool isnull;
	char *result;
	HeapTuple myPT = SearchSysCache1(PROCOID, ObjectIdGetDatum(myOid));
	Form_pg_proc myPS;
	Oid langId;
	HeapTuple langTup;
	Form_pg_language langSt;
	Oid handlerOid;
	HeapTuple handlerPT;
	Datum probinattr;
	char *probinstring;

	if (!HeapTupleIsValid(myPT))
		elog(ERROR, "cache lookup failed for function %u", myOid);
	myPS = (Form_pg_proc) GETSTRUCT(myPT);
	langId = myPS->prolang;
	ReleaseSysCache(myPT);
	langTup = SearchSysCache1(LANGOID, ObjectIdGetDatum(langId));
	if (!HeapTupleIsValid(langTup))
		elog(ERROR, "cache lookup failed for language %u", langId);
	langSt = (Form_pg_language) GETSTRUCT(langTup);
	handlerOid = langSt->lanplcallfoid;
	ReleaseSysCache(langTup);
	handlerPT =
		SearchSysCache1(PROCOID, ObjectIdGetDatum(handlerOid));
	if (!HeapTupleIsValid(handlerPT))
		elog(ERROR, "cache lookup failed for function %u", handlerOid);
	probinattr =
		SysCacheGetAttr(PROCOID, handlerPT, Anum_pg_proc_probin, &isnull);
	if ( isnull )
		elog(ERROR, "null probin for C function %u", handlerOid);
	probinstring = TextDatumGetCString(probinattr);
	result = pstrdup( probinstring);
	pfree(probinstring);
	ReleaseSysCache(handlerPT);
	return result;
}

char const *InstallHelper_defaultClassPath(char *pathbuf)
{
	char * const pbend = pathbuf + MAXPGPATH;
	char *pbp = pathbuf;
	size_t remaining;
	size_t verlen = strlen(SO_VERSION_STRING);

	get_share_path(my_exec_path, pathbuf);
	join_path_components(pathbuf, pathbuf, "pljava");
	join_path_components(pathbuf, pathbuf, "pljava-");

	for ( ; pbp < pbend && '\0' != *pbp ; ++ pbp )
		;
	if ( pbend == pbp )
		return NULL;

	remaining = pbend - pbp;
	if ( remaining < verlen + 5 )
		return NULL;

	snprintf(pbp, remaining, "%s.jar", SO_VERSION_STRING);
	return pathbuf;
}

char *InstallHelper_hello()
{
	char pathbuf[MAXPGPATH];
	Invocation ctx;
	jstring nativeVer;
	jstring user;
	jstring dbname;
	jstring ddir;
	jstring ldir;
	jstring sdir;
	jstring edir;
	jstring greeting;
	char *greetingC;

	Invocation_pushBootContext(&ctx);
	nativeVer = String_createJavaStringFromNTS(SO_VERSION_STRING);
	user = String_createJavaStringFromNTS(MyProcPort->user_name);
	dbname = String_createJavaStringFromNTS(MyProcPort->database_name);
	ddir = String_createJavaStringFromNTS(DataDir);

	get_pkglib_path(my_exec_path, pathbuf);
	ldir = String_createJavaStringFromNTS(pathbuf);

	get_share_path(my_exec_path, pathbuf);
	sdir = String_createJavaStringFromNTS(pathbuf);

	get_etc_path(my_exec_path, pathbuf);
	edir = String_createJavaStringFromNTS(pathbuf);

	greeting = JNI_callStaticObjectMethod(
		s_InstallHelper_class, s_InstallHelper_hello,
		nativeVer, user, dbname, ddir, ldir, sdir, edir);

	JNI_deleteLocalRef(nativeVer);
	JNI_deleteLocalRef(user);
	JNI_deleteLocalRef(dbname);
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
	Invocation_pushInvocation(&ctx, false);
	ctx.function = Function_INIT_WRITER;
	PG_TRY();
	{
		jstring pljlp = String_createJavaStringFromNTS(pljavaLoadPath);
		JNI_callStaticObjectMethod(
			s_InstallHelper_class, s_InstallHelper_groundwork, pljlp);
		Invocation_popInvocation(false);
	}
	PG_CATCH();
	{
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
		"Ljava/lang/String;)Ljava/lang/String;");
	s_InstallHelper_groundwork = PgObject_getStaticJavaMethod(
		s_InstallHelper_class, "groundwork", "(Ljava/lang/String;)V");
}
