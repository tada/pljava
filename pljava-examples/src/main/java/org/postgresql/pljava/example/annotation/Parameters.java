/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

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

import org.postgresql.pljava.annotation.Function;
import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Some methods used for testing parameter and return value coersion and
 * resolution of overloaded methods.
 *<p>
 * About the {@code @SQLAction} here: the original, hand-crafted deployment
 * descriptor declared <em>two</em> SQL functions both implemented by the same
 * {@link #getTimestamp() getTimestamp} method here. Only one declaration can be
 * automatically generated from a {@code @Function} annotation on the method
 * itself. This {@code @SQLAction} takes care of the other declaration.
 * Of course, there is now a burden on the author to get this declaration right
 * and to keep it up to date if the method evolves, but at least it is here in
 * the same file, rather than in a separate hand-maintained DDR file.
 * @author Thomas Hallgren
 */
@SQLAction(install = {
	"CREATE OR REPLACE FUNCTION javatest.java_getTimestamptz()" +
	"	RETURNS timestamptz" +
	"	AS 'org.postgresql.pljava.example.annotation.Parameters.getTimestamp'" +
	"	LANGUAGE java"
	},
	remove = "DROP FUNCTION javatest.java_getTimestamptz()"
)
public class Parameters {
	public static double addNumbers(short a, int b, long c, BigDecimal d,
			BigDecimal e, float f, double g) {
		return d.doubleValue() + e.doubleValue() + a + b + c + f + g;
	}

	public static int addOne(int value) {
		return value + 1;
	}

	@Function(schema = "javatest", name = "java_addOne", effects = IMMUTABLE)
	public static int addOne(Integer value) {
		return value.intValue() + 1;
	}

	public static int addOneLong(long value) {
		return (int) value + 1;
	}

	@Function(schema = "javatest")
	public static int countNulls(Integer[] intArray) throws SQLException {
		int nullCount = 0;
		int top = intArray.length;
		for (int idx = 0; idx < top; ++idx) {
			if (intArray[idx] == null)
				nullCount++;
		}
		return nullCount;
	}

	@Function(schema = "javatest")
	public static int countNulls(ResultSet input) throws SQLException {
		int nullCount = 0;
		int top = input.getMetaData().getColumnCount();
		for (int idx = 1; idx <= top; ++idx) {
			input.getObject(idx);
			if (input.wasNull())
				nullCount++;
		}
		return nullCount;
	}

	public static Date getDate() {
		return new Date(System.currentTimeMillis());
	}

	public static Time getTime() {
		return new Time(System.currentTimeMillis());
	}

	@Function(schema = "javatest", name = "java_getTimestamp")
	public static Timestamp getTimestamp() {
		return new Timestamp(System.currentTimeMillis());
	}

	static void log(String msg) {
		Logger.getAnonymousLogger().info(msg);
	}

	@Function(schema = "javatest", effects = IMMUTABLE)
	public static Integer nullOnEven(int value) {
		return (value % 2) == 0 ? null : value;
	}

	/*
	 * Declare parameter and return type as the PostgreSQL-specific "char"
	 * (the quoted one, not SQL CHAR) type ... that's how it was declared
	 * in the original hand-generated deployment descriptor. PL/Java's SQL
	 * generator would otherwise have emitted smallint by default for the
	 * Java byte type.
	 *
	 * Note that the SQL rules for quoted vs. regular identifiers are complex,
	 * and PL/Java has not yet precisely specified how the identifiers given in
	 * annotations are to be treated. A future release may lay down more precise
	 * rules, which may affect code supplying quoted identifiers like this.
	 */
	@Function(schema = "javatest", type = "\"char\"")
	public static byte print(@SQLType("\"char\"") byte value) {
		log("byte " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static byte[] print(byte[] byteArray) {
		StringBuffer buf = new StringBuffer();
		int top = byteArray.length;
		buf.append("byte[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(byteArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(byteArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return byteArray;
	}

	@Function(schema = "javatest")
	public static void print(Date value) {
		DateFormat p = DateFormat.getDateInstance(DateFormat.FULL);
		log("Local Date is " + p.format(value));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Date is " + p.format(value));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}

	@Function(schema = "javatest")
	public static double print(double value) {
		log("double " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static double[] print(double[] doubleArray) {
		StringBuffer buf = new StringBuffer();
		int top = doubleArray.length;
		buf.append("double[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(doubleArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(doubleArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return doubleArray;
	}

	@Function(schema = "javatest")
	public static float print(float value) {
		log("float " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static float[] print(float[] floatArray) {
		StringBuffer buf = new StringBuffer();
		int top = floatArray.length;
		buf.append("float[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(floatArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(floatArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return floatArray;
	}

	@Function(schema = "javatest")
	public static int print(int value) {
		log("int " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static int[] print(int[] intArray) {
		StringBuffer buf = new StringBuffer();
		int top = intArray.length;
		buf.append("int[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(intArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(intArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return intArray;
	}

	@Function(schema = "javatest", name = "printObj")
	public static Integer[] print(Integer[] intArray) {
		StringBuffer buf = new StringBuffer();
		int top = intArray.length;
		buf.append("Integer[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(intArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(intArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return intArray;
	}

	@Function(schema = "javatest")
	public static long print(long value) {
		log("long " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static long[] print(long[] longArray) {
		StringBuffer buf = new StringBuffer();
		int top = longArray.length;
		buf.append("long[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(longArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(longArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return longArray;
	}

	@Function(schema = "javatest")
	public static short print(short value) {
		log("short " + value);
		return value;
	}

	@Function(schema = "javatest")
	public static short[] print(short[] shortArray) {
		StringBuffer buf = new StringBuffer();
		int top = shortArray.length;
		buf.append("short[] of size " + top);
		if (top > 0) {
			buf.append(" {");
			buf.append(shortArray[0]);
			for (int idx = 1; idx < top; ++idx) {
				buf.append(',');
				buf.append(shortArray[idx]);
			}
			buf.append('}');
		}
		log(buf.toString());
		return shortArray;
	}

	/*
	 * Declare the parameter type to be timetz in SQL, to match what the
	 * original hand-crafted deployment descriptor did. The SQL generator
	 * would otherwise assume time (without time zone).
	 */
	@Function(schema = "javatest")
	public static void print(@SQLType("timetz") Time value) {
		DateFormat p = new SimpleDateFormat("HH:mm:ss z Z");
		log("Local Time is " + p.format(value));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Time is " + p.format(value));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}

	/*
	 * Declare the parameter type to be timestamptz in SQL, to match what the
	 * original hand-crafted deployment descriptor did. The SQL generator
	 * would otherwise assume timestamp (without time zone).
	 */
	@Function(schema = "javatest")
	public static void print(@SQLType("timestamptz") Timestamp value) {
		DateFormat p = DateFormat.getDateTimeInstance(DateFormat.FULL,
				DateFormat.FULL);
		log("Local Timestamp is " + p.format(value));
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		log("UTC Timestamp is " + p.format(value));
		log("TZ =  " + TimeZone.getDefault().getDisplayName());
	}
}
