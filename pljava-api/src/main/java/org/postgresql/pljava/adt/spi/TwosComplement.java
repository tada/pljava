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
package org.postgresql.pljava.adt.spi;

/**
 * Methods that have variants on twos-complement Java types that might be signed
 * or unsigned.
 *<p>
 * The {@code Signed} or {@code Unsigned} subinterface below, as appropriate,
 * can be used as a mixin on a class where the right treatment of a Java
 * {@code long}, {@code int}, {@code short}, or {@code byte} might be
 * class-specific.
 *<p>
 * The semantic difference between a {@code short} treated as unsigned and a
 * {@code char} (also an unsigned 16-bit type) is whether the value is expected
 * to mean what UTF-16 says it means.
 */
public interface TwosComplement
{
	boolean unsigned();

	/*
	 * Methods for long
	 */

	int compare(long x, long y);

	long divide(long dividend, long divisor);

	long remainder(long dividend, long divisor);

	long parseLong(CharSequence s, int beginIndex, int endIndex, int radix);

	String deparse(long i, int radix);

	default long parseLong(CharSequence s, int radix)
	{
		return parseLong(s, 0, s.length(), radix);
	}

	default long parseLong(CharSequence s)
	{
		return parseLong(s, 0, s.length(), 10);
	}

	default String deparse(long i)
	{
		return deparse(i, 10);
	}

	/*
	 * Methods for int
	 */

	int compare(int x, int y);

	int divide(int dividend, int divisor);

	int remainder(int dividend, int divisor);

	long toLong(int i);

	int parseInt(CharSequence s, int beginIndex, int endIndex, int radix);

	String deparse(int i, int radix);

	default int parseInt(CharSequence s, int radix)
	{
		return parseInt(s, 0, s.length(), radix);
	}

	default int parseInt(CharSequence s)
	{
		return parseInt(s, 0, s.length(), 10);
	}

	default String deparse(int i)
	{
		return deparse(i, 10);
	}

	/*
	 * Methods for short
	 */

	int compare(short x, short y);

	short divide(short dividend, short divisor);

	short remainder(short dividend, short divisor);

	long toLong(short i);

	int toInt(short i);

	short parseShort(CharSequence s, int beginIndex, int endIndex, int radix);

	String deparse(short i, int radix);

	default short parseShort(CharSequence s, int radix)
	{
		return parseShort(s, 0, s.length(), radix);
	}

	default short parseShort(CharSequence s)
	{
		return parseShort(s, 0, s.length(), 10);
	}

	default String deparse(short i)
	{
		return deparse(i, 10);
	}

	/*
	 * Methods for byte
	 */

	int compare(byte x, byte y);

	byte divide(byte dividend, byte divisor);

	byte remainder(byte dividend, byte divisor);

	long toLong(byte i);

	int toInt(byte i);

	short toShort(byte i);

	byte parseByte(CharSequence s, int beginIndex, int endIndex, int radix);

	String deparse(byte i, int radix);

	default byte parseByte(CharSequence s, int radix)
	{
		return parseByte(s, 0, s.length(), radix);
	}

	default byte parseByte(CharSequence s)
	{
		return parseByte(s, 0, s.length(), 10);
	}

	default String deparse(byte i)
	{
		return deparse(i, 10);
	}

	/**
	 * Mixin with default signed implementations of the interface methods.
	 */
	interface Signed extends TwosComplement
	{
		@Override
		default boolean unsigned()
		{
			return false;
		}

		/*
		 * Methods for long
		 */

		@Override
		default int compare(long x, long y)
		{
			return Long.compare(x, y);
		}

		@Override
		default long divide(long dividend, long divisor)
		{
			return dividend / divisor;
		}

		@Override
		default long remainder(long dividend, long divisor)
		{
			return dividend % divisor;
		}

		@Override
		default long parseLong(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			return Long.parseLong(s, beginIndex, endIndex, radix);
		}

		@Override
		default String deparse(long i, int radix)
		{
			return Long.toString(i, radix);
		}

		/*
		 * Methods for int
		 */

		@Override
		default int compare(int x, int y)
		{
			return Integer.compare(x, y);
		}

		@Override
		default int divide(int dividend, int divisor)
		{
			return dividend / divisor;
		}

		@Override
		default int remainder(int dividend, int divisor)
		{
			return dividend % divisor;
		}

		@Override
		default long toLong(int i)
		{
			return i;
		}

		@Override
		default int parseInt(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			return Integer.parseInt(s, beginIndex, endIndex, radix);
		}

		@Override
		default String deparse(int i, int radix)
		{
			return Integer.toString(i, radix);
		}

		/*
		 * Methods for short
		 */

		@Override
		default int compare(short x, short y)
		{
			return Short.compare(x, y);
		}

		@Override
		default short divide(short dividend, short divisor)
		{
			return (short)(dividend / divisor);
		}

		@Override
		default short remainder(short dividend, short divisor)
		{
			return (short)(dividend % divisor);
		}

		@Override
		default long toLong(short i)
		{
			return i;
		}

		@Override
		default int toInt(short i)
		{
			return i;
		}

		@Override
		default short parseShort(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			int i = Integer.parseInt(s, beginIndex, endIndex, radix);
			if ( Short.MIN_VALUE <= i  &&  i <= Short.MAX_VALUE )
				return (short)i;
			throw new NumberFormatException(String.format(
				"Value out of range. Value:\"%s\" Radix:%d",
				s.subSequence(beginIndex, endIndex), radix));
		}

