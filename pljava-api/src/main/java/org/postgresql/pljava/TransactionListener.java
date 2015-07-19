/*
 * Copyright (c) 2004-2015 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava;

import java.sql.SQLException;

/**
 * Interface for a listener to be notified of prepare, and commit or abort, of
 * distributed transactions. To receive such notifications, implement this
 * interface, with the three methods that will be called in those three cases,
 * and pass an instance to {@link Session#addTransactionListener}.
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
	void onAbort(Session session) throws SQLException;

	void onCommit(Session session) throws SQLException;

	void onPrepare(Session session) throws SQLException;
}
