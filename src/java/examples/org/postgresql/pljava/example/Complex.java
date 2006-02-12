/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.example;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.logging.Logger;

public class Complex implements SQLData
{
	private static Logger s_logger = Logger.getAnonymousLogger();

	private double m_x;
	private double m_y;

	public static Complex parse(String input) throws SQLException
	{
		try
		{
			StreamTokenizer tz = new StreamTokenizer(new StringReader(input));
			if(tz.nextToken() == '('
			&& tz.nextToken() == StreamTokenizer.TT_NUMBER)
			{
				double x = tz.nval;
				if(tz.nextToken() == ','
				&& tz.nextToken() == StreamTokenizer.TT_NUMBER)
				{
					double y = tz.nval;
					if(tz.nextToken() == ')')
					{
						s_logger.info("complex from string");
						return new Complex(x, y);
					}
				}
			}
			throw new SQLException("Unable to parse complex from string \"" + input + '"');
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public Complex()
	{
	}

	public Complex(double x, double y)
	{
		m_x = x;
		m_y = y;
	}

	public String getSQLTypeName() throws SQLException
	{
		return "javatest.complex";
	}

	public void readSQL(SQLInput stream, String typeName) throws SQLException
	{
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		s_logger.info("complex from SQLInput");
	}

	public void writeSQL(SQLOutput stream) throws SQLException
	{
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
		s_logger.info("complex to SQLOutput");
	}

	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append('(');
		sb.append(m_x);
		sb.append(',');
		sb.append(m_y);
		sb.append(')');
		s_logger.info("complex toString");
		return sb.toString();
	}
}