		@Override
		default String deparse(short i, int radix)
		{
			return Integer.toString(i, radix);
		}

		/*
		 * Methods for byte
		 */

		@Override
		default int compare(byte x, byte y)
		{
			return Byte.compare(x, y);
		}

		@Override
		default byte divide(byte dividend, byte divisor)
		{
			return (byte)(dividend / divisor);
		}

		@Override
		default byte remainder(byte dividend, byte divisor)
		{
			return (byte)(dividend % divisor);
		}

		@Override
		default long toLong(byte i)
		{
			return i;
		}

		@Override
		default int toInt(byte i)
		{
			return i;
		}

		@Override
		default short toShort(byte i)
		{
			return i;
		}

		@Override
		default byte parseByte(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			int i = Integer.parseInt(s, beginIndex, endIndex, radix);
			if ( Byte.MIN_VALUE <= i  &&  i <= Byte.MAX_VALUE )
				return (byte)i;
			throw new NumberFormatException(String.format(
				"Value out of range. Value:\"%s\" Radix:%d",
				s.subSequence(beginIndex, endIndex), radix));
		}

		@Override
		default String deparse(byte i, int radix)
		{
			return Integer.toString(i, radix);
		}
	}

	/**
	 * Mixin with default unsigned implementations of the interface methods.
	 */
	interface Unsigned extends TwosComplement
	{
		@Override
		default boolean unsigned()
		{
			return true;
		}

		/*
		 * Methods for long
		 */

		@Override
		default int compare(long x, long y)
		{
			return Long.compareUnsigned(x, y);
		}

		@Override
		default long divide(long dividend, long divisor)
		{
			return Long.divideUnsigned(dividend, divisor);
		}

		@Override
		default long remainder(long dividend, long divisor)
		{
			return Long.remainderUnsigned(dividend, divisor);
		}

		@Override
		default long parseLong(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			return Long.parseUnsignedLong(s, beginIndex, endIndex, radix);
		}

		@Override
		default String deparse(long i, int radix)
		{
			return Long.toUnsignedString(i, radix);
		}

		/*
		 * Methods for int
		 */

		@Override
		default int compare(int x, int y)
		{
			return Integer.compareUnsigned(x, y);
		}

		@Override
		default int divide(int dividend, int divisor)
		{
			return Integer.divideUnsigned(dividend, divisor);
		}

		@Override
		default int remainder(int dividend, int divisor)
		{
			return Integer.remainderUnsigned(dividend, divisor);
		}

		@Override
		default long toLong(int i)
		{
			return Integer.toUnsignedLong(i);
		}

		@Override
		default int parseInt(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			return Integer.parseUnsignedInt(s, beginIndex, endIndex, radix);
		}

		@Override
		default String deparse(int i, int radix)
		{
			return Integer.toUnsignedString(i, radix);
		}

		/*
		 * Methods for short
		 */

		@Override
		default int compare(short x, short y)
		{
			return Short.compareUnsigned(x, y);
		}

		@Override
		default short divide(short dividend, short divisor)
		{
			return (short)
				Integer.divideUnsigned(toInt(dividend), toInt(divisor));
		}

		@Override
		default short remainder(short dividend, short divisor)
		{
			return (short)
				Integer.remainderUnsigned(toInt(dividend), toInt(divisor));
		}

		@Override
		default long toLong(short i)
		{
			return Short.toUnsignedLong(i);
		}

		@Override
		default int toInt(short i)
		{
			return Short.toUnsignedInt(i);
		}

		@Override
		default short parseShort(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			int i =
				Integer.parseUnsignedInt(s, beginIndex, endIndex, radix);
			if ( 0 <= i  &&  i <= 0xffff )
				return (short)i;
			throw new NumberFormatException(String.format(
				"Value out of range. Value:\"%s\" Radix:%d",
				s.subSequence(beginIndex, endIndex), radix));
		}

		@Override
		default String deparse(short i, int radix)
		{
			return Integer.toUnsignedString(toInt(i), radix);
		}

		/*
		 * Methods for byte
		 */

		@Override
		default int compare(byte x, byte y)
		{
			return Byte.compareUnsigned(x, y);
		}

		@Override
		default byte divide(byte dividend, byte divisor)
		{
			return (byte)
				Integer.divideUnsigned(toInt(dividend), toInt(divisor));
		}

		@Override
		default byte remainder(byte dividend, byte divisor)
		{
			return (byte)
				Integer.remainderUnsigned(toInt(dividend), toInt(divisor));
		}

		@Override
		default long toLong(byte i)
		{
			return Byte.toUnsignedLong(i);
		}

		@Override
		default int toInt(byte i)
		{
			return Byte.toUnsignedInt(i);
		}

		@Override
		default short toShort(byte i)
		{
			return (short)Byte.toUnsignedInt(i);
		}

		@Override
		default byte parseByte(
			CharSequence s, int beginIndex, int endIndex, int radix)
		{
			int i =
				Integer.parseUnsignedInt(s, beginIndex, endIndex, radix);
			if ( 0 <= i  &&  i <= 0xff )
				return (byte)i;
			throw new NumberFormatException(String.format(
				"Value out of range. Value:\"%s\" Radix:%d",
				s.subSequence(beginIndex, endIndex), radix));
		}

		@Override
		default String deparse(byte i, int radix)
		{
			return Integer.toUnsignedString(toInt(i), radix);
		}
	}
}
