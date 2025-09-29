/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.adt;

import java.sql.SQLException;
import java.sql.SQLDataException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MICROS;
import static java.time.temporal.JulianFields.JULIAN_DAY;

import java.util.OptionalInt;

import org.postgresql.pljava.Adapter.Contract;

/**
 * Container for abstract-type functional interfaces in PostgreSQL's
 * {@code DATETIME} type category.
 */
public interface Datetime
{
	/**
	 * PostgreSQL "infinitely early" date, as a value of what would otherwise be
	 * days from the PostgreSQL epoch.
	 */
	int DATEVAL_NOBEGIN      = Integer.MIN_VALUE;

	/**
	 * PostgreSQL "infinitely late" date, as a value of what would otherwise be
	 * days from the PostgreSQL epoch.
	 */
	int DATEVAL_NOEND        = Integer.MAX_VALUE;

	/**
	 * PostgreSQL "infinitely early" timestamp, as a value of what would
	 * otherwise be microseconds from the PostgreSQL epoch.
	 */
	long DT_NOBEGIN          = Long.MIN_VALUE;

	/**
	 * PostgreSQL "infinitely late" timestamp, as a value of what would
	 * otherwise be microseconds from the PostgreSQL epoch.
	 */
	long DT_NOEND            = Long.MAX_VALUE;

	/**
	 * The PostgreSQL "epoch", 1 January 2000, as a Julian day; the date
	 * represented by a {@code DATE}, {@code TIMESTAMP}, or {@code TIMESTAMPTZ}
	 * with a stored value of zero.
	 */
	int POSTGRES_EPOCH_JDATE = 2451545;

	/**
	 * Maximum value allowed for a type modifier specifying the seconds digits
	 * to the right of the decimal point for a {@code TIME} or {@code TIMETZ}.
	 */
	int MAX_TIME_PRECISION      = 6;

	/**
	 * Maximum value allowed for a type modifier specifying the seconds digits
	 * to the right of the decimal point for a {@code TIMESTAMP} or
	 * {@code TIMESTAMPTZ}.
	 */
	int MAX_TIMESTAMP_PRECISION = 6;

	/**
	 * The maximum allowed value, inclusive, for a {@code TIME} or the time
	 * portion of a {@code TIMETZ}.
	 *<p>
	 * The limit is inclusive; PostgreSQL officially accepts 24:00:00
	 * as a valid time value.
	 */
	long USECS_PER_DAY          = 86400000000L;

