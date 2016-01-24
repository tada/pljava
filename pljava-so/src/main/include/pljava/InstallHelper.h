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

/*
 * InstallHelper is a place to put functions that are useful for improving the
 * user experience in first setting up PL/Java, but may also involve diving
 * deeper into PostgreSQL internals than is common for PL/Java (just to dig out
 * values that aren't more directly exposed), so those internal .h files can be
 * included only in InstallHelper.c and will not clutter most other code.
 */

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
 */
extern char *pljavaFnOidToLibPath(Oid fn);

extern Oid pljavaTrustedOid, pljavaUntrustedOid;

/*
 * Return the name of the current database, from MyProcPort ... don't free it.
 */
extern char *pljavaDbName();

/*
 * Return the name of the cluster if it has been set (only possible in 9.5+),
 * or an empty string, never NULL.
 */
extern char const *pljavaClusterName();

/*
 * Construct a default for pljava.classpath ($sharedir/pljava/pljava-$VER.jar)
 * in pathbuf (which must have length at least MAXPGPATH), and return pathbuf,
 * or NULL if the constructed path would not fit.
 */
extern char const *InstallHelper_defaultClassPath(char *);

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
extern bool pljavaViableXact();

extern char *InstallHelper_hello();

extern void InstallHelper_groundwork();

extern void InstallHelper_initialize();
