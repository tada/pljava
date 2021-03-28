/*
 * Copyright (c) 2018-2021 Tada AB and other contributors, as listed below.
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

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;

import static java.util.logging.Logger.getAnonymousLogger;
import java.util.TimeZone;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * Some tests of pre-JSR 310 date/time/timestamp conversions.
 *<p>
 * For now, just {@code java.sql.Date}, thanks to issue #199.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example.
 */
@SQLAction(provides="language java_tzset", install={
	"SELECT sqlj.alias_java_language('java_tzset', true)"
}, remove={
	"DROP LANGUAGE java_tzset"
})

@SQLAction(implementor="postgresql_ge_90300", // needs LATERAL
	requires="issue199", install={
	"SELECT javatest.issue199()"
})
public class PreJSR310
{
	private static final String TZPRAGUE = "Europe/Prague";

	static
	{
		TimeZone oldZone = TimeZone.getDefault();
		TimeZone tzPrague = TimeZone.getTimeZone(TZPRAGUE);

		try
		{
			TimeZone.setDefault(tzPrague);
		}
		finally
		{
			TimeZone.setDefault(oldZone);
		}
	}

	/**
	 * Test for a regression in PG date to/from java.sql.Date conversion
	 * identified in issue #199.
	 *<p>
	 * Checks that two months of consecutive dates in October/November 2018
	 * are converted correctly in the Europe/Prague timezone. The actual issue
	 * was by no means limited to that timezone, but this test reproducibly
	 * detects it.
	 *<p>
	 * This function is defined in the 'alias' language {@code java_tzset}, for
	 * which there is an entry in the default {@code pljava.policy} granting
	 * permission to adjust the time zone, which is temporarily done here.
	 */
	@Function(
		schema="javatest", language="java_tzset",
		requires="language java_tzset", provides="issue199"
	)
	public static void issue199() throws SQLException
	{
		TimeZone oldZone = TimeZone.getDefault();
		TimeZone tzPrague = TimeZone.getTimeZone(TZPRAGUE);
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		Statement s = c.createStatement();
		Savepoint svpt = c.setSavepoint();
		boolean ok = true;
		try
		{
			TimeZone.setDefault(tzPrague);
			s.execute("SET LOCAL TIME ZONE '" + TZPRAGUE + "'");

			ResultSet rs = s.executeQuery(
				"SELECT" +
				"  d, to_char(d, 'YYYY-MM-DD')" +
				" FROM" +
				"  generate_series(0, 60) AS s(i)," +
				"  LATERAL (SELECT date '2018-10-01' + i) AS t(d)");
			while ( rs.next() )
			{
				Date dd = rs.getDate(1);
				String ds = rs.getString(2);
				if ( ! ds.equals(dd.toString()) )
					ok = false;
			}
		}
		finally
		{
			TimeZone.setDefault(oldZone);
			c.rollback(svpt); // restore prior PG timezone
			s.close();
			c.close();
		}

		if ( ok )
			getAnonymousLogger().info("issue 199 test ok");
		else
			getAnonymousLogger().warning("issue 199 test not ok");
	}
}