	/**
	 * The {@code DATE} type's PostgreSQL semantics: a signed number of days
	 * since the "Postgres epoch".
	 */
	@FunctionalInterface
	public interface Date<T> extends Contract.Scalar<T>
	{
		/**
		 * The PostgreSQL "epoch" as a {@code java.time.LocalDate}.
		 */
		LocalDate POSTGRES_EPOCH =
			LocalDate.EPOCH.with(JULIAN_DAY, POSTGRES_EPOCH_JDATE);

		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * The argument represents days since
		 * {@link #POSTGRES_EPOCH POSTGRES_EPOCH}, unless it is one of
		 * the special values {@link #DATEVAL_NOBEGIN DATEVAL_NOBEGIN} or
		 * {@link #DATEVAL_NOEND DATEVAL_NOEND}.
		 *<p>
		 * When constructing a representation that lacks notions of positive or
		 * negative "infinity", one option is to simply map the above special
		 * values no differently than ordinary ones, and remember the two
		 * resulting representations as the "infinite" ones. If that is done
		 * without wraparound, the resulting "-infinity" value will precede all
		 * other PostgreSQL-representable dates and the resulting "+infinity"
		 * will follow them.
		 *<p>
		 * The older {@code java.util.Date} cannot represent those values
		 * without wraparound; the two resulting values can still be saved as
		 * representing -infinity and +infinity, but will not have the expected
		 * ordering with respect to other values. They will both be quite far
		 * from the present.
		 */
		T construct(int daysSincePostgresEpoch);

		/**
		 * A reference implementation that maps to {@link LocalDate LocalDate}.
		 *<p>
		 * The PostgreSQL "-infinity" and "+infinity" values are mapped to
		 * {@code LocalDate} instances matching (by {@code equals}) the special
		 * instances {@code NOBEGIN} and {@code NOEND} here, respectively.
		 */
		static class AsLocalDate implements Date<LocalDate>
		{
			private AsLocalDate() // I am a singleton
			{
			}

			public static final AsLocalDate INSTANCE = new AsLocalDate();

			/**
			 * {@code LocalDate} representing PostgreSQL's "infinitely early"
			 * date.
			 */
			public static final LocalDate NOBEGIN =
				INSTANCE.construct(DATEVAL_NOBEGIN);

			/**
			 * {@code LocalDate} representing PostgreSQL's "infinitely late"
			 * date.
			 */
			public static final LocalDate NOEND =
				INSTANCE.construct(DATEVAL_NOEND);

			@Override
			public LocalDate construct(int daysSincePostgresEpoch)
			{
				return POSTGRES_EPOCH.plusDays(daysSincePostgresEpoch);
			}

			public <T> T store(LocalDate d, Date<T> f) throws SQLException
			{
				if ( NOBEGIN.isAfter(d)  ||  d.isAfter(NOEND) )
					throw new SQLDataException(String.format(
						"date out of range: \"%s\"", d), "22008");

				return f.construct((int)POSTGRES_EPOCH.until(d, DAYS));
			}
		}
	}

	/**
	 * The {@code TIME} type's PostgreSQL semantics: microseconds since
	 * midnight.
	 */
	@FunctionalInterface
	public interface Time<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * The argument represents microseconds since midnight, nonnegative
		 * and not exceeding {@code USECS_PER_DAY}.
		 *<p>
		 * PostgreSQL does allow the value to exactly <em>equal</em>
		 * {@code USECS_PER_DAY}. 24:00:00 is considered a valid value. That
		 * may need extra attention if the representation to be constructed
		 * doesn't allow that.
		 */
		T construct(long microsecondsSinceMidnight);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns a {@code Time} function possibly tailored ("curried")
			 * with the values from a PostgreSQL type modifier on the type.
			 *<p>
			 * The precision indicates the number of seconds digits desired
			 * to the right of the decimal point, and must be positive and
			 * no greater than {@code MAX_TIME_PRECISION}.
			 */
			Time<T> modify(OptionalInt precision);
		}

		/**
		 * A reference implementation that maps to {@link LocalTime LocalTime}.
		 *<p>
		 * While PostgreSQL allows 24:00:00 as a valid time, {@code LocalTime}
		 * maxes out at the preceding nanosecond. That is still a value that
		 * can be distinguished, because PostgreSQL's time resolution is only
		 * to microseconds, so the PostgreSQL 24:00:00 value will be mapped
		 * to that.
		 *<p>
		 * In the other direction, nanoseconds will be rounded to microseconds,
		 * so any value within the half-microsecond preceding {@code HOUR24}
		 * will become the PostgreSQL 24:00:00 value.
		 */
		static class AsLocalTime implements Time<LocalTime>
		{
			private AsLocalTime() // I am a singleton
			{
			}

			public static final AsLocalTime INSTANCE = new AsLocalTime();

			/**
			 * {@code LocalTime} representing the 24:00:00 time that PostgreSQL
			 * accepts but {@code LocalTime} does not.
			 *<p>
			 * This {@code LocalTime} represents the immediately preceding
			 * nanosecond. That is still distinguishable from any other
			 * PostgreSQL time, because those have only microsecond
			 * resolution.
			 */
			public static final LocalTime HOUR24 =
				LocalTime.ofNanoOfDay(1000L * USECS_PER_DAY - 1L);

			@Override
			public LocalTime construct(long microsecondsSinceMidnight)
			{
				if ( USECS_PER_DAY == microsecondsSinceMidnight )
					return HOUR24;

				return LocalTime.ofNanoOfDay(1000L * microsecondsSinceMidnight);
			}

			public <T> T store(LocalTime t, Time<T> f)
			{
				long nanos = t.toNanoOfDay();

				return f.construct((500L + nanos) / 1000L);
			}
		}
	}

