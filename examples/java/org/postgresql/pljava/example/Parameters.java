/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.TimeZone;
import java.util.logging.Logger;

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
		return d.doubleValue() + e.doubleValue() + a + b + c + f + g;
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
		DateFormat p = DateFormat.getDateInstance(DateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		Logger.getAnonymousLogger().info("Date is " + p.format(time));
	}

	public static void print(Time time)
	{
		DateFormat p = DateFormat.getTimeInstance(DateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		Logger.getAnonymousLogger().info("Time is " + p.format(time));
	}

	public static void print(Timestamp time)
	{
		DateFormat p = DateFormat.getDateTimeInstance(
				DateFormat.FULL, DateFormat.FULL);
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		Logger.getAnonymousLogger().info("Timestamp is " + p.format(time));
	}
}
