/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <utils/nabstime.h>
#include <utils/datetime.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/Timestamp.h"

#define EPOCH_DIFF (((uint32)86400) * (POSTGRES_EPOCH_JDATE - UNIX_EPOCH_JDATE))

/*
 * Timestamp type. Postgres will pass (and expect in return) a local timestamp.
 * Java on the other hand has no object that represents local time (localization
 * is added when the object is converted to/from readable form). Hence, all
 * postgres timestamps must be converted from local time to UTC when passed as
 * a parameter to a Java method and all Java Timestamps must be converted from UTC
 * to localtime when returned to postgres.
 */
static jclass    s_Timestamp_class;
static jmethodID s_Timestamp_init;
static jmethodID s_Timestamp_getNanos;
static jmethodID s_Timestamp_getTime;
static jmethodID s_Timestamp_setNanos;

static Type s_Timestamp;
static TypeClass s_TimestampClass;

static Type s_Timestamptz;
static TypeClass s_TimestamptzClass;

static bool _Timestamp_canReplaceType(Type self, Type other)
{
	return other->m_class == s_TimestampClass || other->m_class == s_TimestamptzClass;
}

static jvalue Timestamp_coerceDatumTZ(Type self, JNIEnv* env, Datum arg, bool tzAdjust)
{
	jlong mSecs;
	jint  uSecs;
	jvalue result;
	Timestamp ts = DatumGetTimestamp(arg);
#ifdef HAVE_INT64_TIMESTAMP
	/* Expect number of microseconds since 01 Jan 2000
	 */
	mSecs = ts / 1000;			/* Convert to millisecs */
	uSecs = (jint)(ts % 1000);	/* preserve remaining microsecs */
	if(tzAdjust)
		mSecs += getTimeZone(ts) * 1000;/* Adjust from local time to UTC */
	mSecs += EPOCH_DIFF * 1000;			/* Adjust for diff between Postgres and Java (Unix) */
#else
	/* Expect <seconds since Jan 01 2000>.<fractions of seconds>
	 */
	double tmp;
	if(tzAdjust)
		ts += Timestamp_getTimeZone(ts);/* Adjust from local time to UTC */
	ts += EPOCH_DIFF;					/* Adjust for diff between Postgres and Java (Unix) */
	ts *= 1000.0;						/* Convert to millisecs */
	tmp = floor(ts);
	mSecs = (jlong)tmp;
	uSecs = ((ts - tmp) * 1000.0);		/* Preserve remaining microsecs */
#endif
	result.l = PgObject_newJavaObject(env, s_Timestamp_class, s_Timestamp_init, mSecs);
	if(uSecs != 0)
		(*env)->CallIntMethod(env, result.l, s_Timestamp_setNanos, uSecs * 1000);
	return result;
}

static Datum Timestamp_coerceObjectTZ(Type self, JNIEnv* env, jobject jts, bool tzAdjust)
{
	Timestamp ts;
	jlong mSecs = (*env)->CallLongMethod(env, jts, s_Timestamp_getTime);
	jint  nSecs = (*env)->CallIntMethod(env, jts, s_Timestamp_getNanos);
#ifdef HAVE_INT64_TIMESTAMP
	mSecs -= ((jlong)EPOCH_DIFF) * 1000L;
	ts  = mSecs * 1000L; /* Convert millisecs to microsecs */
	if(nSecs != 0)
		ts += nSecs / 1000;	/* Convert nanosecs  to microsecs */
	if(tzAdjust)
		ts -= ((jlong)getTimeZone(ts)) * 1000000L; /* Adjust from UTC to local time */
#else
	ts = ((double)mSecs) / 1000.0; /* Convert to seconds */
	ts -= EPOCH_DIFF;
	if(nSecs != 0)
		ts += ((double)nSecs) / 1000000000.0;	/* Convert to seconds */
	if(tzAdjust)
		ts -= Timestamp_getTimeZone(ts); /* Adjust from UTC to local time */
#endif
	return TimestampGetDatum(ts);
}

