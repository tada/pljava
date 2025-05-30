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
package org.postgresql.pljava.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import java.nio.charset.CharacterCodingException;

import java.sql.SQLException;

import static org.postgresql.pljava.model.CatalogObject.Factory;

import org.postgresql.pljava.adt.spi.Datum;

/**
 * Represents one of PostgreSQL's available character set encodings.
 *<p>
 * Not all of the encodings that PostgreSQL supports for communication with
 * the client are also supported for use in the backend and in storage.
 * The {@link #usableOnServer usableOnServer} method identifies which ones
 * are suitable as server encodings.
 *<p>
 * The encoding that is in use for the current database cannot change during
 * a session, and is found in the final {@link #SERVER_ENCODING SERVER_ENCODING}
 * field.
 *<p>
 * The encoding currently in use by the connected client may change during
 * a session, and is returned by the {@link #clientEncoding clientEncoding}
 * method.
 *<p>
 * The {@link #charset charset} method returns the corresponding Java
 * {@link Charset Charset} if that can be identified, and several convenience
 * methods are provided to decode or encode values accordingly.
 */
public interface CharsetEncoding
{
	CharsetEncoding SERVER_ENCODING = Factory.INSTANCE.serverEncoding();

	/**
	 * A distinguished {@code CharsetEncoding} representing uses such as
	 * {@code -1} in the {@code collencoding} column of {@code pg_collation},
	 * indicating the collation is usable with any encoding.
	 *<p>
	 * This is its only instance.
	 */
	Any ANY = new Any();

	/**
	 * Returns the encoding currently selected by the connected client.
	 */
	static CharsetEncoding clientEncoding()
	{
		return Factory.INSTANCE.clientEncoding();
	}

	/**
	 * Returns the {@code CharsetEncoding} for the given PostgreSQL encoding
	 * number (as used in the {@code encoding} columns of some system catalogs).
	 * @throws IllegalArgumentException if the argument is not the ordinal of
	 * some known encoding
	 */
	static CharsetEncoding fromOrdinal(int ordinal)
	{
		return Factory.INSTANCE.encodingFromOrdinal(ordinal);
	}

	/**
	 * Returns the {@code CharsetEncoding} for the given PostgreSQL encoding
	 * name.
	 * @throws IllegalArgumentException if the argument is not the name of
	 * some known encoding
	 */
	static CharsetEncoding fromName(String name)
	{
		return Factory.INSTANCE.encodingFromName(name);
	}

	/**
	 * Returns the PostgreSQL encoding number (as used in the {@code encoding}
	 * columns of some system catalogs) for this encoding.
	 */
	int ordinal();

	/**
	 * Returns the PostgreSQL name for this encoding.
	 *<p>
	 * The PostgreSQL encoding names have a long history and may not match
	 * cleanly with more standardized names in modern libraries.
	 */
	String name();

	/**
	 * Returns the name identifying this encoding in ICU (international
	 * components for Unicode), or null if its implementation in PostgreSQL
	 * does not define one.
	 *<p>
	 * When present, the ICU name can be a better choice for matching encodings
	 * in other libraries.
	 */
	String icuName();

	/**
	 * Indicates whether this encoding is usable as a server encoding.
	 */
	boolean usableOnServer();

	/**
	 * Returns the corresponding Java {@link Charset Charset}, or null if none
	 * can be identified.
	 */
	Charset charset();

	/**
	 * Returns a {@link CharsetDecoder CharsetDecoder}, configured to report
	 * all decoding errors (rather than silently substituting data), if
	 * {@link #charset charset()} would return a non-null value.
	 */
	default CharsetDecoder newDecoder()
	{
		return charset().newDecoder();
	}

	/**
	 * Returns a {@link CharsetEncoder CharsetEncoder}, configured to report
	 * all encoding errors (rather than silently substituting data), if
	 * {@link #charset charset()} would return a non-null value.
	 */
	default CharsetEncoder newEncoder()
	{
		return charset().newEncoder();
	}

