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
import java.util.List;
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
	 * These do not need to match the values of the PostgreSQL enum (which, over
	 * the years, has had members not merely added but reordered). The C code
	 * will map those to these.
	 */
	private static final int COMMIT              = 0;
	private static final int ABORT               = 1;
	private static final int PREPARE             = 2;
	private static final int PRE_COMMIT          = 3;
	private static final int PRE_PREPARE         = 4;
	private static final int PARALLEL_COMMIT     = 5;
	private static final int PARALLEL_ABORT      = 6;
	private static final int PARALLEL_PRE_COMMIT = 7;

	private static final
	List<Checked.BiConsumer<TransactionListener,Session,SQLException>> s_refs =
	List.of(
		TransactionListener::onCommit,
		TransactionListener::onAbort,
		TransactionListener::onPrepare,
		TransactionListener::onPreCommit,
		TransactionListener::onPrePrepare,
		TransactionListener::onParallelCommit,
		TransactionListener::onParallelAbort,
		TransactionListener::onParallelPreCommit
	);

	/*
	 * A non-thread-safe Deque; will be made safe by doing all mutations on the
	 * PG thread (even though actually calling into PG is necessary only when
	 * the size changes from 0 to 1 or 1 to 0).
	 */
	private static final Deque<Invocable<TransactionListener>> s_listeners =
		new ArrayDeque<>();

	private static void invokeListeners(int eventIndex)
	throws SQLException
	{
		Checked.BiConsumer<TransactionListener,Session,SQLException> target =
			s_refs.get(eventIndex);
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
