/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <utils/date.h>
#include <utils/datetime.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/Timestamp.h"

/*
 * Time type. Postgres will pass (and expect in return) a local Time.
 * The Java java.sql.Time is UTC time and not a perfect fit. Perhaps
 * a LocalTime object should be added to the Java domain?
 */
static jclass    s_Time_class;
static jmethodID s_Time_init;
static jmethodID s_Time_getTime;

static Type s_Time;
static TypeClass s_TimeClass;
static Type s_Timetz;
static TypeClass s_TimetzClass;

static jvalue Time_coerceDatumTZ(Type self, JNIEnv* env, TimeADT t, bool tzAdjust)
{
	jlong mSecs;
	jvalue result;
#ifdef HAVE_INT64_Time
	mSecs = t / 1000;			/* Convert to millisecs */
	if(tzAdjust)
		mSecs += Timestamp_getCurrentTimeZone() * 1000;/* Adjust from local time to UTC */
#else
	if(tzAdjust)
		t += Timestamp_getCurrentTimeZone();/* Adjust from local time to UTC */
	t *= 1000.0;						/* Convert to millisecs */
	mSecs = (jlong)floor(t);
#endif
	result.l = PgObject_newJavaObject(env, s_Time_class, s_Time_init, mSecs);
	return result;
}

static TimeADT Time_coerceObjectTZ(Type self, JNIEnv* env, jobject jt, bool tzAdjust)
{
	jlong mSecs = (*env)->CallLongMethod(env, jt, s_Time_getTime);
	if(tzAdjust)
		mSecs -= ((jlong)Timestamp_getCurrentTimeZone()) * 1000L; /* Adjust from UTC to local time */
	mSecs %= 86400000; /* Strip everything above 24 hours */

#ifdef HAVE_INT64_Time
	return mSecs * 1000L; /* Convert millisecs to microsecs */
#else
	return ((double)mSecs) / 1000.0; /* Convert to seconds */
#endif
}

static jvalue _Time_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	return Time_coerceDatumTZ(self, env, DatumGetTimeADT(arg), true);
}

static Datum _Time_coerceObject(Type self, JNIEnv* env, jobject time)
{
	return TimeADTGetDatum(Time_coerceObjectTZ(self, env, time, true));
}

static Type Time_obtain(Oid typeId)
{
	return s_Time;
}

/* 
 * Time with time zone. Postgres will pass local time and an associated
 * time zone. In the future, we might create a special java object for
 * this. For now, we just convert to UTC and pass a Time object.
 */
static jvalue _Timetz_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	TimeTzADT* tza = DatumGetTimeTzADTP(arg);
	TimeADT t = tza->time + tza->zone; /* Convert to UTC */
	pfree(tza);
	return Time_coerceDatumTZ(self, env, t, false);
}

static Datum _Timetz_coerceObject(Type self, JNIEnv* env, jobject time)
{
	TimeTzADT* tza = (TimeTzADT*)palloc(sizeof(TimeTzADT));
	tza->time = Time_coerceObjectTZ(self, env, time, false);
	tza->zone = Timestamp_getCurrentTimeZone();
	tza->time -= tza->zone; /* Convert UTC to local time */
	return TimeTzADTPGetDatum(tza);
}

static Type Timetz_obtain(Oid typeId)
{
	return s_Timetz;
}

/* Make this datatype available to the postgres system.
 */
extern Datum Time_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Time_initialize);
Datum Time_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Time_class = (*env)->NewGlobalRef(
						env, PgObject_getJavaClass(env, "java/sql/Time"));
						
	s_Time_init = PgObject_getJavaMethod(
						env, s_Time_class, "<init>", "(J)V");
						
	s_Time_getTime = PgObject_getJavaMethod(
						env, s_Time_class, "getTime", "()J");

	s_TimeClass = TypeClass_alloc("type.Time");
	s_TimeClass->JNISignature = "Ljava/sql/Time;";
	s_TimeClass->javaTypeName = "java.sql.Time";
	s_TimeClass->coerceDatum  = _Time_coerceDatum;
	s_TimeClass->coerceObject = _Time_coerceObject;
	s_Time = TypeClass_allocInstance(s_TimeClass, TIMEOID);

	s_TimetzClass = TypeClass_alloc("type.Timetz");
	s_TimetzClass->JNISignature = "Ljava/sql/Time;";
	s_TimetzClass->javaTypeName = "java.sql.Time";
	s_TimetzClass->coerceDatum  = _Timetz_coerceDatum;
	s_TimetzClass->coerceObject = _Timetz_coerceObject;
	s_Timetz = TypeClass_allocInstance(s_TimetzClass, TIMETZOID);

	Type_registerPgType(TIMEOID,   Time_obtain);
	Type_registerPgType(TIMETZOID, Timetz_obtain);
	Type_registerJavaType("java.sql.Time", Timetz_obtain);
	PG_RETURN_VOID();
}
