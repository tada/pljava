/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/date.h>
#include <utils/datetime.h>

#include "pljava/Backend.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Time.h"
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

static jvalue Time_coerceDatumTZ_dd(Type self, double t, bool tzAdjust)
{
	jlong mSecs;
	jvalue result;
	if(tzAdjust)
		t += Timestamp_getCurrentTimeZone();/* Adjust from local time to UTC */
	t *= 1000.0;						/* Convert to millisecs */
	mSecs = (jlong)floor(t);
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs);
	return result;
}

static jvalue Time_coerceDatumTZ_id(Type self, int64 t, bool tzAdjust)
{
	jvalue result;
	jlong mSecs = t / 1000;			/* Convert to millisecs */
	if(tzAdjust)
		mSecs += Timestamp_getCurrentTimeZone() * 1000;/* Adjust from local time to UTC */
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs);
	return result;
}

static jlong Time_getMillisecsToday(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = JNI_callLongMethod(jt, s_Time_getTime);
	if(tzAdjust)
		mSecs -= ((jlong)Timestamp_getCurrentTimeZone()) * 1000L; /* Adjust from UTC to local time */
	mSecs %= 86400000; /* Strip everything above 24 hours */
	return mSecs;
}

static double Time_coerceObjectTZ_dd(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = Time_getMillisecsToday(self, jt, tzAdjust);
	return ((double)mSecs) / 1000.0; /* Convert to seconds */
}

static int64 Time_coerceObjectTZ_id(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = Time_getMillisecsToday(self, jt, tzAdjust);
	return mSecs * 1000L; /* Convert millisecs to microsecs */
}

static jvalue _Time_coerceDatum(Type self, Datum arg)
{
#if (PGSQL_MAJOR_VERSION == 8 && PGSQL_MINOR_VERSION == 0)
	/*
	 * PostgreSQL 8.0 and earlier has a major bug in how int64 times are
	 * stored. They are actually first casted to a double
	 */
	return integerDateTimes
		? Time_coerceDatumTZ_id(self, DatumGetFloat8(arg), true)
		: Time_coerceDatumTZ_dd(self, DatumGetFloat8(arg), true);
#else
	return integerDateTimes
		? Time_coerceDatumTZ_id(self, DatumGetInt64(arg), true)
		: Time_coerceDatumTZ_dd(self, DatumGetFloat8(arg), true);
#endif
}

static Datum _Time_coerceObject(Type self, jobject time)
{
#if (PGSQL_MAJOR_VERSION == 8 && PGSQL_MINOR_VERSION == 0)
	/*
	 * PostgreSQL 8.0 and earlier has a major bug in how int64 times are
	 * stored. They are actually first casted to a double
	 */
	return integerDateTimes
		? Float8GetDatum(Time_coerceObjectTZ_id(self, time, true))
		: Float8GetDatum(Time_coerceObjectTZ_dd(self, time, true));
#else
	return integerDateTimes
		? Int64GetDatum(Time_coerceObjectTZ_id(self, time, true))
		: Float8GetDatum(Time_coerceObjectTZ_dd(self, time, true));
#endif
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
static jvalue _Timetz_coerceDatum(Type self, Datum arg)
{
	jvalue val;
	if(integerDateTimes)
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)DatumGetPointer(arg);
		int64 t = tza->time + (int64)tza->zone * 1000000; /* Convert to UTC */
		val = Time_coerceDatumTZ_id(self, t, false);
	}
	else
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)DatumGetPointer(arg);
		double t = tza->time + tza->zone; /* Convert to UTC */
		val = Time_coerceDatumTZ_dd(self, t, false);
	}
	return val;
}

static Datum _Timetz_coerceObject(Type self, jobject time)
{
	Datum datum;
	if(integerDateTimes)
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)palloc(sizeof(TimeTzADT_id));
		tza->time = Time_coerceObjectTZ_id(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time -= (int64)tza->zone * 1000000; /* Convert UTC to local time */
		datum = PointerGetDatum(tza);
	}
	else
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)palloc(sizeof(TimeTzADT_dd));
		tza->time = Time_coerceObjectTZ_dd(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time -= tza->zone; /* Convert UTC to local time */
		datum = PointerGetDatum(tza);
	}
	return datum;
}

static Type Timetz_obtain(Oid typeId)
{
	return s_Timetz;
}

extern void Time_initialize(void);
void Time_initialize(void)
{
	s_Time_class = JNI_newGlobalRef(PgObject_getJavaClass("java/sql/Time"));
	s_Time_init = PgObject_getJavaMethod(s_Time_class, "<init>", "(J)V");
	s_Time_getTime = PgObject_getJavaMethod(s_Time_class, "getTime", "()J");

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
}
