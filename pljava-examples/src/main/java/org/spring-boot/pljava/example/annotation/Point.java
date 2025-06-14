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
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.logging.Logger;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.MappedUDT;
import org.postgresql.pljava.annotation.SQLAction;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * Example of a "mirrored UDT": a user-defined type that exposes to Java the
 * internal representation of an existing (but not SQL-standard) PostgreSQL
 * type. Naturally, the author of this type has to know (from the PostgreSQL
 * source) that a {@code Point} is stored as two {@code float8}s, {@code x}
 * first and then {@code y}.
 */
@SQLAction(requires={"point mirror type", "point assertHasValues"}, install=
		"SELECT javatest.assertHasValues(CAST('(1,2)' AS point), 1, 2)"
)
@MappedUDT(name="point", provides="point mirror type")
public class Point implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	/**
	 * Return the same 'point' passed in, logging its contents at level INFO.
	 * @param pt any instance of the type this UDT mirrors
	 * @return the same instance passed in
	 */
	@Function(schema="javatest", requires="point mirror type",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static Point logAndReturn(Point pt) {
		s_logger.info(pt.getSQLTypeName() + pt);
		return pt;
	}

	/**
	 * Assert a 'point' has given x and y values, to test that its
	 * representation in Java corresponds to what PostgreSQL sees.
	 * @param pt an instance of this UDT
	 * @param x the x value it should have
	 * @param y the y value it should have
	 * @throws SQLException if the values do not match
	 */
	@Function(schema="javatest",
		requires="point mirror type", provides="point assertHasValues",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void assertHasValues(Point pt, double x, double y)
		throws SQLException
	{
		if ( pt.m_x != x  ||  pt.m_y != y )
			throw new SQLException("assertHasValues fails");
	}

	private double m_x;
	private double m_y;

	private String m_typeName;

	@Override
	public String getSQLTypeName() {
		return m_typeName;
	}

	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException {
		s_logger.info(typeName + " from SQLInput");
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		m_typeName = typeName;
	}

	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		s_logger.info(m_typeName + " to SQLOutput");
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
	}

	@Override
	public String toString()
	{
		return String.format("(%g,%g)", m_x, m_y);
	}
}
