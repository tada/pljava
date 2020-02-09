/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include <postgres.h>
#include <utils/memutils.h>
#include <utils/numeric.h>

#include "pljava/type/Type_priv.h"

/*
 * void primitive type.
 */
static Datum _void_invoke(Type self, Function fn, PG_FUNCTION_ARGS)
{
	pljava_Function_voidInvoke(fn);
	fcinfo->isnull = true;
	return 0;
}

static jvalue _void_coerceDatum(Type self, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _void_coerceObject(Type self, jobject nothing)
{
	return 0;
}

/* Make this datatype available to the postgres system.
 */
extern void Void_initialize(void);
void Void_initialize(void)
{
	TypeClass cls = TypeClass_alloc("type.void");
	cls->JNISignature = "V";
	cls->javaTypeName = "void";
	cls->invoke       = _void_invoke;
	cls->coerceDatum  = _void_coerceDatum;
	cls->coerceObject = _void_coerceObject;
	Type_registerType("void", TypeClass_allocInstance(cls, VOIDOID));
}
