/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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

import static java.util.Arrays.copyOfRange;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;

import static org.postgresql.pljava.jdbc.Invocation.s_unhandled;

/**
 * A Java exception constructed over a {@link ServerException} that has been
 * thrown but not recovered from (as by rolling back to a prior savepoint)
 * before another attempt to call into PostgreSQL routines.
 * @author Thomas Hallgren
 */
public class UnhandledPGException extends SQLException
{
	private static final long serialVersionUID = 1L;

	private static UnhandledPGException obtain()
	{
		assert threadMayEnterPG() : "UnhandledPGException.create thread";

		SQLException e = s_unhandled;

		if ( e instanceof UnhandledPGException )
			return (UnhandledPGException)e;
		else if ( ! (e instanceof ServerException) )
			throw new AssertionError("unexpected s_unhandled");

		e = new UnhandledPGException((ServerException)e);

		StackTraceElement[] es = e.getStackTrace();
		if ( null != es  &&  0 < es.length )
			e.setStackTrace(copyOfRange(es, 1, es.length));

		return (UnhandledPGException)(s_unhandled = e);
	}

	private UnhandledPGException(ServerException e)
	{
		super(
			"an earlier PostgreSQL exception (see Caused by:) prevents " +
			"further calls into PostgreSQL until rollback of this " +
			"transaction or a subtransaction / savepoint", "25P02", e);
	}
}
