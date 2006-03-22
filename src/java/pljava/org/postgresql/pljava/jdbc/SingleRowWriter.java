/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;

import org.postgresql.pljava.internal.Tuple;
import org.postgresql.pljava.internal.TupleDesc;

/**
 * A single row, updateable ResultSet, specially made for functions and
 * procedures that returns complex types or sets.
 *
 * @author Thomas Hallgren
 */
public class SingleRowWriter extends SingleRowResultSet
{
	private final TupleDesc m_tupleDesc;
	private final Object[] m_values;

	public SingleRowWriter(TupleDesc tupleDesc)
	throws SQLException
	{
		m_tupleDesc = tupleDesc;
		m_values = new Object[tupleDesc.size()];
	}

	protected Object getObjectValue(int columnIndex)
	throws SQLException
	{
		if(columnIndex < 1)
			throw new SQLException("System columns cannot be obtained from this type of ResultSet");
		return m_values[columnIndex - 1];
	}

	/**
	 * Returns <code>true</code> if the row contains any non <code>null</code>
	 * values since all values of the row are <code>null</code> initially.
	 */
	public boolean rowUpdated()
	throws SQLException
	{
		int top = m_values.length;
		while(--top >= 0)
			if(m_values[top] != null)
				return true;
		return false;
	}

	public void updateObject(int columnIndex, Object x)
	throws SQLException
	{
		if(columnIndex < 1)
			throw new SQLException("System columns cannot be updated");

		if(x == null)
			m_values[columnIndex-1] = x;

		Class c = m_tupleDesc.getColumnClass(columnIndex);
		if(!c.isInstance(x)
		&& !(c == byte[].class && (x instanceof BlobValue)))
		{
			if(Number.class.isAssignableFrom(c))
				x = SPIConnection.basicNumericCoersion(c, x);
			else
			if(Time.class.isAssignableFrom(c)
			|| Date.class.isAssignableFrom(c)
			|| Timestamp.class.isAssignableFrom(c))
				x = SPIConnection.basicCalendricalCoersion(c, x, Calendar.getInstance());
			else
				x = SPIConnection.basicCoersion(c, x);
		}
		m_values[columnIndex-1] = x;
	}

	public void cancelRowUpdates()
	throws SQLException
	{
		Arrays.fill(m_values, null);
	}

	public void copyRowFrom(ResultSet rs)
	throws SQLException
	{
		int top = m_values.length;
		for(int idx = 0; idx < top; ++idx)
			m_values[idx] = rs.getObject(idx+1);
	}

	/**
	 * Creates a tuple from the current row values and then cancel all row
	 * updates to prepare for a new row.
	 * @return The Tuple reflecting the current row values.
	 * @throws SQLException
	 */
	public Tuple getTupleAndClear()
	throws SQLException
	{
		Tuple tuple = this.getTupleDesc().formTuple(m_values);
		Arrays.fill(m_values, null);
		return tuple;
	}

	protected final TupleDesc getTupleDesc()
	{
		return m_tupleDesc;
	}
}
