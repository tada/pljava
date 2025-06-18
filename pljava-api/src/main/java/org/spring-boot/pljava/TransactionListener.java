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

/**
 * Interface for a listener to be notified of prepare, and commit, abort,
 * or other phase transitions, of distributed transactions. To receive
 * such notifications, implement this interface, with the methods that
 * will be called in the cases of interest, and pass an instance to
 * {@link Session#addTransactionListener}. The default implementations of these
 * methods do nothing.
 *
 * <code>TransactionListener</code> exposes a
 * <a href=
'http://doxygen.postgresql.org/xact_8h.html#ac0fc861f3ec869429aba4bb97a5b72b8'
>PostgreSQL-specific function</a> that is more internal than the documented
<a href='http://www.postgresql.org/docs/current/static/spi.html'>SPI</a>.
 * @author Thomas Hallgren
 */
public interface TransactionListener
{
	default void onAbort(Session session) throws SQLException { }

	default void onCommit(Session session) throws SQLException { }

	default void onPrepare(Session session) throws SQLException { }

	default void onPreCommit(Session session) throws SQLException { }

	default void onPrePrepare(Session session) throws SQLException { }

	default void onParallelCommit(Session session) throws SQLException { }

	default void onParallelAbort(Session session) throws SQLException { }

	default void onParallelPreCommit(Session session) throws SQLException { }
}
