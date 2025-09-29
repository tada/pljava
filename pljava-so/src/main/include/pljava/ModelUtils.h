/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

extern void pljava_ModelUtils_inlineDispatch(PG_FUNCTION_ARGS);

extern Datum pljava_ModelUtils_callDispatch(PG_FUNCTION_ARGS, bool validating);

extern void pljava_ResourceOwner_unregister(void);

/*
 * Return a Java TupleDescriptor based on a PostgreSQL one.
 *
 * If the descriptor's tdtypeid is not RECORDOID (meaning the descriptor is
 * for a named composite type), passing the relation oid here, if handy, will
 * save a lookup in the Java code. In other cases, or if it simply is not
 * handily available, InvalidOid can be passed, and the relation will be looked
 * up if needed.
 *
 * If there is already a cached Java representation, the existing one
 * is returned, and the supplied one's reference count (if it is counted) is
 * untouched. If the supplied one is used to create a cached Java version, its
 * reference count is incremented (without registering it for descriptor leak
 * warnings), and it will be released upon removal from PL/Java's cache for
 * invalidation or unreachability. If the descriptor is non-reference-counted,
 * the returned Java object will not depend on it, and it is expendable
 * after this function returns.
 */
extern jobject pljava_TupleDescriptor_create(TupleDesc tupdesc,  Oid reloid);

/*
 * Create a PostgreSQL TupleTableSlot (of the specific type specified by
 * tts_ops) and return a Java TupleTableSlot wrapping it.
 *
 * reloid is simply passed along to pljava_TupleDescriptor_create, so may be
 * passed as InvalidOid with the same effects described there.
 *
 * If jtd is not NULL, it must be a JNI local reference to an existing Java
 * TupleDescriptor that corresponds to the native tupdesc, and will be used
 * instead of calling pljava_TupleDescriptor_create. On return, the local
 * reference will have been deleted.
 */
extern jobject pljava_TupleTableSlot_create(
	TupleDesc tupdesc, jobject jtd,
	const TupleTableSlotOps *tts_ops, Oid reloid);

#ifdef __cplusplus
}
#endif
#endif
