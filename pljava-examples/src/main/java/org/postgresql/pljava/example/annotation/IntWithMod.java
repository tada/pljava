/*
 * Copyright (c) 2004-2016 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.SQLData;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.BaseUDT;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * An integer-like data type accepting a type modifier: IntWithMod(even) or
 * IntWithMod(odd).
 *<p>
 * Support for type modifiers in PL/Java is only partial so far. It does not
 * yet honor typmods passed to the input/receive functions ... but it may be
 * that only COPY operations require that. Most uses of types with modifiers
 * seem to pass -1 when first constructing the value, then use a typmod-
 * application cast, and all of that can be done in PL/Java already.
 *<p>
 * Serving suggestion:
 *<pre>
 * CREATE TABLE evod (
 *   ev javatest.IntWithMod(even),
 *   od javatest.IntWithMod(odd)
 * );
 * INSERT INTO evod ( ev, od ) VALUES ( '4', '7' );
 *</pre>
 *<p>
 * Of course this example more or less duplicates what you could do in two lines
 * with CREATE DOMAIN. But it is enough to illustrate the process.
 *<p>
 * Certainly, it would be less tedious with some more annotation support and
 * autogeneration of the ordering dependencies that are now added by hand here.
 *<p>
 * Most of this must be suppressed (using conditional implementor tags) if the
 * PostgreSQL instance is older than 8.3, because it won't have the cstring[]
 * type, so the typeModifierInput function can't be declared, and so neither
 * can the type, or functions that accept or return it. See the
 * {@link ConditionalDDR} example for where the implementor tag is set up.
 */
@SQLAction(requires={"IntWithMod type", "IntWithMod modApply"},
	implementor="postgresql_ge_80300",
	remove="DROP CAST (javatest.IntWithMod AS javatest.IntWithMod)",
	install={
		"CREATE CAST (javatest.IntWithMod AS javatest.IntWithMod)" +
		" WITH FUNCTION javatest.intwithmod_typmodapply(" +
		" javatest.IntWithMod, integer, boolean)",

		"COMMENT ON CAST (javatest.IntWithMod AS javatest.IntWithMod) IS '" +
		"Cast that applies/verifies the type modifier on an IntWithMod.'"
	}
)
@BaseUDT(schema="javatest", provides="IntWithMod type",
	implementor="postgresql_ge_80300",
	requires={"IntWithMod modIn", "IntWithMod modOut"},
	typeModifierInput="javatest.intwithmod_typmodin",
	typeModifierOutput="javatest.intwithmod_typmodout",
	like="pg_catalog.int4")
public class IntWithMod implements SQLData {

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static IntWithMod parse(String input, String typeName)
		throws SQLException {
		try {
			int v = Integer.parseInt(input);
			IntWithMod o = new IntWithMod();
			o.m_value = v;
			o.m_typeName = typeName;
			return o;
		}
		catch ( NumberFormatException e ) {
			throw new SQLDataException("invalid IntWithMod value", "22P02", e);
		}
	}

	private int m_value;

	private String m_typeName;

	public IntWithMod() {
	}

	@Override
	public String getSQLTypeName() {
		return m_typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException {
		m_value = stream.readInt();
		m_typeName = typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public String toString() {
		return String.valueOf(m_value);
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		stream.writeInt(m_value);
	}

	/**
	 * Type modifier input function for IntWithMod type: accepts
	 * "even" or "odd". The modifier value is 0 for even or 1 for odd.
	 */
	@Function(schema="javatest", name="intwithmod_typmodin",
		provides="IntWithMod modIn", implementor="postgresql_ge_80300",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static int modIn(@SQLType("cstring[]") String[] toks)
		throws SQLException {
		if ( 1 != toks.length )
			throw new SQLDataException(
				"only one type modifier allowed for IntWithMod", "22023");
		if ( "even".equalsIgnoreCase(toks[0]) )
			return 0;
		if ( "odd".equalsIgnoreCase(toks[0]) )
			return 1;
		throw new SQLDataException(
			"modifier for IntWithMod must be \"even\" or \"odd\"", "22023");
	}

	/**
	 * Type modifier output function for IntWithMod type.
	 */
	@Function(schema="javatest", name="intwithmod_typmodout",
		provides="IntWithMod modOut", type="cstring",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static String modOut(int mod) throws SQLException {
		switch ( mod ) {
			case 0: return "(even)";
			case 1: return "(odd)";
			default:
				throw new SQLException("impossible IntWithMod typmod: " + mod);
		}
	}

	/**
	 * Function backing the type-modifier application cast for IntWithMod type.
	 */
	@Function(schema="javatest", name="intwithmod_typmodapply",
		implementor="postgresql_ge_80300",
		requires="IntWithMod type", provides="IntWithMod modApply",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static IntWithMod modApply(IntWithMod iwm, int mod, boolean explicit)
		throws SQLException
	{
		if ( -1 == mod )
			return iwm;
		if ( (iwm.m_value & 1) != mod )
			throw new SQLDataException(
				"invalid value " + iwm + " for " +
				iwm.getSQLTypeName() + modOut(mod), "22000");
		iwm.m_typeName += modOut(mod);
		return iwm;
	}
}
