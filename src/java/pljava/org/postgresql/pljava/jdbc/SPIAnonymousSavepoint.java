/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
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
