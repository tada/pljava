/*
 * Copyright (c) 2020-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import java.util.AbstractMap;
import java.util.Map;
import static java.util.Objects.requireNonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.regex.Pattern.compile;

import javax.annotation.processing.Messager;

import static org.postgresql.pljava.sqlgen.Lexicals
	.ISO_AND_PG_IDENTIFIER_CAPTURING;
import static org.postgresql.pljava.sqlgen.Lexicals.ISO_REGULAR_IDENTIFIER_PART;
import static org.postgresql.pljava.sqlgen.Lexicals.PG_REGULAR_IDENTIFIER_PART;
import static org.postgresql.pljava.sqlgen.Lexicals.SEPARATOR;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;
import static org.postgresql.pljava.sqlgen.Lexicals.separator;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import static org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple.pgFold;

/**
 * Abstraction of a database type, which is usually specified by an
 * {@code Identifier.Qualified}, but sometimes by reserved SQL syntax.
 */
abstract class DBType
{
	DBType withModifier(String modifier)
	{
		return new Modified(this, modifier);
	}

	DBType asArray(String notated)
	{
		return new Array(this, notated);
	}

	DBType withDefault(String suffix)
	{
		return new Defaulting(this, suffix);
	}

	String toString(boolean withDefault)
	{
		return toString();
	}

	abstract DependTag dependTag();

	/**
	 * Return the original underlying (leaf) type, either a {@code Named} or
	 * a {@code Reserved}.
	 *<p>
	 * Override in non-leaf classes (except {@code Array}).
	 */
	DBType leaf()
	{
		return this;
	}

	boolean isArray()
	{
		return false;
	}

	@Override
	public final boolean equals(Object o)
	{
		return equals(o, null);
	}

	/**
	 * True if the underlying (leaf) types compare equal (overridden for
	 * {@code Array}).
	 *<p>
	 * The assumption is that equality checking will be done for function
	 * signature equivalence, for which defaults and typmods don't matter
	 * (but arrayness does).
	 */
	public final boolean equals(Object o, Messager msgr)
	{
		if ( this == o )
			return true;
		if ( ! (o instanceof DBType) )
			return false;
		DBType dt1 = this.leaf();
		DBType dt2 = ((DBType)o).leaf();
		if ( dt1.getClass() != dt2.getClass() )
			return false;
		if ( dt1 instanceof Array )
		{
			dt1 = ((Array)dt1).m_component.leaf();
			dt2 = ((Array)dt2).m_component.leaf();
			if ( dt1.getClass() != dt2.getClass() )
				return false;
		}
		if ( dt1 instanceof Named )
			return ((Named)dt1).m_ident.equals(((Named)dt2).m_ident, msgr);
		return pgFold(((Reserved)dt1).m_reservedName)
			.equals(pgFold(((Reserved)dt2).m_reservedName));
	}

	/**
	 * Pattern to match type names that are special in SQL, if they appear as
	 * regular (unquoted) identifiers and without a schema qualification.
	 *<p>
	 * This list does not include {@code DOUBLE} or {@code NATIONAL}, as the
	 * reserved SQL form for each includes a following keyword
	 * ({@code PRECISION} or {@code CHARACTER}/{@code CHAR}, respectively).
	 * There is a catch-all test in {@code fromSQLTypeAnnotation} that will fall
	 * back to 'reserved' treatment if the name is followed by anything that
	 * isn't a parenthesized type modifier, so the fallback will naturally catch
	 * these two cases.
	 */
	static final Pattern s_reservedTypeFirstWords = compile(
		"(?i:" +
		"INT|INTEGER|SMALLINT|BIGINT|REAL|FLOAT|DECIMAL|DEC|NUMERIC|" +
		"BOOLEAN|BIT|CHARACTER|CHAR|VARCHAR|TIMESTAMP|TIME|INTERVAL" +
		")"
	);

	/**
	 * Parse a string, representing an optional parameter/column name followed
	 * by a type, into an {@code Identifier.Simple}, possibly null, and a
	 * {@code DBType}.
	 *<p>
	 * Whitespace (or, strictly, separator; comments would be accepted) must
	 * separate the name from the type, if the name is not quoted. To omit a
	 * name and supply only the type, the string must begin with whitespace
	 * (ahem, separator).
	 */
	static Map.Entry<Identifier.Simple,DBType> fromNameAndType(String nandt)
	{
		Identifier.Simple name = null;
		Matcher m = ISO_AND_PG_IDENTIFIER_CAPTURING.matcher(nandt);
		if ( m.lookingAt() )
		{
			nandt = nandt.substring(m.end());
			name = identifierFrom(m);
		}
		return
			new AbstractMap.SimpleImmutableEntry<>(
				name, fromSQLTypeAnnotation(nandt));
	}

