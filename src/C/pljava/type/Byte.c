/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"

/* The byte maps to the postgres type "char", i.e. the
 * 8 bit, one byte quantity. The java byte was chosen instead of
 * char since a Java char is UTF-16 and "char" is not in any way
 * subject to character set encodings.
 */
static Type s_byte;	/* Primitive (scalar) type */
static TypeClass s_byteClass;
static Type s_Byte;	/* Object type */
static TypeClass s_ByteClass;

static jclass    s_Byte_class;
static jmethodID s_Byte_init;
static jmethodID s_Byte_byteValue;

/*
 * byte primitive type.
 */
static Datum _byte_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, bool* wasNull)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	Datum ret = CharGetDatum((*env)->CallStaticByteMethodA(env, cls, method, args));
	isCallingJava = saveicj;
	return ret;
}

static jvalue _byte_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.b = DatumGetChar(arg);
	return result;
}

static Type byte_obtain(Oid typeId)
{
	return s_byte;
}

/*
 * java.lang.Byte type.
 */
static bool _Byte_canReplace(Type self, Type other)
{
	return self->m_class == other->m_class || other->m_class == s_byteClass;
}

static jvalue _Byte_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Byte_class, s_Byte_init, DatumGetChar(arg));
	return result;
}

static Datum _Byte_coerceObject(Type self, JNIEnv* env, jobject byteObj)
{
	return CharGetDatum((*env)->CallByteMethod(env, byteObj, s_Byte_byteValue));
}

static Type Byte_obtain(Oid typeId)
{
	return s_Byte;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Byte_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Byte_initialize);
Datum Byte_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Byte_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "java/lang/Byte"));

	s_Byte_init = PgObject_getJavaMethod(
				env, s_Byte_class, "<init>", "(B)V");

	s_Byte_byteValue = PgObject_getJavaMethod(
				env, s_Byte_class, "byteValue", "()B");

	s_ByteClass = TypeClass_alloc("type.Byte");
	s_ByteClass->canReplaceType = _Byte_canReplace;
	s_ByteClass->JNISignature   = "Ljava/lang/Byte;";
	s_ByteClass->javaTypeName   = "java.lang.Byte";
	s_ByteClass->coerceDatum    = _Byte_coerceDatum;
	s_ByteClass->coerceObject   = _Byte_coerceObject;
	s_Byte = TypeClass_allocInstance(s_ByteClass);

	s_byteClass = TypeClass_alloc("type.byte");
	s_byteClass->JNISignature   = "B";
	s_byteClass->javaTypeName   = "byte";
	s_byteClass->objectType     = s_Byte;
	s_byteClass->invoke         = _byte_invoke;
	s_byteClass->coerceDatum    = _byte_coerceDatum;
	s_byteClass->coerceObject   = _Byte_coerceObject;
	s_byte = TypeClass_allocInstance(s_byteClass);

	Type_registerPgType(CHAROID, byte_obtain);
	Type_registerJavaType("byte", byte_obtain);
	Type_registerJavaType("java.lang.Byte", Byte_obtain);
	PG_RETURN_VOID();
}
