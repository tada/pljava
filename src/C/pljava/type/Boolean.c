/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
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
static Datum _boolean_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jboolean zv = JNI_callStaticBooleanMethodA(cls, method, args);
	return BoolGetDatum(zv == JNI_TRUE);
}

static jvalue _boolean_coerceDatum(Type self, Datum arg)
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

static jvalue _Boolean_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Boolean_class, s_Boolean_init, DatumGetBool(arg));
	return result;
}

static Datum _Boolean_coerceObject(Type self, jobject boolObj)
{
	return BoolGetDatum(JNI_callBooleanMethod(boolObj, s_Boolean_booleanValue));
}

static Type Boolean_obtain(Oid typeId)
{
	return s_Boolean;
}

/* Make this datatype available to the postgres system.
 */
extern void Boolean_initialize(void);
void Boolean_initialize(void)
{
	s_Boolean_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Boolean"));
	s_Boolean_init = PgObject_getJavaMethod(s_Boolean_class, "<init>", "(Z)V");
	s_Boolean_booleanValue = PgObject_getJavaMethod(s_Boolean_class, "booleanValue", "()Z");

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

	Type_registerType(BOOLOID, "boolean", boolean_obtain);
	Type_registerType(InvalidOid, "java.lang.Boolean", Boolean_obtain);
}
