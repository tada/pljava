/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */
public class ServerException extends SQLException
{
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
