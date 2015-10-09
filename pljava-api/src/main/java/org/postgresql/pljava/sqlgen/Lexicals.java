/*
 * Copyright (c) 2015- Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.sqlgen;

import java.util.regex.Pattern;

/**
 * A few useful SQL lexical definitions supplied as {@link Pattern} objects.
 *
 * The idea is not to go overboard and reimplement an SQL lexer, but to
 * capture in one place the rules for those bits of SQL snippets that are
 * likely to be human-supplied in annotations and need to be checked for
 * correctness when emitted into deployment descriptors. For starters, that
 * means regular (not quoted, not Unicode escaped) identifiers.
 *
 * Supplied in the API module so they are available to {@code javac} to
 * compile and generate DDR when the rest of PL/Java is not necessarily
 * present. Of course backend code such as {@code SQLDeploymentDescriptor}
 * can also refer to these.
 */
public interface Lexicals {

	/** Allowed as the first character of a regular identifier by ISO.
	 */
	Pattern ISO_REGULAR_IDENTIFIER_START = Pattern.compile(
		"[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}]"
	);

	/** Allowed as any non-first character of a regular identifier by ISO.
	 */
	Pattern ISO_REGULAR_IDENTIFIER_PART = Pattern.compile(String.format(
		"[\\xb7\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\p{Cf}%1$s]",
		ISO_REGULAR_IDENTIFIER_START.pattern()
	));

	/** A complete regular identifier as allowed by ISO.
	 */
	Pattern ISO_REGULAR_IDENTIFIER = Pattern.compile(String.format(
		"%1$s%2$s*",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A complete ISO regular identifier in a single capturing group.
	 */
	Pattern ISO_REGULAR_IDENTIFIER_CAPTURING = Pattern.compile(String.format(
		"(%1$s)", ISO_REGULAR_IDENTIFIER.pattern()
	));

	/** Allowed as the first character of a regular identifier by PostgreSQL
	 * (PG 7.4 -).
	 */
	Pattern PG_REGULAR_IDENTIFIER_START = Pattern.compile(
		"[A-Za-z\\P{ASCII}_]" // hasn't seen a change since PG 7.4
	);

	/** Allowed as any non-first character of a regular identifier by PostgreSQL
	 * (PG 7.4 -).
	 */
	Pattern PG_REGULAR_IDENTIFIER_PART = Pattern.compile(String.format(
		"[0-9$%1$s]", PG_REGULAR_IDENTIFIER_START.pattern()
	));

	/** A complete regular identifier as allowed by PostgreSQL (PG 7.4 -).
	 */
	Pattern PG_REGULAR_IDENTIFIER = Pattern.compile(String.format(
		"%1$s%2$s*",
		PG_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A complete PostgreSQL regular identifier in a single capturing group.
	 */
	Pattern PG_REGULAR_IDENTIFIER_CAPTURING = Pattern.compile(String.format(
		"(%1$s)", PG_REGULAR_IDENTIFIER.pattern()
	));

	/** A regular identifier that satisfies both ISO and PostgreSQL rules.
	 */
	Pattern ISO_AND_PG_REGULAR_IDENTIFIER = Pattern.compile(String.format(
		"(?:(?=%1$s)%2$s)(?:(?=%3$s)%4$s)*",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A regular identifier that satisfies both ISO and PostgreSQL rules,
	 * in a single capturing group.
	 */
	Pattern ISO_AND_PG_REGULAR_IDENTIFIER_CAPTURING = Pattern.compile(
		String.format( "(%1$s)", ISO_AND_PG_REGULAR_IDENTIFIER.pattern())
	);

	/** An identifier by ISO SQL, PostgreSQL, <em>and</em> Java (not SQL at all)
	 * rules. (Not called {@code REGULAR} because Java allows no other form of
	 * identifier.) This restrictive form is the safest for identifiers being
	 * generated into a deployment descriptor file that an old version of
	 * PL/Java might load, because through 1.4.3 PL/Java used the Java
	 * identifier rules to recognize identifiers in deployment descriptors.
	 */
	Pattern ISO_PG_JAVA_IDENTIFIER = Pattern.compile(String.format(
		"(?:(?=%1$s)(?=\\p{%5$sStart})%2$s)(?:(?=%3$s)(?=\\p{%5$sPart})%4$s)*",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern(),
		"javaJavaIdentifier"
	));
}
