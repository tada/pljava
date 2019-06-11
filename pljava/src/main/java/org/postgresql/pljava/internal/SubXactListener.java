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

import org.postgresql.pljava.SavepointListener;


/**
 * Class that enables registrations using the PostgreSQL <code>RegisterSubXactCallback</code>
 * function.
 *
 * @author Thomas Hallgren
 */
class SubXactListener
{
	private static final Deque<SavepointListener> s_listeners =
		new ArrayDeque<>();

	static void onAbort(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		// Take a snapshot. Handlers might unregister during event processing
		for ( SavepointListener listener :
			s_listeners.toArray(new SavepointListener[s_listeners.size()]) )
			listener.onAbort(Backend.getSession(), sp, parent);
	}

	static void onCommit(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		for ( SavepointListener listener :
			s_listeners.toArray(new SavepointListener[s_listeners.size()]) )
			listener.onCommit(Backend.getSession(), sp, parent);
	}

	static void onStart(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		for ( SavepointListener listener :
			s_listeners.toArray(new SavepointListener[s_listeners.size()]) )
			listener.onStart(Backend.getSession(), sp, parent);
	}

	static void addListener(SavepointListener listener)
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

	static void removeListener(SavepointListener listener)
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
