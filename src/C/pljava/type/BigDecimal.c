/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/memutils.h>
#include <utils/numeric.h>

#include "pljava/type/String_priv.h"

/*
 * BigDecimal type. We use String conversions here. Perhaps there's
 * room for optimizations such as creating a 2's complement byte
 * array directly from the digits. Don't think we'd gain much though.
 */
static jclass    s_BigDecimal_class;
static jmethodID s_BigDecimal_init;
static jmethodID s_BigDecimal_toString;
static TypeClass s_BigDecimalClass;

static jvalue _BigDecimal_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result = _String_coerceDatum(self, env, arg);
	if(result.l != 0)
		result.l = PgObject_newJavaObject(env, s_BigDecimal_class,
						s_BigDecimal_init, result.l);
	return result;
}

static Datum _BigDecimal_coerceObject(Type self, JNIEnv* env, jobject value)
{
	jstring jstr;
	Datum ret;
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jstr = (*env)->CallObjectMethod(env, value, s_BigDecimal_toString);
	isCallingJava = saveicj;

	ret = _String_coerceObject(self, env, jstr);
	(*env)->DeleteLocalRef(env, jstr);
	return ret;
}

static Type BigDecimal_obtain(Oid typeId)
{
	return (Type)StringClass_obtain(s_BigDecimalClass, typeId);
}

/* Make this datatype available to the postgres system.
 */
extern Datum BigDecimal_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(BigDecimal_initialize);
Datum BigDecimal_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);
	s_BigDecimal_class = (*env)->NewGlobalRef(
					env, PgObject_getJavaClass(env, "java/math/BigDecimal"));
	s_BigDecimal_init = PgObject_getJavaMethod(
					env, s_BigDecimal_class, "<init>", "(Ljava/lang/String;)V");
	s_BigDecimal_toString = PgObject_getJavaMethod(
					env, s_BigDecimal_class, "toString", "()Ljava/lang/String;");

	s_BigDecimalClass = TypeClass_alloc2("type.BigDecimal", sizeof(struct TypeClass_), sizeof(struct String_));
	s_BigDecimalClass->JNISignature   = "Ljava/math/BigDecimal;";
	s_BigDecimalClass->javaTypeName   = "java.math.BigDecimal";
	s_BigDecimalClass->canReplaceType = _Type_canReplaceType;
	s_BigDecimalClass->coerceDatum    = _BigDecimal_coerceDatum;
	s_BigDecimalClass->coerceObject   = _BigDecimal_coerceObject;

	Type_registerPgType(NUMERICOID, BigDecimal_obtain);
	Type_registerJavaType("java.math.BigDecimal", BigDecimal_obtain);
	PG_RETURN_VOID();
}

