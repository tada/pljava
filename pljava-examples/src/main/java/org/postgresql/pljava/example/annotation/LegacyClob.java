/*
 * Copyright (c) 2026 Tada AB and other contributors, as listed below.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;

import java.nio.CharBuffer;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.sql.Clob;
import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Arrays;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.MappedUDT;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Captures how PL/Java's Clob implementation has and hasn't (hasn't, mostly)
 * worked.
 */
public class LegacyClob
{
	private LegacyClob() { } // do not instantiate

	static final String CHARS = "ABCD";
	static final byte[] BYTES = CHARS.getBytes(US_ASCII);

	static Connection connect() throws SQLException
	{
		return getConnection("jdbc:default:connection");
	}

	static String readAllAsString(Reader r) throws IOException
	{
		// Java >= 10: can use r.transferTo(a StringWriter)
		// Java >= 25: can use r.readAllAsString()
		CharBuffer cb = CharBuffer.allocate(128);
		StringBuilder sb = new StringBuilder();
		while ( -1 != r.read(cb) )
		{
			sb.append(cb.flip());
			cb.clear();
		}
		return sb.toString();
	}

	static String readAsciiString(InputStream is) throws IOException
	{
		return new String(is.readAllBytes(), US_ASCII);
	}

	// works
	/**
	 * Exercises getCharacterStream on ResultSet, returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean resultSetGetCharacterStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement("SELECT CAST ( ? AS text )");
		)
		{
			ps.setString(1, CHARS);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				try
				(
					Reader r = rs.getCharacterStream(1);
				)
				{
					return CHARS.equals(readAllAsString(r));
				}
			}
		}
	}

	// works
	/**
	 * Exercises getAsciiStream on ResultSet, returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean resultSetGetAsciiStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement("SELECT CAST ( ? AS text )");
		)
		{
			ps.setString(1, CHARS);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				try
				(
					InputStream is = rs.getAsciiStream(1);
				)
				{
					return CHARS.equals(readAsciiString(is));
				}
			}
		}
	}

	// SQLException: Cannot derive a value of class java.lang.String from
	// an object of class org.postgresql.pljava.jdbc.ClobValue
	/**
	 * Exercises Clob in a composite return value, returning two text columns
	 * that should be equal; also tests getClob.
	 */
	@Function(schema = "javatest", out = { "c1 text", "c2 text" })
	public static boolean compositeReturnClob(ResultSet toReturn)
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement("SELECT CAST ( ? AS text )");
		)
		{
			ps.setString(1, CHARS);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				Clob clob = rs.getClob(1);
				toReturn.updateString("c1", CHARS);
				toReturn.updateClob("c2", clob);
				return true;
			}
		}
	}

	/*
	 * Exercises Clob as a scalar return value.
	@Function(schema = "javatest", type = "text")
	public static Clob scalarReturnClob() throws SQLException, IOException
	{
		Without type="text", rejected at compile time (no compile-time mapping)
		With type="text", rejected at validation time (no run-time mapping)
	}
	 */

	// SQLException: Cannot derive a value of class java.lang.String from
	// an object of class org.postgresql.pljava.jdbc.ClobValue
	/**
	 * Exercises setting a composite return column using updateCharacterStream,
	 * returning two text columns that should be equal.
	 */
	@Function(schema = "javatest", out = { "c1 text", "c2 text" })
	public static boolean compositeReturnCharacterStream(ResultSet toReturn)
	throws SQLException, IOException
	{
		toReturn.updateString("c1", CHARS);
		// toReturn.updateCharacterStream("c2", new StringReader(CHARS));
		// toReturn.updateCharacterStream(2, new StringReader(CHARS));
		toReturn.updateCharacterStream(
			2, new StringReader(CHARS), CHARS.length());
		return true;
	}

	/**
	 * Exercises setting a composite return column using updateAsciiStream,
	 * returning two text columns that should be equal.
	 */
	@Function(schema = "javatest", out = { "c1 text", "c2 text" })
	public static boolean compositeReturnAsciiStream(ResultSet toReturn)
	throws SQLException, IOException
	{
		toReturn.updateString("c1", CHARS);

		InputStream is = new ByteArrayInputStream(BYTES);

		// toReturn.updateAsciiStream("c2", is);
		// toReturn.updateAsciiStream(2, is);
		toReturn.updateAsciiStream(2, is, BYTES.length);
		return true;
	}

	// XXX returns false; Clob probably rendered by Object.toString
	/**
	 * Exercises setCharacterStream on PreparedStatement,
	 * returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean preparedStmtSetCharacterStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)");
		)
		{
			ps.setString(1, CHARS);
			// ps.setCharacterStream(2, new StringReader(CHARS));
			ps.setCharacterStream(
				2, new StringReader(CHARS), CHARS.length());
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				return rs.getBoolean(1);
			}
		}
	}

	// XXX returns false; Clob probably rendered by Object.toString
	/**
	 * Exercises setAsciiStream on PreparedStatement,
	 * returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean preparedStmtSetAsciiStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)");
		)
		{
			ps.setString(1, CHARS);
			// ps.setAsciiStream(2, new ByteArrayInputStream(BYTES));
			ps.setAsciiStream(
				2, new ByteArrayInputStream(BYTES), BYTES.length);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				return rs.getBoolean(1);
			}
		}
	}

	// XXX returns false; Clob probably rendered by Object.toString
	/**
	 * Exercises setClob on PreparedStatement, returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean preparedStmtSetClob()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps1 =
				c.prepareStatement("SELECT CAST ( ? AS text )");
			PreparedStatement ps2 =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)");
		)
		{
			ps1.setString(1, CHARS);

			try
			(
				ResultSet rs = ps1.executeQuery();
			)
			{
				rs.next();
				Clob clob = rs.getClob(1);
				ps2.setString(1, CHARS);
				ps2.setClob(2, clob);
			}

			try
			(
				ResultSet rs = ps2.executeQuery();
			)
			{
				rs.next();
				return rs.getBoolean(1);
			}
		}
	}

	// XXX writeClob produces Object.toString of the Clob instance
	@MappedUDT(schema = "javatest", structure = { "t text" })
	public static class ClobbedClob implements SQLData
	{
		private String name;
		private Clob clob;

		@Override
		public String getSQLTypeName()
		{
			return name;
		}

		@Override
		public void readSQL(SQLInput stream, String typeName)
		throws SQLException
		{
			name = typeName;
			clob = stream.readClob();
		}

		@Override
		public void writeSQL(SQLOutput stream) throws SQLException
		{
			stream.writeClob(clob);
		}
	}

	// XXX writeCharacterStream produces Object.toString of the Clob instance
	@MappedUDT(schema = "javatest", structure = { "b text" })
	public static class StreamedClob implements SQLData
	{
		private String name;
		private String chars;

		@Override
		public String getSQLTypeName()
		{
			return name;
		}

		@Override
		public void readSQL(SQLInput stream, String typeName)
		throws SQLException
		{
			name = typeName;
			try
			{
				chars = readAllAsString(stream.readCharacterStream());
			}
			catch ( IOException e )
			{
				throw new SQLException(e.getMessage(), e);
			}
		}

		@Override
		public void writeSQL(SQLOutput stream) throws SQLException
		{
			stream.writeCharacterStream(new StringReader(chars));
		}
	}

	// OutOfMemoryError: Requested array size exceeds VM limit
	@MappedUDT(schema = "javatest", structure = { "b text" })
	public static class AsciiedClob implements SQLData
	{
		private String name;
		private String chars;

		@Override
		public String getSQLTypeName()
		{
			return name;
		}

		@Override
		public void readSQL(SQLInput stream, String typeName)
		throws SQLException
		{
			name = typeName;
			try
			{
				chars = readAsciiString(stream.readAsciiStream());
			}
			catch ( IOException e )
			{
				throw new SQLException(e.getMessage(), e);
			}
		}

		@Override
		public void writeSQL(SQLOutput stream) throws SQLException
		{
			stream.writeAsciiStream(
				new ByteArrayInputStream(chars.getBytes(US_ASCII)));
		}
	}
}
