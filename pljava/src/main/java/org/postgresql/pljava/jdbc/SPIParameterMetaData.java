/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Copyright (c) 2010, 2011 PostgreSQL Global Development Group
 * 
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://wiki.tada.se/index.php?title=PLJava_License
 */
package org.postgresql.pljava.jdbc;

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 *
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
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	public boolean isWrapperFor(Class<?> iface)
	throws SQLException
	{
	    throw new SQLFeatureNotSupportedException
		( this.getClass()
		  + ".isWrapperFor( Class<?> ) not implemented yet.",
		  "0A000" );
	}

	public <T> T unwrap(Class<T> iface)
	throws SQLException
	{
	    throw new SQLFeatureNotSupportedException
		( this.getClass()
		  + ".unwrapClass( Class<?> ) not implemented yet.",
		  "0A000" );
	}
}
