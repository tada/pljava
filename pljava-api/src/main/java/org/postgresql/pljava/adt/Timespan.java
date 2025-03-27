/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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

import java.util.EnumSet;
import static java.util.Collections.unmodifiableSet;
import static java.util.EnumSet.of;
import static java.util.EnumSet.noneOf;
import static java.util.EnumSet.range;
import java.util.OptionalInt;
import java.util.Set;

import org.postgresql.pljava.Adapter.Contract;

/*
 * For the javadoc:
 */
import java.time.Duration;
import java.time.Period;
import java.time.chrono.ChronoPeriod;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

/**
 * Container for abstract-type functional interfaces in PostgreSQL's
 * {@code TIMESPAN} type category (which, at present, includes the single
 * type {@code INTERVAL}).
 */
public interface Timespan
{
	/**
	 * The {@code INTERVAL} type's PostgreSQL semantics: separate microseconds,
	 * days, and months components, independently signed.
	 *<p>
	 * A type modifier can specify field-presence bits, and precision (number of
	 * seconds digits to the right of the decimal point). An empty fields set
	 * indicates that fields were not specified.
	 *<h2>Infinitely negative or positive intervals</h2>
	 *<p>
	 * Starting with PostgreSQL 17, intervals whose three components are
	 * {@code (Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)} or
	 * {@code (Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)} have the
	 * semantics of infinitely negative or positive intervals, respectively.
	 * In PostgreSQL versions before 17, they are simply the most negative or
	 * positive representable finite intervals.
	 *<h2>Why no reference implementation?</h2>
	 *<p>
	 * The types in the {@link Datetime Datetime} interface come with reference
	 * implementations returning Java's JSR310 {@code java.time} types.
	 *<p>
	 * For PostgreSQL {@code INTERVAL}, there are two candidate JSR310 concrete
	 * types, {@link Period Period} and {@link Duration Duration}, each of which
	 * would be appropriate for a different subset of PostgreSQL
	 * {@code INTERVAL} values.
	 *<p>
	 * {@code Period} is appropriate for the months and days components.
	 * A {@code Period} treats the length of a day as subject to daylight
	 * adjustments following time zone rules, as does PostgreSQL.
	 *<p>
	 * {@code Duration} is suitable for the sub-day components. It also allows
	 * access to a "day" field, but treats that field, unlike PostgreSQL, as
	 * having invariant 24-hour width.
	 *<p>
	 * Both share the superinterface {@link TemporalAmount TemporalAmount}. That
	 * interface itself is described as "a framework-level interface that should
	 * not be widely used in application code", recommending instead that new
	 * concrete types can be created that implement it.
	 *<p>
	 * PostgreSQL's {@code INTERVAL} could be represented by a concrete type
	 * that implements {@code TemporalAmount} or, preferably (because its days
	 * and months components are subject to rules of a chronology), its
	 * subinterface {@link ChronoPeriod ChronoPeriod}. The most natural such
	 * implementation would have {@link TemporalAmount#getUnits getUnits} return
	 * {@link ChronoUnit#MONTHS MONTHS}, {@link ChronoUnit#DAYS DAYS}, and
	 * {@link ChronoUnit#MICROS MICROS}, except for instances representing the
	 * infinitely negative or positive intervals and using the unit
	 * {@link ChronoUnit#FOREVER FOREVER} with a negative or positive value.
	 *<p>
	 * In the datatype library that comes with the PGJDBC-NG driver, there is
	 * a class {@code com.impossibl.postgres.api.data.Interval} that does
	 * implement {@code TemporalAmount} (but not {@code ChronoPeriod}) and
	 * internally segregates the PostgreSQL {@code INTERVAL} components into
	 * a {@code Period} and a {@code Duration}. An application with that library
	 * available could use an implementation of this functional interface that
	 * would return instances of that class. As of PGJDBC-NG 0.8.9, the class
	 * does not seem to have a representation for the PostgreSQL 17 infinite
	 * intervals. Its {@code getUnits} method returns a longer list of units
	 * than needed to naturally represent the PostgreSQL type.
	 *<p>
	 * The PGJDBC driver includes the {@code org.postgresql.util.PGInterval}
	 * class for the same purpose; that one does not derive from any JSR310
	 * type. As of PGJDBC 42.7.5, it does not explicitly represent infinite
	 * intervals, and also has an internal state split into more units than the
	 * natural representation would require.
	 *<h3>Related notes from the ISO SQL/XML specification</h3>
	 *<p>
	 * SQL/XML specifies how to map SQL {@code INTERVAL} types and values to
	 * the XML Schema types {@code xs:yearMonthDuration} and
	 * {@code xs:dayTimeDuration}, which were added in XML Schema 1.1 as
	 * distinct subtypes of the broader {@code xs:duration} type from XML Schema
	 * 1.0. That Schema 1.0 supertype has a corresponding class in the standard
	 * Java library,
	 * {@link javax.xml.datatype.Duration javax.xml.datatype.Duration}, so
	 * an implementation of this functional interface to return that type would
	 * also be easy. It would not, however, represent PostgreSQL 17 infinitely
	 * negative or positive intervals.
	 *<p>
	 * These XML Schema types do not perfectly align with the PostgreSQL
	 * {@code INTERVAL} type, because they group the day with the sub-day
	 * components and treat it as having invariant width. (The only time zone
	 * designations supported in XML Schema are fixed offsets, for which no
	 * daylight rules apply). The XML Schema types allow one overall sign,
	 * positive or negative, but do not allow the individual components to have
	 * signs that differ, as PostgreSQL does.
	 *<p>
	 * Java's JSR310 types can be used with equal convenience in the PostgreSQL
	 * way (by assigning <var>days</var> to the {@code Period} and the smaller
	 * components to the {@code Duration}) or in the XML Schema way (by storing
	 * <var>days</var> in the {@code Duration} along with the smaller
	 * components), but of course those choices have different implications.
	 *<p>
	 * A related consideration is, in a scheme like SQL/XML's where the SQL
	 * {@code INTERVAL} can be mapped to a choice of types, whether that choice
	 * is made statically (i.e. by looking at the declared type modifier such as
	 * {@code YEAR TO MONTH} or {@code HOUR TO SECOND} for a column) or
	 * per-value (by looking at which fields are nonzero in each value
	 * encountered).
	 *<p>
	 * The SQL/XML rule is to choose a static mapping at analysis time according
	 * to the type modifier. {@code YEAR}, {@code MONTH}, or
	 * {@code YEAR TO MONTH} call for a mapping to {@code xs:yearMonthDuration},
	 * while any of the finer modifiers call for mapping to
	 * {@code xs:dayTimeDuration}, and no mapping is defined for an
	 * {@code INTERVAL} lacking a type modifier to constrain its fields in one
	 * of those ways. Again, those specified mappings assume that days are not
	 * subject to daylight rules, contrary to the behavior of the PostgreSQL
	 * type.
	 *<p>
	 * In view of those considerations, there seems to be no single mapping of
	 * PostgreSQL {@code INTERVAL} to a common Java type that is sufficiently
	 * free of caveats to stand as a reference implementation. An application
	 * ought to choose an implementation of this functional interface to create
	 * whatever representation of an {@code INTERVAL} will suit that
	 * application's purposes.
	 */
	@FunctionalInterface
	public interface Interval<T> extends Contract.Scalar<T>
	{
		enum Field
		{
			YEAR, MONTH, DAY, HOUR, MINUTE, SECOND
		}

