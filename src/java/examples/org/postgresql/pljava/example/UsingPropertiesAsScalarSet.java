/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This implementation uses another function that returns a set of a complex
 * type, concatenates the name and value of that type and returns this as
 * a set of a scalar type. Somewhat cumbersome way to display properties
 * but it's a good test.
 *
 * @author Thomas Hallgren
 */
public class UsingPropertiesAsScalarSet implements Iterator
{
	private Statement m_statement;
	private ResultSet m_resultSet;
	private String m_nextRow;

	public UsingPropertiesAsScalarSet()
	throws SQLException
	{
		Connection conn = DriverManager.getConnection("jdbc:default:connection");
		m_statement = conn.createStatement();
		m_resultSet = m_statement.executeQuery("SELECT name, value FROM propertyExample()");
		m_nextRow   = null;
	}

	public boolean hasNext()
	{
		if(m_nextRow == null)
		{
			try
			{
				if(m_resultSet == null)
					return false;

				if(m_resultSet.next())
				{
					String key = m_resultSet.getString(1);
					String val = m_resultSet.getString(2);
					m_nextRow = key + " = " + val;
					return true;
				}
				
				try { m_resultSet.close(); } catch(SQLException e) {}
				try { m_statement.close(); } catch(SQLException e) {}
				m_resultSet = null;
				m_statement = null;
			}
			catch(SQLException e)
			{
				throw new RuntimeException(e);
			}
			return false;
		}
		return true;
	}

	public Object next()
	{
		if(!this.hasNext())
			throw new NoSuchElementException();

		String v = m_nextRow;
		m_nextRow = null;
		return v;
	}
	
	public void remove()
	{
		throw new UnsupportedOperationException();
	}

	public static Iterator getProperties()
	throws SQLException
	{
		return new UsingPropertiesAsScalarSet();
	}

}
