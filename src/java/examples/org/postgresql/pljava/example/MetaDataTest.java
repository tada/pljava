/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.example;

import java.sql.*;
import org.postgresql.pljava.ResultSetProvider;

/**
 * @author Filip Hrbek Note: ResultSetProvider interface implementation is
 *         needed only for getTypeInfo method
 */
public class MetaDataTest implements ResultSetProvider
{
	public static String getDatabaseProductName() throws SQLException
	{
		Connection conn = DriverManager
			.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		return md.getDatabaseProductName();
	}

	public static String getDatabaseProductVersion() throws SQLException
	{
		Connection conn = DriverManager
			.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		return md.getDatabaseProductVersion();
	}

	public static String getSchemas() throws SQLException
	{
		Connection conn = DriverManager
			.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		ResultSet rs = md.getSchemas();

		StringBuffer buf = new StringBuffer();

		while(rs.next())
		{
			buf.append(rs.getString(1) + ";");
		}

		rs.close();

		return buf.toString();
	}

	public static String getTableTypes() throws SQLException
	{
		Connection conn = DriverManager
			.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		ResultSet rs = md.getTableTypes();

		StringBuffer buf = new StringBuffer();

		while(rs.next())
		{
			buf.append(rs.getString(1) + ";");
		}

		rs.close();

		return buf.toString();
	}

	ResultSet typeInfo;

	public MetaDataTest() throws SQLException
	{
		Connection conn = DriverManager
			.getConnection("jdbc:default:connection");
		DatabaseMetaData md = conn.getMetaData();
		typeInfo = md.getTypeInfo();
	}

	public boolean assignRowValues(ResultSet receiver, int currentRow)
	throws SQLException
	{
		if(typeInfo.next())
		{
			receiver.updateString(1, typeInfo.getString(1));
			receiver.updateInt(2, typeInfo.getInt(2));
			receiver.updateInt(3, typeInfo.getInt(3));
			receiver.updateString(4, typeInfo.getString(4));
			receiver.updateString(5, typeInfo.getString(5));
			receiver.updateString(6, typeInfo.getString(6));
			receiver.updateShort(7, typeInfo.getShort(7));
			receiver.updateBoolean(8, typeInfo.getBoolean(8));
			receiver.updateShort(9, typeInfo.getShort(9));
			receiver.updateBoolean(10, typeInfo.getBoolean(10));
			receiver.updateBoolean(11, typeInfo.getBoolean(11));
			receiver.updateBoolean(12, typeInfo.getBoolean(12));
			receiver.updateString(13, typeInfo.getString(13));
			receiver.updateShort(14, typeInfo.getShort(14));
			receiver.updateShort(15, typeInfo.getShort(15));
			receiver.updateInt(16, typeInfo.getInt(16));
			receiver.updateInt(17, typeInfo.getInt(17));
			receiver.updateInt(18, typeInfo.getInt(18));

			return true;
		}
		return false;
	}

	public static ResultSetProvider getTypeInfo() throws SQLException
	{
		try
		{
			return new MetaDataTest();
		}
		catch(SQLException e)
		{
			throw new SQLException("Error reading DatabaseMetaData", e
				.getMessage());
		}
	}
}
