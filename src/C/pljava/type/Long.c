/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"

static Type s_long;	/* Primitive (scalar) type */
static TypeClass s_longClass;
static Type s_Long;	/* Object type */
static TypeClass s_LongClass;

static jclass    s_Long_class;
static jmethodID s_Long_init;
static jmethodID s_Long_longValue;

/*
 * long primitive type.
 */
static Datum _long_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, bool* wasNull)
{
	return Int64GetDatum((*env)->CallStaticLongMethodA(env, cls, method, args));
}

static jvalue _long_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.j = DatumGetInt64(arg);
	return result;
}

static Type long_obtain(Oid typeId)
{
	return s_long;
}

/*
 * java.lang.Long type.
 */
static bool _Long_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_longClass;
}

static jvalue _Long_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = (*env)->NewObject(env, s_Long_class, s_Long_init, DatumGetInt64(arg));
	return result;
}

static Datum _Long_coerceObject(Type self, JNIEnv* env, jobject longObj)
{
	return Int64GetDatum((*env)->CallLongMethod(env, longObj, s_Long_longValue));
}

static Type Long_obtain(Oid typeId)
{
	return s_Long;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Long_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Long_initialize);
Datum Long_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Long_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Long"));

	s_Long_init = PgObject_getJavaMethod(
				env, s_Long_class, "<init>", "(J)V");

	s_Long_longValue = PgObject_getJavaMethod(
				env, s_Long_class, "longValue", "()J");

	s_LongClass = TypeClass_alloc("type.Long");
	s_LongClass->canReplaceType = _Long_canReplace;
	s_LongClass->JNISignature   = "Ljava/lang/Long;";
	s_LongClass->javaTypeName   = "java.lang.Long";
	s_LongClass->coerceObject   = _Long_coerceObject;
	s_LongClass->coerceDatum    = _Long_coerceDatum;
	s_Long = TypeClass_allocInstance(s_LongClass);

	s_longClass = TypeClass_alloc("type.long");
	s_longClass->JNISignature   = "J";
	s_longClass->javaTypeName   = "long";
	s_longClass->objectType     = s_Long;
	s_longClass->invoke         = _long_invoke;
	s_longClass->coerceDatum    = _long_coerceDatum;
	s_longClass->coerceObject   = _Long_coerceObject;
	s_long = TypeClass_allocInstance(s_longClass);

	Type_registerPgType(INT8OID, long_obtain);
	Type_registerJavaType("long", long_obtain);
	Type_registerJavaType("java.lang.Long", Long_obtain);
	PG_RETURN_VOID();
}
