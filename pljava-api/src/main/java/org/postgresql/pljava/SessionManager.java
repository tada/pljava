/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava;

import java.sql.SQLException;

import static java.util.ServiceLoader.load;

/**
 * The SessionManager makes the current {@link Session} available to the
 * caller.
 * @author Thomas Hallgren
 */
public class SessionManager
{
	private static Session s_session;

	/**
	 * Returns the current session.
	 */
	public static Session current()
	throws SQLException
	{
		if(s_session == null)
		{
			s_session = load(
				Session.class.getModule().getLayer(), Session.class)
				.findFirst().orElseThrow(() -> new SQLException(
					"could not obtain PL/Java Session object"));
		}
		return s_session;
	}
}
