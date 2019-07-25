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
#include <postgres.h>
#include <utils/date.h>
#include <utils/datetime.h>

#include "pljava/type/Type_priv.h"
#include "pljava/type/Timestamp.h"

#define EPOCH_DIFF (POSTGRES_EPOCH_JDATE - UNIX_EPOCH_JDATE)
#define MSECS_PER_DAY 86400000

static jclass    s_Date_class;
static jmethodID s_Date_init;
static jmethodID s_Date_getTime;

static TypeClass s_LocalDateClass;
/*
 * The following statics are specific to Java 8 +, and will be initialized
 * only on demand (pre-8 application code will have no way to demand them).
 */
static jclass    s_LocalDate_class;
static jmethodID s_LocalDate_ofEpochDay;
static jmethodID s_LocalDate_toEpochDay;

/*
 * LocalDate data type. This is introduced with JDBC 4.2 and Java 8. For
 * backward-compatibility reasons it does not become the default class returned
 * by getObject() for a PostgreSQL date, but application code in Java 8+ can and
 * should prefer it, by passing LocalDate.class to getObject. It represents a
 * purely local, non-zoned, notion of date, which is exactly what PostgreSQL
 * date represents, so the correspondence is direct, with no need to fudge up
 * some timezone info just to shoehorn the data into java.sql.Date.
 */

/*
 * This only answers true for (same class or) DATEOID.
 * The obtainer (below) only needs to construct and remember one instance.
 */
static bool _LocalDate_canReplaceType(Type self, Type other)
{
	TypeClass cls = Type_getClass(other);
	return Type_getClass(self) == cls  ||  Type_getOid(other) == DATEOID;
}

static jvalue _LocalDate_coerceDatum(Type self, Datum arg)
{
	DateADT pgDate = DatumGetDateADT(arg);
	jlong days = (jlong)(pgDate + EPOCH_DIFF);
	jvalue result;
	result.l = JNI_callStaticObjectMethod(
		s_LocalDate_class, s_LocalDate_ofEpochDay, days);
	return result;
}

static Datum _LocalDate_coerceObject(Type self, jobject date)
{
	jlong days =
		JNI_callLongMethod(date, s_LocalDate_toEpochDay) - EPOCH_DIFF;
	return DateADTGetDatum((DateADT)(days));
}

static Type _LocalDate_obtain(Oid typeId)
{
	static Type instance = NULL;
	if ( NULL == instance )
	{
		s_LocalDate_class = JNI_newGlobalRef(PgObject_getJavaClass(
			"java/time/LocalDate"));
		s_LocalDate_ofEpochDay = PgObject_getStaticJavaMethod(s_LocalDate_class,
			"ofEpochDay", "(J)Ljava/time/LocalDate;");
		s_LocalDate_toEpochDay = PgObject_getJavaMethod(s_LocalDate_class,
			"toEpochDay", "()J");

		instance = TypeClass_allocInstance(s_LocalDateClass, DATEOID);
	}
	return instance;
}

/*
 * Date data type. Postgres will pass and expect number of days since
 * Jan 01 2000. Java uses number of millisecs since midnight Jan 01 1970.
 */
static jvalue _Date_coerceDatum(Type self, Datum arg)
{
	DateADT pgDate = DatumGetDateADT(arg);
	jlong date = (jlong)(pgDate + EPOCH_DIFF);
	jvalue result;
	date *= MSECS_PER_DAY;	// Convert to milliseconds
	date = Timestamp_utcMasquerade(date, JNI_FALSE);
	result.l = JNI_newObject(s_Date_class, s_Date_init, date);
	return result;
}

static Datum _Date_coerceObject(Type self, jobject date)
{
	jlong milliSecs = JNI_callLongMethod(date, s_Date_getTime);
	milliSecs = Timestamp_utcMasquerade(milliSecs, JNI_TRUE);
	return DateADTGetDatum((DateADT)(milliSecs / MSECS_PER_DAY - EPOCH_DIFF));
}

/* Make this datatype available to the postgres system.
 */
extern void Date_initialize(void);
void Date_initialize(void)
{
	TypeClass cls = TypeClass_alloc("type.Date");
	cls->JNISignature = "Ljava/sql/Date;";
	cls->javaTypeName = "java.sql.Date";
	cls->coerceDatum  = _Date_coerceDatum;
	cls->coerceObject = _Date_coerceObject;
	Type_registerType("java.sql.Date", TypeClass_allocInstance(cls, DATEOID));

	s_Date_class = JNI_newGlobalRef(PgObject_getJavaClass("java/sql/Date"));
	s_Date_init = PgObject_getJavaMethod(s_Date_class, "<init>", "(J)V");
	s_Date_getTime = PgObject_getJavaMethod(s_Date_class, "getTime",  "()J");

	cls = TypeClass_alloc("type.LocalDate");
	cls->JNISignature = "Ljava/time/LocalDate;";
	cls->javaTypeName = "java.time.LocalDate";
	cls->canReplaceType = _LocalDate_canReplaceType;
	cls->coerceDatum  = _LocalDate_coerceDatum;
	cls->coerceObject = _LocalDate_coerceObject;
	s_LocalDateClass  = cls;
	Type_registerType2(InvalidOid, "java.time.LocalDate", _LocalDate_obtain);
}
