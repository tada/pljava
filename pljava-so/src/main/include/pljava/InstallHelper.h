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
 * If a LoadStatement is what the current ActivePortal is executing, then save
 * a copy of the pathname being loaded (pstrdup'd in TopMemoryContext) in
 * pljavaLoadPath, otherwise leave that variable unchanged/NULL. Nothing like
 * this would be necessary if PostgreSQL called _PG_init functions with the
 * path of the library being loaded.
 */
extern void pljavaCheckLoadPath();

extern char const *pljavaLoadPath;

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