	/**
	 * The {@code TIMETZ} type's PostgreSQL semantics: microseconds since
	 * midnight, accompanied by a time zone offset expressed in seconds.
	 */
	@FunctionalInterface
	public interface TimeTZ<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * The first argument represents microseconds since midnight,
		 * nonnegative and not exceeding {@code USECS_PER_DAY}, and
		 * the second is a time zone offset expressed in seconds, positive
		 * for locations west of the prime meridian.
		 *<p>
		 * It should be noted that other common conventions, such as ISO 8601
		 * and {@code java.time.ZoneOffset}, use positive offsets for locations
		 * <em>east</em> of the prime meridian, requiring a sign flip.
		 *<p>
		 * Also noteworthy, as with {@link Time Time}, is that the first
		 * argument may exactly <em>equal</em> {@code USECS_PER_DAY}; 24:00:00
		 * is a valid value to PostgreSQL. That may need extra attention if
		 * the representation to be constructed doesn't allow that.
		 * @param microsecondsSinceMidnight the time of day, in the zone
		 * indicated by the second argument
		 * @param secondsWestOfPrimeMeridian note that the sign of this time
		 * zone offset will be the opposite of that used in other common systems
		 * using positive values for offsets east of the prime meridian.
		 */
		T construct(
			long microsecondsSinceMidnight, int secondsWestOfPrimeMeridian);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns a {@code TimeTZ} function possibly tailored ("curried")
			 * with the values from a PostgreSQL type modifier on the type.
			 *<p>
			 * The precision indicates the number of seconds digits desired
			 * to the right of the decimal point, and must be positive and
			 * no greater than {@code MAX_TIME_PRECISION}.
			 */
			TimeTZ<T> modify(OptionalInt precision);
		}

		/**
		 * A reference implementation that maps to
		 * {@link OffsetTime OffsetTime}.
		 *<p>
		 * While PostgreSQL allows 24:00:00 as a valid time, Java's rules
		 * max out at the preceding nanosecond. That is still a value that
		 * can be distinguished, because PostgreSQL's time resolution is only
		 * to microseconds, so the PostgreSQL 24:00:00 value will be mapped
		 * to a value whose {@code LocalTime} component matches (with
		 * {@code equals}) {@link Time.AsLocalTime#HOUR24 AsLocalTime.HOUR24},
		 * which is really one nanosecond shy of 24 hours.
		 *<p>
		 * In the other direction, nanoseconds will be rounded to microseconds,
		 * so any value within the half-microsecond preceding {@code HOUR24}
		 * will become the PostgreSQL 24:00:00 value.
		 */
		static class AsOffsetTime implements TimeTZ<OffsetTime>
		{
			private AsOffsetTime() // I am a singleton
			{
			}

			public static final AsOffsetTime INSTANCE = new AsOffsetTime();

			@Override
			public OffsetTime construct(
				long microsecondsSinceMidnight, int secondsWestOfPrimeMeridian)
			{
				ZoneOffset offset =
					ZoneOffset.ofTotalSeconds( - secondsWestOfPrimeMeridian);

				LocalTime local = Time.AsLocalTime.INSTANCE
					.construct(microsecondsSinceMidnight);

				return OffsetTime.of(local, offset);
			}

