/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
static Datum _float_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jfloat fv = JNI_callStaticFloatMethodA(cls, method, args);
	return Float4GetDatum(fv);
}

static jvalue _float_coerceDatum(Type self, Datum arg)
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

static jvalue _Float_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Float_class, s_Float_init, DatumGetFloat4(arg));
	return result;
}

static Datum _Float_coerceObject(Type self, jobject floatObj)
{
	jfloat fv = JNI_callFloatMethod(floatObj, s_Float_floatValue);
	return Float4GetDatum(fv);
}

static Type Float_obtain(Oid typeId)
{
	return s_Float;
}

/* Make this datatype available to the postgres system.
 */
extern void Float_initialize(void);
void Float_initialize()
{
	s_Float_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Float"));
	s_Float_init = PgObject_getJavaMethod(s_Float_class, "<init>", "(F)V");
	s_Float_floatValue = PgObject_getJavaMethod(s_Float_class, "floatValue", "()F");

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
}
