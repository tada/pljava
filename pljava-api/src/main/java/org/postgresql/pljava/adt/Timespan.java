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

import java.util.EnumSet;
import static java.util.EnumSet.of;
import static java.util.EnumSet.noneOf;
import static java.util.EnumSet.range;
import java.util.OptionalInt;
import java.util.Set;

import org.postgresql.pljava.Adapter.Contract;

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
	 *<h2>Why no reference implementation?</h2>
	 *<p>
	 * The types in the {@link Datetime Datetime} interface come with reference
	 * implementations returning Java's JSR310 {@code java.time} types.
	 *<p>
	 * For PostgreSQL {@code INTERVAL}, there are two candidate JSR310 types,
	 * {@code Period} and {@code Duration}, each of which would be appropriate
	 * for a different subset of PostgreSQL {@code INTERVAL} values.
	 *<p>
	 * {@code Period} is appropriate for the months and days components.
	 * A {@code Period} treats the length of a day as subject to daylight
	 * adjustments following time zone rules, as does PostgreSQL.
	 *<p>
	 * {@code Duration} is suitable for the sub-day components. It also allows
	 * access to a "day" field, but treats that as having invariant 24-hour
	 * width.
	 *<p>
	 * Both share the superinterface {@code TemporalAmount}. That interface
	 * itself is described as "a framework-level interface that should not
	 * be widely used in application code", recommending instead that new
	 * concrete types can be created that implement it.
	 *<p>
	 * In the datatype library that comes with the PGJDBC-NG driver, there is
	 * a class {@code com.impossibl.postgres.api.data.Interval} that takes that
	 * approach exactly; it implements {@code TemporalAmount} and represents
	 * all three components of the PostgreSQL interval with their PostgreSQL
	 * semantics. An application with that library available could use an
	 * implementation of this functional interface that would return instances
	 * of that class.
	 *<p>
	 * The PGJDBC driver includes a {@code org.postgresql.util.PGInterval} class
	 * for the same purpose; that one does not derive from any JSR310 type.
	 *<h3>Related notes from the ISO SQL/XML specification</h3>
	 *<p>
	 * SQL/XML specifies how to map SQL {@code INTERVAL} types and values to
	 * the XML Schema types {@code xs:yearMonthDuration} and
	 * {@code xs:dayTimeDuration}, which were added in XML Schema 1.1 as
	 * distinct subtypes of the broader {@code xs:duration} type from XML Schema
	 * 1.0. That Schema 1.0 supertype has a corresponding class in the standard
	 * Java library, {@code javax.xml.datatype.Duration}, so an implementation
	 * of this functional interface returning that type would also be easy.
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

		EnumSet<Field>   YEAR = of(Field.YEAR);
		EnumSet<Field>  MONTH = of(Field.MONTH);
		EnumSet<Field>    DAY = of(Field.DAY);
		EnumSet<Field>   HOUR = of(Field.HOUR);
		EnumSet<Field> MINUTE = of(Field.MINUTE);
		EnumSet<Field> SECOND = of(Field.SECOND);

		EnumSet<Field>   YEAR_TO_MONTH  = range(Field.YEAR, Field.MONTH);
		EnumSet<Field>    DAY_TO_HOUR   = range(Field.DAY,  Field.HOUR);
		EnumSet<Field>    DAY_TO_MINUTE = range(Field.DAY,  Field.MINUTE);
		EnumSet<Field>    DAY_TO_SECOND = range(Field.DAY,  Field.SECOND);
		EnumSet<Field>   HOUR_TO_MINUTE = range(Field.HOUR, Field.MINUTE);
		EnumSet<Field>   HOUR_TO_SECOND = range(Field.HOUR, Field.SECOND);
		EnumSet<Field> MINUTE_TO_SECOND = range(Field.HOUR, Field.SECOND);

		Set<EnumSet<Field>> ALLOWED_FIELDS =
			Set.of(
				noneOf(Field.class), YEAR, MONTH, DAY, HOUR, MINUTE, SECOND,
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
			Interval<T> modify(EnumSet<Field> fields, OptionalInt precision);
		}
	}
}
