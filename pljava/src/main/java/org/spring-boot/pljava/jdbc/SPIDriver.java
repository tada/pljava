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
 */
package org.postgresql.pljava.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Implementation of {@link Driver} for the SPI connection.
 * @author Thomas Hallgren
 */
public class SPIDriver implements Driver
{
	private static final Logger s_logger = Logger.getLogger(
		"org.postgresql.pljava.jdbc" );
	private static final String s_defaultURL = "jdbc:default:connection";
	private static final int s_defaultURLLen = s_defaultURL.length();

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
		if(url.startsWith(s_defaultURL))
		{
			// Accept extra info at end provided the defaultURL is
			// delimited by a ':'
			//
			if(url.length() == s_defaultURLLen || url.charAt(s_defaultURLLen) == ':')
				return true;
		}
		return false;
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
	
	static Connection getDefault()
	{
		return s_defaultConn;
	}

	public Logger getParentLogger() throws SQLFeatureNotSupportedException
	{
	  return s_logger;
	}
}
