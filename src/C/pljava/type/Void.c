/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/memutils.h>
#include <utils/numeric.h>

#include "pljava/type/Type_priv.h"

/*
 * void primitive type.
 */
static TypeClass s_voidClass;
static Type s_void;

static Datum _void_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	JNI_callStaticVoidMethodA(cls, method, args);
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

static Type void_obtain(Oid typeId)
{
	return s_void;
}

/* Make this datatype available to the postgres system.
 */
extern void Void_initialize(void);
void Void_initialize(void)
{
	s_voidClass = TypeClass_alloc("type.void");
	s_voidClass->JNISignature = "V";
	s_voidClass->javaTypeName = "void";
	s_voidClass->invoke       = _void_invoke;
	s_voidClass->coerceDatum  = _void_coerceDatum;
	s_voidClass->coerceObject = _void_coerceObject;
	s_void = TypeClass_allocInstance(s_voidClass, VOIDOID);

	Type_registerType(VOIDOID, "void", void_obtain);
}
