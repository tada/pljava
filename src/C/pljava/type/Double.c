/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"
#include "pljava/SPI.h"

static Type s_double;	/* Primitive (scalar) type */
static TypeClass s_doubleClass;
static Type s_Double;	/* Object type */
static TypeClass s_DoubleClass;

static jclass    s_Double_class;
static jmethodID s_Double_init;
static jmethodID s_Double_doubleValue;

/*
 * double primitive type.
 */
static Datum _double_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jdouble dv = (*env)->CallStaticDoubleMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	
	/* Since we don't know if 64 bit quantities are passed by reference or
	 * by value, we have to make sure that the correct context is used if
	 * it's the former.
	 */
	MemoryContext currCtx = SPI_switchToReturnValueContext();
	Datum ret = Float8GetDatum(dv);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static jvalue _double_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.d = DatumGetFloat8(arg);
	return result;
}

static Type double_obtain(Oid typeId)
{
	return s_double;
}

/*
 * java.lang.Double type.
 */
static bool _Double_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_doubleClass;
}

static jvalue _Double_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Double_class, s_Double_init, DatumGetFloat8(arg));
	return result;
}

static Datum _Double_coerceObject(Type self, JNIEnv* env, jobject doubleObj)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jdouble dv = (*env)->CallDoubleMethod(env, doubleObj, s_Double_doubleValue);
	isCallingJava = saveicj;
	return Float8GetDatum(dv);
}

static Type Double_obtain(Oid typeId)
{
	return s_Double;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Double_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Double_initialize);
Datum Double_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Double_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Double"));

	s_Double_init = PgObject_getJavaMethod(
				env, s_Double_class, "<init>", "(D)V");

	s_Double_doubleValue = PgObject_getJavaMethod(
				env, s_Double_class, "doubleValue", "()D");

	s_DoubleClass = TypeClass_alloc("type.Double");
	s_DoubleClass->canReplaceType = _Double_canReplace;
	s_DoubleClass->JNISignature   = "Ljava/lang/Double;";
	s_DoubleClass->javaTypeName   = "java.lang.Double";
	s_DoubleClass->coerceDatum    = _Double_coerceDatum;
	s_DoubleClass->coerceObject   = _Double_coerceObject;
	s_Double = TypeClass_allocInstance(s_DoubleClass, FLOAT8OID);

	s_doubleClass = TypeClass_alloc("type.double");
	s_doubleClass->JNISignature   = "D";
	s_doubleClass->javaTypeName   = "double";
	s_doubleClass->objectType     = s_Double;
	s_doubleClass->invoke         = _double_invoke;
	s_doubleClass->coerceDatum    = _double_coerceDatum;
	s_doubleClass->coerceObject   = _Double_coerceObject;
	s_double = TypeClass_allocInstance(s_doubleClass, FLOAT8OID);

	Type_registerPgType(FLOAT8OID, double_obtain);
	Type_registerJavaType("double", double_obtain);
	Type_registerJavaType("java.lang.Double", Double_obtain);
	PG_RETURN_VOID();
}
