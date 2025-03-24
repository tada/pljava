/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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
	/**
	 * Returns the current session.
	 */
	public static Session current()
	throws SQLException
	{
		try
		{
			return Holder.s_session;
		}
		catch ( ExceptionInInitializerError e )
		{
			Throwable c = e.getCause();
			if ( c instanceof SQLException )
				throw (SQLException)c;
			throw e;
		}
	}

	private static class Holder
	{
		private static final Session s_session;

		static {
			try
			{
				s_session = load(
					Session.class.getModule().getLayer(), Session.class)
					.findFirst().orElseThrow(() -> new SQLException(
						"could not obtain PL/Java Session object"));
			}
			catch ( SQLException e )
			{
				throw new ExceptionInInitializerError(e);
			}
		}
	}
}
