/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

/**
 *
 * @author Thomas Hallgren
 */
public class SPIDriver implements Driver
{
	private static final Connection s_defaultConn = new SPIConnection();
	private static final DriverPropertyInfo[] s_noInfo = new DriverPropertyInfo[0];

	static
	{
		try
		{
			DriverManager.registerDriver(new SPIDriver());
		}
		catch(SQLException e)
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	public Connection connect(String url, Properties info)
	throws SQLException
	{
		return this.acceptsURL(url)
			? s_defaultConn
			: null;
	}

	public boolean acceptsURL(String url)
	throws SQLException
	{
		return "jdbc:default:connection".equals(url);
	}

	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
	throws SQLException
	{
		return s_noInfo;
	}

	public int getMajorVersion()
	{
		return 1;
	}

	public int getMinorVersion()
	{
		return 0;
	}

	public boolean jdbcCompliant()
	{
		return false;	// Not all functionality is supported at present.
	}
}