			public <T> T store(OffsetTime t, TimeTZ<T> f)
			{
				int secondsWest = - t.getOffset().getTotalSeconds();

				LocalTime local = t.toLocalTime();

				return Time.AsLocalTime.INSTANCE
					.store(local, micros -> f.construct(micros, secondsWest));
			}
		}
	}

	/**
	 * The {@code TIMESTAMP} type's PostgreSQL semantics: microseconds since
	 * midnight of the PostgreSQL epoch, without an assumed time zone.
	 */
	@FunctionalInterface
	public interface Timestamp<T> extends Contract.Scalar<T>
	{
		/**
		 * The PostgreSQL "epoch" as a {@code java.time.LocalDateTime}.
		 */
		LocalDateTime POSTGRES_EPOCH = Date.POSTGRES_EPOCH.atStartOfDay();

		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * The argument represents microseconds since midnight on
		 * {@link #POSTGRES_EPOCH POSTGRES_EPOCH}.
		 *<p>
		 * Because no particular time zone is understood to apply, the exact
		 * corresponding point on a standard timeline cannot be identified,
		 * absent outside information. It is typically used to represent
		 * a timestamp in the local zone, whatever that is.
		 *<p>
		 * The argument represents microseconds since
		 * {@link #POSTGRES_EPOCH POSTGRES_EPOCH}, unless it is one of
		 * the special values {@link #DT_NOBEGIN DT_NOBEGIN} or
		 * {@link #DT_NOEND DT_NOEND}.
		 *<p>
		 * When constructing a representation that lacks notions of positive or
		 * negative "infinity", one option is to simply map the above special
		 * values no differently than ordinary ones, and remember the two
		 * resulting representations as the "infinite" ones. If that is done
		 * without wraparound, the resulting "-infinity" value will precede all
		 * other PostgreSQL-representable dates and the resulting "+infinity"
		 * will follow them.
		 *<p>
		 * The older {@code java.util.Date} cannot represent those values
		 * without wraparound; the two resulting values can still be saved as
		 * representing -infinity and +infinity, but will not have the expected
		 * ordering with respect to other values. They will both be quite far
		 * from the present.
		 */
		T construct(long microsecondsSincePostgresEpoch);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns a {@code Timestamp} function possibly tailored
			 * ("curried") with the values from a PostgreSQL type modifier
			 * on the type.
			 *<p>
			 * The precision indicates the number of seconds digits desired
			 * to the right of the decimal point, and must be positive and
			 * no greater than {@code MAX_TIMESTAMP_PRECISION}.
			 */
			Timestamp<T> modify(OptionalInt precision);
		}

		/**
		 * A reference implementation that maps to
		 * {@link LocalDateTime LocalDateTime}.
		 *<p>
		 * The PostgreSQL "-infinity" and "+infinity" values are mapped to
		 * {@code LocalDateTime} instances matching (by {@code equals})
		 * the special instances {@code NOBEGIN} and {@code NOEND} here,
		 * respectively.
		 */
		static class AsLocalDateTime implements Timestamp<LocalDateTime>
		{
			private AsLocalDateTime() // I am a singleton
			{
			}

			public static final AsLocalDateTime INSTANCE =
				new AsLocalDateTime();

			/**
			 * {@code LocalDateTime} representing PostgreSQL's "infinitely
			 * early" timestamp.
			 */
			public static final LocalDateTime NOBEGIN =
				INSTANCE.construct(DT_NOBEGIN);

			/**
			 * {@code LocalDateTime} representing PostgreSQL's "infinitely
			 * late" timestamp.
			 */
			public static final LocalDateTime NOEND =
				INSTANCE.construct(DT_NOEND);

			@Override
			public LocalDateTime construct(long microsecondsSincePostgresEpoch)
			{
				return
					POSTGRES_EPOCH.plus(microsecondsSincePostgresEpoch, MICROS);
			}

			public <T> T store(LocalDateTime d, Timestamp<T> f)
			throws SQLException
			{
				try
				{
					return f.construct(POSTGRES_EPOCH.until(d, MICROS));
				}
				catch ( ArithmeticException e )
				{
					throw new SQLDataException(String.format(
						"timestamp out of range: \"%s\"", d), "22008", e);
				}
			}
		}
	}

	/**
	 * The {@code TIMESTAMPTZ} type's PostgreSQL semantics: microseconds since
	 * midnight UTC of the PostgreSQL epoch.
	 */
	@FunctionalInterface
	public interface TimestampTZ<T> extends Contract.Scalar<T>
	{
		/**
		 * The PostgreSQL "epoch" as a {@code java.time.OffsetDateTime}.
		 */
		OffsetDateTime POSTGRES_EPOCH =
			OffsetDateTime.of(Timestamp.POSTGRES_EPOCH, UTC);

		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * The argument represents microseconds since midnight UTC on
		 * {@link #POSTGRES_EPOCH POSTGRES_EPOCH}.
		 *<p>
		 * Given any desired local time zone, conversion to/from this value
		 * is possible if the rules for that time zone as of the represented
		 * date are available.
		 *<p>
		 * The argument represents microseconds since
		 * {@link #POSTGRES_EPOCH POSTGRES_EPOCH}, unless it is one of
		 * the special values {@link #DT_NOBEGIN DT_NOBEGIN} or
		 * {@link #DT_NOEND DT_NOEND}.
		 *<p>
		 * When constructing a representation that lacks notions of positive or
		 * negative "infinity", one option is to simply map the above special
		 * values no differently than ordinary ones, and remember the two
		 * resulting representations as the "infinite" ones. If that is done
		 * without wraparound, the resulting "-infinity" value will precede all
		 * other PostgreSQL-representable dates and the resulting "+infinity"
		 * will follow them.
		 *<p>
		 * The older {@code java.util.Date} cannot represent those values
		 * without wraparound; the two resulting values can still be saved as
		 * representing -infinity and +infinity, but will not have the expected
		 * ordering with respect to other values. They will both be quite far
		 * from the present.
		 */
		T construct(long microsecondsSincePostgresEpochUTC);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns a {@code TimestampTZ} function possibly tailored
			 * ("curried") with the values from a PostgreSQL type modifier
			 * on the type.
			 *<p>
			 * The precision indicates the number of seconds digits desired
			 * to the right of the decimal point, and must be positive and
			 * no greater than {@code MAX_TIMESTAMP_PRECISION}.
			 */
			TimestampTZ<T> modify(OptionalInt precision);
		}

		/**
		 * A reference implementation that maps to
		 * {@link OffsetDateTime OffsetDateTime}.
		 *<p>
		 * A value from PostgreSQL is always understood to be at UTC, and
		 * will be mapped always to an {@code OffsetDateTime} with UTC as
		 * its offset.
		 *<p>
		 * A value from Java is adjusted by its offset so that PostgreSQL will
		 * always be passed {@code microsecondsSincePostgresEpochUTC}.
		 *<p>
		 * The PostgreSQL "-infinity" and "+infinity" values are mapped to
		 * instances whose corresponding {@code LocalDateTime} at UTC will match
		 * (by {@code equals}) the constants {@code NOBEGIN} and {@code NOEND}
		 * of {@code AsLocalDateTime}, respectively.
		 */
		static class AsOffsetDateTime implements TimestampTZ<OffsetDateTime>
		{
			private AsOffsetDateTime() // I am a singleton
			{
			}

			public static final AsOffsetDateTime INSTANCE =
				new AsOffsetDateTime();

			@Override
			public OffsetDateTime construct(long microsecondsSincePostgresEpoch)
			{
				return
					POSTGRES_EPOCH.plus(microsecondsSincePostgresEpoch, MICROS);
			}

			public <T> T store(OffsetDateTime d, TimestampTZ<T> f)
			throws SQLException
			{
				try
				{
					return f.construct(POSTGRES_EPOCH.until(d, MICROS));
				}
				catch ( ArithmeticException e )
				{
					throw new SQLDataException(String.format(
						"timestamp out of range: \"%s\"", d), "22008", e);
				}
			}
		}
	}
}
