/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
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
static Datum _byte_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	jbyte bv = JNI_callStaticByteMethodA(cls, method, args);
	return CharGetDatum(bv);
}

static jvalue _byte_coerceDatum(Type self, Datum arg)
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

static jvalue _Byte_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = JNI_newObject(s_Byte_class, s_Byte_init, DatumGetChar(arg));
	return result;
}

static Datum _Byte_coerceObject(Type self, jobject byteObj)
{
	return CharGetDatum(JNI_callByteMethod(byteObj, s_Byte_byteValue));
}

static Type Byte_obtain(Oid typeId)
{
	return s_Byte;
}

/* Make this datatype available to the postgres system.
 */
extern void Byte_initialize(void);
void Byte_initialize(void)
{
	s_Byte_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Byte"));
	s_Byte_init = PgObject_getJavaMethod(s_Byte_class, "<init>", "(B)V");
	s_Byte_byteValue = PgObject_getJavaMethod(s_Byte_class, "byteValue", "()B");

	s_ByteClass = TypeClass_alloc("type.Byte");
	s_ByteClass->canReplaceType = _Byte_canReplace;
	s_ByteClass->JNISignature   = "Ljava/lang/Byte;";
	s_ByteClass->javaTypeName   = "java.lang.Byte";
	s_ByteClass->coerceDatum    = _Byte_coerceDatum;
	s_ByteClass->coerceObject   = _Byte_coerceObject;
	s_Byte = TypeClass_allocInstance(s_ByteClass, CHAROID);

	s_byteClass = TypeClass_alloc("type.byte");
	s_byteClass->JNISignature   = "B";
	s_byteClass->javaTypeName   = "byte";
	s_byteClass->objectType     = s_Byte;
	s_byteClass->invoke         = _byte_invoke;
	s_byteClass->coerceDatum    = _byte_coerceDatum;
	s_byteClass->coerceObject   = _Byte_coerceObject;
	s_byte = TypeClass_allocInstance(s_byteClass, CHAROID);

	Type_registerType(CHAROID, "byte", byte_obtain);
	Type_registerType(InvalidOid, "java.lang.Byte", Byte_obtain);
}
