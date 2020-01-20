/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.internal;

import static org.postgresql.pljava.internal.Backend.doInPG;

import java.sql.SQLException;

/**
 * The <code>TupleDesc</code> correspons to the internal PostgreSQL
 * <code>TupleDesc</code>.
 *
 * @author Thomas Hallgren
 */
public class TupleDesc
{
	private final State m_state;
	private final int m_size;
	private Class[] m_columnClasses;

	TupleDesc(DualState.Key cookie, long resourceOwner, long pointer, int size)
	throws SQLException
	{
		m_state = new State(cookie, this, resourceOwner, pointer);
		m_size = size;
	}

	private static class State
	extends DualState.SingleFreeTupleDesc<TupleDesc>
	{
		private State(
			DualState.Key cookie, TupleDesc td, long ro, long hth)
		{
			super(cookie, td, ro, hth);
		}

		/**
		 * Return the TupleDesc pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * TupleDesc, or subclasses, or something with a strong reference
		 * to this TupleDesc, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while an instance method's on the call
		 * stack, and the {@code Invocation} marking this state's native scope
		 * can't be popped before return of any method using the value.
		 */
		private long getTupleDescPtr() throws SQLException
		{
			pin();
			try
			{
				return guardedLong();
			}
			finally
			{
				unpin();
			}
		}
	}

	/**
	 * Return pointer to native TupleDesc structure as a long; use only while
	 * a reference to this class is live and the THREADLOCK is held.
	 */
	public final long getNativePointer() throws SQLException
	{
		return m_state.getTupleDescPtr();
	}

	/**
	 * Returns the name of the column at <code>index</code>.
	 * @param index The one based index of the column.
	 * @return The name of the column.
	 * @throws SQLException If the index is out of range for this
	 * tuple descriptor.
	 */
	public String getColumnName(int index)
	throws SQLException
	{
		return doInPG(() -> _getColumnName(this.getNativePointer(), index));
	}

	/**
	 * Returns the index of the column named <code>colName</code>.
	 * @param colName The name of the column.
	 * @return The index for column <code>colName</code>.
	 * @throws SQLException If no column with the given name can
	 * be found in this tuple descriptor.
	 */
	public int getColumnIndex(String colName)
	throws SQLException
	{
		return doInPG(() ->
			_getColumnIndex(this.getNativePointer(), colName.toLowerCase()));
	}

	/**
	 * Creates a <code>Tuple</code> that is described by this descriptor and
	 * initialized with the supplied <code>values</code>.
	 * @return The created <code>Tuple</code>.
	 * @throws SQLException If the length of the values array does not
	 * match the size of the descriptor or if the handle of this descriptor
	 * has gone stale.
	 */
	public Tuple formTuple(Object[] values)
	throws SQLException
	{
		return doInPG(() -> _formTuple(this.getNativePointer(), values));
	}

	/**
	 * Returns the number of columns in this tuple descriptor.
	 */
	public int size()
	{
		return m_size;
	}

	/**
	 * Returns the Java class of the column at index
	 */
	public Class getColumnClass(int index)
	throws SQLException
	{
		if(m_columnClasses == null)
		{
			m_columnClasses = new Class[m_size];
			doInPG(() ->
			{				
				long _this = this.getNativePointer();
				for(int idx = 0; idx < m_size; ++idx)
					m_columnClasses[idx] = _getOid(_this, idx+1).getJavaClass();
			});
		}
		return m_columnClasses[index-1];
	}

	/**
	 * Returns OID of the column type.
	 */
	public Oid getOid(int index)
	throws SQLException
	{
		return doInPG(() -> _getOid(this.getNativePointer(), index));
	}

	private static native String _getColumnName(long _this, int index) throws SQLException;
	private static native int _getColumnIndex(long _this, String colName) throws SQLException;
	private static native Tuple _formTuple(long _this, Object[] values) throws SQLException;
	private static native Oid _getOid(long _this, int index) throws SQLException;
}
