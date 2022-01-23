/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
#ifndef __pljava_ModelUtils_h
#define __pljava_ModelUtils_h

#include <postgres.h>
#include <access/tupdesc.h>
#include <executor/tuptable.h>

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

extern void pljava_ModelUtils_initialize(void);

extern void pljava_ResourceOwner_unregister(void);

#ifdef __cplusplus
}
#endif
#endif
