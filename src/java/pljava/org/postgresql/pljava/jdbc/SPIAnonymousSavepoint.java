/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */

public class SPIAnonymousSavepoint extends SPISavepoint
{
	private static int s_savePointId = 1;

	private final int    m_identity;

	/**
	 * Creates an unnamed savepoint
	 */
	public SPIAnonymousSavepoint()
	throws SQLException
	{
		super("auto_sp_" + s_savePointId);
		m_identity = s_savePointId++;
	}

	public int getSavepointId()
	{
		return m_identity;
	}

	public String getSavepointName() throws SQLException
	{
		throw new SQLException("not a named savepoint");
	}
}
