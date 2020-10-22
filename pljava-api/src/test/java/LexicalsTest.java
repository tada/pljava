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
package org.postgresql.pljava;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.InputMismatchException;

import junit.framework.TestCase;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import static
	org.postgresql.pljava.sqlgen.Lexicals.ISO_AND_PG_IDENTIFIER_CAPTURING;
import static
	org.postgresql.pljava.sqlgen.Lexicals.SEPARATOR;
import static
	org.postgresql.pljava.sqlgen.Lexicals.PG_OPERATOR;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;
import static org.postgresql.pljava.sqlgen.Lexicals.separator;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Operator;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Qualified;

public class LexicalsTest extends TestCase
{
	public LexicalsTest(String name) { super(name); }

	public void testSeparator() throws Exception
	{
		Pattern allTheRest = Pattern.compile(".*", Pattern.DOTALL);

		Matcher m = SEPARATOR.matcher("no starting separator");
		assertFalse("separator 0", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("no starting separator", m.group(0));

		m.reset();
		assertFalse("separator 1", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("no starting separator", m.group(0));

		m.reset(" 	 simple separator");
		assertTrue("separator 2", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("simple separator", m.group(0));

		m.reset();
		assertFalse("separator 3", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("simple separator", m.group(0));

		m.reset(" 	\n simple separator");
		assertTrue("separator 4", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("simple separator", m.group(0));

		m.reset();
		assertTrue("separator 5", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("simple separator", m.group(0));

		m.reset(" -- a simple comment\nsimple comment");
		assertTrue("separator 6", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("simple comment", m.group(0));

		m.reset();
		assertTrue("separator 7", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("simple comment", m.group(0));

		m.reset("/* a bracketed comment\n */ bracketed comment");
		assertTrue("separator 8", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("bracketed comment", m.group(0));

		m.reset();
		assertFalse("separator 9", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("bracketed comment", m.group(0));

		m.reset("/* a /* nested */ comment\n */ nested comment");
		assertTrue("separator 10", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("nested comment", m.group(0));

		m.reset();
		assertFalse("separator 11", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("nested comment", m.group(0));

		m.reset("/* an /* unclosed */ comment\n * / unclosed comment");
		try
		{
			separator(m, true);
			fail("unclosed comment not detected");
		}
		catch ( Exception ex )
		{
			assertTrue("separator 12", ex instanceof InputMismatchException);
		}

		m.reset("/* -- tricky \n */ nested comment");
		assertTrue("separator 13", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("nested comment", m.group(0));

		m.reset();
		assertFalse("separator 14", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("nested comment", m.group(0));

		m.reset("-- /* tricky \n */ nested comment");
		assertTrue("separator 15", separator(m, true));
		m.usePattern(allTheRest).matches();
		assertEquals("*/ nested comment", m.group(0));

		m.reset();
		assertTrue("separator 16", separator(m, false));
		m.usePattern(allTheRest).matches();
		assertEquals("*/ nested comment", m.group(0));
	}

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
		Identifier baß = Simple.from("baß", false);
		Identifier Baß = Simple.from("Baß", false);
		Identifier bass = Simple.from("bass", false);
		Identifier BASS = Simple.from("BASS", false);

		Identifier qbaß = Simple.from("baß", true);
		Identifier qBaß = Simple.from("Baß", true);
		Identifier qbass = Simple.from("bass", true);
		Identifier qBASS = Simple.from("BASS", true);

		Identifier sab = Simple.from("sopran alt baß", true);
		Identifier SAB = Simple.from("Sopran Alt Baß", true);

		/* DESERET SMALL LETTER OW */
		Identifier ow = Simple.from("\uD801\uDC35", false);
		/* DESERET CAPITAL LETTER OW */
		Identifier OW = Simple.from("\uD801\uDC0D", false);

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

	public void testIdentifierSimpleFrom() throws Exception
	{
		Identifier.Simple s1 = Simple.fromJava("aB");
		Identifier.Simple s2 = Simple.fromJava("\"ab\"");
		Identifier.Simple s3 = Simple.fromJava("\"A\"\"b\"");
		Identifier.Simple s4 = Simple.fromJava("A\"b");

		Identifier.Simple s5 = Simple.fromCatalog("ab");
		Identifier.Simple s6 = Simple.fromCatalog("AB");
		Identifier.Simple s7 = Simple.fromCatalog("A\"b");

		assertEquals("eq1", s1, s2);
		assertEquals("eq2", s3, s4);
		assertEquals("eq3", s1, s5);
		assertEquals("eq4", s2, s5);
		assertEquals("eq5", s1, s6);
		assertEquals("eq6", s5, s6);
		assertEquals("eq7", s3, s7);

		assertThat("ne1", s2, not(equalTo(s6)));

		assertEquals("deparse1", s1.toString(), "aB");
		assertEquals("deparse2", s2.toString(), "\"ab\"");
		assertEquals("deparse3", s3.toString(), "\"A\"\"b\"");
		assertEquals("deparse4", s4.toString(), "\"A\"\"b\"");
		assertEquals("deparse5", s5.toString(), "ab");
		assertEquals("deparse6", s6.toString(), "\"AB\"");
		assertEquals("deparse7", s7.toString(), "\"A\"\"b\"");
	}

	public void testOperatorPattern() throws Exception
	{
		Matcher m = PG_OPERATOR.matcher("+");
		assertTrue("+", m.matches());
		assertTrue("-", m.reset("-").matches());
		assertFalse("--", m.reset("--").matches());
		assertFalse("/-", m.reset("/-").matches());
		assertTrue("@-", m.reset("@-").matches());
		assertTrue("@_--", m.reset("@--").lookingAt());
		assertEquals("eq1", m.group(), "@");
		assertTrue("@/", m.reset("@/").lookingAt());
		assertEquals("eq2", m.group(), "@/");
		assertTrue("@_/*", m.reset("@/*").lookingAt());
		assertEquals("eq3", m.group(), "@");
		assertTrue("+_-", m.reset("+-").lookingAt());
		assertEquals("eq4", m.group(), "+");
		assertTrue("-_+", m.reset("-+").lookingAt());
		assertEquals("eq5", m.group(), "-");
		assertFalse("--+", m.reset("--+").lookingAt());
		assertTrue("-_++", m.reset("-++").lookingAt());
		assertEquals("eq6", m.group(), "-");
		assertTrue("**_-++", m.reset("**-++").lookingAt());
		assertEquals("eq7", m.group(), "**");
		assertTrue("*!*-++", m.reset("*!*-++").lookingAt());
		assertEquals("eq8", m.group(), "*!*-++");
	}

	public void testIdentifierOperatorFrom() throws Exception
	{
		Operator o1 = Operator.from("!@#%*");
		Operator o2 = Operator.from("!@#%*");
		assertEquals("eq1", o1, o2);
		assertEquals("eq2", o1.toString(), "!@#%*");
		Simple s1 = Simple.from("foo", false);
		Qualified q1 = o1.withQualifier(null);
		assertEquals("eq3", q1.toString(), o1.toString());
		Qualified q2 = o1.withQualifier(s1);
		assertEquals("eq4", q2.toString(), "OPERATOR(foo.!@#%*)");
		Simple s2 = Simple.from("foo", true);
		Qualified q3 = o1.withQualifier(s2);
		assertEquals("eq5", q3.toString(), "OPERATOR(\"foo\".!@#%*)");
	}
}
