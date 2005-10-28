/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Relation</code> correspons to the internal PostgreSQL
 * <code>Relation</code>.
 *
 * @author Thomas Hallgren
 */
public class Relation extends JavaHandle
{
	private TupleDesc m_tupleDesc;

	/**
	 * Returns the name of this <code>Relation</code>.
	 * @throws SQLException
	 */
	public String getName()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getName(this.getNative());
		}
	}

	/**
	 * Returns a descriptor that describes tuples in this <code>Relation</code>.
	 * @throws SQLException
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		if(m_tupleDesc == null)
		{
			synchronized(Backend.THREADLOCK)
			{
				m_tupleDesc = _getTupleDesc(this.getNative());
			}
		}
		return m_tupleDesc;
	}

	/**
	 * Creates a new <code>Tuple</code> by substituting new values for selected columns
	 * copying the columns of the original <code>Tuple</code> at other positions. The
	 * original <code>Tuple</code> is not modified.<br/>
	 * @param original The tuple that serves as the source.
	 * @param fieldNumbers An array of one based indexes denoting the positions that
	 * are to receive modified values.
	 * @param values The array of new values. Each value in this array corresponds to
	 * an index in the <code>fieldNumbers</code> array.
	 * @return A copy of the original with modifications.
	 * @throws SQLException if indexes are out of range or the values illegal.
	 */
	public Tuple modifyTuple(Tuple original, int[] fieldNumbers, Object[] values)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _modifyTuple(this.getNative(), original.getNativePointer(), fieldNumbers, values);
		}
	}

	private static native String _getName(long pointer)
	throws SQLException;

	private static native TupleDesc _getTupleDesc(long pointer)
	throws SQLException;

	private static native Tuple _modifyTuple(long pointer, long original, int[] fieldNumbers, Object[] values)
	throws SQLException;
}
