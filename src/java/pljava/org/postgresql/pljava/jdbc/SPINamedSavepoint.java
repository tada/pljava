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

public class SPINamedSavepoint extends SPISavepoint
{
	/**
	 * Creates an named savepoint
	 */
	public SPINamedSavepoint(String name)
	throws SQLException
	{
		super(name);
	}

	public int getSavepointId()
	throws SQLException
	{
		throw new SQLException("ID not generated for savepoint");
	}

	public String getSavepointName()
	{
		return this.getSPIName();
	}
}
