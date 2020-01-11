/*
 * Copyright (c) 2016-2020 Tada AB and other contributors, as listed below.
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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.net.URL;
import java.net.MalformedURLException;

import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.Scanner;

import java.sql.SQLData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.BaseUDT;
import org.postgresql.pljava.annotation.SQLAction;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * A special user-defined type simply to exercise the I/O routines.
 *<p>
 * There is only one 'value' of this type. Its text representation is an empty
 * string. Its binary representation is a sequence of fixed values in all the
 * supported JDBC data types, which it writes on output, and reads/verifies on
 * input.
 */
@SQLAction(requires= { "udtscalariotest type" },
	install = {
		"SELECT CAST('' AS javatest.udtscalariotest)" // test send/recv
	})
@BaseUDT(schema="javatest", provides="udtscalariotest type")
public class UDTScalarIOTest implements SQLData
{

	private String m_typeName;

	private static BigDecimal s_bigdec = new BigDecimal(
		"11111111111111111111111111111111111.22222222222222222222222222222222");

	private static String s_gedicht =
"Dû bist mîn, ich bin dîn:\n" +
"des solt dû gewis sîn;\n" +
"dû bist beslozzen in mînem herzen,\n" +
"verlorn ist daz slüzzelîn:\n" +
"dû muost och immer darinne sîn.";
	private static byte[] s_utfgedicht;

	private static boolean s_bool = true;
	private static byte s_byte = 42;
	private static Date s_date = Date.valueOf("2004-01-07");
	private static double s_double = Math.PI;
	private static float s_float = (float)Math.E;
	private static int s_int = 42424242;
	private static long s_long = 4242424242424242L;
	private static short s_short = 4242;
	private static Time s_time = Time.valueOf("06:33:24");
	private static Timestamp s_timestamp =
		Timestamp.valueOf("2004-01-07 06:33:24");
	private static URL s_url;

	static
	{
		try
		{
			s_gedicht = s_gedicht + s_gedicht + s_gedicht; // x3
			s_gedicht = s_gedicht + s_gedicht + s_gedicht; // x9

			ByteBuffer bb = UTF_8.newEncoder().encode(
				CharBuffer.wrap(s_gedicht));
			s_utfgedicht = new byte[bb.limit()];
			bb.get(s_utfgedicht);

			s_url = new URL("http://tada.github.io/pljava/");
		}
		catch ( CharacterCodingException | MalformedURLException e )
		{
			throw new RuntimeException(e);
		}
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static UDTScalarIOTest parse(String input, String typeName)
			throws SQLException
	{
		if ( ! "".equals(input) )
			throw new SQLDataException(
				"The only valid text value for UDTScalarIOTest is ''", "22P02");
		UDTScalarIOTest instance = new UDTScalarIOTest();
		instance.m_typeName = typeName;
		return instance;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public String toString()
	{
		return "";
	}

	@Override
	public String getSQLTypeName()
	{
		return m_typeName;
	}

	public UDTScalarIOTest()
	{
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void writeSQL(SQLOutput stream) throws SQLException
	{
		stream.writeBigDecimal(s_bigdec);
		stream.writeBinaryStream(new ByteArrayInputStream(s_utfgedicht));
		stream.writeBoolean(s_bool);
		stream.writeByte(s_byte);
		stream.writeBytes(s_utfgedicht);
		stream.writeCharacterStream(new StringReader(s_gedicht));
		stream.writeDate(s_date);
		stream.writeDouble(s_double);
		stream.writeFloat(s_float);
		stream.writeInt(s_int);
		stream.writeLong(s_long);
		stream.writeShort(s_short);
		stream.writeString(s_gedicht);
		stream.writeTime(s_time);
		stream.writeTimestamp(s_timestamp);
		stream.writeURL(s_url);
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException
	{
		m_typeName = typeName;

		if ( ! s_bigdec.equals(stream.readBigDecimal()) )
			throw new SQLException("BigDecimal mismatch");
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		InputStream is = stream.readBinaryStream();
		try
		{
			for ( int b ; -1 != (b = is.read()) ; )
				baos.write(b);
		}
		catch ( IOException e )
		{
			throw new SQLException("Reading binary stream",
				"58030", e);
		}
		if ( ! Arrays.equals(s_utfgedicht, baos.toByteArray()) )
			throw new SQLException("binaryStream mismatch");
		if ( s_bool != stream.readBoolean() )
			throw new SQLException("boolean mismatch");
		if ( s_byte != stream.readByte() )
			throw new SQLException("byte mismatch");
		if ( ! Arrays.equals(s_utfgedicht, stream.readBytes()) )
			throw new SQLException("bytes mismatch");
		String charstream = new Scanner(stream.readCharacterStream())
			.useDelimiter("\\A").next();
		if ( ! s_gedicht.equals(charstream) )
			throw new SQLException("characterStream mismatch");
		if ( ! s_date.equals(stream.readDate()) )
			throw new SQLException("date mismatch");
		if ( s_double != stream.readDouble() )
			throw new SQLException("double mismatch");
		if ( s_float != stream.readFloat() )
			throw new SQLException("float mismatch");
		if ( s_int != stream.readInt() )
			throw new SQLException("int mismatch");
		if ( s_long != stream.readLong() )
			throw new SQLException("long mismatch");
		if ( s_short != stream.readShort() )
			throw new SQLException("short mismatch");
		if ( ! s_gedicht.equals(stream.readString()) )
			throw new SQLException("string mismatch");
		if ( ! s_time.equals(stream.readTime()) )
			throw new SQLException("time mismatch");
		if ( ! s_timestamp.equals(stream.readTimestamp()) )
			throw new SQLException("timestamp mismatch");
		if ( ! s_url.equals(stream.readURL()) )
			throw new SQLException("url mismatch");
	}
}
