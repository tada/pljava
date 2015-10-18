/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 */
package org.postgresql.pljava.example;

import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.logging.Logger;

public class ComplexTuple implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	public static ComplexTuple logAndReturn(ComplexTuple cpl) {
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
