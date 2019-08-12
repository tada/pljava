/*
 * Copyright (c) 2018- Tada AB and other contributors, as listed below.
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

import static java.util.logging.Logger.getAnonymousLogger;
import java.util.TimeZone;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * Some tests of pre-JSR 310 date/time/timestamp conversions.
 *<p>
 * Checks {@code java.sql.Date}, thanks to issue #199, and a number
 * of bugs of longer standing first reported in issue #200.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example.
 */
@SQLAction(implementor="postgresql_ge_90300", // needs LATERAL
	requires={"issue199", "issue200"}, install={
	"SELECT javatest.issue199()",
	"SELECT javatest.issue200()"
})
public class PreJSR310
{
	private static final String TZPRAGUE = "Europe/Prague";

	/**
	 * Test for a regression in PG date to/from java.sql.Date conversion
	 * identified in issue #199.
	 *<p>
	 * Checks that two months of consecutive dates in October/November 2018
	 * are converted correctly in the Europe/Prague timezone. The actual issue
	 * was by no means limited to that timezone, but this test reproducibly
	 * detects it.
	 */
	@Function(schema="javatest", provides="issue199")
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

	/**
	 * Test for java.sql.{Date,Time,Timestamp} conversion behaviors fixed after
	 * issue #200.
	 */
	@Function(schema="javatest", provides="issue200")
	public static void issue200() throws SQLException
	{
		TimeZone oldZone = TimeZone.getDefault();
		TimeZone tzPrague = TimeZone.getTimeZone(TZPRAGUE);
		TimeZone tzPlus1 = TimeZone.getTimeZone("GMT+1");
		TimeZone tzMinus1 = TimeZone.getTimeZone("GMT-1");
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		Statement s = c.createStatement();
		Savepoint svpt = c.setSavepoint();
		boolean ok = true;

		try
		{
			/*
			 * Test that conversion results for without-time-zone types are
			 * correct when the PostgreSQL and Java time zone settings do not
			 * match.
			 *
			 * The confounding subtlety of PostgreSQL's time zone input syntax:
			 * what Java calls GMT+1 can be expressed in PostgreSQL as:
			 * SET TIME ZONE 'GMT-1' (note the sign flip!) or 'FOO-1' (the
			 * letter string makes no difference), OR
			 * SET TIME ZONE '+1' (having NO letter string inverts the meaning
			 * of the sign!).
			 */
			try
			{
				TimeZone.setDefault(tzPlus1);
				s.execute("SET LOCAL TIME ZONE '-1'"); // so these zones differ

				ResultSet rs = s.executeQuery(
					"SELECT" +
					"  date '2017-08-21'," +
					"  time '18:25:29'," +
					"  timestamp '2017-08-21 18:25:29'");
				while ( rs.next() )
				{
					Date dd = rs.getDate(1);
					Time tt = rs.getTime(2);
					Timestamp ts = rs.getTimestamp(3);
					if ( ! Date.valueOf("2017-8-21").equals(dd) )
					{
						ok = false;
						getAnonymousLogger().warning("issue 200 date not ok");
					}
					if ( ! Time.valueOf("18:25:29").equals(tt) )
					{
						ok = false;
						getAnonymousLogger().warning("issue 200 time not ok");
					}
					if ( ! Timestamp.valueOf("2017-8-21 18:25:29").equals(ts) )
					{
						ok = false;
						getAnonymousLogger().warning(
							"issue 200 timestamp not ok");
					}
				}
			}
			finally
			{
				TimeZone.setDefault(oldZone);
				c.rollback(svpt); // restore prior PG timezone
			}

			/*
			 * Test that java.sql.Time objects are constructed with millisecond
			 * values in range.
			 */
			try
			{
				TimeZone.setDefault(tzPlus1);
				s.execute("SET LOCAL TIME ZONE '+1'"); // same zone this time

				String qryTimes =
					"SELECT" +
					"  time '00:05:00'," +
					"  time '23:55:00'," +
					"  time with time zone '00:05:00+01'," +
					"  time with time zone '23:55:00+01'";

				ResultSet rs = s.executeQuery(qryTimes);
				rs.next();
				Time ta = rs.getTime(1);
				Time tb = rs.getTime(2);
				Time tc = rs.getTime(3);
				Time td = rs.getTime(4);

				TimeZone.setDefault(tzMinus1);
				s.execute("SET LOCAL TIME ZONE '-1'"); // same zone this time

				rs = s.executeQuery(qryTimes);
				rs.next();
				Time te = rs.getTime(1);
				Time tf = rs.getTime(2);
				Time tg = rs.getTime(3);
				Time th = rs.getTime(4);

				for ( Time t : new Time[] { ta, tb, tc, td, te, tf, tg, th } )
				{
					long msecs = t.getTime();
					if ( 0 <= msecs  &&  msecs < 86400000 )
						continue;
					ok = false;
					getAnonymousLogger().warning(
							"issue 200 time bounds not ok");
				}

				/*
				 * Sneak in the test for the round/floor mod ambiguity
				 * (as it uses the same time zone setup).
				 */

				PreparedStatement ps = c.prepareStatement(
					"SELECT CAST(CAST(? AS TIME WITH TIME ZONE) AS TEXT)");
				ps.setTime(1, Time.valueOf("23:00:00"));
				rs = ps.executeQuery();
				rs.next();
				if ( ! "23:00:00-01".equals(rs.getString(1)) )
				{
					ok = false;
					getAnonymousLogger().warning(
							"issue 200 timetz floor-mod not ok");
				}
			}
			finally
			{
				TimeZone.setDefault(oldZone);
				c.rollback(svpt); // restore prior PG timezone
			}

			/*
			 * Test for the timestamp without time zone bogosity near the start
			 * and end of summer time.
			 */
			try
			{
				TimeZone.setDefault(tzPrague);
				s.execute("SET LOCAL TIME ZONE '" + TZPRAGUE + "'");

				String qryTimes =
					"VALUES" +
					"  ('2018-03-25 00:00:00'::timestamp)," +
					"  ('2018-03-25 01:00:00')," +
					"  ('2018-03-25 02:00:00')," +
					"  ('2018-03-25 03:00:00')," +
					"  ('2018-10-28 00:00:00')," +
					"  ('2018-10-28 01:00:00')," +
					"  ('2018-10-28 02:00:00')";

				String[] expect = new String[] {
					"2018-03-25 00:00:00.0",
					"2018-03-25 01:00:00.0",
					"2018-03-25 03:00:00.0", // mind the gap
					"2018-03-25 03:00:00.0",
					"2018-10-28 00:00:00.0",
					"2018-10-28 01:00:00.0",
					"2018-10-28 02:00:00.0",
				};

				ResultSet rs = s.executeQuery(qryTimes);
				for ( String e : expect )
				{
					rs.next();
					if ( ! e.equals(rs.getTimestamp(1).toString()) )
					{
						ok = false;
						getAnonymousLogger().warning(
							"issue 200 summer transitions not ok");
					}
				}
			}
			finally
			{
				TimeZone.setDefault(oldZone);
				c.rollback(svpt); // restore prior PG timezone
			}
		}
		finally
		{
			s.close();
			c.releaseSavepoint(svpt);
			c.close();
		}

		if ( ok )
			getAnonymousLogger().info("issue 200 tests ok");
	}
}
