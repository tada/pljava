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
package org.postgresql.pljava.internal;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Function
{
	public static Object create(
		ResultSet procTup, String langName, String schemaName,
		boolean calledAsTrigger)
	throws SQLException
	{
		Matcher m = parse(procTup);
		return null;
	}

	private static Matcher parse(ResultSet procTup) throws SQLException
	{
		String spec = getAS(procTup);

		Matcher m = specForms.matcher(spec);
		if ( ! m.matches() )
		{
			System.err.println("huh? " + spec);
			return null;
		}

		say(m, "udtcls");
		say(m, "udtfun");
		say(m, "ret");
		say(m, "cls");
		say(m, "meth");
		say(m, "sig");
		System.err.println("----");
		return m;
	}

	private static void say(Matcher m, String grpname)
	{
		String s = m.group(grpname);
		if ( null != s )
			System.err.println(grpname+": "+s);
	}


	private static String getAS(ResultSet procTup) throws SQLException
	{
		String spec = procTup.getString("prosrc"); // has NOT NULL constraint

		/* COPIED COMMENT */
		/* Strip all whitespace except the first one if it occures after
		 * some alpha numeric characers and before some other alpha numeric
		 * characters. We insert a '=' when that happens since it delimits
		 * the return value from the method name.
		 */
		/* ANALYZED COMMENT */
		/* Original code skipped every isspace() character encountered while
		 * atStart or passedFirst was true. Initially true, atStart was reset
		 * by the first non-isspace character. Initially false, passedFirst
		 * was set by ANY encounter of a non-isspace non-isalnum, OR of any
		 * non-isspace following at least one isspace AFTER atStart was reset.
		 * The = was added if the non-isspace character satisfied isalpha.
		 */
		spec = stripEarlyWSinAS.matcher(spec).replaceFirst("$2=");
		spec = stripOtherWSinAS.matcher(spec).replaceAll("");
		return spec;
	}


	private static final Pattern stripEarlyWSinAS = Pattern.compile(
		"^(\\s*+)(\\p{Alnum}++)(\\s*+)(?=\\p{Alpha})"
	);
	private static final Pattern stripOtherWSinAS = Pattern.compile(
		"\\s*+"
	);

	private static final Pattern specForms = Pattern.compile(
		"(?i:udt\\[(?<udtcls>[^]]++)\\](?<udtfun>input|output|receive|send))" +
		"|(?!(?i:udt\\[))" +
		"(?:(?<ret>[^=]++)=)?+(?<cls>(?:[^.(]++\\.?)+)\\.(?<meth>[^.(]++)" +
		"(?:\\((?<sig>[^)]*+)\\))?+"
	);
}
