/*
 * Copyright (c) 2004-2015 Tada AB and other contributors, as listed below.
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
import org.postgresql.pljava.annotation.SQLType;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * Complex (re and im parts are doubles) implemented in Java as a mapped UDT.
 */
@SQLAction(requires={
	"complextuple type", "complextuple assertHasValues"}, install=
		"SELECT javatest.assertHasValues(" +
		" CAST('(1,2)' AS javatest.complextuple), 1, 2)"
)
@MappedUDT(schema="javatest", name="complextuple", provides="complextuple type",
structure={
	"x float8",
	"y float8"
})
public class ComplexTuple implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	/**
	 * Return the same 'complextuple' passed in, logging its contents at
	 * level INFO.
	 * @param cpl any instance of this UDT
	 * @return the same instance passed in
	 */
	@Function(schema="javatest", name="logcomplex",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL,
		type="javatest.complextuple", requires="complextuple type")
	public static ComplexTuple logAndReturn(
		@SQLType("javatest.complextuple") ComplexTuple cpl) {
		s_logger.info(cpl.getSQLTypeName() + "(" + cpl.m_x + ", " + cpl.m_y
				+ ")");
		return cpl;
	}

	/**
	 * Assert a 'complextuple' has given re and im values, to test that its
	 * representation in Java corresponds to what PostgreSQL sees.
	 * @param cpl an instance of this UDT
	 * @param re the 'real' value it should have
	 * @param im the 'imaginary' value it should have
	 * @throws SQLException if the values do not match
	 */
	@Function(schema="javatest",
		requires="complextuple type", provides="complextuple assertHasValues",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void assertHasValues(
		@SQLType("javatest.complextuple") ComplexTuple cpl,
		double re, double im)
		throws SQLException
	{
		if ( cpl.m_x != re  ||  cpl.m_y != im )
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
		m_typeName = typeName;
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		s_logger.info(typeName + " from SQLInput");
	}

	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
		s_logger.info(m_typeName + " to SQLOutput");
	}
}
