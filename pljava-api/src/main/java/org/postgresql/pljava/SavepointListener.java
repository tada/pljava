/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava;

import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * Interface for a listener to be notified of the start and pre-commit, commit,
 * or abort of savepoints. To receive such notifications, implement this
 * interface, with the methods that will be called in the cases of interest,
 * and pass an instance to {@link Session#addSavepointListener}. The default
 * implementations of these methods do nothing.
 *<p>
 * It is possible for a listener method to be called with <em>savepoint</em>
 * null, or <em>parent</em> null, or both; that can happen if the application
 * code has not kept a strong reference to the {@code Savepoint} object in
 * question.
 *<p>
 * <code>SavepointListener</code> exposes a
 * <a href=
'http://doxygen.postgresql.org/xact_8h.html#aceb46988cbad720cc8a2d7ac6951f0ef'
>PostgreSQL-specific function</a> that is more internal than the documented
<a href='http://www.postgresql.org/docs/current/static/spi.html'>SPI</a>.
 * @author Thomas Hallgren
 */
public interface SavepointListener
{
	default void onAbort(Session session, Savepoint savepoint, Savepoint parent)
	throws SQLException
	{
	}

	default void onCommit(
		Session session, Savepoint savepoint, Savepoint parent)
	throws SQLException
	{
	}

	default void onStart(Session session, Savepoint savepoint, Savepoint parent)
	throws SQLException
	{
	}

	default void onPreCommit(
		Session session, Savepoint savepoint, Savepoint parent)
	throws SQLException
	{
	}
}
