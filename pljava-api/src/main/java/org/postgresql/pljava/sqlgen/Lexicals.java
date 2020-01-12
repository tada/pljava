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
package org.postgresql.pljava.sqlgen;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.InputMismatchException;

import static java.util.Objects.requireNonNull;

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

	/** An operator by PostgreSQL rules. The length limit ({@code NAMELEN - 1})
	 * is not applied here. The match will not include a {@code -} followed by
	 * {@code -} or a {@code /} followed by {@code *}, and a multicharacter
	 * match will not end with {@code +} or {@code -} unless it also contains
	 * one of {@code ~ ! @ # % ^ & | ` ?}.
	 */
	public static final Pattern PG_OPERATOR =
	Pattern.compile(
		"(?:(?!--|/\\*)(?![-+][+]*+(?:$|[^-+*/<>=~!@#%^&|`?]))[-+*/<>=])++" +
		"(?:[~!@#%^&|`?](?:(?!--|/\\*)[-+*/<>=~!@#%^&|`?])*+)?+" +
		"|" +
		"[~!@#%^&|`?](?:(?!--|/\\*)[-+*/<>=~!@#%^&|`?])*+" +
		"|" +
		"(?!--)[-+]"
	);

	/** A newline, in any of the various forms recognized by the Java regex
	 * engine, letting it handle the details.
	 */
	public static final Pattern NEWLINE = Pattern.compile(
		"(?ms:$(?:(?<!^).|(?<=\\G).){1,2}+)"
	);

	/** White space <em>except</em> newline, for any Java-recognized newline.
	 */
	public static final Pattern WHITESPACE_NO_NEWLINE = Pattern.compile(
		"(?-s:(?=\\s).)"
	);

	/** The kind of comment that extends from -- to the end of the line.
	 * This pattern does not eat the newline (though the ISO production does).
	 */
	public static final Pattern SIMPLE_COMMENT = Pattern.compile("(?-s:--.*+)");

	/** Most of the inside of a bracketed comment, defined in an odd way.
	 * It expects both characters of the /* introducer to have been consumed
	 * already. This pattern will then eat the whole comment including both
	 * closing characters <em>if</em> it encounters no nested comment;
	 * otherwise it will consume everything including the / of the nested
	 * introducer, but leaving the *, and the {@code <nest>} capturing group
	 * will be present in the result. That signals the caller to increment the
	 * nesting level, consume one * and invoke this pattern again. If the nested
	 * match succeeds (without again setting the {@code <nest>} group), the
	 * caller should then decrement the nest level and match this pattern again
	 * to consume the rest of the comment at the original level.
	 *<p>
	 * This pattern leaves the * unconsumed upon finding a nested comment
	 * introducer as a way to end the repetition in the SEPARATOR pattern, as
	 * nothing the SEPARATOR pattern can match can begin with a *.
	 */
	public static final Pattern BRACKETED_COMMENT_INSIDE = Pattern.compile(
		"(?:(?:[^*/]++|/(?!\\*)|\\*(?!/))*+(?:\\*/|(?<nest>/(?=\\*))))"
	);

	/** SQL's SEPARATOR, which can include any amount of whitespace, simple
	 * comments, or bracketed comments. This pattern will consume as much of all
	 * that as it can in one match. There are two capturing groups that might be
	 * set in a match result: {@code <nl>} if there was at least one newline
	 * matched among the whitespace (which needs to be known to get the
	 * continuation of string literals right), and {@code <nest>} if the
	 * start of a bracketed comment was encountered.
	 *<p>
	 * In the {@code <nest>} case, the / of the comment introducer will have
	 * been consumed but the * will remain to consume (as described above
	 * for BRACKETED_COMMENT_INSIDE); the caller will need to increment a nest
	 * level, consume the *, and match BRACKETED_COMMENT_INSIDE to handle the
	 * nesting comment. Assuming that completes without another {@code <nest>}
	 * found, the level should be decremented and BRACKETED_COMMENT_INSIDE
	 * matched again to match the rest of the outer comment. When that completes
	 * (without a {@code <nest>}) at the outermost level, this pattern should be
	 * matched again to mop up any remaining SEPARATOR content.
	 */
	public static final Pattern SEPARATOR =
	Pattern.compile(String.format(
		"(?:(?:%1$s++|(?<nl>%2$s))++|%3$s|(?<nest>/(?=\\*)))++",
		WHITESPACE_NO_NEWLINE.pattern(),
		NEWLINE.pattern(),
		SIMPLE_COMMENT.pattern()
	));

	/**
	 * Consume any SQL SEPARATOR at the beginning of {@code Matcher}
	 * <em>m</em>'s current region.
	 *<p>
	 * The region start is advanced to the character following any separator
	 * (or not at all, if no separator is found).
	 *<p>
	 * The meaning of the return value is altered by the <em>significant</em>
	 * parameter: when <em>significant</em> is true (meaning the very presence
	 * or absence of a separator is significant at that point in the grammar),
	 * the result will be true if any separator was found, false otherwise.
	 * When <em>significant</em> is false, the result does not reveal whether
	 * any separator was found, but will be true only if a separator was found
	 * that includes at least one newline. That information is needed for the
	 * grammar of string and binary-string literals.
	 * @param m a {@code Matcher} whose current region should have any separator
	 * at the beginning consumed. The region start is advanced past any
	 * separator found. The {@code Pattern} associated with the {@code Matcher}
	 * may be changed.
	 * @param significant when true, the result should report whether any
	 * separator was found or not; when false, the result should report only
	 * whether a separator containing at least one newline was found, or not.
	 * @return whether any separator was found, or whether any separator
	 * containing a newline was found, as selected by <em>significant</em>.
	 * @throws InputMismatchException if an unclosed /*-style comment is found.
	 */
	public static boolean separator(Matcher m, boolean significant)
	{
		int state = 0;
		int level = 0;
		boolean result = false;

	loop:
		for ( ;; )
		{
			switch ( state )
			{
			case 0:
				m.usePattern(SEPARATOR);
				if ( ! m.lookingAt() )
					return result; // leave matcher region alone
				if ( significant  ||  -1 != m.start("nl") )
					result = true;
				if ( -1 != m.start("nest") )
				{
					m.region(m.end(0) + 1, m.regionEnd()); // + 1 to eat the *
					m.usePattern(BRACKETED_COMMENT_INSIDE);
					++ level;
					state = 1;
					continue;
				}
				state = 2; // advance matcher region, then break loop
				break;
			case 1:
				if ( ! m.lookingAt() )
					throw new InputMismatchException("unclosed comment");
				if ( -1 != m.start("nest") )
				{
					m.region(m.end(0) + 1, m.regionEnd()); // + 1 to eat the *
					++ level;
					continue;
				}
				else if ( 0 == -- level )
					state = 0;
				break;
			case 2:
				break loop;
			}
			m.region(m.end(0), m.regionEnd()); // advance past matched portion
		}
		return result;
	}

	/**
	 * Return an Identifier.Simple, given a {@code Matcher} that has matched an
	 * ISO_AND_PG_IDENTIFIER_CAPTURING. Will determine from the matching named
	 * groups which type of identifier it was, process the matched sequence
	 * appropriately, and return it.
	 * @param m A {@code Matcher} known to have matched an identifier.
	 * @return Identifier.Simple made from the recovered string.
	 */
	public static Identifier.Simple identifierFrom(Matcher m)
	{
		String s = m.group("i");
		if ( null != s )
			return Identifier.Simple.from(s, false);
		s = m.group("xd");
		if ( null != s )
			return Identifier.Simple.from(s.replace("\"\"", "\""), true);
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
		return Identifier.Simple.from(replacer.appendTail(sb).toString(), true);
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
	public static abstract class Identifier implements Serializable
	{
		/**
		 * This Identifier represented as it would be in SQL source.
		 *<p>
		 * The passed {@code Charset} indicates the character encoding
		 * in which the deparsed result will be stored; the method should verify
		 * that the characters can be encoded there, or use the Unicode
		 * delimited identifier form and escape the ones that cannot.
		 * @return The identifier, quoted, unless it is folding.
		 */
		public abstract String deparse(Charset cs);

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
		public abstract boolean equals(Object other, Messager msgr);

		/**
		 * Convert to {@code String} as by {@code deparse} passing a character
		 * set of {@code UTF_8}.
		 */
		@Override
		public String toString()
		{
			return deparse(UTF_8);
		}

		/**
		 * Ensure deserialization doesn't produce any unknown {@code Identifier}
		 * subclass.
		 *<p>
		 * The natural hierarchy means not everything can be made {@code final}.
		 */
		private void readObject(ObjectInputStream in)
		throws IOException, ClassNotFoundException
		{
			Class<?> c = getClass();
			if ( c != Simple.class && c != Foldable.class && c != Folding.class
				&& c != Pseudo.class && c != Operator.class
				&& c != Qualified.class )
				throw new InvalidObjectException(
					"deserializing unknown Identifier subclass: "
					+ c.getName());
		}

		public static abstract class Unqualified<T extends Unqualified<T>>
		extends Identifier
		{
			/**
			 * Produce the deparsed form of a qualified identifier with the
			 * given <em>qualifier</em> and this as the local part.
			 * @throws NullPointerException if qualifier is null
			 */
			public abstract String deparse(Simple qualifier, Charset cs);

			/**
			 * Form an {@code Identifier.Qualified} with this as the local part.
			 */
			public abstract Qualified<T> withQualifier(Simple qualifier);
		}

		public static class Simple extends Unqualified<Simple>
		{
			protected final String m_nonFolded;

			/**
			 * Create an {@code Identifier.Simple} given its original,
			 * non-folded spelling, and whether it represents a quoted
			 * identifier.
			 * @param s The exact, internal, non-folded spelling of the
			 * identifier (unwrapped from any quoting in its external form).
			 * @param quoted Pass {@code true} if this was parsed from any
			 * quoted external form, false if non-quoted.
			 * @return A corresponding Identifier.Simple
			 * @throws IllegalArgumentException if {@code quoted} is
			 * {@code false} but {@code s} cannot be a non-quoted identifier,
			 * or {@code s} is empty or longer than the ISO SQL maximum 128
			 * codepoints.
			 */
			public static Simple from(String s, boolean quoted)
			{
				boolean foldable =
					ISO_AND_PG_REGULAR_IDENTIFIER.matcher(s).matches();
				if ( ! quoted )
				{
					if ( ! foldable )
						throw new IllegalArgumentException(String.format(
							"impossible for \"%1$s\" to be" +
							" a non-quoted identifier", s));
					return new Folding(s);
				}
				if ( foldable )
					return new Foldable(s);
				return new Simple(s);
			}

			/**
			 * Create an {@code Identifier.Simple} from a name string found in
			 * a PostgreSQL system catalog.
			 *<p>
			 * There is not an explicit indication in the catalog of whether the
			 * name was originally quoted. It must have been, however, if it
			 * does not have the form of a regular identifier, or if it has that
			 * form but does not match its pgFold-ed form (without quotes, PG
			 * would have folded it in that case).
			 * @param s name of the simple identifier, as found in a system
			 * catalog.
			 * @return an Identifier.Simple or subclass appropriate to the form
			 * of the name.
			 */
			public static Simple fromCatalog(String s)
			{
				if ( PG_REGULAR_IDENTIFIER.matcher(s).matches() )
				{
					if ( s.equals(Folding.pgFold(s)) )
						return new Folding(s);
					/*
					 * Having just determined it does not match its pgFolded
					 * form, there is no point returning it as a Foldable; there
					 * is no chance PG will see it as a match to a folded one.
					 */
				}
				return new Simple(s);
			}

			/**
			 * Create an {@code Identifier.Simple} from a name string supplied
			 * in Java source, such as an annotation value.
			 *<p>
			 * Historically, PL/Java has treated these identifiers as regular
			 * ones, requiring delimited ones to be represented by adding quotes
			 * explicitly at start and end, and doubling internal quotes, all
			 * escaped for Java, naturally. This method accepts either of those
			 * forms, and will also accept a string that neither qualifies as a
			 * regular identifier nor starts and ends with quotes. Such a string
			 * will be treated as if it were a delimited identifier with the
			 * start/end quotes already stripped and internal ones already
			 * undoubled.
			 *<p>
			 * The SQL Unicode escape syntax is not accepted here. Java already
			 * has its own Unicode escape syntax, which is what should be used.
			 * @param s name of the simple identifier, as found in Java source.
			 * @return an Identifier.Simple or subclass appropriate to the form
			 * of the name.
			 */
			public static Simple fromJava(String s)
			{
				Matcher m = ISO_DELIMITED_IDENTIFIER_CAPTURING.matcher(s);
				if ( m.matches() )
					s = m.group("xd").replace("\"\"", "\"");
				else if ( m.usePattern(PG_REGULAR_IDENTIFIER).matches() )
					return new Folding(s);
				return from(s, true);
			}

			@Override
			public Qualified<Simple> withQualifier(Simple qualifier)
			{
				return new Qualified<>(qualifier, this);
			}

			@Override
			public String deparse(Charset cs)
			{
				if ( ! cs.contains(UTF_8)
					&& ! cs.newEncoder().canEncode(m_nonFolded) )
					throw noUnicodeQuotingYet(m_nonFolded);
				return '"' + m_nonFolded.replace("\"", "\"\"") + '"';
			}

			@Override
			public String deparse(Simple qualifier, Charset cs)
			{
				return qualifier.deparse(cs) + "." + deparse(cs);
			}

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
			 * @return The spelling with ASCII letters (only) folded to
			 * lowercase, if this Identifier folds.
			 */
			public String pgFolded()
			{
				return m_nonFolded;
			}

			/**
			 * This Identifier as ISO SQL would case-fold it (or the same as
			 * nonFolded if this was quoted and does not fold).
			 * @return The spelling with lowercase and titlecase letters folded
			 * to (possibly length-changing) uppercase equivalents, if this
			 * Identifier folds.
			 */
			public String isoFolded()
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
			public boolean equals(Object other, Messager msgr)
			{
				if ( this == other )
					return true;
				if ( other instanceof Pseudo )
					return false;
				if ( ! (other instanceof Simple) )
					return false;
				Simple oi = (Simple)other;
				if ( oi.folds() )
					return oi.equals(this);
				return m_nonFolded.equals(oi.nonFolded());
			}

			private Simple(String nonFolded)
			{
				String diag = checkLength(nonFolded);
				if ( null != diag )
					throw new IllegalArgumentException(diag);
				m_nonFolded = nonFolded;
			}

			private static String checkLength(String s)
			{
				int cpc = s.codePointCount(0, s.length());
				if ( 0 < cpc && cpc <= 128 )
					return null; /* check has passed */
				return String.format(
					"identifier empty or longer than 128 codepoints: \"%s\"",
					s);
			}

			private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException
			{
				in.defaultReadObject();
				String diag = checkLength(m_nonFolded);
				if ( null != diag )
					throw new InvalidObjectException(diag);
			}
		}

		/**
		 * Class representing an Identifier that was quoted, therefore does
		 * not case-fold, but satisfies {@code ISO_AND_PG_REGULAR_IDENTIFIER}
		 * and so could conceivably be matched by a non-quoted identifier.
		 */
		static class Foldable extends Simple
		{
			private transient /*otherwise final*/ int m_hashCode;

			private Foldable(String nonFolded)
			{
				this(nonFolded, isoFold(nonFolded));
			}

			private Foldable(String nonFolded, String isoFolded)
			{
				super(nonFolded);
				m_hashCode = isoFolded.hashCode();
			}

			private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException
			{
				in.defaultReadObject();
				if ( ! PG_REGULAR_IDENTIFIER.matcher(m_nonFolded).matches() )
					throw new InvalidObjectException(
						"cannot be an SQL regular identifier: " + m_nonFolded);
				m_hashCode = isoFold(m_nonFolded).hashCode();
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
			private transient /*otherwise final*/ String m_pgFolded;
			private transient /*otherwise final*/ String m_isoFolded;

			private Folding(String nonFolded)
			{
				this(nonFolded, isoFold(nonFolded));
			}

			private Folding(String nonFolded, String isoFolded)
			{
				super(nonFolded, isoFolded);
				m_pgFolded = pgFold(nonFolded);
				m_isoFolded = isoFolded;
			}

			private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException
			{
				in.defaultReadObject();
				m_pgFolded = pgFold(m_nonFolded);
				m_isoFolded = isoFold(m_nonFolded);
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
			public String deparse(Charset cs)
			{
				if ( ! cs.contains(UTF_8)
					&& ! cs.newEncoder().canEncode(m_nonFolded) )
					throw noUnicodeQuotingYet(m_nonFolded);
				return m_nonFolded;
			}

			@Override
			public boolean equals(Object other, Messager msgr)
			{
				if ( this == other )
					return true;
				if ( other instanceof Pseudo )
					return false;
				if ( ! (other instanceof Simple) )
					return false;
				Simple oi = (Simple)other;
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
			private static String pgFold(String s)
			{
				Matcher m = s_pgFolded.matcher(s);
				StringBuffer sb = new StringBuffer();
				while ( m.find() )
					m.appendReplacement(sb, m.group().toLowerCase());
				return m.appendTail(sb).toString();
			}
		}

		/**
		 * Displays/deparses like a {@code Simple} identifier, but no singleton
		 * of this class matches anything but itself, to represent
		 * pseudo-identifiers like {@code PUBLIC} as a privilege grantee.
		 */
		public static class Pseudo extends Simple
		{
			public static final Pseudo PUBLIC = new Pseudo("PUBLIC");

			@Override
			public boolean equals(Object other)
			{
				return this == other;
			}

			private Pseudo(String name)
			{
				super(name);
			}

			private Object readResolve() throws ObjectStreamException
			{
				switch ( m_nonFolded )
				{
				case "PUBLIC": return PUBLIC;
				default:
					throw new InvalidObjectException(
						"not a known Pseudo-identifier: " + m_nonFolded);
				}
			}
		}

		/**
		 * Class representing an Identifier that names a PostgreSQL operator.
		 */
		public static class Operator extends Unqualified<Operator>
		{
			private final String m_name;

			private Operator(String name)
			{
				m_name = name;
			}

			public static Operator from(String name)
			{
				return from(name, null);
			}

			public static Operator from(String name, Messager msgr)
			{
				requireNonNull(name);
				String diag = checkMatch(name);

				if ( null != diag )
				{
					if ( null == msgr )
						throw new IllegalArgumentException(diag);
					msgr.printMessage(Kind.ERROR, diag);
				}

				/*
				 * It would be considerate to check the length here, but that
				 * would require knowing the server encoding, because the length
				 * limit in PostgreSQL is NAMELEN - 1 encoded octets (or
				 * possibly fewer, if the character that overflows encodes to
				 * more than one octet). In the SQL generator, that would
				 * require an argument to supply the assumed PG server encoding
				 * being compiled for, and passing it here. Too much work.
				 */
				return new Operator(name);
			}

			private static String checkMatch(String name)
			{
				if ( PG_OPERATOR.matcher(name).matches() )
					return null; /* the check has passed */
				return String.format(
					"not a valid PostgreSQL operator name: %s", name);
			}

			private void readObject(ObjectInputStream in)
			throws IOException, ClassNotFoundException
			{
				in.defaultReadObject();
				String diag = checkMatch(m_name);
				if ( null != diag )
					throw new InvalidObjectException(diag);
			}

			@Override
			public Qualified<Operator> withQualifier(Simple qualifier)
			{
				return new Qualified<>(qualifier, this);
			}

			@Override
			public int hashCode()
			{
				return m_name.hashCode();
			}

			@Override
			public boolean equals(Object other, Messager msgr)
			{
				if ( this == other )
					return true;
				if ( ! (other instanceof Operator) )
					return false;
				return m_name.equals(((Operator)other).m_name);
			}

			@Override
			public String deparse(Charset cs)
			{
				/*
				 * Operator characters are limited to ASCII. Don't bother
				 * checking that cs can encode m_name.
				 */
				return m_name;
			}

			@Override
			public String deparse(Simple qualifier, Charset cs)
			{
				return "OPERATOR("
					+ qualifier.deparse(cs) + "." + deparse(cs) + ")";
			}
		}

		/**
		 * Class representing a schema-qualified identifier.
		 * This is distinct from an Identifier.Unqualified even when it has no
		 * qualifier (and would therefore deparse the same way).
		 */
		public static class Qualified<T extends Unqualified<T>>
		extends Identifier
		{
			private final Simple m_qualifier;
			private final T m_local;

			/**
			 * Create an {@code Identifier.Qualified} from name strings found in
			 * PostgreSQL system catalogs.
			 *<p>
			 * There is not an explicit indication in the catalog of whether a
			 * name was originally quoted. It must have been, however, if it
			 * does not have the form of a regular identifier, or if it has that
			 * form but does not match its pgFold-ed form (without quotes, PG
			 * would have folded it in that case).
			 * @param qualifier string with the name of a schema, as found in
			 * the pg_namespace system catalog.
			 * @param local string with the local name of an object in that
			 * schema.
			 * @return an Identifier.Qualified
			 * @throws NullPointerException if the local name is null.
			 */
			public static Qualified<Simple> nameFromCatalog(
				String qualifier, String local)
			{
				Simple localId = Simple.fromCatalog(local);
				Simple qualId = ( null == qualifier ) ?
					null : Simple.fromCatalog(qualifier);
				return localId.withQualifier(qualId);
			}

			/**
			 * Create an {@code Identifier.Qualified} representing an operator
			 * from name strings found in PostgreSQL system catalogs.
			 * @param qualifier string with the name of a schema, as found in
			 * the pg_namespace system catalog.
			 * @param local string with the local name of an object in that
			 * schema.
			 * @return an Identifier.Qualified
			 * @throws NullPointerException if the local name is null.
			 */
			public static Qualified<Operator> operatorFromCatalog(
				String qualifier, String local)
			{
				Operator localId = Operator.from(local);
				Simple qualId = ( null == qualifier ) ?
					null : Simple.fromCatalog(qualifier);
				return localId.withQualifier(qualId);
			}

			private Qualified(Simple qualifier, T local)
			{
				m_qualifier = qualifier;
				m_local = local;
			}

			@Override
			public String deparse(Charset cs)
			{
				if ( null == m_qualifier )
					return m_local.deparse(cs);
				return m_local.deparse(m_qualifier, cs);
			}

			@Override
			public int hashCode()
			{
				return (null == m_qualifier? 0 : 31 * m_qualifier.hashCode())
						+ m_local.hashCode();
			}

			@Override
			public boolean equals(Object other, Messager msgr)
			{
				if ( ! (other instanceof Qualified) )
					return false;
				Qualified oi = (Qualified)other;

				return (null == m_qualifier
						? null == oi.m_qualifier
						: m_qualifier.equals(oi.m_qualifier, msgr))
						&& m_local.equals(oi.m_local, msgr);
			}

			public Simple qualifier()
			{
				return m_qualifier;
			}

			public T local()
			{
				return m_local;
			}
		}

		private static RuntimeException noUnicodeQuotingYet(String n)
		{
			return new UnsupportedOperationException(
				"cannot yet Unicode-escape identifier \"" + n + '"');
		}
	}
}
