/*
 * Copyright (c) 2016 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava;

import java.util.regex.Matcher;

import junit.framework.TestCase;

import static
	org.postgresql.pljava.sqlgen.Lexicals.ISO_AND_PG_IDENTIFIER_CAPTURING;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;

public class LexicalsTest extends TestCase
{
	public LexicalsTest(String name) { super(name); }

	public void testIdentifierFrom() throws Exception
	{
		Matcher m = ISO_AND_PG_IDENTIFIER_CAPTURING.matcher("anIdentifier");
		assertTrue("i", m.matches());
		assertEquals("anIdentifier", identifierFrom(m));

		m.reset("\"an\"\"Identifier\"\"\"");
		assertTrue("xd", m.matches());
		assertEquals("an\"Identifier\"", identifierFrom(m));

		m.reset("u&\"an\\0049dent\"\"if\\+000069er\"");
		assertTrue("xui2", m.matches());
		assertEquals("anIdent\"ifier", identifierFrom(m));

		m.reset("u&\"an@@\"\"@0049dent@+000069fier\"\"\" uescape '@'");
		assertTrue("xui3", m.matches());
		assertEquals("an@\"Identifier\"", identifierFrom(m));
	}
}
