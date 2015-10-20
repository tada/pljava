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
import org.postgresql.pljava.annotation.SQLType;

@MappedUDT(schema="javatest", name="complextuple", provides="complextuple type",
structure={
	"x float8",
	"y float8"
})
public class ComplexTuple implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	@Function(schema="javatest", name="logcomplex",
		type=Function.Type.IMMUTABLE,
		onNullInput=Function.OnNullInput.RETURNS_NULL,
		complexType="javatest.complextuple", requires="complextuple type")
	public static ComplexTuple logAndReturn(
		@SQLType("javatest.complextuple") ComplexTuple cpl) {
		s_logger.info(cpl.getSQLTypeName() + "(" + cpl.m_x + ", " + cpl.m_y
				+ ")");
		return cpl;
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
