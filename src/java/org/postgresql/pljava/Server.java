/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import org.postgresql.pljava.internal.Backend;

/**
 * Provides access to some useful routines in the PostgreSQL server.
 * @author Thomas Hallgren
 */
public class Server
{
	private static Session s_session;

	public static Session getSession()
	{
		if(s_session == null)
		{
			s_session = new Session();
			Backend.addEOXactListener(s_session);
		}
		return s_session;
	}
}
