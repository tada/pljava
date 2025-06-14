/*
 * Copyright (c) 2025
  Tada AB and other contributors, as listed below.
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

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Illustrates how not to handle an exception thrown by a call into PostgreSQL.
 *<p>
 * Such an exception must either be rethrown (or result in some higher-level
 * exception being rethrown) or cleared by rolling back the transaction or
 * a previously-established savepoint. If it is simply caught and not propagated
 * and the error condition is not cleared, no further calls into PostgreSQL
 * functionality can be made within the containing transaction.
 *
 * @see <a href="../../RELDOTS/use/catch.html">Catching PostgreSQL exceptions
 * in Java</a>
 */
public interface MishandledExceptions
{
	/**
	 * Executes an SQL statement that produces an error (twice, if requested),
	 * catching the resulting exception but not propagating it or rolling back
	 * a savepoint; then throws an unrelated exception if succeed is false.
	 */
	@Function(schema = "javatest")
	static String mishandle(
		boolean twice, @SQLType(defaultValue="true")boolean succeed)
	throws SQLException
	{
		String rslt = null;
		do
		{
			try
			(
				Connection c = getConnection("jdbc:default:connection");
				Statement s = c.createStatement();
			)
			{
				s.execute("DO LANGUAGE \"no such language\" 'no such thing'");
			}
			catch ( SQLException e )
			{
				rslt = e.toString();
				/* nothing rethrown, nothing rolled back <- BAD PRACTICE */
			}
		}
		while ( ! (twice ^= true) );

		if ( succeed )
			return rslt;

		throw new SQLException("unrelated");
	}
}
