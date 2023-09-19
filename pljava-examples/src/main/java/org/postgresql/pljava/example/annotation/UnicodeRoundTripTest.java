/*
 * Copyright (c) 2015-2023 Tada AB and other contributors, as listed below.
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

import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.Function;

/**
 * Test that strings containing characters from all Unicode planes
 * are passed between PG and Java without alteration (issue 21).
 * <p>
 * This function takes a string and an array of ints constructed in PG, such
 * that PG believes the codepoints in the string to correspond exactly with the
 * ints in the array. The function compares the two, generates a new array from
 * the codepoints Java sees in the string and a new Java string from the
 * original array, and returns a tuple (matched, cparray, s) where
 * {@code matched} indicates whether the original array and string matched
 * as seen by Java, and {@code cparray} and {@code s} are the new array and
 * string generated in Java.
 * <p>
 * The supplied test query generates all Unicode code points 1k at a time,
 * calls this function on each (1k array, 1k string) pair, and counts a failure
 * if {@code matched} is false or the original and returned arrays or strings
 * do not match as seen in SQL.
 * <p>
 * This example sets an {@code implementor} tag based on a PostgreSQL condition,
 * as further explained in the {@link ConditionalDDR} example.
 */
@SQLAction(provides="postgresql_unicodetest", install=
	"SELECT CASE" +
	" WHEN 'UTF8' = current_setting('server_encoding')" +
	" THEN set_config('pljava.implementors', 'postgresql_unicodetest,' ||" +
	" current_setting('pljava.implementors'), true) " +
	"END"
)
@SQLAction(requires="unicodetest fn",
implementor="postgresql_unicodetest",
install=
"   WITH " +
"    usable_codepoints ( cp ) AS ( " +
"     SELECT generate_series(1,x'd7ff'::int) " +
"     UNION ALL " +
"     SELECT generate_series(x'e000'::int,x'10ffff'::int) " +
"    ), " +
"    test_inputs ( groupnum, cparray, s ) AS ( " +
"     SELECT " +
"       cp / 1024 AS groupnum, " +
"       array_agg(cp ORDER BY cp), string_agg(chr(cp), '' ORDER BY cp) " +
"     FROM usable_codepoints " +
"     GROUP BY groupnum " +
"    ), " +
"    test_outputs AS ( " +
"     SELECT groupnum, cparray, s, unicodetest(s, cparray) AS roundtrip " +
"     FROM test_inputs " +
"    ), " +
"    test_failures AS ( " +
"     SELECT * " +
"     FROM test_outputs " +
"     WHERE " +
"      cparray != (roundtrip).cparray OR s != (roundtrip).s " +
"      OR NOT (roundtrip).matched " +
"    ), " +
"    test_summary ( n_failing_groups, first_failing_group ) AS ( " +
"     SELECT count(*), min(groupnum) FROM test_failures " +
"    ) " +
"   SELECT " +
"    CASE WHEN n_failing_groups > 0 THEN " +
"     javatest.logmessage('WARNING', n_failing_groups || " +
"      ' 1k codepoint ranges had mismatches, first is block starting 0x' || " +
"      to_hex(1024 * first_failing_group)) " +
"    ELSE " +
"     javatest.logmessage('INFO', " +
"        'all Unicode codepoint ranges roundtripped successfully.') " +
"    END " +
"    FROM test_summary"
)
public class UnicodeRoundTripTest {
	/**
	 * This function takes a string and an array of ints constructed in PG,
	 * such that PG believes the codepoints in the string to correspond exactly
	 * with the ints in the array. The function compares the two, generates a
	 * new array from the codepoints Java sees in the string and a new Java
	 * string from the original array, and returns a tuple (matched, cparray,
	 * s) where {@code matched} indicates whether the original array and string
	 * matched as seen by Java, and {@code cparray} and {@code s} are the new
	 * array and string generated in Java.
	 *
	 * @param s A string, whose codepoints should match the entries of
	 *        {@code ints}
	 * @param ints Array of ints that should match the codepoints in {@code s}
	 * @param rs OUT (matched, cparray, s) as described above
	 * @return true to indicate the OUT tuple is not null
	 */
	@Function(out={"matched boolean", "cparray integer[]", "s text"},
		provides="unicodetest fn")
	public static boolean unicodetest(String s, int[] ints, ResultSet rs)
	throws SQLException {
		boolean ok = true;

		int cpc = s.codePointCount(0, s.length());
		Integer[] myInts = new Integer[cpc];
		int ci = 0;
		for ( int cpi = 0; cpi < cpc; cpi++ ) {
			myInts[cpi] = s.codePointAt(ci);
			ci = s.offsetByCodePoints(ci, 1);
		}

		String myS = new String(ints, 0, ints.length);

		if ( ints.length != myInts.length )
			ok = false;
		else
			for ( int i = 0; i < ints.length; ++ i )
	if ( ints[i] != myInts[i] )
		ok = false;

		rs.updateBoolean("matched", ok);
		rs.updateObject("cparray", myInts);
		rs.updateString("s", myS);
		return true;
	}
}
