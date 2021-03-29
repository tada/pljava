/*
 * Copyright (c) 2020-2021 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 *   Chapman Flack
 */

import org.junit.Before;
import org.junit.Test;
import org.postgresql.pljava.pgxs.AbstractPGXS;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PgConfigPropertyAsListTest {

	AbstractPGXS pgxs;
	@Before
	public void setup() {
		pgxs = new AbstractPGXSMock();
	}

	@Test
	public void testSimpleExample()
	{
		List<String> actualResult = pgxs.getPgConfigPropertyAsList(
			"foo 'bar' 'foo bar'");
		List<String> expectedResult = List.of("foo", "bar", "foo bar");
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testPracticalExample()
	{
		List<String> actualResult = pgxs.getPgConfigPropertyAsList(
			"-Wl,--as-needed -Wl,-rpath,'/usr/local/pgsql/lib',--enable-new-dtags");
		List<String> expectedResult = List.of("-Wl,--as-needed",
			"-Wl,-rpath,/usr/local/pgsql/lib,--enable-new-dtags");
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testWhitespaceInQuotes()
	{
		List<String> actualResult = pgxs.getPgConfigPropertyAsList(
			"-Wl,--as-needed -Wl,-rpath,'/usr/local test/pgsql/lib',--enable-new-dtags");
		List<String> expectedResult = List.of("-Wl,--as-needed",
			"-Wl,-rpath,/usr/local test/pgsql/lib,--enable-new-dtags");
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testMultipleSpaceSeparator()
	{
		List<String> actualResult = pgxs.getPgConfigPropertyAsList(
			"-Wl,--as-needed  -Wl,-rpath,'/usr/local test/pgsql/lib',--enable-new-dtags");
		List<String> expectedResult = List.of("-Wl,--as-needed",
			"-Wl,-rpath,/usr/local test/pgsql/lib,--enable-new-dtags");
		assertEquals(expectedResult, actualResult);
	}

}
