/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

import java.lang.reflect.Array;

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import static java.sql.Types.VARCHAR;

import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.SQLNonTransientException;

import java.util.Arrays;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

import org.postgresql.pljava.example.annotation.ConditionalDDR; // for javadoc

/**
 * A class to simplify testing of PL/Java's mappings between PostgreSQL and
 * Java/JDBC types.
 *<p>
 * Provides one function, {@link #roundTrip roundTrip()}. Its single input
 * parameter is an unspecified row type, so you can pass it a row that has
 * exactly one column of any type.
 *<p>
 * Its return type is also an unspecified row type, so you need to follow the
 * function call with a column definition list of up to six columns. Each
 * requested output column must have its name (case-insensitively) and type
 * drawn from this table:
 *<table>
 *<caption>Items the roundTrip function can return</caption>
 *<thead>
 *<tr>
 *<th>Column name</th><th>Column type</th><th>What is returned</th>
 *</tr>
 *</thead>
 *<tbody>
 *<tr>
 *<td>TYPEPG</td><td>any text/varchar</td><td>The PostgreSQL type name</td>
 *</tr>
 *<tr>
 *<td>TYPEJDBC</td><td>any text/varchar</td><td>The JDBC Types constant</td>
 *</tr>
 *<tr>
 *<td>CLASSJDBC</td><td>any text/varchar</td>
 *<td>Name of the Java class JDBC claims (in metadata) it will instantiate</td>
 *</tr>
 *<tr>
 *<td>CLASS</td><td>any text/varchar</td>
 *<td>Name of the Java class JDBC did instantiate</td>
 *</tr>
 *<tr>
 *<td>TOSTRING</td><td>any text/varchar</td>
 *<td>Result of {@code toString()} on the object returned by
 * {@code ResultSet.getObject()} ({@code Arrays.toString} if it is a primitive
 * array, {@code Arrays.deepToString} if an array of reference type)</td>
 *</tr>
 *<tr>
 *<td>ROUNDTRIPPED</td><td>same as input column</td>
 *<td>Result of passing the object returned by {@code ResultSet.getObject()}
 * directly to {@code ResultSet.updateObject()}</td>
 *</tr>
 *</tbody>
 *</table>
 *<p>
 * Serving suggestion:
 *<pre>
 *SELECT
 *  orig = roundtripped AS good, *
 *FROM
 *  (VALUES (timestamptz '2017-08-21 18:25:29.900005Z')) AS p(orig),
 *  roundtrip(p) AS (roundtripped timestamptz);
 *</pre>
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example.
 */
@SQLAction(implementor = "postgresql_ge_90300", // funcs see earlier FROM items
	requires = {"TypeRoundTripper.roundTrip", "point mirror type"},
	install = {
	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'timestamp roundtrip passes')" +
	"  ELSE javatest.logmessage('WARNING', 'timestamp roundtrip fails')" +
	"  END" +
	" FROM" +
	"  (VALUES" +
	"   (timestamp '2017-08-21 18:25:29.900005')," +
	"   (timestamp '1970-03-07 17:37:49.300009')," +
	"   (timestamp '1919-05-29 13:08:33.600001')" +
	"  ) AS p(orig)," +
	"  roundtrip(p) AS (roundtripped timestamp)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'timestamptz roundtrip passes')" +
	"  ELSE javatest.logmessage('WARNING', 'timestamptz roundtrip fails')" +
	"  END" +
	" FROM" +
	"  (VALUES" +
	"   (timestamptz '2017-08-21 18:25:29.900005Z')," +
	"   (timestamptz '1970-03-07 17:37:49.300009Z')," +
	"   (timestamptz '1919-05-29 13:08:33.600001Z')" +
	"  ) AS p(orig)," +
	"  roundtrip(p) AS (roundtripped timestamptz)",

	" SELECT" +
	"  CASE WHEN classjdbc = 'org.postgresql.pljava.example.annotation.Point'" +
	"  THEN javatest.logmessage('INFO', 'issue192 test passes')" +
	"  ELSE javatest.logmessage('WARNING', 'issue192 test fails')" +
	"  END" +
	" FROM" +
	"  (VALUES (point '0,0')) AS p," +
	"  roundtrip(p) AS (classjdbc text)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'boolean[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'boolean[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{t,null,f}'::boolean[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Boolean;'), ('[Z')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped boolean[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, false)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    '\"char\"[] passes')" +
	"  ELSE javatest.logmessage('WARNING', '\"char\"[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{A,null,B}'::\"char\"[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Byte;'), ('[B')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped \"char\"[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::\"char\")" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'bytea passes')" +
	"  ELSE javatest.logmessage('WARNING', 'bytea fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '\\x010203'::bytea) AS p(orig)," +
	"  (VALUES (''), ('[B')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped bytea)," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped = orig" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'int2[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'int2[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{1,null,3}'::int2[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Short;'), ('[S')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped int2[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::int2)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'int4[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'int4[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{1,null,3}'::int4[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Integer;'), ('[I')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped int4[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::int4)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'int8[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'int8[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{1,null,3}'::int8[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Long;'), ('[J')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped int8[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::int8)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'float4[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'float4[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{1,null,3}'::float4[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Float;'), ('[F')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped float4[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::float4)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'float8[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'float8[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{1,null,3}'::float8[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.Double;'), ('[D')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped float8[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped =" +
	"   CASE WHEN class LIKE '[_' THEN array_replace(orig, null, 0::float8)" +
	"   ELSE orig END" +
	"  ) AS outcome(ok)",

	" SELECT" +
	"  CASE WHEN every(outcome.ok)" +
	"  THEN javatest.logmessage('INFO',    'text[] passes')" +
	"  ELSE javatest.logmessage('WARNING', 'text[] fails')" +
	"  END" +
	" FROM" +
	"  (SELECT '{foo,null,bar}'::text[]) AS p(orig)," +
	"  (VALUES (''), ('[Ljava.lang.String;')) as q(rqcls)," +
	"  roundtrip(p, rqcls) AS (class text, roundtripped text[])," +
	"  LATERAL (SELECT" +
	"   (rqcls = class OR rqcls = '')" +
	"   AND roundtripped = orig" +
	"  ) AS outcome(ok)",
	}
)
public class TypeRoundTripper
{
	private TypeRoundTripper() { }

