/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Filip Hrbek
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A Synthetic ResultSet that provides direct access to data stored
 * in a {@link java.util.ArrayList}; chiefly used to return tabular information
 * from {@code ...MetaData} objects. This kind of ResultSet has nothing
 * common with any statement.
 *
 * @author Filip Hrbek
 */
public class SyntheticResultSet extends ResultSetBase
{
	private final ResultSetField[]        m_fields;
	private final ArrayList<Object[]>     m_tuples;
	private final HashMap<String,Integer> m_fieldIndexes;

	/**
	 * Construct a {@code SyntheticResultSet} whose column types are described
	 * by an array of {@code ResultSetField} instances, and whose rows are
	 * supplied as an {@code ArrayList} whose elements are themselves arrays of
	 * {@code Object}.
	 * @throws SQLException if a non-null reference at index <em>j</em> in any
	 * 'row' array is an instance of a class that does not satisfy the
	 * {@link ResultSetField#canContain canContain} method of the
	 * {@code ResultSetField} instance at index <em>j</em>.
	 */
	SyntheticResultSet(ResultSetField[] fields, ArrayList<Object[]> tuples)
	throws SQLException
	{
		super(tuples.size());
		m_fields = fields;
		m_tuples = tuples;
        m_fieldIndexes = new HashMap<>();
        int i = m_fields.length;
        while(--i >= 0)
            m_fieldIndexes.put(m_fields[i].getColumnLabel(), i+1);

		Object[][] tupleTest = (Object[][]) m_tuples.toArray(new Object[0][]);
		Object value;
		for (i=0; i < tupleTest.length; i++)
		{
			int j = m_fields.length;
			while(--j >= 0)
			{
				value = tupleTest[i][j];
				if (value != null && !m_fields[j].canContain(value.getClass()))
				{
					throw new SQLException(
						"Unable to store class " + value.getClass() +
						" in ResultSetField '" + m_fields[j].getColumnLabel() + "'" +
						" with OID " + m_fields[j].getOID() +
						" (expected class: " + m_fields[j].getJavaClass() + ")");
				}
			}
		}
	}

    @Override
	public void close()
	throws SQLException
	{
    	m_tuples.clear();
		super.close();
	}

	@Override
	public int findColumn(String columnName)
	throws SQLException
	{
        Integer idx = m_fieldIndexes.get(columnName.toUpperCase());
        if(idx != null)
        {
            return idx;
        }
        throw new SQLException("No such field: '" + columnName + "'");
	}

	/**
	 * Returns exactly the object that was supplied at {@code columnIndex}
	 * (less one) in the current row.
	 *<p>
	 * Ignores the {@code type} argument and returns whatever object is there.
	 * If it is not what the caller needed, let the caller complain.
	 */
	@Override // defined in ObjectResultSet
	protected Object getObjectValue(int columnIndex, Class<?> type)
	throws SQLException
	{
        return getCurrentRow()[columnIndex-1];
	}

    protected final Object[] getCurrentRow()
	throws SQLException
	{
    	int row = this.getRow();
		if(row < 1 || row > m_tuples.size())
			throw new SQLException("ResultSet is not positioned on a valid row");
		return m_tuples.get(row-1);
	}

	@Override
	public boolean isLast() throws SQLException
	{
		return this.getRow() == m_tuples.size();
	}

	@Override
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

	/**
	 * Returns metadata describing this {@code SyntheticResultSet}, based on the
	 * {@link ResultSetField ResultSetField}s supplied to the constructor.
	 */
	@Override
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		return new SyntheticResultSetMetaData(m_fields);
	}
}
