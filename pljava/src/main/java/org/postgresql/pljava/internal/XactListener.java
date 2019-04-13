/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

import org.postgresql.pljava.TransactionListener;


/**
 * Class that enables registrations using the PostgreSQL <code>RegisterXactCallback</code>
 * function.
 *
 * @author Thomas Hallgren
 */
class XactListener
{
	/*
	 * A non-thread-safe Deque; will be made safe by doing all mutations on the
	 * PG thread (even though actually calling into PG is necessary only when
	 * the size changes from 0 to 1 or 1 to 0).
	 */
	private static final Deque<TransactionListener> s_listeners =
		new ArrayDeque<TransactionListener>();

	static void onAbort() throws SQLException
	{
		for ( TransactionListener listener : s_listeners )
			listener.onAbort(Backend.getSession());
	}

	static void onCommit() throws SQLException
	{
		for ( TransactionListener listener : s_listeners )
			listener.onCommit(Backend.getSession());
	}

	static void onPrepare() throws SQLException
	{
		for ( TransactionListener listener : s_listeners )
			listener.onPrepare(Backend.getSession());
	}
	
	static void addListener(TransactionListener listener)
	{
		synchronized(Backend.THREADLOCK)
		{
			if ( s_listeners.contains(listener) )
				return;
			s_listeners.push(listener);
			if( 1 == s_listeners.size() )
				_register();
		}
	}
	
	static void removeListener(TransactionListener listener)
	{
		synchronized(Backend.THREADLOCK)
		{
			if ( ! s_listeners.remove(listener) )
				return;
			if ( 0 == s_listeners.size() )
				_unregister();
		}
	}

	private static native void _register();

	private static native void _unregister();
}
