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
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.BaseUDT;

import static org.postgresql.pljava.annotation.Function.Type.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

@BaseUDT(schema="javatest", name="complex", provides="scalar complex type",
	internalLength=16, alignment=BaseUDT.Alignment.DOUBLE)
public class ComplexScalar implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	@Function(requires="scalar complex type", complexType="javatest.complex",
		schema="javatest", name="logcomplex", type=IMMUTABLE,
		onNullInput=RETURNS_NULL)
	public static ComplexScalar logAndReturn(
		@SQLType("javatest.complex") ComplexScalar cpl) {
		s_logger.info(cpl.getSQLTypeName() + cpl);
		return cpl;
	}

	@Function(type=IMMUTABLE, onNullInput=RETURNS_NULL)
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

	@Function(type=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException {
		s_logger.info(typeName + " from SQLInput");
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		m_typeName = typeName;
	}

	@Function(type=IMMUTABLE, onNullInput=RETURNS_NULL)
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

	@Function(type=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		s_logger.info(m_typeName + " to SQLOutput");
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
	}
}
