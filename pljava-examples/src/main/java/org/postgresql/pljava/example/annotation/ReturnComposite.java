/*
 * Copyright (c) 2020-2022 Tada AB and other contributors, as listed below.
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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Demonstrates {@code @Function(out={...})} for a function that returns a
 * non-predeclared composite type.
 */
@SQLAction(requires = { "helloOutParams", "helloTable" }, install = {
	"SELECT" +
	"  CASE WHEN want IS NOT DISTINCT FROM helloOutParams()" +
	"  THEN javatest.logmessage('INFO',    'composite return passes')" +
	"  ELSE javatest.logmessage('WARNING', 'composite return fails')" +
	"  END" +
	" FROM" +
	"  (SELECT 'Hello' ::text, 'world' ::text) AS want",

	"WITH" +
	" expected AS (VALUES" +
	"  ('Hello' ::text, 'twelve' ::text)," +
	"  ('Hello',        'thirteen')," +
	"  ('Hello',        'love')" +
	" )" +
	"SELECT" +
	"  CASE WHEN every(want IS NOT DISTINCT FROM got)" +
	"  THEN javatest.logmessage('INFO',    'set of composite return passes')" +
	"  ELSE javatest.logmessage('WARNING', 'set of composite return fails')" +
	"  END" +
	" FROM" +
	"  (SELECT row_number() OVER (), * FROM expected) AS want" +
	"  LEFT JOIN (SELECT row_number() OVER (), * FROM hellotable()) AS got" +
	"  USING (row_number)"
})
public class ReturnComposite implements ResultSetProvider.Large
{
	/**
	 * Returns a two-column composite result that does not have to be
	 * a predeclared composite type, or require the calling SQL query to
	 * follow the function call with a result column definition list, as is
	 * needed for a bare {@code RECORD} return type.
	 */
	@Function(
		schema = "javatest", out = { "greeting text", "addressee text" },
		provides = "helloOutParams"
	)
	public static boolean helloOutParams(ResultSet out) throws SQLException
	{
		out.updateString(1, "Hello");
		out.updateString(2, "world");
		return true;
	}

	/**
	 * A function that does not return a composite type, despite having
	 * a similar Java form.
	 *<p>
	 * Without the {@code type=} element, this would not be mistaken for
	 * composite. With the {@code type=} element (a contrived example, will cast
	 * the method's boolean result to text), PL/Java would normally match the
	 * method to the composite pattern (other than {@code pg_catalog.RECORD},
	 * PL/Java does not pretend to know at compile time which types might be
	 * composite). The explicit {@code SQLType} annotation on the trailing
	 * {@code ResultSet} parameter forces it to be seen as an input, and the
	 * method to be seen as an ordinary method that happens to return boolean.
	 */
	@Function(
		schema = "javatest", type = "text"
	)
	public static boolean
		notOutParams(@SQLType("pg_catalog.record") ResultSet in)
	throws SQLException
	{
		return true;
	}

	/**
	 * Returns a two-column table result that does not have to be
	 * a predeclared composite type, or require the calling SQL query to
	 * follow the function call with a result column definition list, as is
	 * needed for a bare {@code RECORD} return type.
	 */
	@Function(
		schema = "javatest", out = { "greeting text", "addressee text" },
		provides = "helloTable"
	)
	public static ResultSetProvider helloTable()
	throws SQLException
	{
		return new ReturnComposite();
	}

	Iterator<String> addressees =
		List.of("twelve", "thirteen", "love").iterator();

	@Override
	public boolean assignRowValues(ResultSet out, long currentRow)
	throws SQLException
	{
		if ( ! addressees.hasNext() )
			return false;

		out.updateString(1, "Hello");
		out.updateString(2, addressees.next());
		return true;
	}

	@Override
	public void close()
	{
	}

	/**
	 * Returns a result described by <em>one</em> {@code out} parameter.
	 *<p>
	 * Such a method is written in the style of any method that returns
	 * a scalar value, rather than receiving a writable {@code ResultSet}
	 * as a parameter.
	 */
	@Function(
		schema = "javatest", out = { "greeting text" }
	)
	public static String helloOneOut() throws SQLException
	{
		return "Hello";
	}

	/**
	 * Has a boolean result described by <em>one</em> {@code out} parameter.
	 *<p>
	 * Because this method returns boolean and has a trailing row-typed
	 * <em>input</em> parameter, that parameter must have an {@code SQLType}
	 * annotation so that the method will not look like the more-than-one-OUT
	 * composite form, which would be rejected as a likely mistake.
	 */
	@Function(
		schema = "javatest", out = { "exquisite boolean" }
	)
	public static boolean boolOneOut(@SQLType("pg_catalog.record") ResultSet in)
	throws SQLException
	{
		return true;
	}

	/**
	 * Returns a table result described by <em>one</em> {@code out} parameter.
	 *<p>
	 * Such a method is written in the style of any method that returns a set
	 * of some scalar value, using an {@code Iterator} rather than a
	 * {@code ResultSetProvider} or {@code ResultSetHandle}.
	 */
	@Function(
		schema = "javatest", out = { "addressee text" }
	)
	public static Iterator<String> helloOneOutTable() throws SQLException
	{
		return new ReturnComposite().addressees;
	}
}
