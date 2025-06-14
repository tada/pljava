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
 * A Java exception constructed over a PostgreSQL error report.
 * @author Thomas Hallgren
 */
public class ServerException extends SQLException
{
	private static final long serialVersionUID = 8812755938793744633L;

	private transient final ErrorData m_errorData;

	private static ServerException obtain(ErrorData errorData)
	{
		assert threadMayEnterPG() : "ServerException obtain() thread";

		ServerException e = new ServerException(errorData);

		StackTraceElement[] es = e.getStackTrace();
		if ( null != es  &&  0 < es.length )
			e.setStackTrace(copyOfRange(es, 1, es.length));

		if ( null == s_unhandled )
			s_unhandled = e;
		else
			s_unhandled.addSuppressed(e);
		return e;
	}

	private ServerException(ErrorData errorData)
	{
		super(errorData.getMessage(), errorData.getSqlState());
		m_errorData = errorData;
	}

	public final ErrorData getErrorData()
	{
		return m_errorData;
	}
}
