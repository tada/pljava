/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
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
#ifndef __pljava_Backend_h
#define __pljava_Backend_h

#include "pljava/Function.h"

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Backend contains the call handler, initialization of the
 * PL/Java, access to config variables, and logging.
 * 
 * @author Thomas Hallgren
 *****************************************************************/
#ifndef PLJAVA_SO_VERSION
#error "PLJAVA_SO_VERSION needs to be defined to compile this file."
#else
#define SO_VERSION_STRING CppAsString2(PLJAVA_SO_VERSION)
#endif

#if PG_VERSION_NUM < 100000
extern bool integerDateTimes;
#endif

int Backend_setJavaLogLevel(int logLevel);

/*
 * Called at the ends of committing transactions to emit a warning about future
 * JEP 411 impacts, at most once per session, if any PL/Java functions were
 * declared or redeclared in the transaction, or if PL/Java was installed or
 * upgraded. Also called from InstallHelper, if pg_upgrade is happening. Yes,
 * this is a bit tangled. The tracking of function declaration and
 * install/upgrade is encapsulated in Backend.c. If isCommit is false,
 * no warning is emitted, and the tracking bit is reset.
 */
void Backend_warnJEP411(bool isCommit);

#ifdef PG_GETCONFIGOPTION
#error The macro PG_GETCONFIGOPTION needs to be renamed.
#endif

#define PG_GETCONFIGOPTION(key) GetConfigOption(key, false, true)

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
