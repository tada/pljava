/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import org.postgresql.pljava.internal.AclId;

/**
 * Provides access to some useful routines in the PostgreSQL server.
 * @author Thomas Hallgren
 */
public class Server
{
	/**
	 * Return the current user.
	 */
	public static String getUserName()
	{
		return AclId.getUser().getName();
	}

	/**
	 * Return the session user.
	 */
	public static String getSessionUserName()
	{
		return AclId.getSessionUser().getName();
	}
}
