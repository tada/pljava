/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/date.h>
#include <utils/datetime.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/Timestamp.h"

#define EPOCH_DIFF (POSTGRES_EPOCH_JDATE - UNIX_EPOCH_JDATE)

static jclass    s_Date_class;
static jmethodID s_Date_init;
static jmethodID s_Date_getTime;

static Type s_Date;
static TypeClass s_DateClass;

/*
 * Date data type. Postgres will pass and expect number of days since
 * Jan 01 2000. Java uses number of millisecs since midnight Jan 01 1970.
 */
static jvalue _Date_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jlong date = (jlong)(DatumGetDateADT(arg) + EPOCH_DIFF);

	jvalue result;
	result.l = PgObject_newJavaObject(env, s_Date_class, s_Date_init, date * 86400000L);
	return result;
}

static Datum _Date_coerceObject(Type self, JNIEnv* env, jobject date)
{
	jlong secs = (*env)->CallLongMethod(env, date, s_Date_getTime);
	return DateADTGetDatum(((DateADT)(secs / 86400000)) - EPOCH_DIFF);
}

static Type Date_obtain(Oid typeId)
{
	return s_Date;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Date_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Date_initialize);
Datum Date_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Date_class = (*env)->NewGlobalRef(
						env, PgObject_getJavaClass(env, "java/sql/Date"));

	s_Date_init = PgObject_getJavaMethod(
						env, s_Date_class, "<init>", "(J)V");
	s_Date_getTime = PgObject_getJavaMethod(
						env, s_Date_class, "getTime",  "()J");

	s_DateClass = TypeClass_alloc("type.Date");
	s_DateClass->JNISignature = "Ljava/sql/Date;";
	s_DateClass->javaTypeName = "java.sql.Date";
	s_DateClass->coerceDatum  = _Date_coerceDatum;
	s_DateClass->coerceObject = _Date_coerceObject;
	s_Date = TypeClass_allocInstance(s_DateClass, DATEOID);

	Type_registerPgType(DATEOID,   Date_obtain);
	Type_registerJavaType("java.sql.Date", Date_obtain);
	PG_RETURN_VOID();
}
