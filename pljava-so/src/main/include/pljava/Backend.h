/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
#if PG_VERSION_NUM < 100000
extern bool integerDateTimes;
#endif

int Backend_setJavaLogLevel(int logLevel);

#ifdef PG_GETCONFIGOPTION
#error The macro PG_GETCONFIGOPTION needs to be renamed.
#endif

#if PG_VERSION_NUM >= 90100
#define PG_GETCONFIGOPTION(key) GetConfigOption(key, false, true)
#elif PG_VERSION_NUM >= 90000
#define PG_GETCONFIGOPTION(key) GetConfigOption(key, true)
#else
#define PG_GETCONFIGOPTION(key) GetConfigOption(key)
#endif

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
