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
import java.sql.Savepoint;
import java.util.logging.Logger;

/**
 * @author Thomas Hallgren
 */
public abstract class SPISavepoint implements Savepoint
{
	private static final Logger s_log = Logger.getAnonymousLogger();

	abstract String getSPIName();

	public void onInvocationExit()
	throws SQLException
	{
		s_log.warning("Releasing savepoint '" + this.getSPIName() +
			"' since its lifespan exceeds that of the function where it was set");
		SPIDriver.getDefault().releaseSavepoint(this);
	}
}
