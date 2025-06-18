/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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

import static java.util.Arrays.stream;
import java.util.Objects;

import org.postgresql.pljava.annotation.Function;
import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Provides example methods to illustrate variadic functions.
 *<p>
 * The key is the {@code @Function} annotation declaring the function variadic
 * to PostgreSQL. The Java method parameter is declared as an ordinary array,
 * not with Java's {@code ...} syntax; in fact, that would be impossible for a
 * function with a composite return type (where the Java signature would have to
 * include a {@code ResultSet} parameter after the variadic input parameter).
 */
@SQLAction(
	requires = { "sumOfSquares", "sumOfSquaresBoxed" },
	install = {
		/*
		 * In addition to the two sumOfSquares functions that are defined in
		 * this class using annotations, emit some direct SQL to declare a
		 * javaformat function that refers directly to java.lang.String.format,
		 * which is a function declared variadic in Java.
		 */
		"CREATE FUNCTION javatest.javaformat(" +
		"  format pg_catalog.text," +
		"  VARIADIC args pg_catalog.anyarray" +
		"  DEFAULT CAST(ARRAY[] AS pg_catalog.text[]))" +
		" RETURNS pg_catalog.text" +
		" RETURNS NULL ON NULL INPUT" +
		" LANGUAGE java" +
		" AS 'java.lang.String=" +
		"     java.lang.String.format(java.lang.String,java.lang.Object[])'",

		"COMMENT ON FUNCTION javatest.javaformat(" +
		" pg_catalog.text, VARIADIC pg_catalog.anyarray) IS '" +
		"Invoke Java''s String.format with a format string and any number of " +
		"arguments. This is not quite as general as the Java method implies, " +
		"because, while the variadic argument is declared ''anyarray'' and " +
		"its members can have any type, PostgreSQL requires all of them to " +
		"have the same type in any given call. Furthermore, in the VARIADIC " +
		"anyarray case, as here, the actual arguments must not all have " +
		"''unknown'' type; if supplying bare literals, one must be cast to " +
		"a type. PostgreSQL will not recognize a call of a variadic function " +
		"unless at least one argument to populate the variadic parameter is " +
		"supplied; to allow calls that don''t pass any, give the variadic " +
		"parameter an empty-array default, as done here.'",

		/*
		 * Test a bunch of variadic calls.
		 */
		"SELECT" +
		"  CASE" +
		"   WHEN s.ok AND d.ok" +
		"   THEN javatest.logmessage('INFO', 'variadic calls ok')" +
		"   ELSE javatest.logmessage('WARNING', 'variadic calls ng')" +
		"  END" +
		" FROM" +
		"  (SELECT" +
		"    pg_catalog.every(expect IS NOT DISTINCT FROM got)" +
		"   FROM" +
		"    (VALUES" +
		"     (" +
		"      'Hello, world'," +
		"      javatest.javaformat('Hello, %s', 'world'::text)" +
		"     )" +
		"    ) AS t(expect, got)" +
		"  ) AS s(ok)," +
		"  (SELECT" +
		"    pg_catalog.every(expect IS NOT DISTINCT FROM got)" +
		"   FROM" +
		"    (VALUES" +
		"     (14.0, javatest.sumOfSquares(1, 2, 3))," +
		"     (14.0, javatest.sumOfSquares(1, 2, null, 3))," +
		"     ( 0.0, javatest.sumOfSquares())," +
		"     (14.0, javatest.sumOfSquaresBoxed(1, 2, 3))," +
		"     (null, javatest.sumOfSquaresBoxed(1, 2, null, 3))" +
		"    ) AS t(expect, got)" +
		"  ) AS d(ok)"
	},

	remove = "DROP FUNCTION javatest.javaformat(pg_catalog.text,anyarray)"
)
public class Variadic {
	private Variadic() { } // do not instantiate

	/**
	 * Compute a double-precision sum of squares, returning null if any input
	 * value is null.
	 *<p>
	 * The {@code RETURNS_NULL} annotation does not mean the array collecting
	 * the variadic arguments cannot have null entries; it only means PostgreSQL
	 * will never call this function with null for the array itself.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL,
		variadic = true, provides = "sumOfSquaresBoxed"
	)
	public static Double sumOfSquaresBoxed(Double[] vals)
	{
		if ( stream(vals).anyMatch(Objects::isNull) )
			return null;

		return
			stream(vals).mapToDouble(Double::doubleValue).map(v -> v*v).sum();
	}

	/**
	 * Compute a double-precision sum of squares, treating any null input
	 * as zero.
	 *<p>
	 * The {@code RETURNS_NULL} annotation does not mean the array collecting
	 * the variadic arguments cannot have null entries; it only means PostgreSQL
	 * will never call this function with null for the array itself. Because
	 * the Java parameter type here is primitive and cannot represent nulls,
	 * PL/Java will have silently replaced any nulls in the input with zeros.
	 *<p>
	 * This version also demonstrates using {@link SQLType @SQLType} to give
	 * the variadic parameter an empty-array default, so PostgreSQL will allow
	 * the function to be called with no corresponding arguments. Without that,
	 * PostgreSQL won't recognize a call to the function unless at least one
	 * argument corresponding to the variadic parameter is supplied.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL,
		variadic = true, provides = "sumOfSquares"
	)
	public static double sumOfSquares(@SQLType(defaultValue={}) double[] vals)
	{
		return stream(vals).map(v -> v*v).sum();
	}
}
