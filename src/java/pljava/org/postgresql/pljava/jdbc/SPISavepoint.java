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
import org.postgresql.pljava.internal.Savepoint;
import java.util.logging.Logger;

import org.postgresql.pljava.internal.Backend;

/**
 * @author Thomas Hallgren
 */
public abstract class SPISavepoint implements java.sql.Savepoint
{
	private Savepoint m_internalSavepoint;

	private static final Logger s_log = Logger.getAnonymousLogger();

	SPISavepoint(String name)
	throws SQLException
	{
		m_internalSavepoint = Savepoint.set(name);
	}

	public void onInvocationExit()
	throws SQLException
	{
		if(m_internalSavepoint == null)
			return;

		Connection conn = SPIDriver.getDefault();
		String spiName = m_internalSavepoint.getName();
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

	final void release()
	throws SQLException
	{
		if(m_internalSavepoint != null)
		{
			m_internalSavepoint.release();
			m_internalSavepoint = null;
		}
	}

	final void rollback()
	throws SQLException
	{
		if(m_internalSavepoint != null)
		{
			m_internalSavepoint.rollback();
			m_internalSavepoint = null;
		}
	}

	/**
	 * @return Returns the active.
	 */
	final boolean isActive()
	{
		return m_internalSavepoint != null;
	}

	final String getSPIName()
	{
		return (m_internalSavepoint == null)
			? "inactive savepoint"
			: m_internalSavepoint.getName(); 
	}
}