	/**
	 * Make a {@code DBType} from whatever might appear in an {@code SQLType}
	 * annotation.
	 *<p>
	 * The possibilities are numerous, as that text used to be dumped rather
	 * blindly into the descriptor and thus could be whatever PostgreSQL would
	 * make sense of. The result could be a {@code DBType.Named} if the start of
	 * the text parses as a (possibly schema-qualified) identifier, or a
	 * {@code DBType.Reserved} if it doesn't (or it parses as a non-schema-
	 * qualified regular identifier and matches one of SQL's grammatically
	 * reserved type names). It could be either of those wrapped in a
	 * {@code DBType.Modified} if a type modifier was parsed out. It could be
	 * any of those wrapped in a {@code DBType.Array} if the text ended with any
	 * of the recognized forms of array dimension notation. The one thing it
	 * can't be (as a result from this method) is a {@code DBType.Defaulting};
	 * that wrapping can be applied to the result later, to carry a default
	 * value that has been specified at a particular site of use.
	 *<p>
	 * The parsing strategy is a bit heuristic. An attempt is made to parse a
	 * (possibly schema-qualified) identifier at the start of the string.
	 * An attempt is made to find a match for array-dimension notation that runs
	 * to the end of the string. Whatever lies between gets to be a typmod if it
	 * looks enough like one, or gets rolled with the front of the string into a
	 * {@code DBType.Reserved}, which is not otherwise scrutinized; the
	 * {@code Reserved} case is still more or less a catch-all that will be
	 * dumped blindly into the descriptor in the hope that PostgreSQL will make
	 * sense of it.
	 *<p>
	 * This strategy is used because compared to what can appear in a typmod
	 * (which could require arbitrary constant expression parsing), the array
	 * grammar depends on much less.
	 */
	static DBType fromSQLTypeAnnotation(String value)
	{
		Identifier.Qualified<Identifier.Simple> qname = null;

		Matcher m = SEPARATOR.matcher(value);
		separator(m, false);
		int postSeparator = m.regionStart();

		if ( m.usePattern(ISO_AND_PG_IDENTIFIER_CAPTURING).lookingAt() )
		{
			Identifier.Simple id1 = identifierFrom(m);
			m.region(m.end(), m.regionEnd());

			separator(m, false);
			if ( value.startsWith(".", m.regionStart()) )
			{
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);
				if ( m.usePattern(ISO_AND_PG_IDENTIFIER_CAPTURING).lookingAt() )
				{
					Identifier.Simple id2 = identifierFrom(m);
					qname = id2.withQualifier(id1);
					m.region(m.end(), m.regionEnd());
					separator(m, false);
				}
			}
			else
				qname = id1.withQualifier(null);
		}

		/*
		 * At this point, qname may have a local name and qualifier, or it may
		 * have a local name and null qualifier (if a single identifier was
		 * successfully matched but not followed by a dot). It is also possible
		 * for qname to be null, either because the start of the string didn't
		 * look like an identifier at all, or because it did, but was followed
		 * by a dot, and what followed the dot could not be parsed as another
		 * identifier. Probably both of those cases are erroneous, but they can
		 * also be handled by simply treating the content as Reserved and hoping
		 * PostgreSQL can make sense of it.
		 *
		 * Search from here to the end of the string for possible array notation
		 * that can be stripped off the end, leaving just the middle (if any) to
		 * be dealt with.
		 */

		String arrayNotation = arrayNotationIfPresent(m, value);

		/*
		 * If arrayNotation is not null, m's region end has been adjusted to
		 * exclude the array notation.
		 */

		boolean reserved;

		if ( null == qname )
			reserved = true;
		else if ( null != qname.qualifier() )
			reserved = false;
		else
		{
			Identifier.Simple local = qname.local();
			if ( ! local.folds() )
				reserved = false;
			else
			{
				Matcher m1 =
					s_reservedTypeFirstWords.matcher(local.nonFolded());
				reserved = m1.matches();
			}
		}