	/**
	 * Function accepting one parameter of row type (one column, any type)
	 * and returning a row with up to six columns (use a column definition list
	 * after the function call, choose column names from TYPEPG, TYPEJDBC,
	 * CLASSJDBC, CLASS, TOSTRING, ROUNDTRIPPED where any of the first five
	 * must have text/varchar type, while ROUNDTRIPPED must match the type of
	 * the input column).
	 * @param in The input row value (required to have exactly one column).
	 * @param classname Name of class to be explicitly requested (JDBC 4.1
	 * feature) from {@code getObject}; pass an empty string (the default) to
	 * make no such explicit request. Accepts the form {@code Class.getName}
	 * would produce: canonical names or spelled-out primitives if not an array
	 * type, otherwise prefix left-brackets and primitive letter codes or
	 * {@code L}<em>classname</em>{@code ;}.
	 * @param prepare Whether the object retrieved from {@code in} should be
	 * passed as a parameter to an identity {@code PreparedStatement} and the
	 * result of that be returned. If false (the default), the value from
	 * {@code in} is simply forwarded directly to {@code out}.
	 * @param out The output row (supplied by PL/Java, representing the column
	 * definition list that follows the call of this function in SQL).
	 * @throws SQLException if {@code in} does not have exactly one column, if
	 * {@code out} has more than six, if a requested column name in {@code out}
	 * is not among those recognized, if a column of {@code out} is not of its
	 * required type, or if other stuff goes wrong.
	 */
	@Function(
		schema = "javatest",
		type = "RECORD",
		provides = "TypeRoundTripper.roundTrip",
		implementor = "postgresql_ge_80400" // supports function param DEFAULTs
		)
	public static boolean roundTrip(
		ResultSet in, @SQLType(defaultValue="") String classname,
		@SQLType(defaultValue="false") boolean prepare, ResultSet out)
	throws SQLException
	{
		ResultSetMetaData inmd = in.getMetaData();
		ResultSetMetaData outmd = out.getMetaData();

		Class<?> clazz = null;
		if ( ! "".equals(classname) )
			clazz = loadClass(classname);

		if ( 1 != inmd.getColumnCount() )
			throw new SQLDataException(
				"in parameter must be a one-column row type", "22000");

		int outcols = outmd.getColumnCount();
		if ( 6 < outcols )
			throw new SQLDataException(
				"result description may have no more than six columns",
				"22000");

		String inTypePG = inmd.getColumnTypeName(1);
		int inTypeJDBC = inmd.getColumnType(1);
		Object val = (null == clazz) ? in.getObject(1) : in.getObject(1, clazz);

		if ( prepare )
		{
			Connection c = getConnection("jdbc:default:connection");
			PreparedStatement ps = c.prepareStatement("SELECT ?");
			ps.setObject(1, val);
			ResultSet rs = ps.executeQuery();
			rs.next();
			val = (null == clazz) ? rs.getObject(1) : rs.getObject(1, clazz);
			rs.close();
			ps.close();
			c.close();
		}

		for ( int i = 1; i <= outcols; ++ i )
		{
			String what = outmd.getColumnLabel(i);

			if ( "TYPEPG".equalsIgnoreCase(what) )
			{
				assertTypeJDBC(outmd, i, VARCHAR);
				out.updateObject(i, inTypePG);
			}
			else if ( "TYPEJDBC".equalsIgnoreCase(what) )
			{
				assertTypeJDBC(outmd, i, VARCHAR);
				out.updateObject(i, typeNameJDBC(inTypeJDBC));
			}
			else if ( "CLASSJDBC".equalsIgnoreCase(what) )
			{
				assertTypeJDBC(outmd, i, VARCHAR);
				out.updateObject(i, inmd.getColumnClassName(1));
			}
			else if ( "CLASS".equalsIgnoreCase(what) )
			{
				assertTypeJDBC(outmd, i, VARCHAR);
				out.updateObject(i, val.getClass().getName());
			}
			else if ( "TOSTRING".equalsIgnoreCase(what) )
			{
				assertTypeJDBC(outmd, i, VARCHAR);
				out.updateObject(i, toString(val));
			}
			else if ( "ROUNDTRIPPED".equalsIgnoreCase(what) )
			{
				if ( ! inTypePG.equals(outmd.getColumnTypeName(i)) )
					throw new SQLDataException(
					"Result ROUNDTRIPPED column must have same type as input",
						"22000");
				out.updateObject(i, val);
			}
			else
				throw new SQLDataException(
					"Output column label \""+ what + "\" should be one of: " +
					"TYPEPG, TYPEJDBC, CLASSJDBC, CLASS, TOSTRING, " +
					"ROUNDTRIPPED",
					"22000");
		}

		return true;
	}

