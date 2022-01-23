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
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.SQLException;

import java.util.Iterator;

import java.util.stream.Stream;

import org.postgresql.pljava.ResultSetProvider;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Functions to check that allocations are being made in the "upper" memory
 * context as necessary when SPI has been used.
 */
public class MemoryContexts {
	private MemoryContexts()
	{
	}

	private static Connection ensureSPIConnected() throws SQLException
	{
		Connection c = getConnection("jdbc:default:connection");
		try ( Statement s = c.createStatement() )
		{
			s.execute("UPDATE javatest.foobar_1 SET stuff = 'a' WHERE FALSE");
		}
		return c;
	}

	/**
	 * Return an array result after connecting SPI, to ensure the result isn't
	 * allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest")
	public static String[] nonSetArrayResult() throws SQLException
	{
		ensureSPIConnected();
		return new String[] { "Hello", "world" };
	}

	/**
	 * Return a coerced result after connecting SPI, to ensure the result isn't
	 * allocated in SPI's short-lived memory context.
	 *<p>
	 * The mismatch of the Java type {@code int} and the PostgreSQL type
	 * {@code numeric} forces PL/Java to create a {@code Coerce} node applying
	 * a cast, the correct allocation of which is tested here.
	 */
	@Function(schema = "javatest", type = "numeric")
	public static int nonSetCoercedResult() throws SQLException
	{
		ensureSPIConnected();
		return 42;
	}

	/**
	 * Return a composite result after connecting SPI, to ensure the result
	 * isn't allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest", out = { "a text", "b text" })
	public static boolean nonSetCompositeResult(ResultSet out)
	throws SQLException
	{
		ensureSPIConnected();
		out.updateString(1, "Hello");
		out.updateString(2, "world");
		return true;
	}

	/**
	 * Return a fixed-length base UDT result after connecting SPI, to ensure
	 * the result isn't allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest")
	public static ComplexScalar nonSetFixedUDTResult() throws SQLException
	{
		ensureSPIConnected();
		return new ComplexScalar(1.2, 3.4, "javatest.complexscalar");
	}

	/**
	 * Return a composite UDT result after connecting SPI, to ensure
	 * the result isn't allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest")
	public static ComplexTuple nonSetCompositeUDTResult() throws SQLException
	{
		Connection c = ensureSPIConnected();
		try (
			Statement s = c.createStatement();
			ResultSet r = s.executeQuery(
				"SELECT CAST ( '(1.2,3.4)' AS javatest.complextuple )")
		)
		{
			r.next();
			return r.getObject(1, ComplexTuple.class);
		}
	}

	/**
	 * Return a set-of (non-composite) result after connecting SPI, to ensure
	 * the result isn't allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest")
	public static Iterator<String> setNonCompositeResult()
	{
		final Iterator<String> it = Stream.of("a", "b", "c").iterator();
		return new Iterator<>()
		{
			@Override
			public boolean hasNext()
			{
				try
				{
					ensureSPIConnected();
					return it.hasNext();
				}
				catch ( SQLException e )
				{
					throw new RuntimeException(e.getMessage(), e);
				}
			}

			@Override
			public String next()
			{
				try
				{
					ensureSPIConnected();
					return it.next();
				}
				catch ( SQLException e )
				{
					throw new RuntimeException(e.getMessage(), e);
				}
			}
		};
	}

	/**
	 * Return a set-of composite result after connecting SPI, to ensure
	 * the result isn't allocated in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest", out = {"a text", "b text"})
	public static ResultSetProvider setCompositeResult()
	{
		return new ResultSetProvider.Large()
		{
			@Override
			public boolean assignRowValues(ResultSet out, long currentRow)
			throws SQLException
			{
				ensureSPIConnected();
				if ( currentRow > 2 )
					return false;
				out.updateString(1, "a");
				out.updateString(2, "b");
				return true;
			}

			@Override
			public void close()
			{
			}
		};
	}

	/**
	 * Prepare a statement after connecting SPI and use it later, to ensure
	 * important allocations are not in SPI's short-lived memory context.
	 */
	@Function(schema = "javatest", out = {"a text", "b text"})
	public static ResultSetProvider preparedStatementContext()
	throws SQLException
	{
		Connection c = ensureSPIConnected();
		final PreparedStatement ps = c.prepareStatement(
			"SELECT " +
			" to_char( " +
			"  extract(microseconds FROM statement_timestamp()) % 3999, " +
			"  ?)");
		ps.setString(1, "RN");

		return new ResultSetProvider.Large()
		{
			@Override
			public boolean assignRowValues(ResultSet out, long currentRow)
			throws SQLException
			{
				ensureSPIConnected();
				if ( currentRow > 2 )
					return false;
				try ( ResultSet rs = ps.executeQuery() )
				{
					rs.next();
					out.updateString(1, rs.getString(1));
					ps.setString(1, "RN");
					return true;
				}
			}

			@Override
			public void close()
			{
			}
		};
	}
}
