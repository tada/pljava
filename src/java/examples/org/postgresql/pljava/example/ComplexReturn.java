/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.example;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.postgresql.pljava.ResultSetProvider;

/**
 * @author Thomas Hallgren
 */
public class ComplexReturn implements ResultSetProvider
{
	private final int m_base;
	private final int m_increment;
	
	public ComplexReturn(int base, int increment)
	{
		m_base = base;
		m_increment = increment;
	}

	public boolean assignRowValues(ResultSet receiver, int currentRow)
	throws SQLException
	{
		// Stop when we reach 12 rows.
		//
		if(currentRow >= 12)
			return false;

		receiver.updateInt(1, m_base);
		receiver.updateInt(2, m_base + m_increment * currentRow);
		receiver.updateTimestamp(3, new Timestamp(System.currentTimeMillis()));
		return true;
	}

	public static ResultSetProvider setReturn(int base, int increment)
	throws SQLException
	{
		return new ComplexReturn(base, increment);
	}

	public static boolean complexReturn(int base, int increment, ResultSet receiver)
	throws SQLException
	{
		receiver.updateInt(1, base);
		receiver.updateInt(2, base + increment);
		receiver.updateTimestamp(3, new Timestamp(System.currentTimeMillis()));
		return true;
	}
	
	public static String makeString(ResultSet _testSetReturn)
	throws SQLException
	{
		int base = _testSetReturn.getInt(1);
		int incbase = _testSetReturn.getInt(2);
		Timestamp ctime = _testSetReturn.getTimestamp(3);
		return "Base = \"" + base +
			"\", incbase = \"" + incbase +
			"\", ctime = \"" + ctime + "\"";
	}
}
