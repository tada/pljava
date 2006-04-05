/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.example;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
	static void log(String msg)
	{
		// GCJ has a somewhat serious bug (reported)
		//
		if("GNU libgcj".equals(System.getProperty("java.vm.name")))
		{
			System.out.print("INFO: ");
			System.out.println(msg);
		}
		else
			Logger.getAnonymousLogger().info(msg);
	}

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

	public static int countNulls(ResultSet input) throws SQLException
	{
		int nullCount = 0;
		int top = input.getMetaData().getColumnCount();
		for(int idx = 1; idx <= top; ++idx)
		{
			input.getObject(idx);
			if(input.wasNull())
				nullCount++;
		}
		return nullCount;
	}

	public static void print(Date time)
	{
		DateFormat p = DateFormat.getDateInstance(DateFormat.FULL);
		log("Local Date is " + p.format(time));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Date is " + p.format(time));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}

	public static void print(Time time)
	{
		DateFormat p = new SimpleDateFormat("HH:mm:ss z Z");
		log("Local Time is " + p.format(time));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Time is " + p.format(time));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}

	public static void print(Timestamp time)
	{
		DateFormat p = DateFormat.getDateTimeInstance(
				DateFormat.FULL, DateFormat.FULL);
		log("Local Timestamp is " + p.format(time));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Timestamp is " + p.format(time));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}
}
