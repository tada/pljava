/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"

static Type s_boolean;	/* Primitive (scalar) type */
static TypeClass s_booleanClass;
static Type s_Boolean;	/* Object type */
static TypeClass s_BooleanClass;

static jclass    s_Boolean_class;
static jmethodID s_Boolean_init;
static jmethodID s_Boolean_booleanValue;

/*
 * boolean primitive type.
 */
static Datum _boolean_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jboolean zv;
	bool saveicj = isCallingJava;
	isCallingJava = true;
	zv = (*env)->CallStaticBooleanMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	return BoolGetDatum(zv == JNI_TRUE);
}

static jvalue _boolean_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.z = DatumGetBool(arg);
	return result;
}

static Type boolean_obtain(Oid typeId)
{
	return s_boolean;
}

/*
 * java.lang.Boolean type.
 */
static bool _Boolean_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_booleanClass;
}

static jvalue _Boolean_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Boolean_class, s_Boolean_init, DatumGetBool(arg));
	return result;
}

static Datum _Boolean_coerceObject(Type self, JNIEnv* env, jobject boolObj)
{
	return BoolGetDatum((*env)->CallBooleanMethod(env, boolObj, s_Boolean_booleanValue));
}

static Type Boolean_obtain(Oid typeId)
{
	return s_Boolean;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Boolean_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Boolean_initialize);
Datum Boolean_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Boolean_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Boolean"));

	s_Boolean_init = PgObject_getJavaMethod(
				env, s_Boolean_class, "<init>", "(Z)V");

	s_Boolean_booleanValue = PgObject_getJavaMethod(
				env, s_Boolean_class, "booleanValue", "()Z");

	s_BooleanClass = TypeClass_alloc("type.Boolean");
	s_BooleanClass->canReplaceType = _Boolean_canReplace;
	s_BooleanClass->JNISignature   = "Ljava/lang/Boolean";
	s_BooleanClass->javaTypeName   = "java.lang.Boolean";
	s_BooleanClass->coerceDatum    = _Boolean_coerceDatum;
	s_BooleanClass->coerceObject   = _Boolean_coerceObject;
	s_Boolean = TypeClass_allocInstance(s_BooleanClass, BOOLOID);

	s_booleanClass = TypeClass_alloc("type.boolean");
	s_booleanClass->JNISignature   = "Z";
	s_booleanClass->javaTypeName   = "boolean";
	s_booleanClass->objectType     = s_Boolean;
	s_booleanClass->invoke         = _boolean_invoke;
	s_booleanClass->coerceDatum    = _boolean_coerceDatum;
	s_booleanClass->coerceObject   = _Boolean_coerceObject;
	s_boolean = TypeClass_allocInstance(s_booleanClass, BOOLOID);

	Type_registerPgType(BOOLOID, boolean_obtain);
	Type_registerJavaType("boolean", boolean_obtain);
	Type_registerJavaType("java.lang.Boolean", Boolean_obtain);
	PG_RETURN_VOID();
}
