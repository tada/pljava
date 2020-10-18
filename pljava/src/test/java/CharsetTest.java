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

import junit.framework.TestCase;

import static org.junit.Assert.*;
import org.junit.Ignore;
import static org.hamcrest.CoreMatchers.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.US_ASCII;

import static java.util.regex.Pattern.matches;

public class CharsetTest extends TestCase
{
	public CharsetTest(String name) { super(name); }

	public void testSQL_ASCII() throws Exception
	{
		Charset sqa = Charset.forName("SQL_ASCII");
		assertNotNull(sqa);

		assertTrue(sqa.contains(sqa));
		assertTrue(sqa.contains(US_ASCII));

		ByteBuffer bb = ByteBuffer.allocate(256);

		while ( bb.hasRemaining() )
			bb.put((byte)bb.position());

		bb.flip();

		CharBuffer cb = sqa.decode(bb);

		assertTrue(matches("[\\u0000-\\u007f]{128}+" +
						   "(?:[\\ufdd8-\\ufddf][\\ufde0-\\ufdef]){128}+", cb));

		cb.rewind();

		ByteBuffer bb2 = sqa.encode(cb);
		bb.rewind();

		assertTrue(bb2.equals(bb));
	}
}
