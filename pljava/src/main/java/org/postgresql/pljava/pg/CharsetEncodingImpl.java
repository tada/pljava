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
package org.postgresql.pljava.pg;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;
import java.nio.CharBuffer;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.regex.Pattern;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.CacheMap;

import org.postgresql.pljava.model.CharsetEncoding;

import static org.postgresql.pljava.pg.ModelConstants.NAMEDATALEN;
import static org.postgresql.pljava.pg.ModelConstants.PG_ENCODING_BE_LAST;
import static org.postgresql.pljava.pg.ModelConstants.PG_LATIN1;
import static org.postgresql.pljava.pg.ModelConstants.PG_SQL_ASCII;
import static org.postgresql.pljava.pg.ModelConstants.PG_UTF8;

class CharsetEncodingImpl implements CharsetEncoding
{
	private static final CacheMap<CharsetEncoding> s_byOrdinal =
		CacheMap.newConcurrent(
			() -> ByteBuffer.allocate(4).order(nativeOrder()));

	private static final ByteBuffer s_nameWindow =
		ByteBuffer.allocateDirect(NAMEDATALEN);

	private static final Pattern s_name_sqlascii = Pattern.compile(
		"(?i)(?:X[-_]?+)?+(?:PG)?+SQL[-_]?+ASCII");

	private static final String s_property = "org.postgresql.server.encoding";

	/**
	 * Only called once to initialize the {@code SERVER_ENCODING} static.
	 *<p>
	 * Doesn't use {@code fromOrdinal}, because that method will check against
	 * {@code SERVER_ENCODING}.
	 */
	static CharsetEncoding serverEncoding()
	{
		String charsetOverride = System.getProperty(s_property);
		CharsetEncoding result = doInPG(() ->
		{
			int ordinal = EarlyNatives._serverEncoding();
			return s_byOrdinal.softlyCache(
				b -> b.putInt(ordinal),
				b -> new CharsetEncodingImpl(ordinal, charsetOverride)
			);
		});
		if ( null != result.charset() )
		{
			System.setProperty(s_property, result.charset().name());
			return result;
		}
		throw new UnsupportedOperationException(
			"No Java Charset found for PostgreSQL server encoding " +
			"\"" + result.name() + "\" (" + result.ordinal() +"). Consider " +
			"adding -D" + s_property + "=... in pljava.vmoptions.");
	}

	static CharsetEncoding clientEncoding()
	{
		return doInPG(() -> fromOrdinal(EarlyNatives._clientEncoding()));
	}

	static CharsetEncoding fromOrdinal(int ordinal)
	{
		if ( SERVER_ENCODING.ordinal() == ordinal )
			return SERVER_ENCODING;
		return s_byOrdinal.softlyCache(
			b -> b.putInt(ordinal),
			b -> doInPG(() -> new CharsetEncodingImpl(ordinal, null))
		);
	}

	static CharsetEncoding fromName(String name)
	{
		try
		{
			return doInPG(() ->
				{
					s_nameWindow.clear();
					/*
					 * Charset names should all be ASCII, according to IANA,
					 * which neatly skirts a "how do I find the encoder for
					 * the name of my encoding?" conundrum.
					 */
					CharsetEncoder e = US_ASCII.newEncoder();
					CoderResult r = e.encode(
						CharBuffer.wrap(name), s_nameWindow, true);
					if ( r.isUnderflow() )
						r = e.flush(s_nameWindow);
					if ( ! r.isUnderflow() )
						r.throwException();
					/*
					 * PG will want a NUL-terminated string (and yes, the NAME
					 * datatype is limited to NAMEDATALEN - 1 encoded octets
					 * plus the NUL, so if this doesn't fit, overflow exception
					 * is the right outcome).
					 */
					s_nameWindow.put((byte)0).flip();
					int o = EarlyNatives._nameToOrdinal(s_nameWindow);
					if ( -1 != o )
						return fromOrdinal(o);
					if ( s_name_sqlascii.matcher(name).matches() )
						return fromOrdinal(PG_SQL_ASCII);
					throw new IllegalArgumentException(
						"no such PostgreSQL character encoding: \""
						+ name + "\"");
				}
			);
		}
		catch ( BufferOverflowException | CharacterCodingException e )
		{
			throw new IllegalArgumentException(
				"no such PostgreSQL character encoding: \"" + name + "\"", e);
		}
	}

	private final int m_ordinal;
	private final String m_name;
	private final String m_icuName;
	private final Charset m_charset;

	private CharsetEncodingImpl(int ordinal, String charsetOverride)
	{
		assert threadMayEnterPG();
		ByteBuffer b = EarlyNatives._ordinalToName(ordinal);
		if ( null == b )
			throw new IllegalArgumentException(
				"no such PostgreSQL character encoding: " + ordinal);

		m_ordinal = ordinal;

		try
		{
			m_name = US_ASCII.newDecoder().decode(b).toString();
		}
		catch ( CharacterCodingException e )
		{
			throw new AssertionError(
				"PG encoding " + ordinal + " has a non-ASCII name");
		}

		String altName = null;
		if ( usableOnServer() )
		{
			b = EarlyNatives._ordinalToIcuName(ordinal);
			if ( null != b )
			{
				try
				{
					altName = US_ASCII.newDecoder().decode(b).toString();
				}
				catch ( CharacterCodingException e )
				{
					throw new AssertionError(
						"PG encoding " + ordinal + " has a non-ASCII ICU name");
				}
			}
		}
		m_icuName = altName;

		Charset c = null;
		if ( null == charsetOverride )
		{
			switch ( ordinal )
			{
			case PG_LATIN1   : c = ISO_8859_1; break;
			case PG_UTF8     : c = UTF_8     ; break;
			default:
			}
		}
		else
			altName = charsetOverride;

		if ( null == c )
		{
			try
			{
				c = Charset.forName(null != altName ? altName : m_name);
			}
			catch ( IllegalArgumentException e )
			{
			}
		}
		m_charset = c;
	}

	@Override
	public int ordinal()
	{
		return m_ordinal;
	}

	@Override
	public String name()
	{
		return m_name;
	}

	@Override
	public String icuName()
	{
		return m_icuName;
	}

	@Override
	public boolean usableOnServer()
	{
		return 0 <= m_ordinal  &&  m_ordinal <= PG_ENCODING_BE_LAST;
	}

	@Override
	public Charset charset()
	{
		return m_charset;
	}

	@Override
	public String toString()
	{
		return "CharsetEncoding[" + m_ordinal + "]" + m_name;
	}

	private static class EarlyNatives
	{
		private static native int _serverEncoding();
		private static native int _clientEncoding();
		private static native int _nameToOrdinal(ByteBuffer nulTerminated);
		private static native ByteBuffer _ordinalToName(int ordinal);
		private static native ByteBuffer _ordinalToIcuName(int ordinal);
	}
}
