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
static TypeClass s_byteClass;

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

/*
 * java.lang.Byte type.
 */
static bool _Byte_canReplace(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls || cls == s_byteClass;
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

/* Make this datatype available to the postgres system.
 */
extern void Byte_initialize(void);
void Byte_initialize(void)
{
	Type t_byte;
	Type t_Byte;
	TypeClass cls;
	s_Byte_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Byte"));
	s_Byte_init = PgObject_getJavaMethod(s_Byte_class, "<init>", "(B)V");
	s_Byte_byteValue = PgObject_getJavaMethod(s_Byte_class, "byteValue", "()B");

	cls = TypeClass_alloc("type.Byte");
	cls->canReplaceType = _Byte_canReplace;
	cls->JNISignature   = "Ljava/lang/Byte;";
	cls->javaTypeName   = "java.lang.Byte";
	cls->coerceDatum    = _Byte_coerceDatum;
	cls->coerceObject   = _Byte_coerceObject;
	t_Byte = TypeClass_allocInstance(cls, CHAROID);

	cls = TypeClass_alloc("type.byte");
	cls->JNISignature   = "B";
	cls->javaTypeName   = "byte";
	cls->invoke         = _byte_invoke;
	cls->coerceDatum    = _byte_coerceDatum;
	cls->coerceObject   = _Byte_coerceObject;
	t_byte = TypeClass_allocInstance(cls, CHAROID);
	t_byte->objectType = t_Byte;
	s_byteClass = cls;

	Type_registerType("byte", t_byte);
	Type_registerType("java.lang.Byte", t_Byte);
}
