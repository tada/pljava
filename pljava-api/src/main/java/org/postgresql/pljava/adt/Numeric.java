/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import static java.lang.Math.multiplyExact;

import java.math.BigDecimal;

import java.sql.SQLException;
import java.sql.SQLDataException;

import org.postgresql.pljava.Adapter.Contract;

/**
 * The {@code NUMERIC} type's PostgreSQL semantics: a sign (or indication
 * that the value is NaN, + infinity, or - infinity), a display scale,
 * a weight, and zero or more base-ten-thousand digits.
 *<p>
 * This data type can have a type modifier that specifies a maximum
 * precision (total number of base-ten digits to retain) and a maximum scale
 * (how many of those base-ten digits are right of the decimal point).
 *<p>
 * A curious feature of the type is that, when a type modifier is specified,
 * the value becomes "anchored" to the decimal point: all of its decimal
 * digits must be within <var>precision</var> places of the decimal point,
 * or an error is reported. This rules out the kind of values that can crop
 * up in physics, for example, where there might be ten digits of precision
 * but those are twenty places away from the decimal point. This limitation
 * apparently follows from the ISO SQL definitions of the precision and
 * scale.
 *<p>
 * However, when PostgreSQL {@code NUMERIC} is used with no type modifier,
 * such values are not rejected, and are stored efficiently, just as you
 * would expect, keeping only the digits that are needed and adjusting
 * <var>weight</var> for the distance to the decimal point.
 *<p>
 * In mapping to and from a Java representation, extra care may be needed
 * if that capability is to be preserved.
 */
@FunctionalInterface
public interface Numeric<T> extends Contract.Scalar<T>
{
	/**
	 * The maximum precision that may be specified in a {@code numeric} type
	 * modifier.
	 *<p>
	 * Without a modifier, the type is subject only to its implementation
	 * limits, which are much larger.
	 */
	int NUMERIC_MAX_PRECISION = 1000;

	/**
	 * The minimum 'scale' that may be specified in a {@code numeric} type
	 * modifier in PostgreSQL 15 or later.
	 *<p>
	 * Negative scale indicates rounding left of the decimal point. A scale of
	 * -1000 indicates rounding to a multiple of 10<sup>1000</sup>.
	 *<p>
	 * Prior to PostgreSQL 15, a type modifier did not allow a negative
	 * scale.
	 *<p>
	 * Without a modifier, the type is subject only to its implementation
	 * limits.
	 */
	int NUMERIC_MIN_SCALE = -1000;

	/**
	 * The maximum 'scale' that may be specified in a {@code numeric} type
	 * modifier in PostgreSQL 15 or later.
	 *<p>
	 * When scale is positive, the digits string represents a value smaller by
	 * the indicated power of ten. When scale exceeds precision, the digits
	 * string represents digits that appear following (scale - precision) zeros
	 * to the right of the decimal point.
	 *<p>
	 * Prior to PostgreSQL 15, a type modifier did not allow a scale greater
	 * than the specified precision.
	 *<p>
	 * Without a modifier, the type is subject only to its implementation
	 * limits.
	 */
	int NUMERIC_MAX_SCALE = 1000;

	/**
	 * The base of the 'digit' elements supplied by PostgreSQL.
	 *<p>
	 * This is also built into the parameter name <var>base10000Digits</var> and
	 * is highly unlikely to change; a comment in the PostgreSQL code since 2015
	 * confirms "values of {@code NBASE} other than 10000 are considered of
	 * historical interest only and are no longer supported in any sense".
	 */
	int NBASE = 10000;

	/**
	 * Decimal digits per {@code NBASE} digit.
	 */
	int DEC_DIGITS = 4;

	/**
	 * Label to distinguish positive, negative, and three kinds of special
	 * values.
	 */
	enum Kind { POSITIVE, NEGATIVE, NAN, POSINFINITY, NEGINFINITY }