static jvalue _Timestamp_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	return Timestamp_coerceDatumTZ(self, env, arg, true);
}

static Datum _Timestamp_coerceObject(Type self, JNIEnv* env, jobject ts)
{
	return Timestamp_coerceObjectTZ(self, env, ts, true);
}

/* 
 * Timestamp with time zone. Basically same as Timestamp but postgres will pass
 * this one in GMT timezone so there's no without ajustment for time zone.
 */
static jvalue _Timestamptz_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	return Timestamp_coerceDatumTZ(self, env, arg, false);
}

static Datum _Timestamptz_coerceObject(Type self, JNIEnv* env, jobject ts)
{
	return Timestamp_coerceObjectTZ(self, env, ts, false);
}

static Type Timestamp_obtain(Oid typeId)
{
	return s_Timestamp;
}

static Type Timestamptz_obtain(Oid typeId)
{
	return s_Timestamptz;
}

int Timestamp_getTimeZone(Timestamp ts)
{
#if (PGSQL_MAJOR_VER == 7 && PGSQL_MINOR_VER < 5)
	struct tm tmp_tm;
#else
	struct pg_tm tmp_tm;
#endif
	fsec_t fsec;
	int tz = 0;
	timestamp2tm(ts, &tz, &tmp_tm, &fsec, NULL);
	return tz;
}

int Timestamp_getCurrentTimeZone()
{
	int usec = 0;
	AbsoluteTime sec = GetCurrentAbsoluteTimeUsec(&usec);
	return Timestamp_getTimeZone(AbsoluteTimeUsecToTimestampTz(sec, usec));
}

/* Make this datatype available to the postgres system.
 */
extern Datum Timestamp_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(Timestamp_initialize);
Datum Timestamp_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_Timestamp_class = (*env)->NewGlobalRef(
						env, PgObject_getJavaClass(env, "java/sql/Timestamp"));
	s_Timestamp_init = PgObject_getJavaMethod(
						env, s_Timestamp_class, "<init>", "(J)V");
	s_Timestamp_getNanos = PgObject_getJavaMethod(
						env, s_Timestamp_class, "getNanos", "()I");
	s_Timestamp_getTime  = PgObject_getJavaMethod(
						env, s_Timestamp_class, "getTime",  "()J");
	s_Timestamp_setNanos = PgObject_getJavaMethod(
						env, s_Timestamp_class, "setNanos", "(I)V");

	s_TimestampClass = TypeClass_alloc("type.Timestamp");
	s_TimestampClass->JNISignature   = "Ljava/sql/Timestamp;";
	s_TimestampClass->javaTypeName   = "java.sql.Timestamp";
	s_TimestampClass->canReplaceType = _Timestamp_canReplaceType;
	s_TimestampClass->coerceDatum    = _Timestamp_coerceDatum;
	s_TimestampClass->coerceObject   = _Timestamp_coerceObject;
	s_Timestamp = TypeClass_allocInstance(s_TimestampClass, TIMESTAMPOID);

	s_TimestamptzClass = TypeClass_alloc("type.Timestamptz");
	s_TimestamptzClass->JNISignature   = "Ljava/sql/Timestamp;";
	s_TimestamptzClass->javaTypeName   = "java.sql.Timestamp";
	s_TimestamptzClass->canReplaceType = _Timestamp_canReplaceType;
	s_TimestamptzClass->coerceDatum    = _Timestamptz_coerceDatum;
	s_TimestamptzClass->coerceObject   = _Timestamptz_coerceObject;
	s_Timestamptz = TypeClass_allocInstance(s_TimestamptzClass, TIMESTAMPTZOID);

	Type_registerPgType(TIMESTAMPOID, Timestamp_obtain);
	Type_registerPgType(TIMESTAMPTZOID, Timestamptz_obtain);
	Type_registerJavaType("java.sql.Timestamp", Timestamptz_obtain);
	PG_RETURN_VOID();
}
