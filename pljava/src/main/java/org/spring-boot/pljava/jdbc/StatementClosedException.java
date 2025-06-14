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
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;

/**
 * An {@code SQLException} specific to the case of attempted use of a
 * {@code Statement} that has been closed.
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class StatementClosedException extends SQLException
{
	private static final long serialVersionUID = 9108917755099200271L;

	public static final String INVALID_SQL_STATEMENT_NAME = "26000";

	public StatementClosedException()
	{
		super("Statement is closed", INVALID_SQL_STATEMENT_NAME);
	}
}
