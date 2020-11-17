/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
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
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example, and also sets its own.
 */
@SQLAction(provides="postgresql_unicodetest",
	implementor="postgresql_ge_90000", install=
	"SELECT CASE" +
	" WHEN 'UTF8' = current_setting('server_encoding')" +
	" THEN set_config('pljava.implementors', 'postgresql_unicodetest,' ||" +
	" current_setting('pljava.implementors'), true) " +
	"END"
)
@SQLAction(requires="unicodetest fn",
implementor="postgresql_unicodetest",
install=
"   with " +
"    usable_codepoints ( cp ) as ( " +
"     select generate_series(1,x'd7ff'::int) " +
"     union all " +
"     select generate_series(x'e000'::int,x'10ffff'::int) " +
"    ), " +
"    test_inputs ( groupnum, cparray, s ) as ( " +
"     select " +
"       cp / 1024 as groupnum, " +
"       array_agg(cp order by cp), string_agg(chr(cp), '' order by cp) " +
"     from usable_codepoints " +
"     group by groupnum " +
"    ), " +
"    test_outputs as ( " +
"     select groupnum, cparray, s, unicodetest(s, cparray) as roundtrip " +
"     from test_inputs " +
"    ), " +
"    test_failures as ( " +
"     select * " +
"     from test_outputs " +
"     where " +
"      cparray != (roundtrip).cparray or s != (roundtrip).s " +
"      or not (roundtrip).matched " +
"    ), " +
"    test_summary ( n_failing_groups, first_failing_group ) as ( " +
"     select count(*), min(groupnum) from test_failures " +
"    ) " +
"   select " +
"    case when n_failing_groups > 0 then " +
"     javatest.logmessage('WARNING', n_failing_groups || " +
"      ' 1k codepoint ranges had mismatches, first is block starting 0x' || " +
"      to_hex(1024 * first_failing_group)) " +
"    else " +
"     javatest.logmessage('INFO', " +
"        'all Unicode codepoint ranges roundtripped successfully.') " +
"    end " +
"    from test_summary"
)
@SQLAction(
	install=
		"CREATE TYPE unicodetestrow AS " +
		"(matched boolean, cparray integer[], s text)",
	remove="DROP TYPE unicodetestrow",
	provides="unicodetestrow type"
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
	@Function(type="unicodetestrow",
		requires="unicodetestrow type", provides="unicodetest fn")
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
