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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import java.util.Iterator;
import java.util.List;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

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
}
