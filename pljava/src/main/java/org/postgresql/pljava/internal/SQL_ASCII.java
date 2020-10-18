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
package org.postgresql.pljava.internal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.spi.CharsetProvider;
import static java.nio.charset.StandardCharsets.US_ASCII;

import static java.util.Collections.singletonList;
import java.util.Iterator;
import java.util.List;

/**
 * An {@code SQL_ASCII}, a/k/a {@code X-PGSQL_ASCII}, "character set".
 *<p>
 * This is a principled Java take on the PostgreSQL definition of
 * SQL_ASCII as an encoding where the seven-bit ASCII values are
 * themselves and the remaining eight-bit values are who-knows-what.
 * It isn't appropriate to just copy byte values with no conversion,
 * as that would amount to saying we know the values correspond to
 * LATIN-1, which would be lying. Java strings are by definition Unicode,
 * so it's not ok to go stuffing code points in that do not mean what
 * Unicode defines those code points to mean.
 *<p>
 * What this decoder does is decode the seven-bit ASCII values as
 * themselves, and decode each eight-bit value into a pair of Unicode
 * noncharacters, one from the range u+fdd8 to u+fddf, followed by one
 * from u+fde0 to u+fdef, where the first one's four low bits are the
 * four high bits of the original value, and the second has the low four.
 * The encoder transparently reverses that.
 *<p>
 * Those noncharacter code points are permanently defined in Unicode
 * to have no glyphs, no correspondence to specific characters, and
 * no interesting properties. Implementing this charset allows PL/Java
 * code to work usefully in a database with SQL_ASCII encoding, when the
 * expectation is that whatever the code needs to recognize, act on, or
 * edit will be in ASCII, and any non-ASCII content can be passed along
 * uninterpreted and unchanged.
 */
class SQL_ASCII extends Charset
{
	static class Holder
	{
		static final List<Charset> s_list =
			singletonList((Charset)new SQL_ASCII());
	}


	public static class Provider extends CharsetProvider
	{
		static final String s_canonName = "X-PGSQL_ASCII";
		static final String[] s_aliases = { "SQL_ASCII" };

		@Override
		public Charset charsetForName(String charsetName)
		{
			if ( s_canonName.equalsIgnoreCase(charsetName) )
				return Holder.s_list.get(0);
			for ( String s : s_aliases )
				if ( s.equalsIgnoreCase(charsetName) )
					return Holder.s_list.get(0);
			return null;
		}

		@Override
		public Iterator<Charset> charsets()
		{
			return Holder.s_list.iterator();
		}
	}


	private SQL_ASCII()
	{
		super(Provider.s_canonName, Provider.s_aliases);
	}

	@Override
	public boolean contains(Charset cs)
	{
		return this.equals(cs)  ||  US_ASCII.equals(cs);
	}

	@Override
	public CharsetDecoder newDecoder()
	{
		return new Decoder();
	}

	@Override
	public CharsetEncoder newEncoder()
	{
		return new Encoder();
	}


	static class Decoder extends CharsetDecoder
	{
		Decoder()
		{
			super(Holder.s_list.get(0), 1.002f, 2.0f);
		}

		@Override
		protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out)
		{
			int ipos = in.position();
			int opos = out.position();
			int ilim = in.limit();
			int olim = out.limit();

			for ( ; ipos < ilim ; ++ ipos )
			{
				char b = (char)(0xff & in.get(ipos));

				if ( b < 128 )
				{
					if ( opos == olim )
					{
						in.position(ipos);
						out.position(opos);
						return CoderResult.OVERFLOW;
					}
					out.put(opos++, b);
				}
				else
				{
					if ( opos + 1 >= olim )
					{
						in.position(ipos);
						out.position(opos);
						return CoderResult.OVERFLOW;
					}
					out.put(opos++, (char)(0xFDD0 | (b >> 4)));
					out.put(opos++, (char)(0xFDE0 | (b & 0xf)));
				}
			}
			in.position(ipos);
			out.position(opos);
			return CoderResult.UNDERFLOW;
		}
	}

	static class Encoder extends CharsetEncoder
	{
		Encoder()
		{
			super(Holder.s_list.get(0), 0.998f, 1.0f);
		}

		@Override
		protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out)
		{
			int ipos = in.position();
			int opos = out.position();
			int ilim = in.limit();
			int olim = out.limit();

			for ( ; ipos < ilim ; ++ ipos )
			{
				if ( opos == olim )
				{
					in.position(ipos);
					out.position(opos);
					return CoderResult.OVERFLOW;
				}

				char c = in.get(ipos);

				if ( '\uFDD8' <= c  &&  c < '\uFDE0' )
				{
					if ( ipos + 1 == ilim )
						break;

					char d = in.get(ipos + 1);

					if ( '\uFDE0' > d  ||  d > '\uFDEF' )
					{
						in.position(ipos);
						out.position(opos);
						return CoderResult.malformedForLength(2);
					}
					c = (char)(( (c & 0xf) << 4 ) | (d & 0xf));
					++ ipos;
				}
				else if ( c >= 128 )
				{
					in.position(ipos);
					out.position(opos);
					return CoderResult.unmappableForLength(1);
				}
				out.put(opos++, (byte)c);
			}
			in.position(ipos);
			out.position(opos);
			return CoderResult.UNDERFLOW;
		}
	}
}