		/*
		 * If this is a reserved type, just wrap up everything from its start to
		 * the array notation (if any) as a Reserved; there is no need to try to
		 * tease out a typmod separately. (The reserved syntax can be quite
		 * unlike the generic typename(typmod) pattern; there could be what
		 * looks like a (typmod) between TIME and WITH TIME ZONE, or the moral
		 * equivalent of a typmod could look like HOUR TO MINUTE, and so on.)
		 *
		 * If we think this is a non-reserved type, and there is anything left
		 * in the matching region (preceding the array notation, if any), then
		 * it had better be a typmod in the generic form starting with a (. We
		 * will capture whatever is there and call it a typmod as long as it
		 * does start that way. (More elaborate checking, such as balancing the
		 * parens, would require ability to parse an expr_list.) This can allow
		 * malformed syntax to be uncaught until deployment time when PostgreSQL
		 * sees it, but that's unchanged from when the entire SQLType string was
		 * passed along verbatim. The 'threat' model here is just that the
		 * legitimate developer may get an error later when earlier would be
		 * more helpful, not a malicious adversary bent on injection.
		 *
		 * On the other hand, if what's left doesn't start with a ( then we
		 * somehow don't know what we're looking at, so fall back and treat it
		 * as reserved. This will naturally catch the two-token reserved names
		 * DOUBLE PRECISION, NATIONAL CHARACTER or NATIONAL CHAR, which were
		 * therefore left out of the s_reservedTypeFirstWords pattern.
		 */

		if ( ! reserved  &&  m.regionStart() < m.regionEnd() )
			if ( ! value.startsWith("(", m.regionStart()) )
				reserved = true;

		DBType result;

		if ( reserved )
			result = new DBType.Reserved(
				value.substring(postSeparator, m.regionEnd()));
		else
		{
			result = new DBType.Named(qname);
			if ( m.regionStart() < m.regionEnd() )
				result = result.withModifier(
					value.substring(m.regionStart(), m.regionEnd()));
		}

		if ( null != arrayNotation )
			result = result.asArray(arrayNotation);

