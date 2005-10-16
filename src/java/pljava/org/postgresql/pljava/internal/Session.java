/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.postgresql.pljava.ObjectPool;


/**
 * An instance of this interface reflects the current session. The attribute
 * store is transactional.
 *
 * @author Thomas Hallgren
 */
public class Session implements EOXactListener, org.postgresql.pljava.Session
{
	private final ArrayList m_xactListeners = new ArrayList();
	private final TransactionalMap m_attributes = new TransactionalMap(new HashMap());

	/**
	 * Adds the specified listener to the list of listeners that will
	 * receive transactional events.
	 */
	public void addTransactionListener(TransactionListener listener)
	{
		if(!m_xactListeners.contains(listener))
			m_xactListeners.add(listener);
	}

	public Object getAttribute(String attributeName)
	{
		return m_attributes.get(attributeName);
	}

	/**
	 * Returns the list of listeners that will receive transactional events.
	 */
	public TransactionListener[] getTransactionListeners()
	{
		return (TransactionListener[])m_xactListeners.toArray(
				new TransactionListener[m_xactListeners.size()]);
	}

	public ObjectPool getObjectPool(Class cls)
	{
		return ObjectPoolImpl.getObjectPool(cls);
	}

	/**
	 * Return the current user.
	 */
	public String getUserName()
	{
		return AclId.getUser().getName();
	}

	/**
	 * Return the session user.
	 */
	public String getSessionUserName()
	{
		return AclId.getSessionUser().getName();
	}

	
	public void onEOXact(boolean isCommit)
	{
		if(isCommit)
			m_attributes.commit();
		else
			m_attributes.abort();

		int top = m_xactListeners.size();
		if(top == 0)
			return;

		TransactionEvent te = new TransactionEvent(this);

		// Take a snapshot. Handlers might unregister during event processing
		//
		TransactionListener[] listeners = this.getTransactionListeners();
		if(isCommit)
		{
			for(int idx = 0; idx < top; ++idx)
				listeners[idx].afterCommit(te);
		}
		else
		{
			for(int idx = 0; idx < top; ++idx)
				listeners[idx].afterAbort(te);
		}	
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
		m_xactListeners.remove(listener);
	}

	public boolean executeAsSessionUser(Connection conn, String statement)
	throws SQLException
	{
		Statement stmt = conn.createStatement();
		synchronized(Backend.THREADLOCK)
		{
			AclId sessionUser = AclId.getSessionUser();
			AclId effectiveUser = AclId.getUser();
			if(sessionUser.equals(effectiveUser))
				return stmt.execute(statement);

			try
			{
				_setUser(sessionUser);
				return stmt.execute(statement);
			}
			finally
			{
				_setUser(effectiveUser);
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

	private static native void _setUser(AclId userId);
}
