/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/Invocation.h"

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
static Datum _long_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jlong lv = JNI_callStaticLongMethodA(cls, method, args);

	/* Since we don't know if 64 bit quantities are passed by reference or
	 * by value, we have to make sure that the correct context is used if
	 * it's the former.
	 */
	MemoryContext currCtx = Invocation_switchToUpperContext();
	Datum ret = Int64GetDatum(lv);
	MemoryContextSwitchTo(currCtx);
	return ret;
}

static jvalue _long_coerceDatum(Type self, Datum arg)
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

static jvalue _Long_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Long_class, s_Long_init, DatumGetInt64(arg));
	return result;
}

static Datum _Long_coerceObject(Type self, jobject longObj)
{
	jlong lv = JNI_callLongMethod(longObj, s_Long_longValue);
	return Int64GetDatum(lv);
}

static Type Long_obtain(Oid typeId)
{
	return s_Long;
}

/* Make this datatype available to the postgres system.
 */
extern void Long_initialize(void);
void Long_initialize(void)
{
	s_Long_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Long"));
	s_Long_init = PgObject_getJavaMethod(s_Long_class, "<init>", "(J)V");
	s_Long_longValue = PgObject_getJavaMethod(s_Long_class, "longValue", "()J");

	s_LongClass = TypeClass_alloc("type.Long");
	s_LongClass->canReplaceType = _Long_canReplace;
	s_LongClass->JNISignature   = "Ljava/lang/Long;";
	s_LongClass->javaTypeName   = "java.lang.Long";
	s_LongClass->coerceObject   = _Long_coerceObject;
	s_LongClass->coerceDatum    = _Long_coerceDatum;
	s_Long = TypeClass_allocInstance(s_LongClass, INT8OID);

	s_longClass = TypeClass_alloc("type.long");
	s_longClass->JNISignature   = "J";
	s_longClass->javaTypeName   = "long";
	s_longClass->objectType     = s_Long;
	s_longClass->invoke         = _long_invoke;
	s_longClass->coerceDatum    = _long_coerceDatum;
	s_longClass->coerceObject   = _Long_coerceObject;
	s_long = TypeClass_allocInstance(s_longClass, INT8OID);

	Type_registerPgType(INT8OID, long_obtain);
	Type_registerJavaType("long", long_obtain);
	Type_registerJavaType("java.lang.Long", Long_obtain);
}
