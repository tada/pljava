/*
 * Copyright (c) 2015-2021 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

/*
 * InstallHelper is a place to put functions that are useful for improving the
 * user experience in first setting up PL/Java, but may also involve diving
 * deeper into PostgreSQL internals than is common for PL/Java (just to dig out
 * values that aren't more directly exposed), so those internal .h files can be
 * included only in InstallHelper.c and will not clutter most other code.
 */

/*
 * CppAsString2 first appears in PG8.4.  Once the compatibility target reaches
 * 8.4, this fallback will not be needed. Used in InstallHelper and Backend,
 * both of which include this file.
 */
#ifndef CppAsString2
#define CppAsString2(x) CppAsString(x)
#endif

/*
 * The path from which this library is being loaded, which is surprisingly
 * tricky to find (and wouldn't be, if PostgreSQL called _PG_init functions
 * with the path of the library being loaded!). Set by pljavaCheckExtension().
 */
extern char const *pljavaLoadPath;

/*
 * If an extension is being created, try to determine pljavaLoadPath from a
 * temporary table in the sqlj schema; if it's there, created by PL/Java's
 * extension script, then the extension being created is PL/Java itself, so
 * set pljavaLoadingAsExtension and pljavaLoadPath accordingly. Otherwise
 * PL/Java is just being mentioned while creating some other extension.
 * If an extension is not being created, just check for a LOAD command and
 * set pljavaLoadPath accordingly.
 *
 * When called from _PG_init, which only calls once, the argument is null,
 * indicating that the static result variables should be set. If the address of
 * a boolean is provided, the static variables are not set, and the supplied
 * boolean is set true if an extension is being created. (It is not touched if
 * an extension is not being created.) That serves the case
 * where PL/Java is already loaded, sqlj.install_jar has been called, and needs
 * to know if the jar is being installed as part of an(other) extension. Such
 * PL/Java-managed extensions aren't supported yet, but the case has to be
 * recognized, even if only to say "you can't do that yet."
 */
extern void pljavaCheckExtension(bool*);

extern bool pljavaLoadingAsExtension;

/*
 * Another way of getting the library path: if invoked by the fmgr before
 * initialization is complete, save the last function Oid seen (trusted or
 * untrusted) ... can be used later to get the library path if needed.
 * isPLJavaFunction can use the stashed information to determine whether an
 * arbitrary function Oid is a function built on PL/Java, without relying on
 * assumptions about the language name, etc.
 *
 * It can return the language name and/or trusted flag if non-null pointers
 * are supplied, as it will be looking up the language anyway.
 */
extern char *pljavaFnOidToLibPath(Oid fn, char **langName, bool *trusted);

extern Oid pljavaTrustedOid, pljavaUntrustedOid;

extern bool InstallHelper_isPLJavaFunction(
	Oid fn, char **langName, bool *trusted);

/*
 * Return the name of the current database, from MyProcPort ... don't free it.
 * In a background or autovacuum worker, there's no MyProcPort, and the name is
 * found another way and strdup'd in TopMemoryContext. It'll keep; don't bother
 * freeing it.
 */
extern char *pljavaDbName(void);

/*
 * Return the name of the cluster if it has been set (only possible in 9.5+),
 * or an empty string, never NULL.
 */
extern char const *pljavaClusterName(void);

/*
 * Construct a default for pljava.module_path ($sharedir/pljava/pljava-$VER.jar
 * and pljava-api-$VER.jar) in pathbuf (which must have length at least
 * MAXPGPATH), and return pathbuf, or NULL if the constructed path would not
 * fit. (pathbuf, pathSepChar).
 */
extern char const *InstallHelper_defaultModulePath(char *, char);

/*
 * Return true if in a 'viable' transaction (not aborted or abort pending).
 * The assign hooks can do two things: 1. they assign the variable values
 * (no surprises there, and nothing to go wrong). 2. they *can* re-enter the
 * init sequencer, if it has not completed the sequence, to see if the new
 * value helped--otherwise, there really isn't an easy way to try to resume
 * it, because LOAD/_PG_init only give you one shot per session. This is a use
 * of the assign-hook machinery not envisioned in its design, and it should be
 * very rare in practice (i.e., only in a session where a superuser is poking
 * about to install a new PL/Java), and in that context it can make the
 * installation process much less frustrating. BUT: a very important limit on
 * the behavior of an assign hook is that it might be called during abort of
 * a transaction, and must not do things that could throw errors and disrupt
 * the rollback. That's a very big BUT, because the init sequencer can do all
 * sorts of things that might throw errors. On the other hand, it doesn't NEED
 * to be reentered at all: because that is purely a user-experience convenience,
 * we are totally free to skip it if the assign hook has been called in the
 * context of an aborting transaction, and then there is nothing to go wrong
 * and disrupt the abort. The trickiest bit was finding available API to
 * recognize the ABORT_PENDING cases.
 */
extern bool pljavaViableXact(void);

/*
 * Backend's initsequencer needs to know whether it's being called in a 9.3+
 * background worker process, or during a pg_upgrade (in either case, the
 * init sequence needs to be lazier). Those should both be simple tests of
 * IsBackgroundWorker or IsBinaryUpgrade, except (wouldn't you know) for more
 * version-specific Windows visibility issues, so the ugly details are in
 * InstallHelper, and Backend just asks this nice function.
 */
extern bool InstallHelper_shouldDeferInit(void);

/*
 * Emit a debug message as early as possible with the native code's version
 * and build information. A nicer message is produced later by hello and
 * includes both the native and Java versions, but that's too late if something
 * goes wrong first.
 */
extern void InstallHelper_earlyHello(void);

/*
 * Perform early setup needed on every start (properties, security policy, etc.)
 * and also construct and return a string of native code, Java code, and JVM
 * version and build information, to be included in the "PL/Java loaded"
 * message.
 */
extern char *InstallHelper_hello(void);

/*
 * Called only when the loading is directly due to CREATE EXTENSION or LOAD, and
 * not simply to service a PL/Java function; checks for, and populates or brings
 * up to date, as needed, the sqlj schema and its contents.
 */
extern void InstallHelper_groundwork(void);

extern void InstallHelper_initialize(void);
