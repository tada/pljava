/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/Invocation.h"

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
static Datum _double_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jdouble dv = JNI_callStaticDoubleMethodA(cls, method, args);

	/* Since we don't know if 64 bit quantities are passed by reference or
	 * by value, we have to make sure that the correct context is used if
	 * it's the former.
	 */
	MemoryContext currCtx = Invocation_switchToUpperContext();
	Datum ret = Float8GetDatum(dv);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static jvalue _double_coerceDatum(Type self, Datum arg)
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

static jvalue _Double_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Double_class, s_Double_init, DatumGetFloat8(arg));
	return result;
}

static Datum _Double_coerceObject(Type self, jobject doubleObj)
{
	jdouble dv = JNI_callDoubleMethod(doubleObj, s_Double_doubleValue);
	return Float8GetDatum(dv);
}

static Type Double_obtain(Oid typeId)
{
	return s_Double;
}

/* Make this datatype available to the postgres system.
 */
extern void Double_initialize(void);
void Double_initialize(void)
{
	s_Double_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Double"));
	s_Double_init = PgObject_getJavaMethod(s_Double_class, "<init>", "(D)V");

	s_Double_doubleValue = PgObject_getJavaMethod(s_Double_class, "doubleValue", "()D");

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

	Type_registerType(FLOAT8OID, "double", double_obtain);
	Type_registerType(InvalidOid, "java.lang.Double", Double_obtain);
}
