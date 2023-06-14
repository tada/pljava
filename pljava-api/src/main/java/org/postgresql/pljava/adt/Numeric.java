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
	 * Label to distinguish positive, negative, and three kinds of special
	 * values.
	 */
	enum Kind { POSITIVE, NEGATIVE, NAN, POSINFINITY, NEGINFINITY }

	/**
	 * Constructs a representation <var>T</var> from the components
	 * of the PostgreSQL data type.
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
		short[] base10000Digits);

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
}
