/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.polyglot;

import java.sql.SQLException;

import org.postgresql.pljava.PLJavaBasedLanguage.InlineBlocks;

import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.model.ProceduralLanguage;

/*
 * The imports above are the basics to make this a language handler.
 *
 * These imports below for JDBC / database access might not be so common in a
 * real language handler; you'd expect it to focus on compiling/executing some
 * client code, and the client code is where you'd expect to see what looks
 * more like application logic like this. But this is a handler for a very
 * simple language that only takes the given string and hands it to JDBC, so it
 * does look a bit like application logic.
 */
import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.Statement;
import org.postgresql.pljava.model.Portal;
import static org.postgresql.pljava.model.Portal.ALL;
import static org.postgresql.pljava.model.Portal.Direction.FORWARD;
import org.postgresql.pljava.model.SlotTester; // temporary development hack

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example of a procedural language with only DO blocks, built atop PL/Java.
 */
@SQLAction(requires = "pljavahandler language", install = {
"CREATE OR REPLACE FUNCTION javatest.dosql_validator(oid)" +
" RETURNS void" +
" LANGUAGE pljavahandler AS 'org.postgresql.pljava.example.polyglot.DoSQL'",

"COMMENT ON FUNCTION javatest.dosql_validator(oid) IS " +
"'Validator function for the dosql procedural language'",

"CREATE LANGUAGE dosql" +
" HANDLER sqlj.pljavaDispatchRoutine" +
" INLINE  sqlj.pljavaDispatchInline" +
" VALIDATOR javatest.dosql_validator",

"COMMENT ON LANGUAGE dosql IS " +
"'The dosql procedural language, which is implemented atop PL/Java, " +
"and supports inline code blocks that are just plain SQL, to be executed " +
"with any output discarded. COMMIT and ROLLBACK are recognized " +
"for transaction control.'",

"DO LANGUAGE dosql 'SELECT javatest.logmessage(''INFO'', ''DoSQL ok'')'"
}, remove = {
"DROP LANGUAGE dosql",
"DROP FUNCTION javatest.dosql_validator(oid)"
})
public class DoSQL implements InlineBlocks
{
	private final ProceduralLanguage pl;

	/**
	 * There must be a public constructor with a {@code ProceduralLanguage}
	 * parameter.
	 *<p>
	 * The parameter can be ignored, or used to determine the name, oid,
	 * accessibility, or other details of the declared PostgreSQL language
	 * your handler class has been instantiated for.
	 */
	public DoSQL(ProceduralLanguage pl)
	{
		this.pl = pl;
	}

	/**
	 * The sole method needed to implement inline code blocks.
	 *<p>
	 * This implementation will recognize {@code COMMIT} or {@code ROLLBACK}
	 * and call the dedicated JDBC {@code Connection} methods for those, or
	 * otherwise just pass the string to {@code Statement.execute} and consume
	 * and discard any results.
	 */
	@Override
	public void execute(String inlineSource, boolean atomic) throws SQLException
	{
		try (
			Connection c = getConnection("jdbc:default:connection");
			Statement s = c.createStatement()
		)
		{
			Matcher m = COMMIT_OR_ROLLBACK.matcher(inlineSource);
			if ( m.matches() )
			{
				if ( -1 != m.start(1) )
					c.commit();
				else
					c.rollback();
				return;
			}

			/*
			 * Not COMMIT or ROLLBACK, just hand it to execute() and consume
			 * any results.
			 */

			SlotTester st = c.unwrap(SlotTester.class);
			long count = 0;

			for (
				boolean isRS = s.execute(inlineSource);
				-1 != count;
				isRS = s.getMoreResults()
			)
			{
				if ( isRS )
				{
					try ( Portal p = st.unwrapAsPortal(s.getResultSet()) )
					{
						p.move(FORWARD, ALL);
					}
				}
				else
					count = s.getLargeUpdateCount();
			}
		}
	}

	static final Pattern COMMIT_OR_ROLLBACK =
		Pattern.compile("^\\s*+(?i:(commit)|(rollback))\\s*+$");
}
