/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.TimeZone;

/**
 * Some methods used for testing parameter and return value coersion and resolution
 * of overloaded methods.
 *
 * @author Thomas Hallgren
 */
public class Parameters
{

	public static int addOne(int value)
	{
		return value + 1;
	}

	public static int addOne(Integer value)
	{
		return value.intValue() + 1;
	}

	public static Integer nullOnEven(int value)
	{
		return (value % 2) == 0 ? null : new Integer(value);
	}

	public static int addOneLong(long value)
	{
		return (int)value + 1;
	}

	public static double addNumbers(short a, int b, long c, BigDecimal d, BigDecimal e, float f, double g)
	{
		return d.doubleValue() + e.doubleValue() + (double)a + (double)b + (double)c + (double)f + g;
	}

	public static Date getDate()
	{
		return new Date(System.currentTimeMillis());
	}

	public static Time getTime()
	{
		return new Time(System.currentTimeMillis());
	}

	public static Timestamp getTimestamp()
	{
		return new Timestamp(System.currentTimeMillis());
	}
	
	public static void print(Date time)
	{
		DateFormat p = SimpleDateFormat.getDateInstance(
				SimpleDateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		System.out.println(p.format(time));
	}

	public static void print(Time time)
	{
		DateFormat p = SimpleDateFormat.getTimeInstance(
				SimpleDateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		System.out.println(p.format(time));
	}

	public static void print(Timestamp time)
	{
		String x;
		DateFormat p = SimpleDateFormat.getDateTimeInstance(
				SimpleDateFormat.FULL, SimpleDateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		System.out.println(p.format(time));
	}
}