	/**
	 * Decode bytes to characters, with exceptions reported.
	 *<p>
	 * Unlike the corresponding convenience method on {@link Charset Charset},
	 * this method will throw exceptions rather than silently substituting
	 * characters. This is a database system; it doesn't go changing your data
	 * without telling you.
	 *<p>
	 * Other behaviors can be obtained by calling {@link #newDecoder newDecoder}
	 * and configuring it as desired.
	 */
	default CharBuffer decode(ByteBuffer bb) throws CharacterCodingException
	{
		return newDecoder().decode(bb);
	}

	/**
	 * Encode characters to bytes, with exceptions reported.
	 *<p>
	 * Unlike the corresponding convenience method on {@link Charset Charset},
	 * this method will throw exceptions rather than silently substituting
	 * characters. This is a database system; it doesn't go changing your data
	 * without telling you.
	 *<p>
	 * Other behaviors can be obtained by calling {@link #newEncoder newEncoder}
	 * and configuring it as desired.
	 */
	default ByteBuffer encode(CharBuffer cb) throws CharacterCodingException
	{
		return newEncoder().encode(cb);
	}

	/**
	 * Encode characters to bytes, with exceptions reported.
	 *<p>
	 * Unlike the corresponding convenience method on {@link Charset Charset},
	 * this method will throw exceptions rather than silently substituting
	 * characters. This is a database system; it doesn't go changing your data
	 * without telling you.
	 *<p>
	 * Other behaviors can be obtained by calling {@link #newEncoder newEncoder}
	 * and configuring it as desired.
	 */
	default ByteBuffer encode(String s) throws CharacterCodingException
	{
		return encode(CharBuffer.wrap(s));
	}

	/**
	 * Decode bytes to characters, with exceptions reported.
	 *<p>
	 * The input {@link Datum Datum} is pinned around the decoding operation.
	 */
	default CharBuffer decode(Datum.Input in, boolean close)
	throws SQLException, IOException
	{
		in.pin();
		try
		{
			return decode(in.buffer());
		}
		finally
		{
			in.unpin();
			if ( close )
				in.close();
		}
	}

	/**
	 * Return an {@link InputStreamReader InputStreamReader} that reports
	 * exceptions.
	 *<p>
	 * Other behaviors can be obtained by calling {@link #newDecoder newDecoder}
	 * and configuring it as desired before constructing an
	 * {@code InputStreamReader}.
	 */
	default InputStreamReader reader(InputStream in)
	{
		return new InputStreamReader(in, newDecoder());
	}

	/**
	 * Return an {@link OutputStreamWriter OutputStreamWriter} that reports
	 * exceptions.
	 *<p>
	 * Other behaviors can be obtained by calling {@link #newEncoder newEncoder}
	 * and configuring it as desired before constructing an
	 * {@code OutputStreamWriter}.
	 */
	default OutputStreamWriter writer(OutputStream out)
	{
		return new OutputStreamWriter(out, newEncoder());
	}

	/**
	 * A distinguished {@code CharsetEncoding} representing uses such as
	 * {@code -1} in the {@code collencoding} column of {@code pg_collation},
	 * indicating the collation is usable with any encoding.
	 *<p>
	 * This returns -1 from {@code ordinal()} and {@code null} or {@code false}
	 * from the other non-default methods according to their types. The only
	 * instance of this class is {@code CharsetEncoding.ANY}.
	 */
	class Any implements CharsetEncoding
	{
		private Any()
		{
		}

		@Override
		public int ordinal()
		{
			return -1;
		}

		@Override
		public String name()
		{
			return null;
		}

		@Override
		public String icuName()
		{
			return null;
		}

		@Override
		public boolean usableOnServer()
		{
			return false;
		}

		@Override
		public Charset charset()
		{
			return null;
		}

		@Override
		public String toString()
		{
			return "CharsetEncoding.ANY";
		}
	}
}
