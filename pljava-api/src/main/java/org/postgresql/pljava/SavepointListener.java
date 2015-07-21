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
import java.sql.Savepoint;

/**
 * Interface for a listener to be notified of the start and commit or abort of
 * savepoints. To receive such notifications, implement this interface, with
 * the three methods that will be called in those three cases, and pass an
 * instance to {@link Session#addSavepointListener}.
 *
 * <code>SavepointListener</code> exposes a
 * <a href=
'http://doxygen.postgresql.org/xact_8h.html#aceb46988cbad720cc8a2d7ac6951f0ef'
>PostgreSQL-specific function</a> that is more internal than the documented
<a href='http://www.postgresql.org/docs/current/static/spi.html'>SPI</a>.
 * @author Thomas Hallgren
 */
public interface SavepointListener
{
	void onAbort(Session session, Savepoint savepoint, Savepoint parent) throws SQLException;

	void onCommit(Session session, Savepoint savepoint, Savepoint parent) throws SQLException;

	void onStart(Session session, Savepoint savepoint, Savepoint parent) throws SQLException;
}
