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

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.logging.Logger;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.BaseUDT;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * Complex (re and im parts are doubles) implemented in Java as a scalar UDT.
 */
@SQLAction(requires={
	"scalar complex type", "complex assertHasValues", "complexscalar boot fn"
	}, install={
		"SELECT javatest.complexscalar()",
		"SELECT javatest.assertHasValues(" +
		" CAST('(1,2)' AS javatest.complex), 1, 2)"
	}
)
@BaseUDT(schema="javatest", name="complex", provides="scalar complex type",
	internalLength=16, alignment=BaseUDT.Alignment.DOUBLE)
public class ComplexScalar implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	/**
	 * Return the same 'complex' passed in, logging its contents at level INFO.
	 * @param cpl any instance of this UDT
	 * @return the same instance passed in
	 */
	@Function(requires="scalar complex type", type="javatest.complex",
		schema="javatest", name="logcomplex", effects=IMMUTABLE,
		onNullInput=RETURNS_NULL)
	public static ComplexScalar logAndReturn(
		@SQLType("javatest.complex") ComplexScalar cpl) {
		s_logger.info(cpl.getSQLTypeName() + cpl);
		return cpl;
	}

	/**
	 * Assert a 'complex' has given re and im values, to test that its
	 * representation in Java corresponds to what PostgreSQL sees.
	 * @param cpl an instance of this UDT
	 * @param re the 'real' value it should have
	 * @param im the 'imaginary' value it should have
	 * @throws SQLException if the values do not match
	 */
	@Function(schema="javatest",
		requires="scalar complex type", provides="complex assertHasValues",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void assertHasValues(
		@SQLType("javatest.complex") ComplexScalar cpl, double re, double im)
		throws SQLException
	{
		if ( cpl.m_x != re  ||  cpl.m_y != im )
			throw new SQLException("assertHasValues fails");
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static ComplexScalar parse(String input, String typeName)
			throws SQLException {
		try {
			StreamTokenizer tz = new StreamTokenizer(new StringReader(input));
			if (tz.nextToken() == '('
					&& tz.nextToken() == StreamTokenizer.TT_NUMBER) {
				double x = tz.nval;
				if (tz.nextToken() == ','
						&& tz.nextToken() == StreamTokenizer.TT_NUMBER) {
					double y = tz.nval;
					if (tz.nextToken() == ')') {
						s_logger.info(typeName + " from string");
						return new ComplexScalar(x, y, typeName);
					}
				}
			}
			throw new SQLException("Unable to parse complex from string \""
					+ input + '"');
		} catch (IOException e) {
			throw new SQLException(e.getMessage());
		}
	}

	private double m_x;

	private double m_y;

	private String m_typeName;

	public ComplexScalar() {
	}

	public ComplexScalar(double x, double y, String typeName) {
		m_x = x;
		m_y = y;
		m_typeName = typeName;
	}

	@Override
	public String getSQLTypeName() {
		return m_typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException {
		s_logger.info(typeName + " from SQLInput");
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		m_typeName = typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public String toString() {
		s_logger.info(m_typeName + " toString");
		StringBuffer sb = new StringBuffer();
		sb.append('(');
		sb.append(m_x);
		sb.append(',');
		sb.append(m_y);
		sb.append(')');
		return sb.toString();
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		s_logger.info(m_typeName + " to SQLOutput");
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
	}

	/**
	 * A no-op function that forces the ComplexScalar class to be loaded.
	 * This is only necessary because the deployment-descriptor install
	 * actions contain a query making use of this type, and PostgreSQL does
	 * not expect type in/out/send/recv functions to need an updated
	 * snapshot, so it will try to find this class in the snapshot from
	 * before the jar was installed, and fail. By providing this function,
	 * which defaults to volatile so it gets an updated snapshot, and
	 * calling it first, the class will be found and loaded; once it is
	 * loaded, the user-defined type operations are able to find it.
	 * <p>
	 * Again, this is only an issue when trying to make use of the newly
	 * loaded UDT from right within the deployment descriptor for the jar.
	 */
	@Function(schema="javatest", provides="complexscalar boot fn")
	public static void ComplexScalar()
	{
	}
}
