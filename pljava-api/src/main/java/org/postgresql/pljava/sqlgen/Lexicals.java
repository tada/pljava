/*
 * Copyright (c) 2015-2019 Tada AB and other contributors, as listed below.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic.Kind;

/**
 * A few useful SQL lexical definitions supplied as {@link Pattern} objects.
 *
 * The idea is not to go overboard and reimplement an SQL lexer, but to
 * capture in one place the rules for those bits of SQL snippets that are
 * likely to be human-supplied in annotations and need to be checked for
 * correctness when emitted into deployment descriptors. Identifiers, for a
 * start.
 *
 * Supplied in the API module so they are available to {@code javac} to
 * compile and generate DDR when the rest of PL/Java is not necessarily
 * present. Of course backend code such as {@code SQLDeploymentDescriptor}
 * can also refer to these.
 */
public abstract class Lexicals
{
	/** Allowed as the first character of a regular identifier by ISO.
	 */
	public static final Pattern ISO_REGULAR_IDENTIFIER_START = Pattern.compile(
		"[\\p{Lu}\\p{Ll}\\p{Lt}\\p{Lm}\\p{Lo}\\p{Nl}]"
	);

	/** Allowed as any non-first character of a regular identifier by ISO.
	 */
	public static final Pattern ISO_REGULAR_IDENTIFIER_PART =
	Pattern.compile(String.format(
		"[\\xb7\\p{Mn}\\p{Mc}\\p{Nd}\\p{Pc}\\p{Cf}%1$s]",
		ISO_REGULAR_IDENTIFIER_START.pattern()
	));