	/**
	 * Constructs a representation <var>T</var> from the components
	 * of the PostgreSQL data type.
	 *<p>
	 * A note about <var>displayScale</var>: when positive, it is information,
	 * stored with the PostgreSQL value, that conveys how far (right of the
	 * units place) the least significant decimal digit of the intended value
	 * falls.
	 *<p>
	 * An <var>apparentScale</var> can also be computed:
	 *<pre>
	 *  apparentScale = (1 + weight - base10000Digits.length) * (- DEC_DIGITS)
	 *</pre>
	 * This computation has a simple meaning, and gives the distance, right of
	 * the units place, of the least-significant decimal digit in the stored
	 * representation. When negative, of course, it means that least stored
	 * digit falls left of the units place.
	 *<p>
	 * Because of the {@code DEC_DIGITS} factor, <var>apparentScale</var>
	 * computed this way will always be a multiple of four, the next such (in
	 * the direction of more significant digits) from the position of the
	 * actual least significant digit in the value. So <var>apparentScale</var>
	 * may exceed <var>displayScale</var> by as much as three, and, if so,
	 * <var>displayScale</var> should be used in preference, to avoid
	 * overstating the value's significant figures.
	 *<p>
	 * Likewise, if <var>displayScale</var> is positive, it should be used even
	 * if it exceeds <var>apparentScale</var>. In that case, it conveys that
	 * PostgreSQL knows additional digits are significant, even though they were
	 * zero and it did not store them.
	 *<p>
	 * However, the situation when <var>displayScale</var> is zero is less
	 * clear-cut, because PostgreSQL simply disallows it ever to be negative.
	 * This clamping of <var>displayScale</var> loses information, such that a
	 * value with <var>displayScale</var> zero and <var>apparentScale</var>
	 * negative may represent any of:
	 *<ul>
	 * <li>A limited-precision value with non-significant trailing zeros (from
	 * -<var>apparentScale</var> to as many as -<var>apparentScale</var>+3 of
	 * them)</li>
	 * <li>A precise integer, all of whose -<var>apparentScale</var> non-stored
	 * significant digits just happened to be zeros</li>
	 * <li>or anything in between.</li>
	 *</ul>
	 *<p>
	 * That these cases can't be distinguished is inherent in PostgreSQL's
	 * representation of the type, and any implementation of this interface will
	 * need to make and document a choice of how to proceed. If the choice is
	 * to rely on <var>apparentScale</var>, then the fact that it is a multiple
	 * of four and may overstate, by up to three, the number of significant
	 * digits (as known, perhaps, to a human who assigned the value) has to be
	 * lived with; when <var>displayScale</var> is clamped to zero there simply
	 * isn't enough information to do better.
	 *<p>
	 * For example, consider this adapter applied to the result of:
	 *<pre>
	 * SELECT 6.62607015e-34 AS planck, 6.02214076e23 AS avogadro;
	 *</pre>
	 *<p>
	 * Planck's constant (a small number defined with nine significant places)
	 * will be presented with <var>displayScale</var>=42, <var>weight</var>=-9,
	 * and <var>base10000Digits</var>=[662, 6070, 1500].
	 * Because <var>apparentScale</var> works out to 44 (placing the least
	 * stored digit 44 places right of the decimal point, a multiple of 4) but
	 * <var>displayScale</var> is only 42, it is clear that the two trailing
	 * zeroes in the last element are non-significant, and the value has not
	 * eleven but only nine significant figures.
	 *<p>
	 * In contrast, Avogadro's number (a large one, defined also with nine
	 * significant places) will arrive with <var>weight</var>=5 and
	 * <var>base10000Digits</var>=[6022, 1407, 6000], but
	 * <var>displayScale</var> will not be -15; it is clamped to zero instead.
	 * If an implementation of this contract chooses to compute
	 * <var>apparentScale</var>, that will be -12 (the next larger multiple of
	 * four) and the value will seem to have gained three extra significant
	 * figures. On the other hand, in an implementation that takes the
	 * clamped-to-zero <var>displayScale</var> at face value, the number will
	 * seem to have gained fifteen extra significant figures.
	 * @param kind POSITIVE, NEGATIVE, POSINFINITY, NEGINFINITY, or NAN
	 * @param displayScale nominal precision, nonnegative; the number of
	 * <em>base ten</em> digits right of the decimal point. If this exceeds
	 * the number of right-of-decimal digits determined by the stored value,
	 * the excess represents a number of trailing decimal zeroes that are
	 * significant but trimmed from storage.
	 * @param weight indicates the power of ten thousand which the first
	 * base ten-thousand digit is taken is taken to represent. If the array
	 * <var>base10000Digits</var> has length one, and that one digit has the
	 * value 3, and <var>weight</var> is zero, the value is 3. If
	 * <var>weight</var> is 1, the value is 30000, and if <var>weight</var>
	 * is -1, the value is 0.0003.
	 * @param base10000Digits each array element is a nonnegative value not
	 * above 9999, representing a single digit of a base-ten-thousand
	 * number. The element at index zero is the most significant. The caller
	 * may pass a zero-length array, but may not pass null. The array is
	 * unshared and may be used as desired.
	 */
	T construct(Kind kind, int displayScale, int weight,
		short[] base10000Digits) throws SQLException;

	/**
	 * Functional interface to obtain information from the PostgreSQL type
	 * modifier applied to the type.
	 */
	@FunctionalInterface
	interface Modifier<T>
	{
		/**
		 * Returns a {@code Numeric} function possibly tailored
		 * ("curried") with the values from a PostgreSQL type modifier
		 * on the type.
		 *<p>
		 * If specified, <var>precision</var> must be at least one and
		 * not greater than {@code NUMERIC_MAX_PRECISION}, and scale
		 * must be not less than {@code NUMERIC_MIN_SCALE} nor more than
		 * {@code NUMERIC_MAX_SCALE}.
		 * @param specified true if a type modifier was specified, false if
		 * omitted
		 * @param precision the maximum number of base-ten digits to be
		 * retained, counting those on both sides of the decimal point
		 * @param scale maximum number of base-ten digits to be retained
		 * to the right of the decimal point.
		 */
		Numeric<T> modify(boolean specified, int precision, int scale);
	}

