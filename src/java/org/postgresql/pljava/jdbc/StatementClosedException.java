/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;

/**
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class StatementClosedException extends SQLException
{
	public static final String INVALID_SQL_STATEMENT_NAME = "26000";

	public StatementClosedException()
	{
		super("Statement is closed", INVALID_SQL_STATEMENT_NAME);
	}
}
