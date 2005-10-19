/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
static Datum _short_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jshort sv = JNI_callStaticShortMethodA(cls, method, args);
	return Int16GetDatum(sv);
}

static jvalue _short_coerceDatum(Type self, Datum arg)
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

static jvalue _Short_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Short_class, s_Short_init, DatumGetInt16(arg));
	return result;
}

static Datum _Short_coerceObject(Type self, jobject shortObj)
{
	jshort sv = JNI_callShortMethod(shortObj, s_Short_shortValue);
	return Int16GetDatum(sv);
}

static Type Short_obtain(Oid typeId)
{
	return s_Short;
}

/* Make this datatype available to the postgres system.
 */
extern void Short_initialize(void);
void Short_initialize()
{
	s_Short_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Short"));
	s_Short_init = PgObject_getJavaMethod(s_Short_class, "<init>", "(S)V");
	s_Short_shortValue = PgObject_getJavaMethod(s_Short_class, "shortValue", "()S");

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
}
