/*
 * Copyright (c) 2004-2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#include <time.h>

#include <postgres.h>
#include <utils/date.h>
#include <utils/datetime.h>

#include "pljava/Backend.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Time.h"
#include "pljava/type/Timestamp.h"

/*
 * Types time and timetz. This compilation unit supplies code for both
 * PostgreSQL types. The legacy JDBC mapping for both is to java.sql.Time, which
 * holds an implicit timezone offset and therefore can't be an equally good fit
 * for both. Also, it loses precision: PostgreSQL maintains microseconds, but
 * java.sql.Time only holds milliseconds.
 *
 * Java 8 and JDBC 4.2 introduce java.time.LocalTime and java.time.OffsetTime,
 * which directly fit PG's time and timetz, respectively. For compatibility
 * reasons, the legacy behavior of getObject (with no Class parameter) is
 * unchanged, and still returns the data weirdly shoehorned into java.sql.Time.
 * But Java 8 application code can and should use the form of getObject with a
 * Class parameter to request java.time.LocalTime or java.time.OffsetTime, as
 * appropriate.
 *
 * The legacy shoehorning adjusts the PostgreSQL-maintained time by its
 * associated offset (in the timetz case), or by the current value of the server
 * timezone offset (in the time case). Which convention is weirder?
 */
static jclass    s_Time_class;
static jmethodID s_Time_init;
static jmethodID s_Time_getTime;

static TypeClass s_LocalTimeClass;
static TypeClass s_OffsetTimeClass;
/*
 * The following statics are specific to Java 8 +, and will be initialized
 * only on demand (pre-8 application code will have no way to demand them).
 */
static Type      s_LocalTimeInstance;
static jclass    s_LocalTime_class;
static jmethodID s_LocalTime_ofNanoOfDay;
static jmethodID s_LocalTime_toNanoOfDay;
static Type      s_OffsetTimeInstance;
static jclass    s_OffsetTime_class;
static jmethodID s_OffsetTime_of;
static jmethodID s_OffsetTime_toLocalTime;
static jmethodID s_OffsetTime_getOffset;
static jclass    s_ZoneOffset_class;
static jmethodID s_ZoneOffset_ofTotalSeconds;
static jmethodID s_ZoneOffset_getTotalSeconds;

/*
 * This only answers true for (same class or) TIMEOID.
 * The obtainer (below) only needs to construct and remember one instance.
 */
static bool _LocalTime_canReplaceType(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls  ||  Type_getOid(other) == TIMEOID;
}

static jvalue _LocalTime_coerceDatum(Type self, Datum arg)
{
	jlong nanos =
#if PG_VERSION_NUM < 100000
		(!integerDateTimes) ? (jlong)floor(1e9 * DatumGetFloat8(arg)) :
#endif
		1000 * DatumGetInt64(arg);
	jvalue result;
	if ( 1000L * USECS_PER_DAY == nanos )
		-- nanos;
	result.l = JNI_callStaticObjectMethod(
		s_LocalTime_class, s_LocalTime_ofNanoOfDay, nanos);
	return result;
}

static Datum _LocalTime_coerceObject(Type self, jobject time)
{
	jlong nanos = JNI_callLongMethod(time, s_LocalTime_toNanoOfDay);
	return
#if PG_VERSION_NUM < 100000
		(!integerDateTimes) ? Float8GetDatum(((double)nanos) / 1e9) :
#endif
		Int64GetDatum((nanos + 1) / 1000);
}

static Type _LocalTime_obtain(Oid typeId)
{
	if ( NULL == s_LocalTimeInstance )
	{
		s_LocalTime_class = JNI_newGlobalRef(PgObject_getJavaClass(
			"java/time/LocalTime"));
		s_LocalTime_ofNanoOfDay = PgObject_getStaticJavaMethod(s_LocalTime_class,
			"ofNanoOfDay", "(J)Ljava/time/LocalTime;");
		s_LocalTime_toNanoOfDay = PgObject_getJavaMethod(s_LocalTime_class,
			"toNanoOfDay", "()J");

		s_LocalTimeInstance =
			TypeClass_allocInstance(s_LocalTimeClass, TIMEOID);
	}
	return s_LocalTimeInstance;
}

/*
 * This only answers true for (same class or) TIMETZOID.
 * The obtainer (below) only needs to construct and remember one instance.
 */
static bool _OffsetTime_canReplaceType(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls  ||  Type_getOid(other) == TIMETZOID;
}

static jvalue _OffsetTime_coerceDatum(Type self, Datum arg)
{
	jvalue localTime;
	jobject zoneOffset;
	int32 offsetSecs;
	jvalue result;

#if PG_VERSION_NUM < 100000
	if ( !integerDateTimes )
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)DatumGetPointer(arg);
		localTime =
			Type_coerceDatum(s_LocalTimeInstance, Float8GetDatum(tza->time));
		offsetSecs = tza->zone;
	}
	else
