/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"

static Type s_int;	/* Primitive (scalar) type */
static TypeClass s_intClass;
static Type s_Integer;	/* Object type */
static TypeClass s_IntegerClass;

static jclass    s_Integer_class;
static jmethodID s_Integer_init;
static jmethodID s_Integer_intValue;

/*
 * int primitive type.
 */
static Datum _int_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jint iv = JNI_callStaticIntMethodA(cls, method, args);
	return Int32GetDatum(iv);
}

static jvalue _int_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.i = DatumGetInt32(arg);
	return result;
}

static Type int_obtain(Oid typeId)
{
	return s_int;
}

/*
 * java.lang.Integer type.
 */
static bool _Integer_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_intClass;
}

static jvalue _Integer_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Integer_class, s_Integer_init, DatumGetInt32(arg));
	return result;
}

static Datum _Integer_coerceObject(Type self, jobject intObj)
{
	jint iv = JNI_callIntMethod(intObj, s_Integer_intValue);
	return Int32GetDatum(iv);
}

static Type Integer_obtain(Oid typeId)
{
	return s_Integer;
}

/* Make this datatype available to the postgres system.
 */
extern void Integer_initialize(void);
void Integer_initialize(void)
{
	s_Integer_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Integer"));
	s_Integer_init = PgObject_getJavaMethod(s_Integer_class, "<init>", "(I)V");
	s_Integer_intValue = PgObject_getJavaMethod(s_Integer_class, "intValue", "()I");

	s_IntegerClass = TypeClass_alloc("type.Integer");
	s_IntegerClass->canReplaceType = _Integer_canReplace;
	s_IntegerClass->JNISignature   = "Ljava/lang/Integer;";
	s_IntegerClass->javaTypeName   = "java.lang.Integer";
	s_IntegerClass->coerceDatum    = _Integer_coerceDatum;
	s_IntegerClass->coerceObject   = _Integer_coerceObject;
	s_Integer = TypeClass_allocInstance(s_IntegerClass, INT4OID);

	s_intClass = TypeClass_alloc("type.int");
	s_intClass->JNISignature       = "I";
	s_intClass->javaTypeName       = "int";
	s_intClass->objectType         = s_Integer;
	s_intClass->invoke             = _int_invoke;
	s_intClass->coerceDatum        = _int_coerceDatum;
	s_intClass->coerceObject       = _Integer_coerceObject;
	s_int = TypeClass_allocInstance(s_intClass, INT4OID);

	Type_registerPgType(INT4OID, int_obtain);
	Type_registerJavaType("int", int_obtain);
	Type_registerJavaType("java.lang.Integer", Integer_obtain);
}
