/*
 * Copyright (c) 2004 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root directory of this distribution or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetProvider;

public class BinaryColumnTest implements ResultSetProvider
{
	public boolean assignRowValues(ResultSet rs, int rowCount)
	throws SQLException
	{
		try
		{
			if(rowCount >= 100)
				return false;

			int offset = rowCount * 100;
			ByteArrayOutputStream bld = new ByteArrayOutputStream();
			DataOutputStream da = new DataOutputStream(bld);
			for(int idx = 0; idx < 100; ++idx)
				da.writeInt(offset + idx);
			byte[] bytes = bld.toByteArray();
			ByteArrayInputStream input = new ByteArrayInputStream(bytes);
			rs.updateBinaryStream(1, input, bytes.length);
			rs.updateBytes(2, bytes);
			return true;
		}
		catch(IOException e)
		{
			throw new SQLException(e.getMessage());
		}
	}

	public void close() throws SQLException
	{
	}

	public static ResultSetProvider getBinaryPairs()
	{
		return new BinaryColumnTest();
	}
}
