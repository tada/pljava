/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.logging.Logger;

import org.postgresql.pljava.internal.Backend;

/**
 * @author Thomas Hallgren
 */
public abstract class SPISavepoint implements Savepoint
{
	private boolean m_active = false;

	private static final Logger s_log = Logger.getAnonymousLogger();

	abstract String getSPIName();

	public void onInvocationExit()
	throws SQLException
	{
		if(!this.isActive())
			return;

		Connection conn = SPIDriver.getDefault();
		String spiName = this.getSPIName();
		if(Backend.isReleaseLingeringSavepoints())
		{
			s_log.warning("Releasing savepoint '" + spiName +
				"' since its lifespan exceeds that of the function where it was set");
			conn.releaseSavepoint(this);
		}
		else
		{
			s_log.warning("Rolling back to savepoint '" + spiName +
				"' since its lifespan exceeds that of the function where it was set");
			conn.rollback(this);
		}
	}

	/**
	 * @return Returns the active.
	 */
	final boolean isActive()
	{
		return m_active;
	}
	/**
	 * @param active The active to set.
	 */
	final void setActive(boolean active)
	{
		m_active = active;
	}
}
