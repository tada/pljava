/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava;

/**
 * @author Thomas Hallgren
 */
public interface Session
{
	public Object getAttribute(String attributeName);

	/**
	 * Return the current user.
	 */
	public String getUserName();

	/**
	 * Return the session user.
	 */
	public String getSessionUserName();

	public void removeAttribute(String attributeName);

	public void setAttribute(String attributeName, Object value);
}
