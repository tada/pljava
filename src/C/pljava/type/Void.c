/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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

static Datum _void_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, bool* wasNull)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	(*env)->CallStaticVoidMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	*wasNull = true;
	return 0;
}

static jvalue _void_coerceDatum(Type self, JNIEnv* env, Datum nothing)
{
	jvalue result;
	result.j = 0L;
	return result;
}

static Datum _void_coerceObject(Type self, JNIEnv* env, jobject nothing)
{
	return 0;
}

static Type void_obtain(Oid typeId)
{
	return s_void;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Void_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Void_initialize);
Datum Void_initialize(PG_FUNCTION_ARGS)
{
	s_voidClass = TypeClass_alloc("type.void");
	s_voidClass->JNISignature = "V";
	s_voidClass->javaTypeName = "void";
	s_voidClass->invoke       = _void_invoke;
	s_voidClass->coerceDatum  = _void_coerceDatum;
	s_voidClass->coerceObject = _void_coerceObject;
	s_void = TypeClass_allocInstance(s_voidClass);

	Type_registerPgType(VOIDOID, void_obtain);
	Type_registerJavaType("void", void_obtain);
	PG_RETURN_VOID();
}
