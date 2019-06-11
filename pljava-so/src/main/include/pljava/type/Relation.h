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
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Relation_h
#define __pljava_Relation_h

#include "pljava/type/Type.h"
#ifdef __cplusplus
extern "C" {
#endif

#include <utils/rel.h>

/*******************************************************************
 * The Relation java class provides JNI
 * access to some of the attributes of the Relation structure.
 * 
 * @author Thomas Hallgren
 *******************************************************************/

/*
 * Create an instance of org.postgresql.pljava.Relation
 */
extern jobject pljava_Relation_create(Relation rel);
extern void pljava_Relation_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
