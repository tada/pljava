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
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Captures how PL/Java's {@code Blob} implementation has and hasn't worked.
 *<p>
 * The {@link Blob} implementation in PL/Java, from inception and as currently
 * found in the 1.6 series releases, has never been especially useful. It has
 * used {@code Blob} objects as an alternative interface to binary byte
 * sequences <em>stored inline in a tuple</em>, such as could also be accessed
 * using, for example, {@link ResultSet#getBytes getBytes}. In this legacy
 * design, you would apply {@code getBlob} to a column containing a large byte
 * string, and be able to manipulate that content using the methods of {@code
 * Blob} instead of as a byte array.
 *<p>
 * That contrasts with the function of {@code Blob} in the PGJDBC client-side
 * driver: with that driver, you would apply {@code getBlob} to a column
 * containing the oid of a PostgreSQL
 * <a href="https://www.postgresql.org/docs/18/largeobjects.html">large
 * object</a>, and the {@code Blob} object returned would allow you
 * to manipulate the content of that out-of-tuple large object. That is almost
 * certainly the way the JDBC {@code Blob} API was intended to be used, and
 * the legacy PL/Java approach is not. On top of that, even the rather less
 * useful PL/Java realization has never been quite fully implemented. It has,
 * therefore, probably never been widely used, if at all.
 *<p>
 * These are not shortcomings to be corrected in the middle of a release series;
 * some future PL/Java major release will need to include all-new {@code Blob}
 * support in a thoroughly-revamped JDBC layer. The purpose of this example code
 * is simply to document the current working (and non-working) of the current
 * {@code Blob} support, as a guard against bit-rot making it even worse, just
 * in case anyone anywhere has used it for something.
 *<h2>The interim solution for using actual PostgreSQL large objects</h2>
 * All is not lost for code that needs to manipulate actual large objects
 * in PL/Java. It simply needs to use normal, non-{@code Blob} JDBC methods
 * to call PostgreSQL's <a href=
 * "https://www.postgresql.org/docs/18/lo-funcs.html">server-side large-object
 * functions</a> directly.
 */
@SQLAction(
	requires = { "LegacyBlob members", "TypeRoundTripper.roundTrip" }, install =
	"SELECT" +
	"  CASE WHEN" +
	"    rsgbs" +
	"    AND ( crb.c1 = crb.c2)" +
	"    AND (crbs.c1 = crbs.c2)" +
	"    AND pssbs" +
	"    AND pssb" +
	"    AND bbout.class =" +
	"       'org.postgresql.pljava.example.annotation.LegacyBlob$BlobbedBlob'" +
	"    AND bbout.roundtripped = bbin.orig" +
	"    AND sbout.class =" +
	"       'org.postgresql.pljava.example.annotation.LegacyBlob$StreamedBlob'"+
	"    AND sbout.roundtripped = sbin.orig" +
	"  THEN javatest.logmessage('INFO', 'blob support has not grown worse')" +
	"  ELSE javatest.logmessage('WARNING', 'blob support has grown worse')" +
	"  END" +
	" FROM" +
	"  javatest.resultSetGetBinaryStream() AS rsgbs," +
	"  javatest.compositeReturnBlob() AS crb," +
	"  javatest.compositeReturnBinaryStream() AS crbs," +
	"  javatest.preparedStmtSetBinaryStream() AS pssbs," +
	"  javatest.preparedStmtSetBlob() AS pssb," +
	"  (SELECT '(\\x01234567)'::javatest.blobbedblob) AS bbin(orig), " +
	"  javatest.roundtrip(bbin)" +
	"    AS bbout(class text, roundtripped javatest.blobbedblob)," +
	"  (SELECT '(\\x76543210)'::javatest.streamedblob) AS sbin(orig), " +
	"  javatest.roundtrip(sbin)" +
	"    AS sbout(class text, roundtripped javatest.streamedblob)"
)
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
	@Function(schema = "javatest", provides = "LegacyBlob members")
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
	@Function(schema = "javatest", out = { "c1 bytea", "c2 bytea" },
		provides = "LegacyBlob members")
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
	@Function(schema = "javatest", out = { "c1 bytea", "c2 bytea" },
		provides = "LegacyBlob members")
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
	@Function(schema = "javatest", provides = "LegacyBlob members")
	public static boolean preparedStmtSetBinaryStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS bytea ) AS a, CAST ( ? AS bytea ) AS b)" +
					" AS params");
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
	@Function(schema = "javatest", provides = "LegacyBlob members")
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
					" CAST ( ? AS bytea ) AS a, CAST ( ? AS bytea ) AS b)" +
					" AS params");
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

	/**
	 * A mapped user-defined-type used in testing legacy Blob support.
	 */
	@MappedUDT(schema = "javatest", structure = { "b bytea" },
		provides = "LegacyBlob members")
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

	/**
	 * A mapped user-defined-type used in testing legacy
	 * (read/write)BinaryStream support.
	 */
	@MappedUDT(schema = "javatest", structure = { "b bytea" },
		provides = "LegacyBlob members")
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