	/**
	 * A reference implementation that maps to {@link BigDecimal BigDecimal}
	 * (but cannot represent all possible values).
	 *<p>
	 * A Java {@code BigDecimal} cannot represent the not-a-number or positive
	 * or negative infinity values possible for a PostgreSQL {@code NUMERIC}.
	 */
	static class AsBigDecimal implements Numeric<BigDecimal>
	{
		private AsBigDecimal() // I am a singleton
		{
		}

		public static final AsBigDecimal INSTANCE = new AsBigDecimal();

		/**
		 * Produces a {@link BigDecimal} representation of the {@code NUMERIC}
		 * value, or throws an exception if the value is not-a-number or
		 * positive or negative infinity.
		 *<p>
		 * In resolving the ambiguity when <var>displayScale</var> is zero,
		 * this implementation constructs a {@code BigDecimal} with significant
		 * figures inferred from the <var>base10000Digits</var> array's length,
		 * where decimal digits  are grouped in fours, and therefore the
		 * {@code BigDecimal}'s {@link BigDecimal#scale() scale} method will
		 * always return a multiple of four in such cases. Therefore, from the
		 * query
		 *<pre>
		 * SELECT 6.62607015e-34 AS planck, 6.02214076e23 AS avogadro;
		 *</pre>
		 * this conversion will produce the {@code BigDecimal} 6.62607015E-34
		 * for <var>planck</var> ({@code scale} will return 42, as expected),
		 * but will produce 6.02214076000E+23 for <var>avogadro</var>, showing
		 * three unexpected trailing zeros; {@code scale()} will not return -15
		 * as expected, but the next larger multiple of four, -12.
		 * @throws SQLException 22000 if the value is NaN or +/- infinity.
		 */
		@Override
		public BigDecimal construct(
			Kind kind, int displayScale, int weight, short[] base10000Digits)
		throws SQLException
		{
			switch ( kind )
			{
			case NAN:
			case POSINFINITY:
			case NEGINFINITY:
				throw new SQLDataException(
					"cannot represent PostgreSQL numeric " + kind +
					" as Java BigDecimal", "22000");
			default:
			}

			int scale = multiplyExact(weight, - DEC_DIGITS);

			if ( 0 == base10000Digits.length )
				return BigDecimal.valueOf(0L, scale);

			// check that the final value also won't wrap around
			multiplyExact(1 + weight - base10000Digits.length, - DEC_DIGITS);

			BigDecimal bd = BigDecimal.valueOf(base10000Digits[0], scale);

			for ( int i = 1 ; i < base10000Digits.length ; ++ i )
			{
				scale += DEC_DIGITS;
				bd = bd.add(BigDecimal.valueOf(base10000Digits[i], scale));
			}

			/*
			 * The final value of scale from the loop above is
			 * (1 + weight - base10000Digits.length) * (- DEC_DIGITS), so
			 * will always be a multiple of DEC_DIGITS (i.e. 4). It's also
			 * the scale of the BigDecimal constructed so far, and represents
			 * the position, right of the decimal point, of the least stored
			 * digit. Because of that DEC-DIGITS granularity, thought, it may
			 * reflect up to three trailing zeros from the last element of
			 * base10000Digits that are not really significant. When scale and
			 * displayScale are positive (the value extends right of the decimal
			 * point), we can use displayScale to correct the scale of the
			 * BigDecimal. (This 'correction' applies even when displayScale
			 * is greater than scale; that means PostgreSQL knows even more
			 * trailing zeros are significant, and simply avoided storing them.)
			 *
			 * When scale ends up negative, though (the least stored digit falls
			 * somewhere left of the units place), and displayScale is zero,
			 * we get no such help, because PostgreSQL simply clamps that value
			 * to zero. We are on our own to decide whether we are looking at
			 *
			 * a) a value of limited precision, with (- scale) non-significant
			 *    trailing zeros (and possibly up to three more)
			 * b) a precise integer value, all of whose (- scale) trailing
			 *    digits happen to be zero (figure the odds...)
			 * c) anything in between.
			 *
			 * The Java BigDecimal will believe whatever we tell it and use the
			 * corresponding amount of memory, so on efficiency as well as
			 * plausibility grounds, we'll tell it (a). The scale will still be
			 * that multiple of four, though, so we may still have bestowed
			 * significance upon up to three trailing zeros, compared to what a
			 * human who assigned the value might think. That cannot affect
			 * roundtripping of the value back to PostgreSQL, because indeed the
			 * corresponding PostgreSQL forms are identical, so PostgreSQL can't
			 * notice any difference; that's how we got into this mess.
			 */
			if ( displayScale > 0  ||  scale > displayScale )
			{
				assert displayScale >= 1 + scale - DEC_DIGITS;
				bd = bd.setScale(displayScale);
			}

			return Kind.POSITIVE == kind ? bd : bd.negate();
		}

		public <T> T store(BigDecimal bd, Numeric<T> f)
		throws SQLException
		{
			throw new UnsupportedOperationException(
				"no BigDecimal->NUMERIC store for now");
		}
	}
}
