/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>Relation</code> correspons to the internal PostgreSQL
 * <code>Relation</code>.
 *
 * @author Thomas Hallgren
 */
public class Relation extends NativeStruct
{
	/**
	 * Returns the name of this <code>Relation</code>.
	 * @throws SQLException
	 */
	public String getName()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getName();
		}
	}

	/**
	 * Returns a descriptor that describes tuples in this <code>Relation</code>.
	 * @throws SQLException
	 */
	public TupleDesc getTupleDesc()
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return this._getTupleDesc();
		}
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
			return this._modifyTuple(original, fieldNumbers, values);
		}
	}

	private native String _getName()
	throws SQLException;

	private native TupleDesc _getTupleDesc()
	throws SQLException;

	private native Tuple _modifyTuple(Tuple original, int[] fieldNumbers, Object[] values)
	throws SQLException;
}