		return result;
	}

	private static final Pattern s_arrayDimStart = compile(String.format(
		"(?i:(?<!%1$s|%2$s)ARRAY(?!%1$s|%2$s))|\\[",
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	private static final Pattern s_digits = compile("\\d++");

	/**
	 * Return any array dimension notation (any of the recognized forms) that
	 * "ends" the string (i.e., is followed by at most {@code separator} before
	 * the string ends).
	 *<p>
	 * If a non-null string is returned, the matcher's region-end has been
	 * adjusted to exclude it.
	 *<p>
	 * The matcher's associated pattern may have been changed, and the region
	 * transiently changed, but on return the region will either be the same as
	 * on entry (if no array notation was found), or have only the region end
	 * adjusted to exclude the notation.
	 *<p>
	 * The returned string can include a {@code separator} that followed the
	 * array notation.
	 */
	private static String arrayNotationIfPresent(Matcher m, String s)
	{
		int originalRegionStart = m.regionStart();
		int notationStart;
		int dims;
		boolean atMostOneDimAllowed; // true after ARRAY keyword

restart:for ( ;; )
		{
			notationStart = -1;
			dims = 0;
			atMostOneDimAllowed = false;

			m.usePattern(s_arrayDimStart);
			if ( ! m.find() )
				break restart; // notationStart is -1 indicating not found

			notationStart = m.start();
			if ( ! "[".equals(m.group()) ) // saw ARRAY
			{
				atMostOneDimAllowed = true;
				m.region(m.end(), m.regionEnd());
				separator(m, false);
				if ( ! s.startsWith("[", m.regionStart()) )
				{
					if ( m.regionStart() == m.regionEnd() )
					{
						dims = 1; // ARRAY separator $ --ok (means 1 dim)
						break restart;
					}
					/*
					 * ARRAY separator something-other-than-[
					 * This is not the match we're looking for. The regionStart
					 * already points here, so restart the loop to look for
					 * another potential array notation start beyond this point.
					 */
					continue restart;
				}
				m.region(m.regionStart() + 1, m.regionEnd());
			}

			/*
			 * Invariant: have seen [ and regionStart still points to it.
			 * Accept optional digits, then ]
			 * Repeat if followed by a [
			 */
			for ( ;; )
			{
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);

				if ( m.usePattern(s_digits).lookingAt() )
				{
					m.region(m.end(), m.regionEnd());
					separator(m, false);
				}

				if ( ! s.startsWith("]", m.regionStart()) )
					continue restart;

				++ dims; // have seen a complete [ (\d+)? ]
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);
				if ( s.startsWith("[", m.regionStart()) )
					continue;
				if ( m.regionStart() == m.regionEnd() )
					if ( ! atMostOneDimAllowed  ||  1 == dims )
						break restart;
				continue restart; // not at end, not at [ --start over
			}
		}

		if ( -1 == notationStart )
		{
			m.region(originalRegionStart, m.regionEnd());
			return null;
		}

		m.region(originalRegionStart, notationStart);
		return s.substring(notationStart);
	}

	static final class Reserved extends DBType
	{
		private final String m_reservedName;

		Reserved(String name)
		{
			m_reservedName = name;
		}

		@Override
		public String toString()
		{
			return m_reservedName;
		}

		@Override
		DependTag dependTag()
		{
			return null;
		}

		@Override
		public int hashCode()
		{
			return pgFold(m_reservedName).hashCode();
		}
	}

	static final class Named extends DBType
	{
		private final Identifier.Qualified<Identifier.Simple> m_ident;

		Named(Identifier.Qualified<Identifier.Simple> ident)
		{
			m_ident = ident;
		}

		@Override
		public String toString()
		{
			return m_ident.toString();
		}

		@Override
		DependTag dependTag()
		{
			return new DependTag.Type(m_ident);
		}

		@Override
		public int hashCode()
		{
			return m_ident.hashCode();
		}
	}

	static final class Modified extends DBType
	{
		private final DBType m_raw;
		private final String m_modifier;

		Modified(DBType raw, String modifier)
		{
			m_raw = raw;
			m_modifier = modifier;
		}

		@Override
		public String toString()
		{
			return m_raw.toString() + m_modifier;
		}

		@Override
		DBType withModifier(String modifier)
		{
			throw new UnsupportedOperationException(
				"withModifier on a Modified");
		}

		@Override
		DependTag dependTag()
		{
			return m_raw.dependTag();
		}

		@Override
		public int hashCode()
		{
			return m_raw.hashCode();
		}

		@Override
		DBType leaf()
		{
			return m_raw.leaf();
		}
	}

	static final class Array extends DBType
	{
		private final DBType m_component;
		private final int m_dims;
		private final String m_notated;

		Array(DBType component, String notated)
		{
			assert component instanceof Named
				|| component instanceof Reserved
				|| component instanceof Modified;
			int dims = 0;
			for ( int pos = 0; -1 != (pos = notated.indexOf('[', pos)); ++ pos )
				++ dims;
			m_dims = 0 == dims ? 1 : dims; // "ARRAY" with no [ has dimension 1
			m_notated = notated;
			m_component = requireNonNull(component);
		}

		@Override
		Array asArray(String notated)
		{
			/* Implementable in principle, but may never be needed */
			throw new UnsupportedOperationException("asArray on an Array");
		}

		@Override
		public String toString()
		{
			return m_component.toString() + m_notated;
		}

		@Override
		DependTag dependTag()
		{
			return m_component.dependTag();
		}

		@Override
		boolean isArray()
		{
			return true;
		}

		@Override
		public int hashCode()
		{
			return m_component.hashCode();
		}
	}

	static final class Defaulting extends DBType
	{
		private final DBType m_raw;
		private final String m_suffix;

		Defaulting(DBType raw, String suffix)
		{
			assert ! (raw instanceof Defaulting);
			m_raw = requireNonNull(raw);
			m_suffix = suffix;
		}

		@Override
		Modified withModifier(String notated)
		{
			throw new UnsupportedOperationException(
				"withModifier on a Defaulting");
		}

		@Override
		Array asArray(String notated)
		{
			throw new UnsupportedOperationException("asArray on a Defaulting");
		}

		@Override
		Array withDefault(String suffix)
		{
			/* Implementable in principle, but may never be needed */
			throw new UnsupportedOperationException(
				"withDefault on a Defaulting");
		}

		@Override
		public String toString()
		{
			return m_raw.toString() + " " + m_suffix;
		}

		@Override
		String toString(boolean withDefault)
		{
			return withDefault ? toString() : m_raw.toString();
		}

		@Override
		DependTag dependTag()
		{
			return m_raw.dependTag();
		}

		@Override
		boolean isArray()
		{
			return m_raw.isArray();
		}

		@Override
		public int hashCode()
		{
			return m_raw.hashCode();
		}

		@Override
		DBType leaf()
		{
			return m_raw.leaf();
		}
	}
}