	static void assertTypeJDBC(ResultSetMetaData md, int i, int t)
	throws SQLException
	{
		if ( md.getColumnType(i) != t )
			throw new SQLDataException(
				"Result column " + i + " must be of JDBC type " +
				typeNameJDBC(t));
	}

	static String typeNameJDBC(int t)
	{
		for ( Field f : Types.class.getFields() )
		{
			int m = f.getModifiers();
			if ( isPublic(m) && isStatic(m) && int.class == f.getType() )
				try
				{
					if ( f.getInt(null) == t )
						return f.getName();
				}
				catch ( IllegalAccessException e ) { }
		}
		return String.valueOf(t);
	}

	private static Class<?> loadClass(String className)
	throws SQLException
	{
		String noBrackets = className.replaceFirst("^\\[++", "");
		int ndims = (className.length() - noBrackets.length());

		/*
		 * The naming conventions from Class.getName() could hardly be less
		 * convenient. If *not* an array, it's the same as the canonical name,
		 * with the primitive names spelled out. If it *is* an array, the
		 * primitives get their one-letter codes, and other class names have L
		 * prefix and ; suffix. Condense the two cases here into one offbeat
		 * hybrid form that will be used below.
		 */
		if ( 0 == ndims )
			noBrackets =
				("L" + noBrackets +
				":booleanZ:byteB:shortS:charC:intI:longJ:floatF:doubleD")
				.replaceFirst(
					"^L(\\w++)(?=:)(?:\\w*+:)*\\1(\\w)(?::.*+)?+$|:.++$",
					"$2");
		else
			noBrackets = noBrackets.replaceFirst(";$", "");

		/*
		 * Invariant: thanks to the above normalization, whether array or not,
		 * noBrackets will now have this form: either the first (and only)
		 * character is one of the primitive character codes, or the first
		 * character is L and the rest is a class name (with no ; at the end).
		 */

		Class<?> c;

		switch ( noBrackets.charAt(0) )
		{
		case 'Z': c = boolean.class; break;
		case 'B': c =    byte.class; break;
		case 'S': c =   short.class; break;
		case 'C': c =    char.class; break;
		case 'I': c =     int.class; break;
		case 'J': c =    long.class; break;
		case 'F': c =   float.class; break;
		case 'D': c =  double.class; break;
		default:
			try
			{
				noBrackets = noBrackets.substring(1);
				c = Class.forName(noBrackets);
			}
			catch ( ClassNotFoundException e )
			{
				throw new SQLNonTransientException(
					"No such class: " + noBrackets, "46103", e);
			}
		}

		if ( 0 != ndims )
			c = Array.newInstance(c, new int[ndims]).getClass();

		return c;
	}

	private static String toString(Object o)
	{
		if ( ! o.getClass().isArray() )
			return o.toString();
		if (Object[].class.isInstance(o))
			return Arrays.deepToString(Object[].class.cast(o));
		if (boolean[].class.isInstance(o))
			return Arrays.toString(boolean[].class.cast(o));
		if (byte[].class.isInstance(o))
			return Arrays.toString(byte[].class.cast(o));
		if (short[].class.isInstance(o))
			return Arrays.toString(short[].class.cast(o));
		if (int[].class.isInstance(o))
			return Arrays.toString(int[].class.cast(o));
		if (long[].class.isInstance(o))
			return Arrays.toString(long[].class.cast(o));
		if (char[].class.isInstance(o))
			return Arrays.toString(char[].class.cast(o));
		if (float[].class.isInstance(o))
			return Arrays.toString(float[].class.cast(o));
		if (double[].class.isInstance(o))
			return Arrays.toString(double[].class.cast(o));
		return null;
	}
}
