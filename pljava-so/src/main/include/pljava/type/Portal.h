/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
#ifndef __pljava_Portal_h
#define __pljava_Portal_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <utils/portal.h>

/*****************************************************************
 * The Portal java class provides JNI
 * access to some of the attributes of the Portal structure.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

extern void pljava_Portal_initialize(void);

/*
 * Create the org.postgresql.pljava.Portal instance
 */
extern jobject pljava_Portal_create(Portal portal, jobject jplan);

#ifdef __cplusplus
}
#endif
#endif