#endif
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)DatumGetPointer(arg);
		localTime =
			Type_coerceDatum(s_LocalTimeInstance, Int64GetDatum(tza->time));
		offsetSecs = tza->zone;
	}

	zoneOffset = JNI_callStaticObjectMethod(s_ZoneOffset_class,
		s_ZoneOffset_ofTotalSeconds, - offsetSecs); /* PG/Java signs differ */

	result.l = JNI_callStaticObjectMethod(
		s_OffsetTime_class, s_OffsetTime_of, localTime.l, zoneOffset);

	JNI_deleteLocalRef(localTime.l);
	JNI_deleteLocalRef(zoneOffset);

	return result;
}

static Datum _OffsetTime_coerceObject(Type self, jobject time)
{
	jobject localTime = JNI_callObjectMethod(time, s_OffsetTime_toLocalTime);
	jobject zoneOffset = JNI_callObjectMethod(time, s_OffsetTime_getOffset);
	jint offsetSecs =
		- /* PG/Java signs differ */
		JNI_callIntMethod(zoneOffset, s_ZoneOffset_getTotalSeconds);
	Datum result;

#if PG_VERSION_NUM < 100000
	if ( !integerDateTimes )
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)palloc(sizeof(TimeTzADT_dd));
		tza->zone = offsetSecs;
		tza->time =
			DatumGetFloat8(Type_coerceObject(s_LocalTimeInstance, localTime));
		result = PointerGetDatum(tza);
	}
	else
#endif
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)palloc(sizeof(TimeTzADT_id));
		tza->zone = offsetSecs;
		tza->time =
			DatumGetInt64(Type_coerceObject(s_LocalTimeInstance, localTime));
		result = PointerGetDatum(tza);
	}

	JNI_deleteLocalRef(localTime);
	JNI_deleteLocalRef(zoneOffset);
	return result;
}

static Type _OffsetTime_obtain(Oid typeId)
{
	if ( NULL == s_OffsetTimeInstance )
	{
		_LocalTime_obtain(TIMEOID); /* Make sure LocalTime statics are there */

		s_OffsetTime_class = JNI_newGlobalRef(PgObject_getJavaClass(
			"java/time/OffsetTime"));
		s_OffsetTime_of = PgObject_getStaticJavaMethod(s_OffsetTime_class, "of",
			"(Ljava/time/LocalTime;Ljava/time/ZoneOffset;)"
			"Ljava/time/OffsetTime;");
		s_OffsetTime_toLocalTime = PgObject_getJavaMethod(s_OffsetTime_class,
			"toLocalTime", "()Ljava/time/LocalTime;");
		s_OffsetTime_getOffset = PgObject_getJavaMethod(s_OffsetTime_class,
			"getOffset", "()Ljava/time/ZoneOffset;");

		s_ZoneOffset_class = JNI_newGlobalRef(PgObject_getJavaClass(
			"java/time/ZoneOffset"));
		s_ZoneOffset_ofTotalSeconds = PgObject_getStaticJavaMethod(
			s_ZoneOffset_class, "ofTotalSeconds", "(I)Ljava/time/ZoneOffset;");
		s_ZoneOffset_getTotalSeconds = PgObject_getJavaMethod(
			s_ZoneOffset_class, "getTotalSeconds", "()I");

		s_OffsetTimeInstance =
			TypeClass_allocInstance(s_OffsetTimeClass, TIMETZOID);
	}
	return s_OffsetTimeInstance;
}

static jlong msecsAtMidnight(void)
{
	pg_time_t now = (pg_time_t)time(NULL) / 86400;
	return INT64CONST(1000) * (jlong)(now * 86400);
}

#if PG_VERSION_NUM < 100000
static jvalue Time_coerceDatumTZ_dd(Type self, double t, bool tzAdjust)
{
	jlong mSecs;
	jvalue result;
	if(tzAdjust)
		t += Timestamp_getCurrentTimeZone();/* Adjust from local time to UTC */
	t *= 1000.0;						/* Convert to millisecs */
	mSecs = (jlong)floor(t);
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs + msecsAtMidnight());
	return result;
}
#endif

static jvalue Time_coerceDatumTZ_id(Type self, int64 t, bool tzAdjust)
{
	jvalue result;
	jlong mSecs = t / 1000;			/* Convert to millisecs */
	if(tzAdjust)
		mSecs += Timestamp_getCurrentTimeZone() * 1000;/* Adjust from local time to UTC */
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs + msecsAtMidnight());
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

