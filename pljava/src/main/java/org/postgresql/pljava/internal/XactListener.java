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

import org.postgresql.pljava.TransactionListener;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.EntryPoints.Invocable;
import static org.postgresql.pljava.internal.Privilege.doPrivileged;

import static java.security.AccessController.getContext;

import java.sql.SQLException;

import java.util.ArrayDeque;
import java.util.Deque;
import static java.util.Objects.requireNonNull;

import static java.util.stream.Collectors.toList;

/**
 * Class that enables registrations using the PostgreSQL
 * {@code RegisterXactCallback} function.
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
	private static final Deque<Invocable<TransactionListener>> s_listeners =
		new ArrayDeque<>();

	static void onAbort() throws SQLException
	{
		invokeListeners(TransactionListener::onAbort);
	}

	static void onCommit() throws SQLException
	{
		invokeListeners(TransactionListener::onCommit);
	}

	static void onPrepare() throws SQLException
	{
		invokeListeners(TransactionListener::onPrepare);
	}

	private static void invokeListeners(
		Checked.BiConsumer<TransactionListener,Session,SQLException> target)
	throws SQLException
	{
		Session session = Backend.getSession();

		// Take a snapshot. Handlers might unregister during event processing
		for ( Invocable<TransactionListener> listener :
			s_listeners.stream().collect(toList()) )
		{
			doPrivileged(() ->
			{
				target.accept(listener.payload, session);
			}, listener.acc);
		}
	}
	
	static void addListener(TransactionListener listener)
	{
		Invocable<TransactionListener> invocable =
			new Invocable<>(requireNonNull(listener), getContext());

		doInPG(() ->
		{
			s_listeners.removeIf(v -> v.payload.equals(listener));
			s_listeners.push(invocable);
			if( 1 == s_listeners.size() )
				_register();
		});
	}
	
	static void removeListener(TransactionListener listener)
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
