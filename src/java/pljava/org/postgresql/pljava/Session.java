/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava;

/**
 * A Session maintains transaction coordinated in-memory data. The data
 * added since the last commit will be lost on a transaction rollback, i.e.
 * the Session state is synchronized with the transaction.
 * 
 * Please note that if nested objects (such as lists and maps) are stored
 * in the session, changes internal to those objects are not subject to
 * the session semantics since the session is unaware of them.
 * 
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

	/**
	 * Remove an attribute previously stored in the session. If
	 * no attribute is found, nothing happens.
	 * @param attributeName The name of the attribute.
	 */
	public void removeAttribute(String attributeName);

	/**
	 * Set an attribute to a value in the current session.
	 * @param attributeName
	 * @param value
	 */
	public void setAttribute(String attributeName, Object value);
}
