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
import java.util.HashMap;

import org.postgresql.pljava.SavepointListener;


/**
 * Class that enables registrations using the PostgreSQL <code>RegisterSubXactCallback</code>
 * function.
 *
 * @author Thomas Hallgren
 */
class SubXactListener
{
	private static final HashMap s_listeners = new HashMap();

	static void onAbort(long listenerId, int spId, int parentSpId) throws SQLException
	{
		SavepointListener listener = (SavepointListener)s_listeners.get(new Long(listenerId));
		if(listener != null)
			listener.onAbort(Backend.getSession(), PgSavepoint.forId(spId), PgSavepoint.forId(parentSpId));
	}

	static void onCommit(long listenerId, int spId, int parentSpId) throws SQLException
	{
		SavepointListener listener = (SavepointListener)s_listeners.get(new Long(listenerId));
		if(listener != null)
			listener.onCommit(Backend.getSession(), PgSavepoint.forId(spId), PgSavepoint.forId(parentSpId));
	}

	static void onStart(long listenerId, int spId, int parentSpId) throws SQLException
	{
		SavepointListener listener = (SavepointListener)s_listeners.get(new Long(listenerId));
		if(listener != null)
			listener.onStart(Backend.getSession(), PgSavepoint.forId(spId), PgSavepoint.forId(parentSpId));
	}

	static void addListener(SavepointListener listener)
	{
		synchronized(Backend.THREADLOCK)
		{
			long key = System.identityHashCode(listener);
			if(s_listeners.put(new Long(key), listener) != listener)
				_register(key);
		}
	}

	static void removeListener(SavepointListener listener)
	{
		synchronized(Backend.THREADLOCK)
		{
			long key = System.identityHashCode(listener);
			if(s_listeners.remove(new Long(key)) == listener)
				_unregister(key);
		}
	}

	private static native void _register(long listenerId);

	private static native void _unregister(long listenerId);
}
