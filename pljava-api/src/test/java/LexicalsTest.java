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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import static
	org.postgresql.pljava.sqlgen.Lexicals.ISO_AND_PG_IDENTIFIER_CAPTURING;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

public class LexicalsTest extends TestCase
{
	public LexicalsTest(String name) { super(name); }

	public void testIdentifierFrom() throws Exception
	{
		Matcher m = ISO_AND_PG_IDENTIFIER_CAPTURING.matcher("anIdentifier");
		assertTrue("i", m.matches());
		assertEquals("anIdentifier", identifierFrom(m).nonFolded());

		m.reset("\"an\"\"Identifier\"\"\"");
		assertTrue("xd", m.matches());
		assertEquals("an\"Identifier\"", identifierFrom(m).nonFolded());

		m.reset("u&\"an\\0049dent\"\"if\\+000069er\"");
		assertTrue("xui2", m.matches());
		assertEquals("anIdent\"ifier", identifierFrom(m).nonFolded());

		m.reset("u&\"an@@\"\"@0049dent@+000069fier\"\"\" uescape '@'");
		assertTrue("xui3", m.matches());
		assertEquals("an@\"Identifier\"", identifierFrom(m).nonFolded());
	}

	public void testIdentifierEquivalence() throws Exception
	{
		Identifier baß = Identifier.from("baß", false);
		Identifier Baß = Identifier.from("Baß", false);
		Identifier bass = Identifier.from("bass", false);
		Identifier BASS = Identifier.from("BASS", false);

		Identifier qbaß = Identifier.from("baß", true);
		Identifier qBaß = Identifier.from("Baß", true);
		Identifier qbass = Identifier.from("bass", true);
		Identifier qBASS = Identifier.from("BASS", true);

		Identifier sab = Identifier.from("sopran alt baß", true);
		Identifier SAB = Identifier.from("Sopran Alt Baß", true);

		/* DESERET SMALL LETTER OW */
		Identifier ow = Identifier.from("\uD801\uDC35", false);
		/* DESERET CAPITAL LETTER OW */
		Identifier OW = Identifier.from("\uD801\uDC0D", false);

		assertEquals("hash1", baß.hashCode(), Baß.hashCode());
		assertEquals("hash2", baß.hashCode(), bass.hashCode());
		assertEquals("hash3", baß.hashCode(), BASS.hashCode());

		assertEquals("hash4", baß.hashCode(), qbaß.hashCode());
		assertEquals("hash5", baß.hashCode(), qBaß.hashCode());
		assertEquals("hash6", baß.hashCode(), qbass.hashCode());
		assertEquals("hash7", baß.hashCode(), qBASS.hashCode());

		assertEquals("hash8", ow.hashCode(), OW.hashCode());

		assertEquals("eq1", baß, Baß);
		assertEquals("eq2", baß, bass);
		assertEquals("eq3", baß, BASS);

		assertEquals("eq4", Baß, qbaß);
		assertEquals("eq5", Baß, qBASS);

		assertEquals("eq6", ow, OW);

		assertThat("ne1", Baß, not(equalTo(qBaß)));
		assertThat("ne2", Baß, not(equalTo(qbass)));

		assertThat("ne3", sab, not(equalTo(SAB)));
	}
}
