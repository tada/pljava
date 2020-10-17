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

import java.sql.SQLException;

/**
 * A Java exception constructed over a PostgreSQL error report.
 * @author Thomas Hallgren
 */
public class ServerException extends SQLException
{
	private static final long serialVersionUID = 8812755938793744633L;

	private transient final ErrorData m_errorData;

	public ServerException(ErrorData errorData)
	{
		super(errorData.getMessage(), errorData.getSqlState());
		m_errorData = errorData;
	}

	public final ErrorData getErrorData()
	{
		return m_errorData;
	}
}
