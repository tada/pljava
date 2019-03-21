/*
 * Copyright (c) 2016 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Function
{
	public static Object create(ResultSet procTup, String langName)
	throws SQLException
	{
		System.err.println(procTup.getString(1)+" "+langName);
		return null;
	}
}
