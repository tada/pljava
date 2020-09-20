/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.SavepointListener;
import org.postgresql.pljava.Session;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.EntryPoints.Invocable;
import static org.postgresql.pljava.internal.Privilege.doPrivileged;

import static java.security.AccessController.getContext;

import java.sql.Savepoint;
import java.sql.SQLException;

import java.util.ArrayDeque;
import java.util.Deque;
import static java.util.Objects.requireNonNull;

import static java.util.stream.Collectors.toList;

/**
 * Class that enables registrations using the PostgreSQL
 * {@code RegisterSubXactCallback} function.
 *
 * @author Thomas Hallgren
 */
class SubXactListener
{
	@FunctionalInterface
	private interface Target
	{
		void accept(SavepointListener l, Session s, Savepoint sp, Savepoint p)
		throws SQLException;
	}

	/*
	 * A non-thread-safe Deque; will be made safe by doing all mutations on the
	 * PG thread (even though actually calling into PG is necessary only when
	 * the size changes from 0 to 1 or 1 to 0).
	 */
	private static final Deque<Invocable<SavepointListener>> s_listeners =
		new ArrayDeque<>();

	static void onAbort(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		invokeListeners(SavepointListener::onAbort, sp, parent);
	}

	static void onCommit(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		invokeListeners(SavepointListener::onCommit, sp, parent);
	}

	static void onStart(PgSavepoint sp, PgSavepoint parent)
	throws SQLException
	{
		invokeListeners(SavepointListener::onStart, sp, parent);
	}

	private static void invokeListeners(
		Target target, Savepoint sp, Savepoint parent)
	throws SQLException
	{
		Session session = Backend.getSession();

		// Take a snapshot. Handlers might unregister during event processing
		for ( Invocable<SavepointListener> listener :
			s_listeners.stream().collect(toList()) )
		{
			doPrivileged(() ->
			{
				target.accept(listener.payload, session, sp, parent);
			}, listener.acc);
		}
	}

	static void addListener(SavepointListener listener)
	{
		Invocable<SavepointListener> invocable =
			new Invocable<>(requireNonNull(listener), getContext());

		doInPG(() ->
		{
			s_listeners.removeIf(v -> v.payload.equals(listener));
			s_listeners.push(invocable);
			if( 1 == s_listeners.size() )
				_register();
		});
	}

	static void removeListener(SavepointListener listener)
	{
		doInPG(() ->
		{
			if ( ! s_listeners.removeIf(v -> v.payload.equals(listener)) )
				return;
			if ( 0 == s_listeners.size() )
				_unregister();
		});
	}

	private static native void _register();

	private static native void _unregister();
}