		Set<Field>   YEAR = unmodifiableSet(of(Field.YEAR));
		Set<Field>  MONTH = unmodifiableSet(of(Field.MONTH));
		Set<Field>    DAY = unmodifiableSet(of(Field.DAY));
		Set<Field>   HOUR = unmodifiableSet(of(Field.HOUR));
		Set<Field> MINUTE = unmodifiableSet(of(Field.MINUTE));
		Set<Field> SECOND = unmodifiableSet(of(Field.SECOND));

		Set<Field>   YEAR_TO_MONTH  =
							unmodifiableSet(range(Field.YEAR, Field.MONTH));
		Set<Field>    DAY_TO_HOUR   =
							unmodifiableSet(range(Field.DAY,  Field.HOUR));
		Set<Field>    DAY_TO_MINUTE =
							unmodifiableSet(range(Field.DAY,  Field.MINUTE));
		Set<Field>    DAY_TO_SECOND =
							unmodifiableSet(range(Field.DAY,  Field.SECOND));
		Set<Field>   HOUR_TO_MINUTE =
							unmodifiableSet(range(Field.HOUR, Field.MINUTE));
		Set<Field>   HOUR_TO_SECOND =
							unmodifiableSet(range(Field.HOUR, Field.SECOND));
		Set<Field> MINUTE_TO_SECOND =
							unmodifiableSet(range(Field.HOUR, Field.SECOND));

