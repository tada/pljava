/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import java.util.ArrayList;
import java.util.HashMap;

import org.postgresql.pljava.internal.AclId;
import org.postgresql.pljava.internal.EOXactListener;

/**
 * An instance of this interface reflects the current session. The attribute
 * store is transactional.
 *
 * @author Thomas Hallgren
 */
public class Session extends EOXactListener
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
	public static String getSessionUserName()
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
}
