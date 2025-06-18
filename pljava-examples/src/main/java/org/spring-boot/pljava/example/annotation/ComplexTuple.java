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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * Complex (re and im parts are doubles) implemented in Java as a mapped UDT.
 */
@SQLAction(requires={
		"complextuple assertHasValues","complextuple setParameter"}, install={
		"SELECT javatest.assertHasValues(" +
		" CAST('(1,2)' AS javatest.complextuple), 1, 2)",
		"SELECT javatest.setParameter()"
	}
)
@MappedUDT(schema="javatest", name="complextuple",
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
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static ComplexTuple logAndReturn(ComplexTuple cpl) {
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
	@Function(schema="javatest", provides="complextuple assertHasValues",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void assertHasValues(ComplexTuple cpl, double re, double im)
		throws SQLException
	{
		if ( cpl.m_x != re  ||  cpl.m_y != im )
			throw new SQLException("assertHasValues fails");
	}

	/**
	 * Pass a 'complextuple' UDT as a parameter to a PreparedStatement
	 * that returns it, and verify that it makes the trip intact.
	 */
	@Function(schema="javatest", provides="complextuple setParameter",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void setParameter() throws SQLException
	{
		Connection c = DriverManager.getConnection("jdbc:default:connection");
		PreparedStatement ps =
			c.prepareStatement("SELECT CAST(? AS javatest.complextuple)");
		ComplexTuple ct = new ComplexTuple();
		ct.m_x = 1.5;
		ct.m_y = 2.5;
		ct.m_typeName = "javatest.complextuple";
		ps.setObject(1, ct);
		ResultSet rs = ps.executeQuery();
		rs.next();
		ct = (ComplexTuple)rs.getObject(1);
		ps.close();
		assertHasValues(ct, 1.5, 2.5);
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