		Set<Set<Field>> ALLOWED_FIELDS =
			Set.of(
				unmodifiableSet(noneOf(Field.class)),
				YEAR, MONTH, DAY, HOUR, MINUTE, SECOND,
				YEAR_TO_MONTH, DAY_TO_HOUR, DAY_TO_MINUTE, DAY_TO_SECOND,
				HOUR_TO_MINUTE, HOUR_TO_SECOND, MINUTE_TO_SECOND);

		int MAX_INTERVAL_PRECISION = 6;

		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * PostgreSQL allows the three components to have independent signs.
		 * They are stored separately because the results of combining them with
		 * a date or a timestamp cannot be precomputed without knowing the other
		 * operand.
		 *<p>
		 * In arithmetic involving an interval and a timestamp, the width of one
		 * unit in <var>days</var> can depend on the other operand if a timezone
		 * applies and has daylight savings rules:
		 *<pre>
		 * SELECT (t + i) - t
		 * FROM (VALUES (interval '1' DAY)) AS s(i),
		 * (VALUES (timestamptz '12 mar 2022'), ('13 mar 2022'), ('6 nov 2022')) AS v(t);
		 * ----------------
		 *  1 day
		 *  23:00:00
		 *  1 day 01:00:00
		 *</pre>
		 *<p>
		 * In arithmetic involving an interval and a date or timestamp, the
		 * width of one unit in <var>months</var> can depend on the calendar
		 * month of the other operand, as well as on timezone shifts as for
		 * <var>days</var>:
		 *<pre>
		 * SELECT (t + i) - t
		 * FROM (VALUES (interval '1' MONTH)) AS s(i),
		 * (VALUES (timestamptz '1 feb 2022'), ('1 mar 2022'), ('1 nov 2022')) AS v(t);
		 * ------------------
		 *  28 days
		 *  30 days 23:00:00
		 *  30 days 01:00:00
		 *</pre>
		 */
		T construct(long microseconds, int days, int months);

		/**
		 * Functional interface to obtain information from the PostgreSQL type
		 * modifier applied to the type.
		 */
		@FunctionalInterface
		interface Modifier<T>
		{
			/**
			 * Returns an {@code Interval} function possibly tailored
			 * ("curried") with the values from a PostgreSQL type modifier
			 * applied to the type.
			 *<p>
			 * The notional fields to be present in the interval are indicated
			 * by <var>fields</var>; the SQL standard defines more than three of
			 * these, which PostgreSQL combines into the three components
			 * actually stored. In a valid type modifier, the <var>fields</var>
			 * set must equal one of the members of {@code ALLOWED_FIELDS}: one
			 * of the named constants in this interface or the empty set. If it
			 * is empty, the type modifier does not constrain the fields that
			 * may be present. In practice, it is the finest field allowed in
			 * the type modifier that matters; PostgreSQL rounds away portions
			 * of an interval finer than that, but applies no special treatment
			 * based on the coarsest field the type modifier mentions.
			 *<p>
			 * The desired number of seconds digits to the right of the decimal
			 * point is indicated by <var>precision</var> if present, which must
			 * be between 0 and {@code MAX_INTERVAL_PRECISION} inclusive. In
			 * a valid type modifier, when this is specified, <var>fields</var>
			 * must either include {@code SECONDS}, or be unspecified.
			 */
			Interval<T> modify(Set<Field> fields, OptionalInt precision);
		}
	}
}
