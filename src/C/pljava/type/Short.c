/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"

static Type s_short;	/* Primitive (scalar) type */
static TypeClass s_shortClass;
static Type s_Short;	/* Object type */
static TypeClass s_ShortClass;

static jclass    s_Short_class;
static jmethodID s_Short_init;
static jmethodID s_Short_shortValue;

/*
 * short primitive type.
 */
static Datum _short_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jshort sv = (*env)->CallStaticShortMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	return Int16GetDatum(sv);
}

static jvalue _short_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.s = DatumGetInt16(arg);
	return result;
}

static Type short_obtain(Oid typeId)
{
	return s_short;
}

/*
 * java.lang.Short type.
 */
static bool _Short_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_shortClass;
}

static jvalue _Short_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Short_class, s_Short_init, DatumGetInt16(arg));
	return result;
}

static Datum _Short_coerceObject(Type self, JNIEnv* env, jobject shortObj)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jshort sv = (*env)->CallShortMethod(env, shortObj, s_Short_shortValue);
	isCallingJava = saveicj;
	return Int16GetDatum(sv);
}

static Type Short_obtain(Oid typeId)
{
	return s_Short;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Short_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Short_initialize);
Datum Short_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Short_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Short"));

	s_Short_init = PgObject_getJavaMethod(
				env, s_Short_class, "<init>", "(S)V");

	s_Short_shortValue = PgObject_getJavaMethod(
				env, s_Short_class, "shortValue", "()S");

	s_ShortClass = TypeClass_alloc("type.Short");
	s_ShortClass->canReplaceType = _Short_canReplace;
	s_ShortClass->JNISignature   = "Ljava/lang/Short;";
	s_ShortClass->javaTypeName   = "java.lang.Short";
	s_ShortClass->coerceDatum    = _Short_coerceDatum;
	s_ShortClass->coerceObject   = _Short_coerceObject;
	s_Short = TypeClass_allocInstance(s_ShortClass, INT2OID);

	s_shortClass = TypeClass_alloc("type.short");
	s_shortClass->JNISignature   = "S";
	s_shortClass->javaTypeName   = "short";
	s_shortClass->objectType     = s_Short;
	s_shortClass->invoke         = _short_invoke;
	s_shortClass->coerceDatum    = _short_coerceDatum;
	s_shortClass->coerceObject   = _Short_coerceObject;
	s_short = TypeClass_allocInstance(s_shortClass, INT2OID);

	Type_registerPgType(INT2OID, short_obtain);
	Type_registerJavaType("short", short_obtain);
	Type_registerJavaType("java.lang.Short", Short_obtain);
	PG_RETURN_VOID();
}
