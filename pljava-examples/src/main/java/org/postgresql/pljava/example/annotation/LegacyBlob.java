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
import java.io.IOException;

import java.sql.Blob;
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
 * Captures how PL/Java's Blob implementation has and hasn't worked.
 */
public class LegacyBlob
{
	private LegacyBlob() { } // do not instantiate

	static final byte[] BYTES = { (byte)1, (byte)2, (byte)3, (byte)4 };

	static Connection connect() throws SQLException
	{
		return getConnection("jdbc:default:connection");
	}

	/**
	 * Exercises getBinaryStream on ResultSet, returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean resultSetGetBinaryStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement("SELECT CAST ( ? AS bytea )");
		)
		{
			ps.setBytes(1, BYTES);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				try
				(
					InputStream is = rs.getBinaryStream(1);
				)
				{
					return Arrays.equals(BYTES, is.readAllBytes());
				}
			}
		}
	}

	/**
	 * Exercises Blob in a composite return value, returning two bytea columns
	 * that should be equal; also tests getBlob.
	 */
	@Function(schema = "javatest", out = { "c1 bytea", "c2 bytea" })
	public static boolean compositeReturnBlob(ResultSet toReturn)
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement("SELECT CAST ( ? AS bytea )");
		)
		{
			ps.setBytes(1, BYTES);
			try
			(
				ResultSet rs = ps.executeQuery();
			)
			{
				rs.next();
				Blob b = rs.getBlob(1);
				toReturn.updateBytes("c1", BYTES);
				toReturn.updateBlob("c2", b);
				return true;
			}
		}
	}

	/*
	 * Exercises Blob as a scalar return value.
	@Function(schema = "javatest", type="bytea")
	public static Blob scalarReturnBlob() throws SQLException, IOException
	{
		Without type="bytea", rejected at compile time (no compile-time mapping)
		With type="bytea", rejected at validation time (no run-time mapping)
	}
	 */

	/**
	 * Exercises setting a composite return column using updateBinaryStream,
	 * returning two bytea columns that should be equal.
	 */
	@Function(schema = "javatest", out = { "c1 bytea", "c2 bytea" })
	public static boolean compositeReturnBinaryStream(ResultSet toReturn)
	throws SQLException, IOException
	{
		toReturn.updateBytes("c1", BYTES);
		// toReturn.updateBinaryStream("c2", new ByteArrayInputStream(BYTES));
		// toReturn.updateBinaryStream(2, new ByteArrayInputStream(BYTES));
		toReturn.updateBinaryStream(
			2, new ByteArrayInputStream(BYTES), BYTES.length);
		return true;
	}

	/**
	 * Exercises setBinaryStream on PreparedStatement,
	 * returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean preparedStmtSetBinaryStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS bytea ) AS a, CAST ( ? AS bytea ) AS b)");
		)
		{
			ps.setBytes(1, BYTES);
			// ps.setBinaryStream(2, new ByteArrayInputStream(BYTES));
			ps.setBinaryStream(
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

	/**
	 * Exercises setBlob on PreparedStatement, returning true for success.
	 */
	@Function(schema = "javatest")
	public static boolean preparedStmtSetBlob()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps1 =
				c.prepareStatement("SELECT CAST ( ? AS bytea )");
			PreparedStatement ps2 =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS bytea ) AS a, CAST ( ? AS bytea ) AS b)");
		)
		{
			ps1.setBytes(1, BYTES);

			try
			(
				ResultSet rs = ps1.executeQuery();
			)
			{
				rs.next();
				Blob b = rs.getBlob(1);
				ps2.setBytes(1, BYTES);
				ps2.setBlob(2, b);
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

	@MappedUDT(schema = "javatest", structure = { "b bytea" })
	public static class BlobbedBlob implements SQLData
	{
		private String name;
		private Blob blob;

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
			blob = stream.readBlob();
		}

		@Override
		public void writeSQL(SQLOutput stream) throws SQLException
		{
			stream.writeBlob(blob);
		}
	}

	@MappedUDT(schema = "javatest", structure = { "b bytea" })
	public static class StreamedBlob implements SQLData
	{
		private String name;
		private byte[] bytes;

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
				bytes = stream.readBinaryStream().readAllBytes();
			}
			catch ( IOException e )
			{
				throw new SQLException(e.getMessage(), e);
			}
		}

		@Override
		public void writeSQL(SQLOutput stream) throws SQLException
		{
			stream.writeBinaryStream(new ByteArrayInputStream(bytes));
		}
	}
}
