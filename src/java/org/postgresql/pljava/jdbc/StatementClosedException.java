/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
		super("Statemen is closed", INVALID_SQL_STATEMENT_NAME);
	}
}
