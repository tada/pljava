/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"

static Type s_float;	/* Primitive (scalar) type */
static TypeClass s_floatClass;
static Type s_Float;	/* Object type */
static TypeClass s_FloatClass;

static jclass    s_Float_class;
static jmethodID s_Float_init;
static jmethodID s_Float_floatValue;

/*
 * float primitive type.
 */
static Datum _float_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jfloat fv = (*env)->CallStaticFloatMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	return Float4GetDatum(fv);
}

static jvalue _float_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.f = DatumGetFloat4(arg);
	return result;
}

static Type float_obtain(Oid typeId)
{
	return s_float;
}

/*
 * java.lang.Float type.
 */
static bool _Float_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_floatClass;
}

static jvalue _Float_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Float_class, s_Float_init, DatumGetFloat4(arg));
	return result;
}

static Datum _Float_coerceObject(Type self, JNIEnv* env, jobject floatObj)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jfloat fv = (*env)->CallFloatMethod(env, floatObj, s_Float_floatValue);
	isCallingJava = saveicj;
	return Float4GetDatum(fv);
}

static Type Float_obtain(Oid typeId)
{
	return s_Float;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Float_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Float_initialize);
Datum Float_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Float_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Float"));

	s_Float_init = PgObject_getJavaMethod(
				env, s_Float_class, "<init>", "(F)V");

	s_Float_floatValue = PgObject_getJavaMethod(
				env, s_Float_class, "floatValue", "()F");

	s_FloatClass = TypeClass_alloc("type.Float");
	s_FloatClass->canReplaceType = _Float_canReplace;
	s_FloatClass->JNISignature   = "Ljava/lang/Float;";
	s_FloatClass->javaTypeName   = "java.lang.Float";
	s_FloatClass->coerceDatum    = _Float_coerceDatum;
	s_FloatClass->coerceObject   = _Float_coerceObject;
	s_Float = TypeClass_allocInstance(s_FloatClass, FLOAT4OID);

	s_floatClass = TypeClass_alloc("type.float");
	s_floatClass->JNISignature   = "F";
	s_floatClass->javaTypeName   = "float";
	s_floatClass->objectType     = s_Float;
	s_floatClass->invoke         = _float_invoke;
	s_floatClass->coerceDatum    = _float_coerceDatum;
	s_floatClass->coerceObject   = _Float_coerceObject;
	s_float = TypeClass_allocInstance(s_floatClass, FLOAT4OID);

	Type_registerPgType(FLOAT4OID, float_obtain);
	Type_registerJavaType("float", float_obtain);
	Type_registerJavaType("java.lang.Float", Float_obtain);
	PG_RETURN_VOID();
}
