/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
#ifndef __pljava_VarlenaWrapper_h
#define __pljava_VarlenaWrapper_h

#include <postgres.h>
#include <utils/resowner.h>

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

extern jobject pljava_VarlenaWrapper_Input(
	Datum d, MemoryContext mc, ResourceOwner ro);

extern jobject pljava_VarlenaWrapper_Output(MemoryContext mc, ResourceOwner ro);

extern Datum pljava_VarlenaWrapper_adopt(jobject vlos);

extern void pljava_VarlenaWrapper_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
