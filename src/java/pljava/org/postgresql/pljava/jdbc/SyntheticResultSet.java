/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Constructor;

import org.postgresql.pljava.internal.TypeMap;

/**
 * A Synthetic ResultSet that provides direct access to data stored
 * in a {@link java.util.Vector}. This kind of ResultSet is nothing
 * common with any statement.
 *
 * @author Thomas Hallgren
 */
public class SyntheticResultSet extends ResultSetBase
{
	private final ResultSetField[] m_fields;
	private final ArrayList        m_tuples;
    private final HashMap          m_fieldIndexes;

	SyntheticResultSet(ResultSetField[] fields, ArrayList tuples)
	throws SQLException
	{
		super(tuples.size());
		m_fields = fields;
		m_tuples = tuples;
        m_fieldIndexes = new HashMap();
        int i = m_fields.length;
        while(--i >= 0)
            m_fieldIndexes.put(m_fields[i].getColumnLabel(), new Integer(i+1));
	}

    public Statement getStatement()
    throws SQLException
    {
        return null;
    }

    public void close()
	throws SQLException
	{
    	m_tuples.clear();
		super.close();
	}

	public String getCursorName()
	throws SQLException
	{
		throw new SQLException(
            "A synthetic ResultSet is not associated with a cursor");
	}

	public int findColumn(String columnName)
	throws SQLException
	{
        Integer idx = (Integer)m_fieldIndexes.get(columnName);
        if(idx != null)
        {
            return idx.intValue();
        }
        throw new SQLException("No such field: '" + columnName + "'");
	}

	protected Object getObjectValue(int columnIndex)
	throws SQLException
	{
        return
            convertToJavaObject(
                ((byte[][]) getCurrentRow())[columnIndex-1],
                TypeMap.getClassNameFromPgOid(
                    m_fields[columnIndex-1].getOID()));
	}

    /**
     * Converts a string (stored in a byte array) to a java object.
     * If the class of the name className does not exist or does not contain
     * a constructor <classname>(java.lang.String), an exception is thrown.
     */
    protected Object convertToJavaObject(byte[] byteArray, String className)
    throws SQLException
    {
        if (byteArray == null)
        {
            return null;
        }

        try
        {
            Class cls = Class.forName(className);
            Class[] prototype = {String.class};
            Object[] args = {new String(byteArray)};
            Constructor c = cls.getConstructor(prototype);

            return c.newInstance(args);
        }
        catch (Exception e) {
            throw new SQLException(
                "Unable to convert java.lang.String to " + className + ": " +
                e.getMessage());
        }
    }

    protected final Object getCurrentRow()
	throws SQLException
	{
    	int row = this.getRow();
		if(row < 1 || row > m_tuples.size())
			throw new SQLException("ResultSet is not positioned on a valid row");
		return m_tuples.get(row-1);
	}

	public boolean isLast() throws SQLException
	{
		return this.getRow() == m_tuples.size();
	}

	public boolean next() throws SQLException
	{
    	int row = this.getRow();
		if(row < m_tuples.size())
		{
			this.setRow(row+1);
			return true;
		}
		return false;
	}
}
