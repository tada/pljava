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
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Captures how PL/Java's Clob implementation has and hasn't (hasn't, mostly)
 * worked.
 *<p>
 * The {@link Clob} implementation in PL/Java, from inception and as currently
 * found in the 1.6 series releases, has never been especially useful. It has
 * used {@code Clob} objects as an alternative interface to character strings
 * <em>stored inline in a tuple</em>, such as could also be accessed
 * using, for example, {@link ResultSet#getString getString}. In this legacy
 * design, you would apply {@code getClob} to a column containing a large text
 * string, and be able to manipulate that content using the methods of {@code
 * Clob} instead of as a character string.
 *<p>
 * That contrasts with the function of {@code Clob} in the PGJDBC client-side
 * driver: with that driver, you would apply {@code getClob} to a column
 * containing the oid of a PostgreSQL
 * <a href="https://www.postgresql.org/docs/18/largeobjects.html">large
 * object</a>, and the {@code Clob} object returned would allow you
 * to manipulate the content of that out-of-tuple large object. That is almost
 * certainly the way the JDBC {@code Clob} API was intended to be used, and
 * the legacy PL/Java approach is not. On top of that, even the rather less
 * useful PL/Java realization has never been close to fully implemented. It has,
 * therefore, probably never been widely used, if at all.
 *<p>
 * These are not shortcomings to be corrected in the middle of a release series;
 * some future PL/Java major release will need to include all-new {@code Clob}
 * support in a thoroughly-revamped JDBC layer. The purpose of this example code
 * is simply to document the current working (and non-working) of the current
 * {@code Clob} support, as a guard against bit-rot making it even worse, just
 * in case anyone anywhere has used it for something.
 *<h2>The interim solution for using actual PostgreSQL large objects</h2>
 * All is not lost for code that needs to manipulate actual large objects
 * in PL/Java. It simply needs to use normal, non-{@code Clob} JDBC methods
 * to call PostgreSQL's <a href=
 * "https://www.postgresql.org/docs/18/lo-funcs.html">server-side large-object
 * functions</a> directly and (in the case of a {@code Clob}) apply appropriate
 * character-set encodings.
 */
@SQLAction(
	requires = { "LegacyClob members", "TypeRoundTripper.roundTrip" }, install =
	"SELECT" +
	"  CASE WHEN" +
	"    rsgcs AND rsgas" +
	"    AND (crcs.c1 = crcs.c2)" +
	"    AND (cras.c1 = cras.c2)" +
	"    AND psscs AND pssas" +
	"    AND scout.class =" +
	"       'org.postgresql.pljava.example.annotation.LegacyClob$StreamedClob'"+
	"    AND scout.roundtripped = scin.orig" +
	"    AND acout.class =" +
	"       'org.postgresql.pljava.example.annotation.LegacyClob$AsciiedClob'"+
	"    AND acout.roundtripped = acin.orig" +
	"  THEN javatest.logmessage('INFO', 'clob support has not grown worse')" +
	"  ELSE javatest.logmessage('WARNING', 'clob support has grown worse')" +
	"  END" +
	" FROM" +
	"  javatest.resultSetGetCharacterStream() AS rsgcs," +
	"  javatest.resultSetGetAsciiStream() AS rsgas," +
	"  javatest.compositeReturnCharacterStream() AS crcs," +
	"  javatest.compositeReturnAsciiStream() AS cras," +
	"  javatest.preparedStmtSetCharacterStream() AS psscs," +
	"  javatest.preparedStmtSetAsciiStream() AS pssas," +
	"  (SELECT '(PostgreSQL)'::javatest.streamedclob) AS scin(orig), " +
	"  javatest.roundtrip(scin)" +
	"    AS scout(class text, roundtripped javatest.streamedclob)," +
	"  (SELECT '(LQSergtsoP)'::javatest.asciiedclob) AS acin(orig), " +
	"  javatest.roundtrip(acin)" +
	"    AS acout(class text, roundtripped javatest.asciiedclob)"
)
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
	@Function(schema = "javatest", provides = "LegacyClob members")
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
	@Function(schema = "javatest", provides = "LegacyClob members")
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

	// Now works! Formerly:
	// SQLException: Cannot derive a value of class java.lang.String from
	// an object of class org.postgresql.pljava.jdbc.ClobValue
	/**
	 * Exercises setting a composite return column using updateCharacterStream,
	 * returning two text columns that should be equal.
	 */
	@Function(schema = "javatest", out = { "c1 text", "c2 text" },
		provides = "LegacyClob members")
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
	@Function(schema = "javatest", out = { "c1 text", "c2 text" },
		provides = "LegacyClob members")
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

	// Now works! Formerly:
	// XXX returns false; Clob probably rendered by Object.toString
	/**
	 * Exercises setCharacterStream on PreparedStatement,
	 * returning true for success.
	 */
	@Function(schema = "javatest", provides = "LegacyClob members")
	public static boolean preparedStmtSetCharacterStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)" +
					" AS params");
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

	// Now works! Formerly:
	// XXX returns false; Clob probably rendered by Object.toString
	/**
	 * Exercises setAsciiStream on PreparedStatement,
	 * returning true for success.
	 */
	@Function(schema = "javatest", provides = "LegacyClob members")
	public static boolean preparedStmtSetAsciiStream()
	throws SQLException, IOException
	{
		try
		(
			Connection c = connect();
			PreparedStatement ps =
				c.prepareStatement(
					"SELECT a = b FROM (SELECT" +
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)" +
					" AS params");
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
					" CAST ( ? AS text ) AS a, CAST ( ? AS text ) AS b)" +
					" AS params");
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
	/**
	 * A mapped user-defined-type used in testing legacy Clob support.
	 */
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

	// Now works! Formerly:
	// writeCharacterStream produces Object.toString of the Clob instance
	/**
	 * A mapped user-defined-type used in testing legacy
	 * (read/write}CharacterStream support.
	 */
	@MappedUDT(schema = "javatest", structure = { "b text" },
		provides = "LegacyClob members")
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

	// Now works! Formerly:
	// OutOfMemoryError: Requested array size exceeds VM limit
	/**
	 * A mapped user-defined-type used in testing legacy
	 * (read/write}AsciiStream support.
	 */
	@MappedUDT(schema = "javatest", structure = { "b text" },
		provides = "LegacyClob members")
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