#if PG_VERSION_NUM < 100000
static double Time_coerceObjectTZ_dd(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = Time_getMillisecsToday(self, jt, tzAdjust);
	return ((double)mSecs) / 1000.0; /* Convert to seconds */
}
#endif

static int64 Time_coerceObjectTZ_id(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = Time_getMillisecsToday(self, jt, tzAdjust);
	return mSecs * 1000L; /* Convert millisecs to microsecs */
}

static jvalue _Time_coerceDatum(Type self, Datum arg)
{
	return
#if PG_VERSION_NUM < 100000
		(!integerDateTimes) ?
		Time_coerceDatumTZ_dd(self, DatumGetFloat8(arg), true) :
#endif
		Time_coerceDatumTZ_id(self, DatumGetInt64(arg), true);
}

static Datum _Time_coerceObject(Type self, jobject time)
{
	return
#if PG_VERSION_NUM < 100000
		(!integerDateTimes) ?
		Float8GetDatum(Time_coerceObjectTZ_dd(self, time, true)) :
#endif
		Int64GetDatum(Time_coerceObjectTZ_id(self, time, true));
}

/* 
 * Time with time zone. Postgres will pass local time and an associated
 * time zone. In the future, we might create a special java object for
 * this. For now, we just convert to UTC and pass a Time object.
 */
static jvalue _Timetz_coerceDatum(Type self, Datum arg)
{
	jvalue val;
#if PG_VERSION_NUM < 100000
	if(!integerDateTimes)
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)DatumGetPointer(arg);
		double t = tza->time + tza->zone; /* Convert to UTC */
		val = Time_coerceDatumTZ_dd(self, t, false);
	}
	else
#endif
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)DatumGetPointer(arg);
		int64 t = tza->time + (int64)tza->zone * 1000000; /* Convert to UTC */
		val = Time_coerceDatumTZ_id(self, t, false);
	}
	return val;
}

static Datum _Timetz_coerceObject(Type self, jobject time)
{
	Datum datum;
#if PG_VERSION_NUM < 100000
	if(!integerDateTimes)
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)palloc(sizeof(TimeTzADT_dd));
		tza->time = Time_coerceObjectTZ_dd(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time -= tza->zone; /* Convert UTC to local time */
		datum = PointerGetDatum(tza);
	}
	else
#endif
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)palloc(sizeof(TimeTzADT_id));
		tza->time = Time_coerceObjectTZ_id(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time -= (int64)tza->zone * 1000000; /* Convert UTC to local time */
		datum = PointerGetDatum(tza);
	}
	return datum;
}

extern void Time_initialize(void);
void Time_initialize(void)
{
	TypeClass cls;
	s_Time_class = JNI_newGlobalRef(PgObject_getJavaClass("java/sql/Time"));
	s_Time_init = PgObject_getJavaMethod(s_Time_class, "<init>", "(J)V");
	s_Time_getTime = PgObject_getJavaMethod(s_Time_class, "getTime", "()J");

	cls = TypeClass_alloc("type.Time");
	cls->JNISignature = "Ljava/sql/Time;";
	cls->javaTypeName = "java.sql.Time";
	cls->coerceDatum  = _Time_coerceDatum;
	cls->coerceObject = _Time_coerceObject;
	Type_registerType(0, TypeClass_allocInstance(cls, TIMEOID));

	cls = TypeClass_alloc("type.Timetz");
	cls->JNISignature = "Ljava/sql/Time;";
	cls->javaTypeName = "java.sql.Time";
	cls->coerceDatum  = _Timetz_coerceDatum;
	cls->coerceObject = _Timetz_coerceObject;
	Type_registerType("java.sql.Time", TypeClass_allocInstance(cls, TIMETZOID));

	cls = TypeClass_alloc("type.LocalTime");
	cls->JNISignature = "Ljava/time/LocalTime;";
	cls->javaTypeName = "java.time.LocalTime";
	cls->coerceDatum  = _LocalTime_coerceDatum;
	cls->coerceObject = _LocalTime_coerceObject;
	cls->canReplaceType = _LocalTime_canReplaceType;
	s_LocalTimeClass  = cls;
	Type_registerType2(InvalidOid, "java.time.LocalTime", _LocalTime_obtain);

	cls = TypeClass_alloc("type.OffsetTime");
	cls->JNISignature = "Ljava/time/OffsetTime;";
	cls->javaTypeName = "java.time.OffsetTime";
	cls->coerceDatum  = _OffsetTime_coerceDatum;
	cls->coerceObject = _OffsetTime_coerceObject;
	cls->canReplaceType = _OffsetTime_canReplaceType;
	s_OffsetTimeClass  = cls;
	Type_registerType2(InvalidOid, "java.time.OffsetTime", _OffsetTime_obtain);
}
