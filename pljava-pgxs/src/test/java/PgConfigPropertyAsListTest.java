/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 */

import org.junit.Test;
import org.postgresql.pljava.pgxs.AbstractPGXS;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PgConfigPropertyAsListTest {

	@Test
	public void testPracticalExample()
	{
		AbstractPGXS pgxs = new AbstractPGXSMock();
		List<String> actualResult =
			pgxs.getPgConfigPropertyAsList(
			"-Wl,--as-needed -Wl,-rpath,'/usr/local/pgsql/lib',--enable-new-dtags");
		List<String> expectedResult = List.of("-Wl,--as-needed",
			"-Wl,-rpath,/usr/local/pgsql/lib,--enable-new-dtags");
		assertEquals(expectedResult, actualResult);
	}
}
