/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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

#define MSECS_PER_DAY 86400000
#define uSECS_PER_DAY 86400000000

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
		Int64GetDatum(nanos / 1000);
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

#if PG_VERSION_NUM < 100000
static jvalue Time_coerceDatumTZ_dd(Type self, double t, bool tzAdjust)
{
	jlong mSecs;
	jvalue result;
	t *= 1000.0;						/* Convert to millisecs */
	mSecs = (jlong)rint(t);
	if(tzAdjust)
		mSecs = Timestamp_utcMasquerade(mSecs, JNI_FALSE);
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs);
	return result;
}
#endif

static jvalue Time_coerceDatumTZ_id(Type self, int64 t, bool tzAdjust)
{
	jvalue result;
	jlong mSecs;
	/*
	 * The incoming t is in microseconds, in some interval microseconds-per-day
	 * wide that contains zero. But it might not be left-anchored at zero, if
	 * tzAdjust is false (meaning it came from a time with time zone, and the
	 * zone offet has been added to it). If tzAdjust is true, it's left-anchored
	 * now, but might not be when we're done utcMasquerading it. Either way, mod
	 * it into the range left-anchored at zero before passing it to Java.
	 *
	 * That will make it a time in the first day of the Java epoch (1970-Jan-1),
	 * just the way Java likes it.
	 */
	mSecs = ((t / 500) + 1) / 2;			/* Convert to millisecs */
	if(tzAdjust)
		mSecs = Timestamp_utcMasquerade(mSecs, JNI_FALSE);
	mSecs = (mSecs + MSECS_PER_DAY) % MSECS_PER_DAY; /* beware signed % */
	result.l = JNI_newObject(s_Time_class, s_Time_init, mSecs);
	return result;
}

static jlong Time_getMillisecsToday(Type self, jobject jt, bool tzAdjust)
{
	jlong mSecs = JNI_callLongMethod(jt, s_Time_getTime);
	if(tzAdjust)
		mSecs = Timestamp_utcMasquerade(mSecs, JNI_TRUE);
	/*
	 * Ensure the result is in a day-wide interval left-anchored at zero.
	 * If !tzAdjust then the caller has its own time zone adjusting to do
	 * on the result, and has to do its own mod then anyway, which makes it
	 * tempting to skip this in that case, leaving the caller to do it. But
	 * without radically refactoring the multiple callers, really, the easiest
	 * way to avoid various overflow or precision loss concerns (think of the
	 * !integer_datetimes case) is just to do it here unconditionally. The
	 * callers with more time zone business to do can then get away with an
	 * add,mod rather than another full mod,add,mod.
	 */
	return ((mSecs % MSECS_PER_DAY) + MSECS_PER_DAY) % MSECS_PER_DAY;
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
 * time zone. A java.time.OffsetTime is the best representation for this.
 * But this conversion is to the older JDBC java.sql.Time class, which requires
 * converting to UTC (and losing the information on the value's original zone).
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

/*
 * In the reverse direction, Java is supplying a time in UTC. We have to
 * explicitly record a zone in the PostgreSQL 'time with time zone' datum.
 * If this is a round trip of a value that came from PostgreSQL originally,
 * we've lost track of what its zone was at first, so the choice of zone here
 * is (regrettably!) arbitrary. UTC would be an obvious choice (as that's just
 * what we're getting from Java), but PL/Java's long-established practice here
 * has been to adjust it to the PostgreSQL session time zone instead. It's
 * possible the original zone was neither of those two choices, but what is one
 * to do? Ideally: just don't use this conversion! Use java.time.OffsetTime
 * instead. This one will continue assuming the current session time zone,
 * as in the past.
 */
static Datum _Timetz_coerceObject(Type self, jobject time)
{
	Datum datum;
#if PG_VERSION_NUM < 100000
	if(!integerDateTimes)
	{
		TimeTzADT_dd* tza = (TimeTzADT_dd*)palloc(sizeof(TimeTzADT_dd));
		tza->time = Time_coerceObjectTZ_dd(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time += 86400.0 - tza->zone; /* Convert UTC to local time */
		tza->time = fmod(tza->time, 86400.0); /* see getMillisecsToday */
		datum = PointerGetDatum(tza);
	}
	else
#endif
	{
		TimeTzADT_id* tza = (TimeTzADT_id*)palloc(sizeof(TimeTzADT_id));
		tza->time = Time_coerceObjectTZ_id(self, time, false);
		tza->zone = Timestamp_getCurrentTimeZone();
		tza->time -= (int64)tza->zone * 1000000; /* Convert UTC to local time */
		tza->time = (tza->time + uSECS_PER_DAY) % uSECS_PER_DAY;
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
