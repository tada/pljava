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
import java.lang.reflect.InvocationTargetException;

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
	private final Constructor[] m_fieldCreators;
	private final ArrayList     m_tuples;
    private final HashMap       m_fieldIndexes;

	SyntheticResultSet(ResultSetField[] fields, ArrayList tuples)
	throws SQLException
	{
		super(tuples.size());
        int i = fields.length;
		m_fieldCreators = new Constructor[i];
		m_tuples = tuples;
        m_fieldIndexes = new HashMap();
        String className = null;

        Class[] prototype = {String.class};
        try
        {
	        while(--i >= 0)
	        {
	        	ResultSetField field = fields[i];
	        	className = TypeMap.getClassNameFromPgOid(field.getOID());
	        	m_fieldIndexes.put(field.getColumnLabel(), new Integer(i+1));
	        	if(className.equals("java.lang.String"))
	        		m_fieldCreators[i] = null;
	        	else
	        	{
	        		Class cls = Class.forName(className);
	        		m_fieldCreators[i] = cls.getConstructor(prototype);
	        	}
	        }
		}
		catch(ClassNotFoundException e)
		{
			throw new SQLException("Unable to load field class: " + e.getMessage());
		}
		catch(SecurityException e)
		{
			throw new SQLException("Constructor in field class " +
				className + " is not public");
		}
		catch(NoSuchMethodException e)
		{
			throw new SQLException("Unable to find constructor " +
				className + "(String)");
		}
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
                ((byte[][]) getCurrentRow())[columnIndex-1], columnIndex);
	}

    /**
     * Converts a string (stored in a byte array) to a java object.
     */
    protected Object convertToJavaObject(byte[] byteArray, int columnIndex)
    throws SQLException
    {
        if (byteArray == null)
            return null;

    	String str = new String(byteArray);
    	Constructor ctor = m_fieldCreators[columnIndex-1];
    	if(ctor == null)
    		//
    		// Should remain a String
    		//
    		return str;

        try
        {
            return ctor.newInstance(new Object[] { str });
        }
        catch (RuntimeException e) {
            throw new SQLException(
                "Unable to convert java.lang.String to " + 
                ctor.getDeclaringClass() + ": " +
                e.getMessage());
        }
		catch(InstantiationException e)
		{
			throw new SQLException("Class " +
				ctor.getDeclaringClass() +
				" is abstract");
		}
		catch(IllegalAccessException e)
		{
			throw new SQLException("Class " +
				ctor.getDeclaringClass() +
				" is not public");
		}
		catch(InvocationTargetException e)
		{
			Throwable t = e.getTargetException();
			if(t instanceof SQLException)
				throw (SQLException)t;
			throw new SQLException(
				"Unable to convert java.lang.String to " +
				ctor.getDeclaringClass() +
				": " + t.getMessage());
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
