/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Implementation of {@link ParameterMetaData} for the SPI connection.
 * @author Thomas Hallgren
 */
public class SPIParameterMetaData implements ParameterMetaData
{
	private final int[] m_sqlTypes;
	
	SPIParameterMetaData(int[] sqlTypes)
	{
		m_sqlTypes = sqlTypes;
	}

	public int getParameterCount()
	throws SQLException
	{
		return m_sqlTypes == null ? 0 : m_sqlTypes.length;
	}

	public int isNullable(int arg0)
	throws SQLException
	{
		return parameterNullableUnknown;
	}

	public boolean isSigned(int arg0)
	throws SQLException
	{
		return true;
	}

	public int getPrecision(int arg0)
	throws SQLException
	{
		return 0;
	}

	public int getScale(int arg0)
	throws SQLException
	{
		return 0;
	}

	public int getParameterType(int paramIndex)
	throws SQLException
	{
		if(paramIndex < 1 || paramIndex > this.getParameterCount())
			throw new SQLException("Parameter index out of range");
		return m_sqlTypes[paramIndex-1];
	}

	/**
	 * This feature is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public String getParameterTypeName(int arg0)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Parameter type name support not yet implemented");
	}

	/**
	 * This feature is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public String getParameterClassName(int arg0)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Parameter class name support not yet implemented");
	}

	/**
	 * Returns {@link ParameterMetaData#parameterModeIn} always since this
	 * is the only supported type at this time.
	 */
	public int getParameterMode(int paramIndex) throws SQLException
	{
		if(paramIndex < 1 || paramIndex > this.getParameterCount())
			throw new SQLException("Parameter index out of range");
		return parameterModeIn;
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

	public boolean isWrapperFor(Class<?> iface)
	throws SQLException
	{
	    return iface.isInstance(this);
	}

	public <T> T unwrap(Class<T> iface)
	throws SQLException
	{
	    if ( iface.isInstance(this) )
			return iface.cast(this);
		throw new SQLFeatureNotSupportedException
		( this.getClass().getSimpleName()
		  + " does not wrap " + iface.getName(),
		  "0A000" );
	}
}
