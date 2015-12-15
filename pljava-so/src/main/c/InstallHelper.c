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
#if PGSQL_MAJOR_VER > 9 || PGSQL_MAJOR_VER == 9 && PGSQL_MINOR_VER >= 3
#include <access/htup_details.h>
#else
#include <access/htup.h>
#endif
#include <catalog/pg_language.h>
#include <catalog/pg_proc.h>
#include <commands/portalcmds.h>
#include <miscadmin.h>
#include <libpq/libpq-be.h>
#include <tcop/pquery.h>
#include <utils/builtins.h>
#include <utils/memutils.h>
#include <utils/syscache.h>

#ifndef SearchSysCache1
#define SearchSysCache1(cid, k1) SearchSysCache(cid, k1, 0, 0, 0)
#endif

#include "pljava/InstallHelper.h"
#include "pljava/Invocation.h"
#include "pljava/JNICalls.h"
#include "pljava/PgObject.h"
#include "pljava/type/String.h"

#ifndef PLJAVA_SO_VERSION
#error "PLJAVA_SO_VERSION needs to be defined to compile this file."
#else
/*
 * CppAsString2 first appears in PG8.4.  IF that's a problem, the definition
 * is really simple.
 */
#define SO_VERSION_STRING CppAsString2(PLJAVA_SO_VERSION)
#endif

static jclass s_InstallHelper_class;
static jmethodID s_InstallHelper_hello;

char const *pljavaLoadPath = NULL;

Oid pljavaTrustedOid = InvalidOid;

Oid pljavaUntrustedOid = InvalidOid;

char *pljavaDbName()
{
	return MyProcPort->database_name;
}

void pljavaCheckLoadPath()
{
	if ( NULL == ActivePortal )
		return;
	List *l = ActivePortal->stmts;
	if ( NULL == l )
		return;
	if ( 1 < list_length( l) )
		elog(DEBUG1, "ActivePortal lists %d statements", list_length( l));
	Node *ut = (Node *)linitial(l);
	if ( NULL == ut )
	{
		elog(DEBUG1, "got null for first statement from ActivePortal");
		return;
	}
	if ( T_LoadStmt != nodeTag(ut) )
		return;
	LoadStmt *ls = (LoadStmt *)ut;
	if ( NULL == ls->filename )
	{
		elog(DEBUG1, "got null for a LOAD statement's filename");
		return;
	}
	pljavaLoadPath =
		(char const *)MemoryContextStrdup( TopMemoryContext, ls->filename);
}

char *pljavaFnOidToLibPath(Oid myOid)
{
	bool isnull;
	char *result;
	HeapTuple myPT = SearchSysCache1(PROCOID, ObjectIdGetDatum(myOid));
	if (!HeapTupleIsValid(myPT))
		elog(ERROR, "cache lookup failed for function %u", myOid);
	Form_pg_proc myPS = (Form_pg_proc) GETSTRUCT(myPT);
	Oid langId = myPS->prolang;
	ReleaseSysCache(myPT);
	HeapTuple langTup = SearchSysCache1(LANGOID, ObjectIdGetDatum(langId));
	if (!HeapTupleIsValid(langTup))
		elog(ERROR, "cache lookup failed for language %u", langId);
	Form_pg_language langSt = (Form_pg_language) GETSTRUCT(langTup);
	Oid handlerOid = langSt->lanplcallfoid;
	ReleaseSysCache(langTup);
	HeapTuple handlerPT =
		SearchSysCache1(PROCOID, ObjectIdGetDatum(handlerOid));
	if (!HeapTupleIsValid(handlerPT))
		elog(ERROR, "cache lookup failed for function %u", handlerOid);
	Datum probinattr =
		SysCacheGetAttr(PROCOID, handlerPT, Anum_pg_proc_probin, &isnull);
	if ( isnull )
		elog(ERROR, "null probin for C function %u", handlerOid);
	char *probinstring = TextDatumGetCString(probinattr);
	result = pstrdup( probinstring);
	pfree(probinstring);
	ReleaseSysCache(handlerPT);
	return result;
}

char *InstallHelper_hello()
{
	char pathbuf[MAXPGPATH];
	Invocation ctx;
	Invocation_pushBootContext(&ctx);
	jstring nativeVer = String_createJavaStringFromNTS(SO_VERSION_STRING);
	jstring user = String_createJavaStringFromNTS(MyProcPort->user_name);
	jstring dbname = String_createJavaStringFromNTS(MyProcPort->database_name);
	jstring ddir = String_createJavaStringFromNTS(DataDir);
	jstring ldir = String_createJavaStringFromNTS(pkglib_path);

	get_share_path(my_exec_path, pathbuf);
	jstring sdir = String_createJavaStringFromNTS(pathbuf);

	get_etc_path(my_exec_path, pathbuf);
	jstring edir = String_createJavaStringFromNTS(pathbuf);

	jstring hi = JNI_callStaticObjectMethod(
		s_InstallHelper_class, s_InstallHelper_hello,
		nativeVer, user, dbname, ddir, ldir, sdir, edir);

	JNI_deleteLocalRef(nativeVer);
	JNI_deleteLocalRef(user);
	JNI_deleteLocalRef(dbname);
	JNI_deleteLocalRef(ddir);
	JNI_deleteLocalRef(ldir);
	JNI_deleteLocalRef(sdir);
	JNI_deleteLocalRef(edir);
	char *hiC = String_createNTS(hi);
	JNI_deleteLocalRef(hi);
	Invocation_popBootContext();
	return hiC;
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
}
