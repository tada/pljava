/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.postgresql.pljava.ObjectPool;
import org.postgresql.pljava.SavepointListener;
import org.postgresql.pljava.TransactionListener;
import org.postgresql.pljava.jdbc.SQLUtils;


/**
 * An instance of this interface reflects the current session. The attribute
 * store is transactional.
 *
 * @author Thomas Hallgren
 */
public class Session implements org.postgresql.pljava.Session
{
	private final TransactionalMap m_attributes = new TransactionalMap(new HashMap());

	/**
	 * Adds the specified listener to the list of listeners that will
	 * receive transactional events.
	 */
	public void addTransactionListener(TransactionListener listener)
	{
		XactListener.addListener(listener);
	}

	/**
	 * Adds the specified listener to the list of listeners that will
	 * receive savepoint events.
	 */
	public void addSavepointListener(SavepointListener listener)
	{
		SubXactListener.addListener(listener);
	}

	public Object getAttribute(String attributeName)
	{
		return m_attributes.get(attributeName);
	}

	public ObjectPool getObjectPool(Class cls)
	{
		return ObjectPoolImpl.getObjectPool(cls);
	}

	@Override
	public String getUserName()
	{
		return AclId.getUser().getName();
	}

	@Override
	public String getOuterUserName()
	{
		return AclId.getOuterUser().getName();
	}

	@Override
	public String getSessionUserName()
	{
		return getOuterUserName();
	}


	public void removeAttribute(String attributeName)
	{
		m_attributes.remove(attributeName);
	}

	public void setAttribute(String attributeName, Object value)
	{
		m_attributes.put(attributeName, value);
	}

	/**
	 * Removes the specified listener from the list of listeners that will
	 * receive transactional events.
	 */
	public void removeTransactionListener(TransactionListener listener)
	{
		XactListener.removeListener(listener);
	}

	/**
	 * Removes the specified listener from the list of listeners that will
	 * receive savepoint events.
	 */
	public void removeSavepointListener(SavepointListener listener)
	{
		SubXactListener.removeListener(listener);
	}

	@Override
	public void executeAsSessionUser(Connection conn, String statement)
	throws SQLException
	{
		executeAsOuterUser(conn, statement);
	}

	@Override
	public void executeAsOuterUser(Connection conn, String statement)
	throws SQLException
	{
		Statement stmt = conn.createStatement();
		synchronized(Backend.THREADLOCK)
		{
			ResultSet rs = null;
			AclId outerUser = AclId.getOuterUser();
			AclId effectiveUser = AclId.getUser();
			boolean wasLocalChange = false;
			boolean changeSucceeded = false;
			try
			{
				wasLocalChange = _setUser(outerUser, true);
				changeSucceeded = true;
				if(stmt.execute(statement))
				{
					rs = stmt.getResultSet();
					rs.next();
				}
			}
			finally
			{
				SQLUtils.close(rs);
				SQLUtils.close(stmt);
				if ( changeSucceeded )
					_setUser(effectiveUser, wasLocalChange);
			}
		}
	}

	/**
	 * Return current_schema() as the outer user would see it.
	 * Currently used only in Commands.java. Not made visible API yet
	 * because there <em>has</em> to be a more general way to do this.
	 */
	public String getOuterUserSchema()
	throws SQLException
	{
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		synchronized(Backend.THREADLOCK)
		{
			ResultSet rs = null;
			AclId sessionUser = AclId.getSessionUser();
			AclId effectiveUser = AclId.getUser();
			boolean wasLocalChange = false;
			boolean changeSucceeded = false;
			try
			{
				wasLocalChange = _setUser(sessionUser, true);
				changeSucceeded = true;
				rs = stmt.executeQuery("SELECT current_schema()");
				if ( rs.next() )
					return rs.getString(1);
				throw new SQLException("Unable to obtain current schema");
			}
			finally
			{
				SQLUtils.close(rs);
				SQLUtils.close(stmt);
				if ( changeSucceeded )
					_setUser(effectiveUser, wasLocalChange);
			}
		}
	}

	/**
	 * Called from native code when the JVM is instantiated.
	 */
	static long init()
	throws SQLException
	{
		ELogHandler.init();
		
		// Should be replace with a Thread.getId() once we abandon
		// Java 1.4
		//
		return System.identityHashCode(Thread.currentThread());
	}

	private static native boolean _setUser(AclId userId, boolean isLocalChange);
}
