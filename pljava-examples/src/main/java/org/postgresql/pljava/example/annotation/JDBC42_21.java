/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.example.annotation.ConditionalDDR; // for javadoc

/**
 * Exercise new mappings between date/time types and java.time classes
 * (JDBC 4.2 change 21).
 *<p>
 * Defines a method {@link #javaSpecificationGE javaSpecificationGE} that may be
 * of use for other examples.
 *<p>
 * Relies on PostgreSQL-version-specific implementor tags set up in the
 * {@link ConditionalDDR} example.
 */
@SQLAction(
	implementor="postgresql_ge_90300",requires="TypeRoundTripper.roundTrip",
	install={
	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'java.time.LocalDate passes')" +
	"  ELSE javatest.logmessage('WARNING', 'java.time.LocalDate fails')" +
	"  END" +
	" FROM" +
	"  (VALUES" +
	"   (date '2017-08-21')," +
	"   (date '1970-03-07')," +
	"   (date '1919-05-29')" +
	"  ) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.LocalDate')" +
	"  AS r(roundtripped date)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'java.time.LocalTime passes')" +
	"  ELSE javatest.logmessage('WARNING', 'java.time.LocalTime fails')" +
	"  END" +
	" FROM" +
	"  (SELECT current_time::time) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.LocalTime')" +
	"  AS r(roundtripped time)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'java.time.OffsetTime passes')" +
	"  ELSE javatest.logmessage('WARNING', 'java.time.OffsetTime fails')" +
	"  END" +
	" FROM" +
	"  (SELECT current_time::timetz) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.OffsetTime')" +
	"  AS r(roundtripped timetz)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'java.time.LocalDateTime passes')" +
	"  ELSE javatest.logmessage('WARNING','java.time.LocalDateTime fails')"+
	"  END" +
	" FROM" +
	"  (SELECT 'on' = current_setting('integer_datetimes')) AS ck(idt)," +
	"  LATERAL (" +
	"   SELECT" +
	"    value" +
	"   FROM" +
	"	 (VALUES" +
	"	  (true, timestamp '2017-08-21 18:25:29.900005')," +
	"	  (true, timestamp '1970-03-07 17:37:49.300009')," +
	"	  (true, timestamp '1919-05-29 13:08:33.600001')," +
	"	  (idt,  timestamp  'infinity')," +
	"	  (idt,  timestamp '-infinity')" +
	"	 ) AS vs(cond, value)" +
	"   WHERE cond" +
	"  ) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.LocalDateTime')" +
	"  AS r(roundtripped timestamp)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'java.time.OffsetDateTime passes')"+
	"  ELSE javatest.logmessage(" +
	"         'WARNING','java.time.OffsetDateTime fails')"+
	"  END" +
	" FROM" +
	"  (SELECT 'on' = current_setting('integer_datetimes')) AS ck(idt)," +
	"  LATERAL (" +
	"   SELECT" +
	"    value" +
	"   FROM" +
	"	 (VALUES" +
	"	  (true, timestamptz '2017-08-21 18:25:29.900005Z')," +
	"	  (true, timestamptz '1970-03-07 17:37:49.300009Z')," +
	"	  (true, timestamptz '1919-05-29 13:08:33.600001Z')," +
	"	  (idt,  timestamptz  'infinity')," +
	"	  (idt,  timestamptz '-infinity')" +
	"	 ) AS vs(cond, value)" +
	"   WHERE cond" +
	"  ) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.OffsetDateTime')" +
	"  AS r(roundtripped timestamptz)",

	" SELECT" +
	"  CASE WHEN every(orig = roundtripped)" +
	"  THEN javatest.logmessage('INFO', 'OffsetTime as stmt param passes')"+
	"  ELSE javatest.logmessage(" +
	"         'WARNING','java.time.OffsetTime as stmt param fails')"+
	"  END" +
	" FROM" +
	"  (SELECT current_time::timetz) AS p(orig)," +
	"  javatest.roundtrip(p, 'java.time.OffsetTime', true)" +
	"  AS r(roundtripped timetz)"
})
public class JDBC42_21
{
	/**
	 * Return true if running under a Java specification version at least as
	 * recent as the argument ('1.6', '1.7', '1.8', '9', '10', '11', ...).
	 */
	@Function(schema="javatest", provides="javaSpecificationGE")
	public static boolean javaSpecificationGE(String want)
	{
		String got = System.getProperty("java.specification.version");
		if ( want.startsWith("1.") )
			want = want.substring(2);
		if ( got.startsWith("1.") )
			got = got.substring(2);
		return 0 <= Integer.valueOf(got).compareTo(Integer.valueOf(want));
	}
}
