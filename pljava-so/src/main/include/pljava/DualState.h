/*
 * Copyright (c) 2018-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
#ifndef __pljava_DualState_h
#define __pljava_DualState_h

#include <postgres.h>
#include <utils/resowner.h>

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

extern jobject pljava_DualState_key(void);

extern void pljava_DualState_cleanEnqueuedInstances(void);

extern void pljava_DualState_initialize(void);

extern void pljava_DualState_unregister(void);

extern void pljava_DualState_nativeRelease(void *);

#ifdef __cplusplus
}
#endif
#endif
