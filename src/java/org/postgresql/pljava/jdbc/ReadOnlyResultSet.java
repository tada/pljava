/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * The ReadOnlyResultSet implements all methods that changes the ResultSet
 * in any way as methods that yield an {@link UnsupportedFeatureException}.
 *
 * @author Thomas Hallgren
 */
public abstract class ReadOnlyResultSet extends ObjectResultSet
{
	/**
	 * Returns {@link ResultSet#CONCUR_READ_ONLY}.
	 */
	public int getConcurrency()
	throws SQLException
	{
		return ResultSet.CONCUR_READ_ONLY;
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void cancelRowUpdates()
	throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void deleteRow()
	throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void insertRow()
	throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * This is a no-op since the <code>moveToInsertRow()</code> method is
	 * unsupported.
	 */
	public void moveToCurrentRow()
	throws SQLException
	{
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void moveToInsertRow()
	throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void updateRow()
	throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * Always returns false.
	 */
	public boolean rowDeleted()
	throws SQLException
	{
		return false;
	}

	/**
	 * Always returns false.
	 */
	public boolean rowInserted()
	throws SQLException
	{
		return false;
	}

	/**
	 * Always returns false.
	 */
	public boolean rowUpdated()
	throws SQLException
	{
		return false;
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void updateObject(int columnIndex, Object x) throws SQLException
	{
		throw readOnlyException();
	}

	/**
	 * This feature is not supported on a <code>ReadOnlyResultSet</code>.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void updateObject(int columnIndex, Object x, int scale)
	throws SQLException
	{
		throw readOnlyException();
	}

	private static SQLException readOnlyException()
	{
		return new UnsupportedFeatureException("ResultSet is read-only");
	}
}