	/** A complete regular identifier as allowed by ISO.
	 */
	public static final Pattern ISO_REGULAR_IDENTIFIER =
	Pattern.compile(String.format(
		"%1$s%2$s{0,127}+",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A complete ISO regular identifier in a single capturing group.
	 */
	public static final Pattern ISO_REGULAR_IDENTIFIER_CAPTURING =
	Pattern.compile(String.format(
		"(%1$s)", ISO_REGULAR_IDENTIFIER.pattern()
	));

	/** A complete delimited identifier as allowed by ISO. As it happens, this
	 * is also the form PostgreSQL uses for elements of a LIST_QUOTE-typed GUC.
	 */
	public static final Pattern ISO_DELIMITED_IDENTIFIER = Pattern.compile(
		"\"(?:[^\"]|\"\"){1,128}+\""
	);

	/** An ISO delimited identifier with a single capturing group that captures
	 * the content (which still needs to have "" replaced with " throughout).
	 * The capturing group is named {@code xd}.
	 */
	public static final Pattern ISO_DELIMITED_IDENTIFIER_CAPTURING =
	Pattern.compile(String.format(
		"\"(?<xd>(?:[^\"]|\"\"){1,128}+)\""
	));

	/** The escape-specifier part of a Unicode delimited identifier or string.
	 * The escape character itself is in the capturing group named {@code uec}.
	 * The group can be absent, in which case \ should be used as the uec.
	 */
	public static final Pattern ISO_UNICODE_ESCAPE_SPECIFIER =
	Pattern.compile(
		"(?:\\p{IsWhite_Space}*+[Uu][Ee][Ss][Cc][Aa][Pp][Ee]"+
		"\\p{IsWhite_Space}*+'(?<uec>[^0-9A-Fa-f+'\"\\p{IsWhite_Space}])')?+"
	);

	/** A Unicode delimited identifier. The body is in capturing group
	 * {@code xui} and the escape character in group {@code uec}. The body
	 * still needs to have "" replaced with ", and {@code Unicode escape value}s
	 * decoded and replaced, and then it has to be verified to be no longer
	 * than 128 codepoints.
	 */
	public static final Pattern ISO_UNICODE_IDENTIFIER =
	Pattern.compile(String.format(
		"[Uu]&\"(?<xui>(?:[^\"]|\"\")++)\"%1$s",
		ISO_UNICODE_ESCAPE_SPECIFIER.pattern()
	));

	/** A compilable pattern to match a {@code Unicode escape value}.
	 * A match should have one of three named capturing groups. If {@code cev},
	 * substitute the {@code uec} itself. If {@code u4d} or {@code u6d},
	 * substitute the codepoint represented by the hex digits. A match with none
	 * of those capturing groups indicates an ill-formed string.
	 *<p>
	 * Maka a Pattern from this by supplying the right {@code uec}, so:
	 * {@code Pattern.compile(String.format(ISO_UNICODE_REPLACER,
	 *   Pattern.quote(uec)));}
	 */
	public static final String ISO_UNICODE_REPLACER =
		"%1$s(?:(?<cev>%1$s)|(?<u4d>[0-9A-Fa-f]{4})|\\+(?<u6d>[0-9A-Fa-f]{6}))";

	/** Allowed as the first character of a regular identifier by PostgreSQL
	 * (PG 7.4 -).
	 */
	public static final Pattern PG_REGULAR_IDENTIFIER_START = Pattern.compile(
		"[A-Za-z\\P{ASCII}_]" // hasn't seen a change since PG 7.4
	);

	/** Allowed as any non-first character of a regular identifier by PostgreSQL
	 * (PG 7.4 -).
	 */
	public static final Pattern PG_REGULAR_IDENTIFIER_PART =
	Pattern.compile(String.format(
		"[0-9$%1$s]", PG_REGULAR_IDENTIFIER_START.pattern()
	));

	/** A complete regular identifier as allowed by PostgreSQL (PG 7.4 -).
	 */
	public static final Pattern PG_REGULAR_IDENTIFIER =
	Pattern.compile(String.format(
		"%1$s%2$s*+",
		PG_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A complete PostgreSQL regular identifier in a single capturing group.
	 */
	public static final Pattern PG_REGULAR_IDENTIFIER_CAPTURING =
	Pattern.compile(String.format(
		"(%1$s)", PG_REGULAR_IDENTIFIER.pattern()
	));

	/** A regular identifier that satisfies both ISO and PostgreSQL rules.
	 */
	public static final Pattern ISO_AND_PG_REGULAR_IDENTIFIER =
	Pattern.compile(String.format(
		"(?:(?=%1$s)%2$s)(?:(?=%3$s)%4$s)*+",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	/** A regular identifier that satisfies both ISO and PostgreSQL rules,
	 * in a single capturing group named {@code i}.
	 */
	public static final Pattern ISO_AND_PG_REGULAR_IDENTIFIER_CAPTURING =
	Pattern.compile(
		String.format( "(?<i>%1$s)", ISO_AND_PG_REGULAR_IDENTIFIER.pattern())
	);

	/** Pattern that matches any identifier valid by both ISO and PG rules,
	 * with the presence of named capturing groups indicating which kind it is:
	 * {@code i} for a regular identifier, {@code xd} for a delimited identifier
	 * (still needing "" replaced with "), or {@code xui} (with or without an
	 * explicit {@code uec} for a Unicode identifier (still needing "" to " and
	 * decoding of {@code Unicode escape value}s).
	 */
	public static final Pattern ISO_AND_PG_IDENTIFIER_CAPTURING =
	Pattern.compile(String.format(
		"%1$s|(?:%2$s)|(?:%3$s)",
		ISO_AND_PG_REGULAR_IDENTIFIER_CAPTURING.pattern(),
		ISO_DELIMITED_IDENTIFIER_CAPTURING.pattern(),
		ISO_UNICODE_IDENTIFIER.pattern()
	));

	/** An identifier by ISO SQL, PostgreSQL, <em>and</em> Java (not SQL at all)
	 * rules. (Not called {@code REGULAR} because Java allows no other form of
	 * identifier.) This restrictive form is the safest for identifiers being
	 * generated into a deployment descriptor file that an old version of
	 * PL/Java might load, because through 1.4.3 PL/Java used the Java
	 * identifier rules to recognize identifiers in deployment descriptors.
	 */
	public static final Pattern ISO_PG_JAVA_IDENTIFIER =
	Pattern.compile(String.format(
		"(?:(?=%1$s)(?=\\p{%5$sStart})%2$s)(?:(?=%3$s)(?=\\p{%5$sPart})%4$s)*+",
		ISO_REGULAR_IDENTIFIER_START.pattern(),
		PG_REGULAR_IDENTIFIER_START.pattern(),
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern(),
		"javaJavaIdentifier"
	));

	/**
	 * Return an Identifier, given a {@code Matcher} that has matched an
	 * ISO_AND_PG_IDENTIFIER_CAPTURING. Will determine from the matching named
	 * groups which type of identifier it was, process the matched sequence
	 * appropriately, and return it.
	 * @param m A {@code Matcher} known to have matched an identifier.
	 * @return Identifier made from the recovered string.
	 */
	public static Identifier identifierFrom(Matcher m)
	{
		String s = m.group("i");
		if ( null != s )
			return Identifier.from(s, false);
		s = m.group("xd");
		if ( null != s )
			return Identifier.from(s.replace("\"\"", "\""), true);
		s = m.group("xui");
		if ( null == s )
			return null; // XXX?
		s = s.replace("\"\"", "\"");
		String uec = m.group("uec");
		if ( null == uec )
			uec = "\\";
		int uecp = uec.codePointAt(0);
		Matcher replacer =
			Pattern.compile(
				String.format(ISO_UNICODE_REPLACER, Pattern.quote(uec)))
				.matcher(s);
		StringBuffer sb = new StringBuffer();
		while ( replacer.find() )
		{
			replacer.appendReplacement(sb, "");
			int cp;
			String uev = replacer.group("u4d");
			if ( null == uev )
				uev = replacer.group("u6d");
			if ( null != uev )
				cp = Integer.parseInt(uev, 16);
			else
				cp = uecp;
			// XXX check validity
			sb.appendCodePoint(cp);
		}
		return Identifier.from(replacer.appendTail(sb).toString(), true);
	}

	/**
	 * Class representing a SQL identifier. These have wild and wooly behavior
	 * depending on whether they were represented in the source in quoted form
	 * or not. Quoted ones are case-sensitive,
	 * and {@link #equals(Object) equals} will only recognize exact matches.
	 * Non-quoted ones match case-insensitively; just to make this interesting,
	 * ISO SQL has one set of case-folding rules, while PostgreSQL has another.
	 * Also, a non-quoted identifier can match a quoted one, if the quoted one's
	 * exact spelling matches the non-quoted one's case-folded form.
	 *<p>
	 * For even more fun, the PostgreSQL rules depend on the server encoding.
	 * For any multibyte encoding, <em>only</em> the 26 ASCII uppercase letters
	 * are folded to lower, leaving all other characters alone. In single-byte
	 * encodings, more letters can be touched. But this code has to run in a
	 * javac annotation processor without knowledge of any particular database's
	 * server encoding. The recommended encoding, UTF-8, is multibyte, so the
	 * PostgreSQL rule will be taken to be: only the 26 ASCII letters, always.
	 */
	public static class Identifier
	{
		protected final String m_nonFolded;

		/**
		 * Whether this Identifier case-folds.
		 * @return true if this Identifier was non-quoted in the source,
		 * false if it was quoted.
		 */
		public boolean folds()
		{
			return false;
		}

		/**
		 * This Identifier's original spelling.
		 * @return The spelling as seen in the source, with no case folding.
		 */
		public String nonFolded()
		{
			return m_nonFolded;
		}

		/**
		 * This Identifier as PostgreSQL would case-fold it (or the same as
		 * nonFolded if this was quoted and does not fold).
		 * @return The spelling with ASCII letters (only) folded to lowercase,
		 * if this Identifier folds.
		 */
		public String pgFolded()
		{
			return m_nonFolded;
		}

		/**
		 * This Identifier as ISO SQL would case-fold it (or the same as
		 * nonFolded if this was quoted and does not fold).
		 * @return The spelling with lowercase and titlecase letters folded to
		 * (possibly length-changing) uppercase equivalents,
		 * if this Identifier folds.
		 */
		public String isoFolded()
		{
			return m_nonFolded;
		}

		/**
		 * Create an Identifier given its original, non-folded spelling,
		 * and whether it represents a quoted identifier.
		 * @param s The exact, internal, non-folded spelling of the identifier
		 * (unwrapped from any quoting in its external form).
		 * @param quoted Pass {@code true} if this was parsed from any quoted
		 * external form, false if non-quoted.
		 * @return A corresponding Identifier
		 * @throws IllegalArgumentException if {@code quoted} is {@code false}
		 * but {@code s} cannot be a non-quoted identifier, or {@code s} is
		 * empty or longer than the ISO SQL maximum 128 codepoints.
		 */
		public static Identifier from(String s, boolean quoted)
		{
			boolean foldable =
				ISO_AND_PG_REGULAR_IDENTIFIER.matcher(s).matches();
			if ( ! quoted )
			{
				if ( ! foldable )
					throw new IllegalArgumentException(String.format(
						"impossible for \"%1$s\" to be a non-quoted identifier",
						s));
				return new Folding(s);
			}
			if ( foldable )
				return new Foldable(s);
			return new Identifier(s);
		}

		@Override
		public String toString()
		{
			return m_nonFolded;
		}

		/**
		 * For a quoted identifier that could not match any non-quoted one,
		 * the hash code of its non-folded spelling is good enough. In other
		 * cases, the code must be derived more carefully.
		 */
		@Override
		public int hashCode()
		{
			return m_nonFolded.hashCode();
		}

		@Override
		public boolean equals(Object other)
		{
			return equals(other, null);
		}

		/**
		 * For use in an annotation processor, a version of {@code equals} that
		 * can take a {@link Messager} and use it to emit warnings. It will
		 * emit a warning whenever it compares two Identifiers that are equal
		 * by one or the other of PostgreSQL's or ISO SQL's rules but not both.
		 * @param other Object to compare to
		 * @param msgr a Messager to use for warnings; if {@code null}, no
		 * warnings will be generated.
		 * @return true if two quoted Identifiers match exactly, or two
		 * non-quoted ones match in either the PostgreSQL or ISO SQL folded
		 * form, or a quoted one exactly matches either folded form of a
		 * non-quoted one.
		 */
		public boolean equals(Object other, Messager msgr)
		{
			if ( ! (other instanceof Identifier) )
				return false;
			Identifier oi = (Identifier)other;
			if ( oi.folds() )
				return oi.equals(this);
			return m_nonFolded.equals(oi.nonFolded());
		}

		protected Identifier(String nonFolded)
		{
			m_nonFolded = nonFolded;
			int cpc = nonFolded.codePointCount(0, nonFolded.length());
			if ( 0 == cpc || cpc > 128 )
				throw new IllegalArgumentException(String.format(
					"identifier empty or longer than 128 codepoints: \"%s\"",
					nonFolded));
		}

		/**
		 * Class representing an Identifier that was quoted, therefore does
		 * not case-fold, but satisfies {@code ISO_AND_PG_REGULAR_IDENTIFIER}
		 * and so could conceivably be matched by a non-quoted identifier.
		 */
		static class Foldable extends Identifier
		{
			private final int m_hashCode;

			protected Foldable(String nonFolded)
			{
				this(nonFolded, isoFold(nonFolded));
			}

			protected Foldable(String nonFolded, String isoFolded)
			{
				super(nonFolded);
				m_hashCode = isoFolded.hashCode();
			}

			/**
			 * For any identifier that case-folds, or even could be matched by
			 * another identifier that case-folds, the hash code is tricky.
			 * Hash codes are required to be equal for any instances that are
			 * equal (but not required to be different for instances that are
			 * unequal). In this case, the hash codes need to be equal whenever
			 * the PostgreSQL <em>or</em> ISO SQL folded forms match.
			 *<p>
			 * This hash code will be derived from the ISO-folded spelling of
			 * the identifier. As long as the PostgreSQL rules only affect the
			 * 26 ASCII letters, all of which are also folded (albeit in the
			 * other direction) by the ISO rules, hash codes will also match for
			 * identifiers equal under PostgreSQL rules.
			 */
			@Override
			public int hashCode()
			{
				return m_hashCode;
			}

			/**
			 * The characters that ISO SQL rules will fold: anything that is
			 * lowercase or titlecase.
			 */
			private static final Pattern s_isoFolded =
				Pattern.compile("[\\p{javaLowerCase}\\p{javaTitleCase}]");

			/**
			 * Case-fold a string by the ISO SQL rules, where any lowercase or
			 * titlecase character gets replaced by its uppercase form (the
			 * generalized, possibly length-changing one, requiring
			 * {@link String#toUpperCase} and not
			 * {@link Character#toUpperCase}.
			 * @param s The non-folded value.
			 * @return The folded value.
			 */
			protected static String isoFold(String s)
			{
				Matcher m = s_isoFolded.matcher(s);
				StringBuffer sb = new StringBuffer();
				while ( m.find() )
					m.appendReplacement(sb, m.group().toUpperCase());
				return m.appendTail(sb).toString();
			}
		}

		/**
		 * Class representing an Identifier that was not quoted, and therefore
		 * has case-folded forms.
		 */
		static class Folding extends Foldable
		{
			private final String m_pgFolded;
			private final String m_isoFolded;

			protected Folding(String nonFolded)
			{
				this(nonFolded, isoFold(nonFolded));
			}

			protected Folding(String nonFolded, String isoFolded)
			{
				super(nonFolded, isoFolded);
				m_pgFolded = pgFold(nonFolded);
				m_isoFolded = isoFolded;
			}

			@Override
			public String pgFolded()
			{
				return m_pgFolded;
			}

			@Override
			public String isoFolded()
			{
				return m_isoFolded;
			}

			@Override
			public boolean folds()
			{
				return true;
			}

			@Override
			public boolean equals(Object other, Messager msgr)
			{
				if ( ! (other instanceof Identifier) )
					return false;
				Identifier oi = (Identifier)other;
				boolean eqPG = m_pgFolded.equals(oi.pgFolded());
				boolean eqISO = m_isoFolded.equals(oi.isoFolded());
				if ( eqPG != eqISO  &&  oi.folds()  &&  null != msgr )
				{
					msgr.printMessage(Kind.WARNING, String.format(
						"identifiers \"%1$s\" and \"%2$s\" are equal by ISO " +
						"or PostgreSQL case-insensitivity rules but not both",
						m_nonFolded, oi.nonFolded()));
				}
				return eqPG || eqISO;
			}

			/**
			 * The characters that PostgreSQL rules will fold: only the 26
			 * uppercase ASCII letters.
			 */
			private static final Pattern s_pgFolded = Pattern.compile("[A-Z]");

			/**
			 * Case-fold a string by the PostgreSQL rules (assuming a
			 * multibyte server encoding, where only the 26 uppercase ASCII
			 * letters fold to lowercase).
			 * @param s The non-folded value.
			 * @return The folded value.
			 */
			private String pgFold(String s)
			{
				Matcher m = s_pgFolded.matcher(s);
				StringBuffer sb = new StringBuffer();
				while ( m.find() )
					m.appendReplacement(sb, m.group().toLowerCase());
				return m.appendTail(sb).toString();
			}
		}
	}
}
